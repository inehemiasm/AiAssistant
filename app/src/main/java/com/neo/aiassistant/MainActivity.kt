package com.neo.aiassistant

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.neo.aiassistant.ui.*
import com.neo.aiassistant.ui.theme.AiAssistantTheme
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.util.UUID

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AiAssistantTheme {
                ChatScreen(viewModel)
            }
        }
    }
}

@Composable
fun ChatScreen(viewModel: ChatViewModel) {
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

    val modelFile = File(context.filesDir, state.selectedModel)

    LaunchedEffect(Unit) {
        if (!state.isReady && !state.isLoading && modelFile.exists()) {
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
                isThinking = state.isLoading,
                selectedModel = state.selectedModel,
                availableModels = state.availableModels.keys.toList(),
                onModelSelected = { modelName ->
                    viewModel.onIntent(ChatIntent.SwitchModel(modelName, context.filesDir.absolutePath))
                }
            )
        },
        bottomBar = {
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
                onCameraClick = {
                    val file = File(context.cacheDir, "images/${UUID.randomUUID()}.jpg")
                    file.parentFile?.mkdirs()
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    tempCameraUri = uri
                    cameraLauncher.launch(uri)
                },
                onRemoveImage = { selectedImageUri = null },
                enabled = state.isReady && !state.isLoading && !state.isDownloading
            )
        },
        containerColor = Color(0xFF0B0E14)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (state.isDownloading) {
                DownloadProgressView(state.selectedModel, state.downloadProgress ?: 0)
            } else if (!modelFile.exists()) {
                BeautifulModelMissingView(
                    selectedModel = state.selectedModel,
                    isFetching = state.isFetchingModels,
                    error = state.error,
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
                        isThinking = state.isLoading,
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
