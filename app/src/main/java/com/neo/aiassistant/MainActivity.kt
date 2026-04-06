package com.neo.aiassistant

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.neo.aiassistant.data.PreferenceManager
import com.neo.aiassistant.ui.BeautifulModelMissingView
import com.neo.aiassistant.ui.ChatInputBar
import com.neo.aiassistant.ui.DownloadProgressView
import com.neo.aiassistant.ui.ErrorSnackbar
import com.neo.aiassistant.ui.FuturisticTopBar
import com.neo.aiassistant.ui.MessageList
import com.neo.aiassistant.ui.QuantumThinkingIndicator
import com.neo.aiassistant.ui.SettingsScreen
import com.neo.aiassistant.ui.designsystem.HighTechAiTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: ChatViewModel by viewModels()
    
    @Inject
    lateinit var preferenceManager: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDarkMode by preferenceManager.themePreference.collectAsState(initial = true)
            
            HighTechAiTheme(darkTheme = isDarkMode) {
                var currentScreen by remember { mutableStateOf(Screen.Chat) }
                
                when (currentScreen) {
                    Screen.Chat -> {
                        ChatScreen(
                            viewModel = viewModel,
                            onSettingsClick = { currentScreen = Screen.Settings }
                        )
                    }
                    Screen.Settings -> {
                        val scope = rememberCoroutineScope()
                        SettingsScreen(
                            isDarkMode = isDarkMode,
                            onThemeChange = { scope.launch { preferenceManager.updateTheme(it) } },
                            onBackClick = { currentScreen = Screen.Chat }
                        )
                        BackHandler {
                            currentScreen = Screen.Chat
                        }
                    }
                }
            }
        }
    }
}

enum class Screen {
    Chat, Settings
}

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onSettingsClick: () -> Unit
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
                },
                onClearChat = {
                    viewModel.onIntent(ChatIntent.ClearConversation)
                },
                onSettingsClick = onSettingsClick
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
                },
                onRemoveImage = { selectedImageUri = null },
                enabled = state.isReady && !state.isLoading && !state.isDownloading
            )
        },
        containerColor = MaterialTheme.colorScheme.background
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
