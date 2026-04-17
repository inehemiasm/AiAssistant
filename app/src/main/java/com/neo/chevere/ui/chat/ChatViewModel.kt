package com.neo.chevere.ui.chat

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.neo.chevere.core.BaseViewModel
import com.neo.chevere.core.DispatcherProvider
import com.neo.chevere.data.PreferenceManager
import com.neo.chevere.domain.ChatMessage
import com.neo.chevere.domain.ChatRepository
import com.neo.chevere.domain.InitializationStatus
import com.neo.chevere.domain.InitializeChatUseCase
import com.neo.chevere.domain.SendMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.system.measureTimeMillis

/**
 * ViewModel for the Chat screen.
 *
 * Manages the UI state for the chat interface, handles user intents,
 * and coordinates with use cases for model initialization and message processing.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val application: Application,
    private val repository: ChatRepository,
    private val initializeChatUseCase: InitializeChatUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val preferenceManager: PreferenceManager,
    private val dispatcherProvider: DispatcherProvider
) : BaseViewModel<ChatState, ChatIntent, ChatEffect>(application, ChatState()) {

    private val intentMutex = Mutex()
    private var initJob: Job? = null

    init {
        viewModelScope.launch {
            // Load local models first (fast IO)
            updateLocalModels()
            
            // Observe status and agent state (passive)
            observeInitStatus()
            observeAgentStatus()
            
            // Small initial delay to avoid collision with splash screen startup animation
            delay(500)
            
            // Now start observing the selected model and initialize it
            observeSelectedModel()
            
            // Initial load of the saved model
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
            // Drop(1) to skip the initial value which we handle in the init block
            preferenceManager.selectedModelPreference.drop(1).collectLatest { savedModel ->
                if (savedModel != null && savedModel != currentState.selectedModel) {
                    setState { copy(selectedModel = savedModel) }
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
                val runtimeState = when (status) {
                    is InitializationStatus.Ready -> RuntimeState.Ready
                    is InitializationStatus.Uninitialized -> RuntimeState.Uninitialized
                    is InitializationStatus.Failure -> RuntimeState.Error(status.message)
                    is InitializationStatus.Initializing -> RuntimeState.Initializing(status.message)
                }
                setState { copy(runtimeState = runtimeState) }
            }
        }
    }

    private suspend fun updateLocalModels() {
        val models = withContext(dispatcherProvider.io) {
            repository.getLocalModels()
        }
        setState { copy(localModels = models) }
    }

    override suspend fun handleIntent(intent: ChatIntent) {
        intentMutex.withLock {
            when (intent) {
                is ChatIntent.Initialize -> initModel(intent.modelPath)
                is ChatIntent.SendMessage -> {
                    if (currentState.isLoading) return@withLock
                    sendMessage(intent.text, intent.imageUri)
                }
                is ChatIntent.SwitchModel -> {
                    if (currentState.isLoading) return@withLock
                    if (currentState.selectedModel == intent.modelName && currentState.runtimeState is RuntimeState.Ready) return@withLock
                    
                    setState { copy(selectedModel = intent.modelName, messages = emptyList(), runtimeState = RuntimeState.Uninitialized) }
                    withContext(dispatcherProvider.io) {
                        preferenceManager.updateSelectedModel(intent.modelName)
                    }
                }
                ChatIntent.ClearError -> setState { copy(
                    runtimeState = if (currentState.runtimeState is RuntimeState.Error) RuntimeState.Uninitialized else currentState.runtimeState,
                    sendState = SendState.Idle
                ) }
                ChatIntent.ClearConversation -> clearConversation()
                is ChatIntent.UpdateInputText -> setState { copy(inputText = intent.text) }
                is ChatIntent.SelectImage -> setState { copy(selectedImageUri = intent.uri) }
                is ChatIntent.SetTempCameraUri -> setState { copy(tempCameraUri = intent.uri) }
                ChatIntent.ConfirmAction -> confirmAction()
                ChatIntent.CancelAction -> cancelAction()
            }
        }
    }

    private fun clearConversation() {
        viewModelScope.launch {
            withContext(dispatcherProvider.io) {
                repository.clearConversation()
            }
            setState { copy(messages = emptyList()) }
        }
    }

    private suspend fun initModel(modelPath: String) {
        // Cancel any previous init job to avoid race conditions
        initJob?.cancel()
        initJob = viewModelScope.launch {
            sendEffect { ChatEffect.HideKeyboard }
            val modelName = File(modelPath).nameWithoutExtension.uppercase()
            setState { copy(runtimeState = RuntimeState.Initializing("INITIALIZING $modelName...")) }
            
            // Use non-blocking default dispatcher for the heavy JNI calls
            val result = withContext(dispatcherProvider.default) {
                initializeChatUseCase(modelPath)
            }
            
            result.onFailure { e ->
                Log.e("ChatViewModel", "Model initialization failed: ${e.message}")
                setState { copy(runtimeState = RuntimeState.Error("Init failed: ${e.message}")) }
            }
        }
    }

    private suspend fun sendMessage(text: String, imageUri: Uri?) {
        sendEffect { ChatEffect.HideKeyboard }
        val userMsg = ChatMessage(text, isUser = true, imageUri = imageUri?.toString())
        setState { copy(messages = messages + userMsg, sendState = SendState.Sending, inputText = "", selectedImageUri = null) }
        sendEffect { ChatEffect.ScrollToBottom }

        processAgentTurn { 
            withContext(dispatcherProvider.default) {
                sendMessageUseCase(text, imageUri)
            }
        }
    }

    private suspend fun confirmAction() {
        sendEffect { ChatEffect.HideKeyboard }
        processAgentTurn { 
            withContext(dispatcherProvider.default) {
                repository.confirmAction()
            }
        }
    }

    private suspend fun cancelAction() {
        sendEffect { ChatEffect.HideKeyboard }
        processAgentTurn { 
            withContext(dispatcherProvider.default) {
                repository.cancelAction()
            }
        }
    }

    private suspend fun processAgentTurn(action: suspend () -> Result<String>) {
        var responseText = ""
        val time = measureTimeMillis {
            action()
                .onSuccess { responseText = it }
                .onFailure { e ->
                    setState { copy(sendState = SendState.Error(e.message ?: "Action failed")) }
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
