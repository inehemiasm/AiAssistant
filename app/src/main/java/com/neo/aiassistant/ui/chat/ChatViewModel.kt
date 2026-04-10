package com.neo.aiassistant.ui.chat

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.neo.aiassistant.core.BaseViewModel
import com.neo.aiassistant.data.PreferenceManager
import com.neo.aiassistant.domain.ChatMessage
import com.neo.aiassistant.domain.ChatRepository
import com.neo.aiassistant.domain.InitializeChatUseCase
import com.neo.aiassistant.domain.SendMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import kotlin.system.measureTimeMillis

/**
 * ViewModel for the Chat screen.
 *
 * Manages the UI state for the chat interface, handles user intents,
 * and coordinates with use cases for model initialization and message processing.
 *
 * @property application The application context.
 * @property repository The repository for chat and model data.
 * @property initializeChatUseCase Use case for initializing the AI model.
 * @property sendMessageUseCase Use case for sending messages to the AI.
 * @property preferenceManager Manages user preferences, including the selected model.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val application: Application,
    private val repository: ChatRepository,
    private val initializeChatUseCase: InitializeChatUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val preferenceManager: PreferenceManager
) : BaseViewModel<ChatState, ChatIntent, ChatEffect>(application, ChatState()) {

    init {
        viewModelScope.launch {
            // First, load what models we have locally
            updateLocalModels()
            
            observeSelectedModel()
            observeInitStatus()
            observeAgentStatus()
            
            // Determine which model to initialize
            val savedModel = preferenceManager.selectedModelPreference.first()
            val modelToLoad = savedModel ?: currentState.localModels.firstOrNull()?.fileName
            
            if (modelToLoad != null) {
                if (savedModel == null) {
                    preferenceManager.updateSelectedModel(modelToLoad)
                }
                setState { copy(selectedModel = modelToLoad) }
                val modelFile = File(application.filesDir, modelToLoad)
                if (modelFile.exists()) {
                    initModel(modelFile.absolutePath)
                }
            }
        }
    }

    private fun observeSelectedModel() {
        viewModelScope.launch {
            preferenceManager.selectedModelPreference.collectLatest { savedModel ->
                if (savedModel != null && savedModel != currentState.selectedModel) {
                    setState { copy(selectedModel = savedModel) }
                    // If the model changed in preferences (e.g. from Models screen), re-init here
                    val modelFile = File(application.filesDir, savedModel)
                    if (modelFile.exists()) {
                        initModel(modelFile.absolutePath)
                    }
                }
            }
        }
    }

    private fun observeAgentStatus() {
        viewModelScope.launch {
            repository.agentState.collectLatest { state ->
                setState { copy(agentState = state) }
            }
        }
    }

    private fun observeInitStatus() {
        viewModelScope.launch {
            repository.getInitStatus().collectLatest { status ->
                Log.d("ChatViewModel", "Init status update: $status")
                if (status == "READY") {
                    setState { copy(runtimeState = RuntimeState.Ready) }
                } else if (status.contains("FAILED")) {
                    setState { copy(runtimeState = RuntimeState.Error(status)) }
                } else if (status.isNotBlank()) {
                    setState { copy(runtimeState = RuntimeState.Initializing(status)) }
                }
            }
        }
    }

    private suspend fun updateLocalModels() {
        val models = repository.getLocalModels()
        setState { copy(localModels = models) }
    }

    override suspend fun handleIntent(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.Initialize -> initModel(intent.modelPath)
            is ChatIntent.SendMessage -> sendMessage(intent.text, intent.imageUri)
            is ChatIntent.SwitchModel -> {
                if (currentState.selectedModel == intent.modelName && currentState.runtimeState is RuntimeState.Ready) return
                
                Log.d("ChatViewModel", "Switching model to: ${intent.modelName}")
                setState { copy(selectedModel = intent.modelName, messages = emptyList(), runtimeState = RuntimeState.Uninitialized) }
                preferenceManager.updateSelectedModel(intent.modelName)
                
                // The actual init will be triggered by the observeSelectedModel collector
            }
            ChatIntent.ClearError -> setState { copy(
                runtimeState = if (currentState.runtimeState is RuntimeState.Error) RuntimeState.Uninitialized else currentState.runtimeState,
                sendState = SendState.Idle
            ) }
            ChatIntent.ClearConversation -> clearConversation()
            is ChatIntent.UpdateInputText -> setState { copy(inputText = intent.text) }
            is ChatIntent.SelectImage -> setState { copy(selectedImageUri = intent.uri) }
            is ChatIntent.SetTempCameraUri -> setState { copy(tempCameraUri = intent.uri) }
        }
    }

    private fun clearConversation() {
        viewModelScope.launch {
            repository.clearConversation()
            setState { copy(messages = emptyList()) }
        }
    }

    private suspend fun initModel(modelPath: String) {
        Log.d("ChatViewModel", "Starting model initialization: $modelPath")
        setState { copy(runtimeState = RuntimeState.Initializing("SYNTHESIZING...")) }
        initializeChatUseCase(modelPath)
            .onFailure { e ->
                Log.e("ChatViewModel", "Initialization failed", e)
                setState { copy(runtimeState = RuntimeState.Error("Init failed: ${e.message}")) }
            }
    }

    private suspend fun sendMessage(text: String, imageUri: Uri?) {
        val userMsg = ChatMessage(text, isUser = true, imageUri = imageUri?.toString())
        setState { copy(messages = messages + userMsg, sendState = SendState.Sending, inputText = "", selectedImageUri = null) }
        sendEffect { ChatEffect.ScrollToBottom }

        var responseText = ""
        val time = measureTimeMillis {
            sendMessageUseCase(text, imageUri)
                .onSuccess { responseText = it }
                .onFailure { e ->
                    setState { copy(sendState = SendState.Error(e.message ?: "Inference failed")) }
                    return
                }
        }

        val aiMsg = ChatMessage(
            text = responseText,
            isUser = false,
            inferenceTimeMs = time,
            modelName = currentState.selectedModel.replace(".litertlm", "").uppercase()
        )
        setState { copy(messages = messages + aiMsg, sendState = SendState.Idle) }
        sendEffect { ChatEffect.ScrollToBottom }
    }
}
