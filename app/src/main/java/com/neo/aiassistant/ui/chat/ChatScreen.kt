package com.neo.aiassistant.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.neo.aiassistant.CatalogState
import com.neo.aiassistant.ChatEffect
import com.neo.aiassistant.ChatIntent
import com.neo.aiassistant.ChatViewModel
import com.neo.aiassistant.DownloadState
import com.neo.aiassistant.RuntimeState
import com.neo.aiassistant.SendState
import com.neo.aiassistant.ui.BeautifulModelMissingView
import com.neo.aiassistant.ui.ChatInputBar
import com.neo.aiassistant.ui.DownloadProgressView
import com.neo.aiassistant.ui.ErrorSnackbar
import com.neo.aiassistant.ui.FuturisticTopBar
import com.neo.aiassistant.ui.MessageList
import com.neo.aiassistant.ui.QuantumThinkingIndicator
import java.io.File
import java.util.UUID

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onSettingsClick: () -> Unit,
    onModelsClick: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) selectedImageUri = uri
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            selectedImageUri = tempCameraUri
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val file = File(context.cacheDir, "images/${UUID.randomUUID()}.jpg")
            file.parentFile?.mkdirs()
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            tempCameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            Toast.makeText(context, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    val onCameraClickAction = {
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) -> {
                val file = File(context.cacheDir, "images/${UUID.randomUUID()}.jpg")
                file.parentFile?.mkdirs()
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                tempCameraUri = uri
                cameraLauncher.launch(uri)
            }
            else -> {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    val modelFile = File(context.filesDir, state.selectedModel)

    LaunchedEffect(Unit) {
        if (state.runtimeState is RuntimeState.Uninitialized && modelFile.exists()) {
            viewModel.onIntent(ChatIntent.Initialize(modelFile.absolutePath))
        }
        
        viewModel.effect.collect { effect ->
            when (effect) {
                ChatEffect.ScrollToBottom -> {
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

    Scaffold(
        topBar = {
            FuturisticTopBar(
                isInteractionEnabled = state.downloadState is DownloadState.Idle && state.catalogState is CatalogState.Idle,
                selectedModel = state.selectedModel,
                availableModels = state.availableModels.keys.toList(),
                onModelSelected = { modelName ->
                    viewModel.onIntent(ChatIntent.SwitchModel(modelName, context.filesDir.absolutePath))
                },
                onClearChat = {
                    viewModel.onIntent(ChatIntent.ClearConversation)
                },
                onSettingsClick = onSettingsClick
            )
        },
        bottomBar = {
            // Only show input bar if model exists and not downloading
            if (!state.isDownloading && modelFile.exists()) {
                ChatInputBar(
                    text = inputText,
                    onTextChange = { inputText = it },
                    onSend = {
                        viewModel.onIntent(ChatIntent.SendMessage(inputText, selectedImageUri))
                        inputText = ""
                        selectedImageUri = null
                    },
                    selectedImageUri = selectedImageUri,
                    onGalleryClick = { imagePicker.launch("image/*") },
                    onCameraClick = onCameraClickAction,
                    onRemoveImage = { selectedImageUri = null },
                    enabled = state.isReady && state.sendState is SendState.Idle && state.downloadState is DownloadState.Idle,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .imePadding()
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.isDownloading) {
                DownloadProgressView(state.selectedModel, state.downloadProgress ?: 0)
            } else if (!modelFile.exists()) {
                BeautifulModelMissingView(
                    selectedModel = state.selectedModel,
                    localModels = state.localModels,
                    catalogState = state.catalogState,
                    metrics = state.metrics,
                    onDownloadClick = { modelName ->
                        viewModel.onIntent(ChatIntent.DownloadModel(modelName, context.filesDir.absolutePath))
                    },
                    onClearError = {
                        viewModel.onIntent(ChatIntent.ClearError)
                    }
                )
            } else {
                Column(Modifier.fillMaxSize()) {
                    MessageList(
                        messages = state.messages,
                        modifier = Modifier.weight(1f),
                        listState = listState
                    )
                    
                    QuantumThinkingIndicator(
                        visible = state.isLoading,
                        statusMessage = state.loadingMessage
                    )
                }
            }

            if (state.error != null && modelFile.exists()) {
                ErrorSnackbar(state.error!!) {
                    viewModel.onIntent(ChatIntent.ClearError)
                }
            }
        }
    }
}
