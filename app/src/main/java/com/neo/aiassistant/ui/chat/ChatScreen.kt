package com.neo.aiassistant.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.neo.aiassistant.ChatEffect
import com.neo.aiassistant.ChatIntent
import com.neo.aiassistant.ChatViewModel
import com.neo.aiassistant.ui.BeautifulModelMissingView
import com.neo.aiassistant.ui.ChatInputBar
import com.neo.aiassistant.ui.ChatTopBar
import com.neo.aiassistant.ui.DownloadProgressView
import com.neo.aiassistant.ui.MessageList
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onModelsClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val modelFile = File(context.filesDir, state.selectedModel)
    val listState = rememberLazyListState()

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

    Scaffold(
        topBar = {
            ChatTopBar(
                isInteractionEnabled = !state.isDownloading && modelFile.exists(),
                selectedModel = state.selectedModel,
                availableModels = state.localModels.map { it.name },
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
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
        ) {
            Column(Modifier.fillMaxSize()) {
                if (state.isDownloading) {
                    DownloadProgressView(state.selectedModel, state.downloadProgress ?: 0)
                } else if (!modelFile.exists()) {
                    BeautifulModelMissingView(
                        selectedModel = state.selectedModel,
                        localModels = state.localModels,
                        remoteModels = state.remoteModels,
                        availableDownloads = state.availableDownloads,
                        catalogState = state.catalogState,
                        metrics = state.metrics,
                        onDownloadClick = { modelName ->
                            val isDownloaded = state.localModels.any { it.name == modelName }
                            if (isDownloaded) {
                                viewModel.onIntent(ChatIntent.SwitchModel(modelName, context.filesDir.absolutePath))
                            } else {
                                viewModel.onIntent(ChatIntent.DownloadModel(modelName, context.filesDir.absolutePath))
                            }
                        },
                        onClearError = {
                            viewModel.onIntent(ChatIntent.ClearError)
                        }
                    )
                } else {
                    MessageList(
                        messages = state.messages,
                        modifier = Modifier.weight(1f),
                        listState = listState
                    )

                    ChatInputBar(
                        text = state.inputText,
                        onTextChange = { viewModel.onIntent(ChatIntent.UpdateInputText(it)) },
                        onSend = { viewModel.onIntent(ChatIntent.SendMessage(state.inputText, state.selectedImageUri)) },
                        onGalleryClick = { imagePickerLauncher.launch("image/*") },
                        onCameraClick = { /* Handle camera */ },
                        selectedImageUri = state.selectedImageUri,
                        onRemoveImage = { viewModel.onIntent(ChatIntent.SelectImage(null)) },
                        enabled = !state.isLoading
                    )
                }
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
                    // Show snackbar or toast
                }
            }
        }
    }
}
