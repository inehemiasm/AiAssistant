package com.neo.aiassistant

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.neo.aiassistant.core.BaseViewModel
import com.neo.aiassistant.domain.ChatMessage
import com.neo.aiassistant.domain.ChatRepository
import com.neo.aiassistant.domain.DownloadProgress
import com.neo.aiassistant.domain.InitializeChatUseCase
import com.neo.aiassistant.domain.SendMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import kotlin.system.measureTimeMillis

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val application: Application,
    private val repository: ChatRepository,
    private val initializeChatUseCase: InitializeChatUseCase,
    private val sendMessageUseCase: SendMessageUseCase
) : BaseViewModel<ChatState, ChatIntent, ChatEffect>(application, ChatState()) {

    private val fallbackModels = mapOf(
        "gemma-4-E2B-it.litertlm" to "gs://aiassistant-88f75.firebasestorage.app/gemma-4-E2B-it.litertlm",
        "gemma-4-E4B-it.litertlm" to "gs://aiassistant-88f75.firebasestorage.app/gemma-4-E4B-it.litertlm"
    )

    init {
        checkLocalModels()
        onIntent(ChatIntent.FetchModels)
        observeInitStatus()
    }

    private fun observeInitStatus() {
        viewModelScope.launch {
            repository.getInitStatus().collectLatest { status ->
                setState { copy(loadingMessage = status) }
            }
        }
    }

    private fun checkLocalModels() {
        val e4b = File(application.filesDir, "gemma-4-E4B-it.litertlm")
        val e2b = File(application.filesDir, "gemma-4-E2B-it.litertlm")
        
        when {
            e4b.exists() -> setState { copy(selectedModel = "gemma-4-E4B-it.litertlm") }
            e2b.exists() -> setState { copy(selectedModel = "gemma-4-E2B-it.litertlm") }
        }
    }

    override suspend fun handleIntent(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.Initialize -> initModel(intent.modelPath)
            is ChatIntent.SendMessage -> sendMessage(intent.text, intent.imageUri)
            is ChatIntent.SwitchModel -> {
                setState { copy(selectedModel = intent.modelName, messages = emptyList(), isReady = false) }
                val modelFile = File("${intent.baseDir}/${intent.modelName}")
                if (modelFile.exists()) {
                    initModel(modelFile.absolutePath)
                }
            }
            is ChatIntent.DownloadModel -> downloadModel(intent.modelName, intent.baseDir)
            ChatIntent.FetchModels -> fetchModels()
            ChatIntent.ClearError -> setState { copy(error = null) }
            ChatIntent.ClearConversation -> clearConversation()
        }
    }

    private fun clearConversation() {
        viewModelScope.launch {
            repository.clearConversation()
            setState { copy(messages = emptyList()) }
        }
    }

    private suspend fun fetchModels() {
        setState { copy(isFetchingModels = true, error = null) }
        repository.fetchAvailableModels()
            .onSuccess { models ->
                val modelMap = models.associate { it.name to it.url }
                val finalModels = if (modelMap.isEmpty()) fallbackModels else modelMap
                setState { copy(availableModels = finalModels, isFetchingModels = false) }
            }
            .onFailure {
                setState { copy(availableModels = fallbackModels, isFetchingModels = false) }
            }
    }

    private suspend fun initModel(modelPath: String) {
        setState { copy(isLoading = true, error = null, isReady = false) }
        initializeChatUseCase(modelPath)
            .onSuccess {
                setState { copy(isLoading = false, isReady = true, loadingMessage = null) }
            }
            .onFailure { e ->
                setState { copy(isLoading = false, error = "Initialization failed: ${e.message}", loadingMessage = null) }
            }
    }

    private suspend fun sendMessage(text: String, imageUri: Uri?) {
        val userMsg = ChatMessage(text, isUser = true, imageUri = imageUri?.toString())
        setState { copy(messages = messages + userMsg, isLoading = true, loadingMessage = "THINKING...") }
        sendEffect { ChatEffect.ScrollToBottom }

        var responseText = ""
        val time = measureTimeMillis {
            sendMessageUseCase(text, imageUri)
                .onSuccess { responseText = it }
                .onFailure { e ->
                    setState { copy(isLoading = false, error = "Inference failed: ${e.message}", loadingMessage = null) }
                    return
                }
        }

        val aiMsg = ChatMessage(responseText, isUser = false, inferenceTimeMs = time)
        setState { copy(messages = messages + aiMsg, isLoading = false, loadingMessage = null) }
        sendEffect { ChatEffect.ScrollToBottom }
    }

    private fun downloadModel(modelName: String, baseDir: String) {
        val url = currentState.availableModels[modelName] ?: fallbackModels[modelName] ?: return
        
        setState { copy(selectedModel = modelName, isDownloading = true, downloadProgress = 0, error = null) }
        
        viewModelScope.launch {
            repository.downloadModel(url, modelName).collect { progress ->
                when (progress) {
                    is DownloadProgress.Progress -> {
                        setState { copy(isDownloading = true, downloadProgress = progress.percent) }
                    }
                    DownloadProgress.Finished -> {
                        setState { copy(isDownloading = false, downloadProgress = null) }
                        val targetFile = File(baseDir, modelName)
                        initModel(targetFile.absolutePath)
                    }
                    is DownloadProgress.Error -> {
                        setState { copy(isDownloading = false, downloadProgress = null, error = progress.message) }
                    }
                }
            }
        }
    }
}
