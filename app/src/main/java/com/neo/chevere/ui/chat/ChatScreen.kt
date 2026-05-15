package com.neo.chevere.ui.chat

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.neo.chevere.R
import com.neo.chevere.core.Constants
import com.neo.chevere.data.agent.AgentState
import com.neo.chevere.domain.ModelCapability
import com.neo.chevere.domain.ModelTaskType
import com.neo.chevere.ui.chat.components.ActionConfirmationDialog
import com.neo.chevere.ui.chat.components.AgeVerificationDialog
import com.neo.chevere.ui.chat.components.ChatInputBar
import com.neo.chevere.ui.chat.components.ChatTopBar
import com.neo.chevere.ui.chat.components.MessageList
import com.neo.chevere.ui.chat.components.ModelInitializationScreen
import com.neo.chevere.ui.common.ChevereHaptic
import com.neo.chevere.ui.common.ErrorSnackbar
import com.neo.chevere.ui.common.hapticForFeedbackMessage
import com.neo.chevere.ui.common.performChevereHaptic
import com.neo.chevere.ui.designsystem.Typography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                statusMessage = (state.runtimeState as? RuntimeState.Initializing)?.message
                    ?: "INITIALIZING..."
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
    val hapticView = LocalView.current
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showImageModelDownloadPrompt by remember { mutableStateOf(false) }
    var wasAiBusy by remember { mutableStateOf(false) }
    val showOnboarding = state.localModels.isEmpty() && !state.isLoading
    val isAiBusy = state.sendState is SendState.Sending ||
            state.sendState is SendState.GeneratingImage ||
            state.agentState is AgentState.Planning ||
            state.agentState is AgentState.ExecutingTool
    val inputBusyMessage = when {
        state.sendState is SendState.GeneratingImage -> Constants.UiStatus.GENERATING_IMAGE
        else -> Constants.UiStatus.THINKING
    }

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
            Toast.makeText(
                context,
                "Camera permission is required to take photos.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Scaffold(
        topBar = {
            ChatTopBar(
                isInteractionEnabled = state.isReady,
                isChatReady = state.localModels.any {
                    it.isHealthy && it.taskType != ModelTaskType.IMAGE_GENERATION
                },
                isImageReady = state.localModels.any {
                    it.isHealthy && (it.taskType == ModelTaskType.IMAGE_GENERATION || ModelCapability.IMAGE_GEN in it.capabilities)
                },
                onClearChat = {
                    hapticView.performChevereHaptic(ChevereHaptic.Warning)
                    viewModel.onIntent(ChatIntent.ClearConversation)
                },
                onModelsClick = {
                    hapticView.performChevereHaptic(ChevereHaptic.Selection)
                    onModelsClick()
                },
                onSettingsClick = {
                    hapticView.performChevereHaptic(ChevereHaptic.Selection)
                    onSettingsClick()
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                ErrorSnackbar(state.error ?: data.visuals.message) {
                    viewModel.onIntent(ChatIntent.ClearError)
                }
            }
        },
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentWindowInsets = WindowInsets.statusBars
    ) { innerPadding ->
        val glassBackground = Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
                MaterialTheme.colorScheme.background,
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.86f)
            )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(glassBackground)
                .padding(innerPadding)
                .imePadding()
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (showOnboarding) {
                    EmptyModelState(onModelsClick)
                } else {
                    MessageList(
                        messages = state.messages,
                        listState = listState,
                        onToggleExplicitImageMask = { index ->
                            hapticView.performChevereHaptic(ChevereHaptic.Selection)
                            viewModel.onIntent(ChatIntent.ToggleExplicitImageMask(index))
                        },
                        onShareMessage = { index ->
                            hapticView.performChevereHaptic(ChevereHaptic.Action)
                            viewModel.onIntent(ChatIntent.ShareMessage(index))
                        },
                        onSaveImage = { index ->
                            hapticView.performChevereHaptic(ChevereHaptic.Action)
                            viewModel.onIntent(ChatIntent.SaveImage(index))
                        }
                    )
                }

                if (state.isWaitingForConfirmation) {
                    ActionConfirmationDialog(
                        message = state.confirmationMessage ?: "Are you sure you want to proceed?",
                        onConfirm = {
                            hapticView.performChevereHaptic(ChevereHaptic.Success)
                            viewModel.onIntent(ChatIntent.ConfirmAction)
                        },
                        onDismiss = {
                            hapticView.performChevereHaptic(ChevereHaptic.Warning)
                            viewModel.onIntent(ChatIntent.CancelAction)
                        }
                    )
                }

                if (state.ageVerificationRequest != null) {
                    AgeVerificationDialog(
                        onSubmit = { year, month, day ->
                            hapticView.performChevereHaptic(ChevereHaptic.Action)
                            viewModel.onIntent(ChatIntent.SubmitBirthdate(year, month, day))
                        },
                        onDismiss = {
                            hapticView.performChevereHaptic(ChevereHaptic.Warning)
                            viewModel.onIntent(ChatIntent.DismissAgeVerification)
                        }
                    )
                }

                if (showImageModelDownloadPrompt) {
                    AlertDialog(
                        onDismissRequest = { showImageModelDownloadPrompt = false },
                        title = { Text("Download image model?") },
                        text = {
                            Text("Chevere AI needs a local image generation model before it can create images on this device.")
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    hapticView.performChevereHaptic(ChevereHaptic.Selection)
                                    showImageModelDownloadPrompt = false
                                    onModelsClick()
                                }
                            ) {
                                Text("Open Models")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                hapticView.performChevereHaptic(ChevereHaptic.Warning)
                                showImageModelDownloadPrompt = false
                            }) {
                                Text("Not now")
                            }
                        }
                    )
                }
            }

            if (!showOnboarding) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .navigationBarsPadding()
                ) {
                    ChatInputBar(
                        text = state.inputText,
                        onTextChange = { viewModel.onIntent(ChatIntent.UpdateInputText(it)) },
                        onSend = {
                            hapticView.performChevereHaptic(ChevereHaptic.Action)
                            viewModel.onIntent(
                                ChatIntent.SendMessage(
                                    state.inputText,
                                    state.selectedImageUri
                                )
                            )
                        },
                        onStop = {
                            hapticView.performChevereHaptic(ChevereHaptic.Warning)
                            viewModel.onIntent(ChatIntent.StopResponse)
                        },
                        onGalleryClick = {
                            hapticView.performChevereHaptic(ChevereHaptic.Selection)
                            imagePickerLauncher.launch("image/*")
                        },
                        onCameraClick = {
                            hapticView.performChevereHaptic(ChevereHaptic.Selection)
                            when (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            )) {
                                PackageManager.PERMISSION_GRANTED -> {
                                    val timeStamp = SimpleDateFormat(
                                        "yyyyMMdd_HHmmss",
                                        Locale.US
                                    ).format(Date())
                                    val storageDir =
                                        File(context.cacheDir, "images").apply { mkdirs() }
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
                        onRemoveImage = {
                            hapticView.performChevereHaptic(ChevereHaptic.Warning)
                            viewModel.onIntent(ChatIntent.SelectImage(null))
                        },
                        enabled = state.isReady && !state.isLoading,
                        isBusy = isAiBusy,
                        busyMessage = inputBusyMessage
                    )
                }
            }
        }
    }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ChatEffect.ScrollToBottom -> {
                    val lastIndex = listState.layoutInfo.totalItemsCount - 1
                    if (lastIndex >= 0) {
                        listState.animateScrollToItem(lastIndex)
                    }
                }

                is ChatEffect.HideKeyboard -> {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }

                is ChatEffect.ShowToast -> {
                    hapticView.performChevereHaptic(effect.message.hapticForFeedbackMessage())
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }

                is ChatEffect.ShareMessage -> {
                    try {
                        shareChatMessage(context, effect)
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.share_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                is ChatEffect.SaveImage -> {
                    val saved = runCatching {
                        withContext(Dispatchers.IO) {
                            saveImageToGallery(context, effect.imageUri)
                        }
                    }
                        .getOrDefault(false)
                    Toast.makeText(
                        context,
                        if (saved) context.getString(R.string.image_saved) else context.getString(R.string.image_save_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                    hapticView.performChevereHaptic(if (saved) ChevereHaptic.Success else ChevereHaptic.Warning)
                }

                ChatEffect.ShowImageModelDownloadPrompt -> {
                    hapticView.performChevereHaptic(ChevereHaptic.Warning)
                    showImageModelDownloadPrompt = true
                }
            }
        }
    }

    LaunchedEffect(isAiBusy, state.error, state.messages.size) {
        if (wasAiBusy && !isAiBusy) {
            hapticView.performChevereHaptic(
                if (state.error == null) ChevereHaptic.Success else ChevereHaptic.Warning
            )
        }
        wasAiBusy = isAiBusy
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            val lastIndex = state.messages.lastIndex
            listState.animateScrollToItem(lastIndex)
            delay(180)
            listState.animateScrollToItem(lastIndex)
        }
    }
}

private fun shareChatMessage(context: Context, effect: ChatEffect.ShareMessage) {
    val imageUri = effect.imageUri
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = imageUri?.let { context.contentResolver.getType(it) ?: "image/*" } ?: "text/plain"
        putExtra(Intent.EXTRA_TEXT, effect.text)
        imageUri?.let { uri ->
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "Chevere AI image", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    imageUri?.let { uri ->
        val targets = context.packageManager.queryIntentActivities(
            sendIntent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        targets.forEach { target ->
            context.grantUriPermission(
                target.activityInfo.packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    val chooser = Intent.createChooser(sendIntent, effect.title).apply {
        imageUri?.let { uri ->
            clipData = ClipData.newUri(context.contentResolver, "Chevere AI image", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    context.startActivity(chooser)
}

private fun saveImageToGallery(context: Context, sourceUri: Uri): Boolean {
    val resolver = context.contentResolver
    val mimeType = resolver.getType(sourceUri) ?: "image/png"
    val extension = when (mimeType) {
        "image/jpeg" -> "jpg"
        "image/webp" -> "webp"
        else -> "png"
    }
    val values = ContentValues().apply {
        put(
            MediaStore.Images.Media.DISPLAY_NAME,
            "chevere_ai_${System.currentTimeMillis()}.$extension"
        )
        put(MediaStore.Images.Media.MIME_TYPE, mimeType)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Chevere AI")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val destinationUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: return false

    return try {
        resolver.openInputStream(sourceUri).use { input ->
            resolver.openOutputStream(destinationUri).use { output ->
                if (input == null || output == null) throw IOException("Could not open image streams")
                input.copyTo(output)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(destinationUri, values, null, null)
        }
        true
    } catch (_: Exception) {
        resolver.delete(destinationUri, null, null)
        false
    }
}

@Composable
private fun EmptyModelState(onModelsClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(88.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
            contentColor = MaterialTheme.colorScheme.primary,
            shape = MaterialTheme.shapes.extraLarge,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)),
            shadowElevation = 8.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(42.dp)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.onboarding_title),
            style = Typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            letterSpacing = 0.sp
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = stringResource(R.string.onboarding_subtitle),
            style = Typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(Modifier.height(24.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OnboardingPoint(
                icon = Icons.Default.Security,
                title = stringResource(R.string.onboarding_privacy_title),
                body = stringResource(R.string.onboarding_privacy_body)
            )
            OnboardingPoint(
                icon = Icons.Default.Memory,
                title = stringResource(R.string.onboarding_heavy_title),
                body = stringResource(R.string.onboarding_heavy_body)
            )
            OnboardingPoint(
                icon = Icons.Default.Image,
                title = stringResource(R.string.onboarding_image_title),
                body = stringResource(R.string.onboarding_image_body)
            )
            OnboardingPoint(
                icon = Icons.Default.CloudOff,
                title = stringResource(R.string.onboarding_offline_title),
                body = stringResource(R.string.onboarding_offline_body)
            )
        }

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = onModelsClick,
            shape = MaterialTheme.shapes.large,
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Download, null)
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.onboarding_download_cta),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.onboarding_footer),
            style = Typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun OnboardingPoint(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f)),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.64f),
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = Typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = body,
                    style = Typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }

}
