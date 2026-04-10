package com.neo.aiassistant.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.neo.aiassistant.ui.chat.components.ChatInputBar
import com.neo.aiassistant.ui.chat.components.ChatTopBar
import com.neo.aiassistant.ui.chat.components.MessageList
import com.neo.aiassistant.ui.chat.components.QuantumThinkingIndicator
import com.neo.aiassistant.ui.common.ErrorSnackbar
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onModelsClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val modelFile = File(context.filesDir, state.selectedModel)
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

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

    LaunchedEffect(state.selectedModel) {
        if (state.runtimeState is RuntimeState.Uninitialized && modelFile.exists()) {
            viewModel.onIntent(ChatIntent.Initialize(modelFile.absolutePath))
        }
    }

    Scaffold(
        topBar = {
            Box(modifier = Modifier.padding(WindowInsets.statusBars.asPaddingValues())) {
                ChatTopBar(
                    isInteractionEnabled = state.isReady,
                    selectedModel = state.selectedModel,
                    availableModels = emptyList(),
                    onModelSelected = { modelName ->
                        viewModel.onIntent(ChatIntent.SwitchModel(modelName, context.filesDir.absolutePath))
                    },
                    onClearChat = {
                        viewModel.onIntent(ChatIntent.ClearConversation)
                    },
                    onModelsClick = onModelsClick,
                    onSettingsClick = onSettingsClick
                )
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                ErrorSnackbar(state.error ?: data.visuals.message) {
                    viewModel.onIntent(ChatIntent.ClearError)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            Box(modifier = Modifier.weight(1f)) {
                MessageList(
                    messages = state.messages,
                    listState = listState
                )

                QuantumThinkingIndicator(
                    visible = state.isLoading,
                    statusMessage = state.loadingMessage,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                )
            }

            // THE SMART UNION FIX:
            // We use the union of ime and navigationBars. 
            // When the keyboard is up, IME covers navigationBars, so union takes the larger value.
            // When closed, it takes the navigationBars value.
            // This prevents adding them together (double padding).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.ime.union(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom)
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp)
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
                    enabled = !state.isLoading && state.isReady
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
                is ChatEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
