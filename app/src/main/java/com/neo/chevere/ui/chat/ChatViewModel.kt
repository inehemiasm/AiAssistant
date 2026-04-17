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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.system.measureTimeMillis

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
        // 1. Observe global initialization status from the repository
        observeInitStatus()
        
        // 2. Observe agent state for tool executions
        observeAgentStatus()
        
        // 3. React to model selection changes (Source of Truth)
        observeSelectedModel()
        
        // 4. Initial load of local models metadata
        viewModelScope.launch {
            updateLocalModels()
        }
    }

    private fun observeSelectedModel() {
        viewModelScope.launch {
            // We don't drop(1) here. We want to initialize whatever is currently saved 
            // as soon as the ViewModel starts.
            preferenceManager.selectedModelPreference.collectLatest { savedModel ->
                val currentModel = currentState.selectedModel
                
                // If there's a saved model and it's different from our current state, 
                // or if we aren't ready yet, trigger initialization.
                if (savedModel != null && (savedModel != currentModel || currentState.runtimeState is RuntimeState.Uninitialized)) {
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
                    // Just update the preference. observeSelectedModel will handle the rest reactively.
                    withContext(dispatcherProvider.io) {
                        preferenceManager.updateSelectedModel(intent.modelName)
                    }
                    setState { copy(messages = emptyList(), runtimeState = RuntimeState.Uninitialized) }
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
        initJob?.cancel()
        initJob = viewModelScope.launch {
            sendEffect { ChatEffect.HideKeyboard }
            // State will be updated to Initializing/Ready via observeInitStatus flow
            withContext(dispatcherProvider.default) {
                initializeChatUseCase(modelPath)
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
