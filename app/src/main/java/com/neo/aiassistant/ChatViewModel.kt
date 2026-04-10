package com.neo.aiassistant

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.neo.aiassistant.core.BaseViewModel
import com.neo.aiassistant.data.PreferenceManager
import com.neo.aiassistant.domain.ChatMessage
import com.neo.aiassistant.domain.ChatRepository
import com.neo.aiassistant.domain.DownloadProgress
import com.neo.aiassistant.domain.InitializeChatUseCase
import com.neo.aiassistant.domain.SendMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import kotlin.system.measureTimeMillis

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val application: Application,
    private val repository: ChatRepository,
    private val initializeChatUseCase: InitializeChatUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val preferenceManager: PreferenceManager
) : BaseViewModel<ChatState, ChatIntent, ChatEffect>(application, ChatState()) {

    init {
        // Move all initialization to a background coroutine to keep the UI thread responsive
        viewModelScope.launch {
            observeSelectedModel()
            updateLocalModels()
            fetchModels()
            observeInitStatus()
            observeAgentStatus()
            refreshMetrics()
        }
    }

    private fun observeSelectedModel() {
        viewModelScope.launch {
            preferenceManager.selectedModelPreference.collectLatest { savedModel ->
                if (savedModel != null && savedModel != currentState.selectedModel) {
                    setState { copy(selectedModel = savedModel) }
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
                Log.d("ChatViewModel", "Received init status: $status")
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

        // Load saved model or auto-select first available model if none selected
        val savedModel = preferenceManager.selectedModelPreference.first()
        val currentSelected = savedModel ?: currentState.selectedModel
        
        if (currentSelected.isEmpty() || !File(application.filesDir, currentSelected).exists()) {
            models.firstOrNull()?.let {
                val modelName = it.fileName
                setState { copy(selectedModel = modelName) }
                preferenceManager.updateSelectedModel(modelName)
            }
        } else if (savedModel != null) {
            setState { copy(selectedModel = savedModel) }
        }
    }


    override suspend fun handleIntent(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.Initialize -> initModel(intent.modelPath)
            is ChatIntent.SendMessage -> sendMessage(intent.text, intent.imageUri)
            is ChatIntent.SwitchModel -> {
                if (currentState.selectedModel == intent.modelName && currentState.runtimeState is RuntimeState.Ready) {
                    Log.d("ChatViewModel", "Model ${intent.modelName} already loaded, skipping init.")
                    return
                }

                Log.d("ChatViewModel", "Switching model to: ${intent.modelName}")
                setState { copy(
                    selectedModel = intent.modelName,
                    messages = emptyList(),
                    runtimeState = RuntimeState.Uninitialized
                ) }
                preferenceManager.updateSelectedModel(intent.modelName)

                val modelFile = File("${intent.baseDir}/${intent.modelName}")
                if (modelFile.exists()) {
                    Log.d("ChatViewModel", "Model file exists locally, loading directly: ${modelFile.absolutePath}")
                    initModel(modelFile.absolutePath)
                } else {
                    Log.d("ChatViewModel", "Model file not found locally, initiating download: ${modelFile.absolutePath}")
                    // Check if model is in remote catalog and download if needed
                    val modelEntry = currentState.remoteModels.find { it.effectiveFileName == intent.modelName || it.name == intent.modelName }
                    if (modelEntry != null) {
                        downloadModel(intent.modelName, intent.baseDir)
                    } else {
                        Log.w("ChatViewModel", "Model not found in remote catalog: ${intent.modelName}")
                    }
                }
            }
            is ChatIntent.DownloadModel -> {
                Log.d("ChatViewModel", "Received DownloadModel intent: ${intent.modelName}")
                downloadModel(intent.modelName, intent.baseDir)
            }
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

        val lastAiMessage = currentState.messages.lastOrNull { !it.isUser }
        val latency = lastAiMessage?.inferenceTimeMs ?: currentState.metrics.lastLatencyMs
        
        val throughput = if (latency > 0 && lastAiMessage != null) {
            (lastAiMessage.text.length / (latency.toFloat() / 1000f)) / 4f
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
        Log.d("ChatViewModel", "Initializing model: $modelPath")
        setState { copy(runtimeState = RuntimeState.Initializing(application.getString(R.string.synthesizing))) }
        
        initializeChatUseCase(modelPath)
            .onFailure { e ->
                Log.e("ChatViewModel", "Model initialization failed", e)
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
                    Log.e("ChatViewModel", "Inference failed", e)
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
        Log.d("ChatViewModel", "Attempting to download model: $modelName")
        val modelEntry = currentState.remoteModels.find { it.effectiveFileName == modelName || it.name == modelName }
        if (modelEntry == null) {
            Log.e("ChatViewModel", "Model not found in remote catalog: $modelName")
            return
        }
        
        val url = modelEntry.url
        val fileName = modelEntry.effectiveFileName
        val sha256 = modelEntry.sha256
        
        setState { copy(selectedModel = fileName, downloadState = DownloadState.Downloading(0)) }
        
        viewModelScope.launch {
            repository.downloadModel(url, fileName, sha256).collect { progress ->
                when (progress) {
                    is DownloadProgress.Progress -> {
                        setState { copy(downloadState = DownloadState.Downloading(progress.percent)) }
                    }
                    DownloadProgress.Finished -> {
                        Log.d("ChatViewModel", "Download finished for $fileName")
                        setState { copy(downloadState = DownloadState.Idle) }
                        updateLocalModels()
                        val targetFile = File(baseDir, fileName)
                        initModel(targetFile.absolutePath)
                    }
                    is DownloadProgress.Error -> {
                        Log.e("ChatViewModel", "Download error: ${progress.message}")
                        setState { copy(downloadState = DownloadState.Error(progress.message)) }
                    }
                }
            }
        }
    }
}
