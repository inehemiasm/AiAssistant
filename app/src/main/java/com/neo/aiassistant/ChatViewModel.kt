package com.neo.aiassistant

import android.app.ActivityManager
import android.app.Application
import android.content.Context
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
        updateLocalModels()
        onIntent(ChatIntent.FetchModels)
        observeInitStatus()
        observeAgentStatus()
        refreshMetrics()
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
                if (status == "READY") {
                    setState { copy(runtimeState = RuntimeState.Ready) }
                } else if (status.contains("FAILED")) {
                    setState { copy(runtimeState = RuntimeState.Error(status)) }
                } else {
                    setState { copy(runtimeState = RuntimeState.Initializing(status)) }
                }
            }
        }
    }

    private fun updateLocalModels() {
        val models = repository.getLocalModels()
        setState { copy(localModels = models) }
        
        // Auto-select first available model if none selected
        if (currentState.selectedModel.isEmpty() || !File(application.filesDir, currentState.selectedModel).exists()) {
            models.firstOrNull()?.let { 
                setState { copy(selectedModel = it.name) }
            }
        }
    }

    override suspend fun handleIntent(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.Initialize -> initModel(intent.modelPath)
            is ChatIntent.SendMessage -> sendMessage(intent.text, intent.imageUri)
            is ChatIntent.SwitchModel -> {
                setState { copy(
                    selectedModel = intent.modelName, 
                    messages = emptyList(), 
                    runtimeState = RuntimeState.Uninitialized 
                ) }
                val modelFile = File("${intent.baseDir}/${intent.modelName}")
                if (modelFile.exists()) {
                    initModel(modelFile.absolutePath)
                }
            }
            is ChatIntent.DownloadModel -> downloadModel(intent.modelName, intent.baseDir)
            is ChatIntent.DeleteModel -> {
                if (repository.deleteModel(intent.modelName)) {
                    updateLocalModels()
                    if (currentState.selectedModel == intent.modelName) {
                        setState { copy(runtimeState = RuntimeState.Uninitialized) }
                    }
                }
            }
            ChatIntent.FetchModels -> fetchModels()
            ChatIntent.ClearError -> setState { copy(
                runtimeState = if (currentState.runtimeState is RuntimeState.Error) RuntimeState.Uninitialized else currentState.runtimeState,
                sendState = SendState.Idle,
                downloadState = DownloadState.Idle,
                catalogState = CatalogState.Idle
            ) }
            ChatIntent.ClearConversation -> clearConversation()
            ChatIntent.RefreshMetrics -> refreshMetrics()
            is ChatIntent.UpdateInputText -> setState { copy(inputText = intent.text) }
            is ChatIntent.SelectImage -> setState { copy(selectedImageUri = intent.uri) }
            is ChatIntent.SetTempCameraUri -> setState { copy(tempCameraUri = intent.uri) }
        }
    }

    private fun refreshMetrics() {
        val activityManager = application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val totalMem = memoryInfo.totalMem
        val availMem = memoryInfo.availMem
        val usedMem = totalMem - availMem
        val usagePercent = ((usedMem.toDouble() / totalMem.toDouble()) * 100).toInt()

        // For throughput and latency, we use the last recorded values from the chat session
        // If no messages yet, we keep defaults.
        val lastAiMessage = currentState.messages.lastOrNull { !it.isUser }
        val latency = lastAiMessage?.inferenceTimeMs ?: currentState.metrics.lastLatencyMs
        
        // Estimation for throughput: characters / (ms/1000) -> chars per second. 
        // Token count would be better but requires tokenizer access.
        val throughput = if (latency > 0 && lastAiMessage != null) {
            (lastAiMessage.text.length / (latency.toFloat() / 1000f)) / 4f // divide by 4 as rough char-to-token ratio
        } else {
            currentState.metrics.throughputTks
        }

        setState {
            copy(
                metrics = PerformanceMetrics(
                    lastLatencyMs = latency,
                    vramUsagePercent = usagePercent,
                    throughputTks = throughput
                )
            )
        }
    }

    private fun clearConversation() {
        viewModelScope.launch {
            repository.clearConversation()
            setState { copy(messages = emptyList()) }
        }
    }

    private suspend fun fetchModels() {
        setState { copy(catalogState = CatalogState.Loading) }
        repository.fetchAvailableModels()
            .onSuccess { models ->
                setState { copy(remoteModels = models, catalogState = CatalogState.Idle) }
            }
            .onFailure { e ->
                setState { copy(
                    catalogState = CatalogState.Error(
                        e.message ?: application.getString(R.string.error_fetch_models)
                    ) 
                ) }
            }
    }

    private suspend fun initModel(modelPath: String) {
        initializeChatUseCase(modelPath)
            .onFailure { e ->
                setState { copy(
                    runtimeState = RuntimeState.Error(
                        application.getString(R.string.error_init_failed, e.message ?: "Unknown")
                    )
                ) }
            }
    }

    private suspend fun sendMessage(text: String, imageUri: Uri?) {
        val userMsg = ChatMessage(text, isUser = true, imageUri = imageUri?.toString())
        setState { 
            copy(
                messages = messages + userMsg, 
                sendState = SendState.Sending,
                inputText = "",
                selectedImageUri = null 
            ) 
        }
        sendEffect { ChatEffect.ScrollToBottom }

        var responseText = ""
        val time = measureTimeMillis {
            sendMessageUseCase(text, imageUri)
                .onSuccess { responseText = it }
                .onFailure { e ->
                    setState { copy(
                        sendState = SendState.Error(
                            e.message ?: application.getString(R.string.error_inference_failed)
                        )
                    ) }
                    return
                }
        }

        val aiMsg = ChatMessage(responseText, isUser = false, inferenceTimeMs = time)
        setState { 
            copy(messages = messages + aiMsg, sendState = SendState.Idle) 
        }
        refreshMetrics()
        sendEffect { ChatEffect.ScrollToBottom }
    }

    private fun downloadModel(modelName: String, baseDir: String) {
        val url = currentState.remoteModels.find { it.name == modelName }?.url
            ?: fallbackModels[modelName] ?: return
        
        setState { copy(selectedModel = modelName, downloadState = DownloadState.Downloading(0)) }
        
        viewModelScope.launch {
            repository.downloadModel(url, modelName).collect { progress ->
                when (progress) {
                    is DownloadProgress.Progress -> {
                        setState { copy(downloadState = DownloadState.Downloading(progress.percent)) }
                    }
                    DownloadProgress.Finished -> {
                        setState { copy(downloadState = DownloadState.Idle) }
                        updateLocalModels()
                        val targetFile = File(baseDir, modelName)
                        initModel(targetFile.absolutePath)
                    }
                    is DownloadProgress.Error -> {
                        setState { copy(downloadState = DownloadState.Error(progress.message)) }
                    }
                }
            }
        }
    }
}
