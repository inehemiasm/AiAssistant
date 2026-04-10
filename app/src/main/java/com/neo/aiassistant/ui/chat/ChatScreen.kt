package com.neo.aiassistant.ui.chat

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.neo.aiassistant.ChatEffect
import com.neo.aiassistant.ChatIntent
import com.neo.aiassistant.ChatViewModel
import com.neo.aiassistant.RuntimeState
import com.neo.aiassistant.ui.BeautifulModelMissingView
import com.neo.aiassistant.ui.ChatInputBar
import com.neo.aiassistant.ui.ChatTopBar
import com.neo.aiassistant.ui.DownloadProgressView
import com.neo.aiassistant.ui.ErrorSnackbar
import com.neo.aiassistant.ui.MessageList
import com.neo.aiassistant.ui.QuantumThinkingIndicator
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    LaunchedEffect(state.selectedModel) {
        if (state.runtimeState is RuntimeState.Uninitialized && modelFile.exists()) {
            viewModel.onIntent(ChatIntent.Initialize(modelFile.absolutePath))
        }
    }

    Scaffold(
        topBar = {
            ChatTopBar(
                isInteractionEnabled = !state.isDownloading && modelFile.exists(),
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
        contentWindowInsets = WindowInsets.systemBars
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
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
                            val isDownloaded = state.localModels.any { it.fileName == modelName }
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
                    Box(modifier = Modifier.weight(1f)) {
                        MessageList(
                            messages = state.messages,
                            modifier = Modifier.fillMaxSize(),
                            listState = listState
                        )

                        QuantumThinkingIndicator(
                            visible = state.isLoading,
                            statusMessage = state.loadingMessage,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
                        )
                    }

                    // Position input bar at the bottom with proper spacing and position-aware IME padding
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .positionAwareImePadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        ChatInputBar(
                            text = state.inputText,
                            onTextChange = { viewModel.onIntent(ChatIntent.UpdateInputText(it)) },
                            onSend = { viewModel.onIntent(ChatIntent.SendMessage(state.inputText, state.selectedImageUri)) },
                            onGalleryClick = { imagePickerLauncher.launch("image/*") },
                            onCameraClick = {
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
                            },
                            selectedImageUri = state.selectedImageUri,
                            onRemoveImage = { viewModel.onIntent(ChatIntent.SelectImage(null)) },
                            enabled = !state.isLoading && state.isReady
                        )
                    }
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
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
fun Int.pxToDp() = with(LocalDensity.current) { this@pxToDp.toDp() }

/**
 * A modifier that applies IME padding while accounting for the component's position
 * relative to the bottom of the screen (e.g., when it sits above a NavigationBar).
 * Source - https://stackoverflow.com/a/79048009
 */
fun Modifier.positionAwareImePadding() = composed {
    var consumePadding by remember { mutableIntStateOf(0) }
    this@positionAwareImePadding
        .onGloballyPositioned { coordinates ->
            val rootCoordinate = coordinates.findRootCoordinates()
            val bottom = coordinates.positionInWindow().y + coordinates.size.height + 60

            // Calculate how much space is already between this component and the screen bottom
            consumePadding = (rootCoordinate.size.height - bottom).toInt().coerceAtLeast(0)
        }
        .consumeWindowInsets(PaddingValues(bottom = consumePadding.pxToDp()))
        .imePadding()
}
