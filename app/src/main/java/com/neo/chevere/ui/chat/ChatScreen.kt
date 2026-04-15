package com.neo.chevere.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.neo.chevere.ui.chat.components.*
import com.neo.chevere.ui.common.ErrorSnackbar
import com.neo.chevere.ui.designsystem.Typography
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * The main Chat screen of the application.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onModelsClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    // Check if the model is initializing to show the full-screen loading state
    val isInitializing = state.runtimeState is RuntimeState.Initializing

    AnimatedContent(
        targetState = isInitializing,
        transitionSpec = {
            fadeIn().togetherWith(fadeOut())
        },
        label = "ChatScreenTransition"
    ) { initializing ->
        if (initializing) {
            ModelInitializationScreen(
                statusMessage = (state.runtimeState as? RuntimeState.Initializing)?.message ?: "INITIALIZING..."
            )
        } else {
            ChatContent(
                state = state,
                viewModel = viewModel,
                onModelsClick = onModelsClick,
                onSettingsClick = onSettingsClick
            )
        }
    }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ChatEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatContent(
    state: ChatState,
    viewModel: ChatViewModel,
    onModelsClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.onIntent(ChatIntent.SelectImage(uri))
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            viewModel.onIntent(ChatIntent.SelectImage(state.tempCameraUri))
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(context, "Permission granted. Try again.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Camera permission is required to take photos.", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            ChatTopBar(
                isInteractionEnabled = state.isReady,
                selectedModel = state.selectedModel,
                availableModels = state.localModels.map { it.fileName },
                onModelSelected = { modelName ->
                    viewModel.onIntent(ChatIntent.SwitchModel(modelName, context.filesDir.absolutePath))
                },
                onClearChat = {
                    viewModel.onIntent(ChatIntent.ClearConversation)
                },
                onModelsClick = onModelsClick,
                onSettingsClick = onSettingsClick
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                ErrorSnackbar(state.error ?: data.visuals.message) {
                    viewModel.onIntent(ChatIntent.ClearError)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.statusBars
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (state.localModels.isEmpty() && !state.isLoading) {
                    EmptyModelState(onModelsClick)
                } else {
                    MessageList(
                        messages = state.messages,
                        listState = listState
                    )
                }

                QuantumThinkingIndicator(
                    visible = state.isLoading && state.runtimeState !is RuntimeState.Initializing,
                    statusMessage = state.loadingMessage,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                )

                if (state.isWaitingForConfirmation) {
                    ActionConfirmationDialog(
                        message = state.confirmationMessage ?: "Are you sure you want to proceed?",
                        onConfirm = { viewModel.onIntent(ChatIntent.ConfirmAction) },
                        onDismiss = { viewModel.onIntent(ChatIntent.CancelAction) }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .navigationBarsPadding()
            ) {
                ChatInputBar(
                    text = state.inputText,
                    onTextChange = { viewModel.onIntent(ChatIntent.UpdateInputText(it)) },
                    onSend = { viewModel.onIntent(ChatIntent.SendMessage(state.inputText, state.selectedImageUri)) },
                    onGalleryClick = { imagePickerLauncher.launch("image/*") },
                    onCameraClick = {
                        when (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)) {
                            PackageManager.PERMISSION_GRANTED -> {
                                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                val storageDir = File(context.cacheDir, "images").apply { mkdirs() }
                                val photoFile = File(storageDir, "JPEG_${timeStamp}_.jpg")
                                val photoUri: Uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    photoFile
                                )
                                viewModel.onIntent(ChatIntent.SetTempCameraUri(photoUri))
                                cameraLauncher.launch(photoUri)
                            }
                            else -> {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    },
                    selectedImageUri = state.selectedImageUri,
                    onRemoveImage = { viewModel.onIntent(ChatIntent.SelectImage(null)) },
                    enabled = state.isReady && !state.isLoading
                )
            }
        }
    }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ChatEffect.ScrollToBottom -> {
                    if (state.messages.isNotEmpty()) {
                        listState.animateScrollToItem(state.messages.size - 1)
                    }
                }
                is ChatEffect.HideKeyboard -> {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun EmptyModelState(onModelsClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(80.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        Text(
            text = "Welcome to Chevere",
            style = Typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(8.dp))
        
        Text(
            text = "To start chatting, you need to download a local AI model first. All processing happens entirely on your device.",
            style = Typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(Modifier.height(32.dp))
        
        Button(
            onClick = onModelsClick,
            shape = MaterialTheme.shapes.large,
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)
        ) {
            Icon(Icons.Default.Download, null)
            Spacer(Modifier.width(12.dp))
            Text(
                "DOWNLOAD MODEL",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            )
        }
    }
}
