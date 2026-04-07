package com.neo.aiassistant

import android.net.Uri
import com.neo.aiassistant.core.UiEffect
import com.neo.aiassistant.core.UiIntent
import com.neo.aiassistant.core.UiState
import com.neo.aiassistant.data.agent.AgentState
import com.neo.aiassistant.domain.ChatMessage
import com.neo.aiassistant.domain.LocalModel
import com.neo.aiassistant.domain.ModelEntry

/**
 * Represents the initialization and health status of the LiteRT-LM runtime.
 */
sealed interface RuntimeState {
    data object Uninitialized : RuntimeState
    data class Initializing(val message: String) : RuntimeState
    data object Ready : RuntimeState
    data class Error(val message: String) : RuntimeState
}

/**
 * Represents the status of a message being sent to the LLM.
 */
sealed interface SendState {
    data object Idle : SendState
    data object Sending : SendState
    data class Error(val message: String) : SendState
}

/**
 * Represents the status of the model download process.
 */
sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val progress: Int) : DownloadState
    data class Error(val message: String) : DownloadState
}

/**
 * Represents the status of fetching the model catalog from the remote data source.
 */
sealed interface CatalogState {
    data object Idle : CatalogState
    data object Loading : CatalogState
    data class Error(val message: String) : CatalogState
}

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val availableModels: Map<String, String> = emptyMap(), // Legacy fallback map
    val remoteModels: List<ModelEntry> = emptyList(),
    val localModels: List<LocalModel> = emptyList(),
    val selectedModel: String = "gemma-4-E4B-it.litertlm",
    val runtimeState: RuntimeState = RuntimeState.Uninitialized,
    val sendState: SendState = SendState.Idle,
    val downloadState: DownloadState = DownloadState.Idle,
    val catalogState: CatalogState = CatalogState.Idle,
    val agentState: AgentState = AgentState.Idle
) : UiState {
    val isReady: Boolean get() = runtimeState is RuntimeState.Ready
    
    val isLoading: Boolean get() = runtimeState is RuntimeState.Initializing || 
            sendState is SendState.Sending || 
            (agentState is AgentState.Planning || agentState is AgentState.ExecutingTool)
            
    val isDownloading: Boolean get() = downloadState is DownloadState.Downloading
    
    val downloadProgress: Int? get() = (downloadState as? DownloadState.Downloading)?.progress
    
    val isFetchingModels: Boolean get() = catalogState is CatalogState.Loading
    
    val error: String? get() = when {
        runtimeState is RuntimeState.Error -> runtimeState.message
        sendState is SendState.Error -> sendState.message
        downloadState is DownloadState.Error -> downloadState.message
        catalogState is CatalogState.Error -> catalogState.message
        agentState is AgentState.Error -> agentState.message
        else -> null
    }
    
    val loadingMessage: String? get() = when {
        agentState is AgentState.Planning -> "PLANNING..."
        agentState is AgentState.ExecutingTool -> "EXECUTING: ${agentState.toolName.uppercase()}"
        sendState is SendState.Sending -> "THINKING..."
        runtimeState is RuntimeState.Initializing -> runtimeState.message
        else -> null
    }
}

sealed class ChatIntent : UiIntent {
    data class Initialize(val modelPath: String) : ChatIntent()
    data class SendMessage(val text: String, val imageUri: Uri? = null) : ChatIntent()
    data class SwitchModel(val modelName: String, val baseDir: String) : ChatIntent()
    data class DownloadModel(val modelName: String, val baseDir: String) : ChatIntent()
    data class DeleteModel(val modelName: String) : ChatIntent()
    data object FetchModels : ChatIntent()
    data object ClearError : ChatIntent()
    data object ClearConversation : ChatIntent()
}

sealed class ChatEffect : UiEffect {
    data object ScrollToBottom : ChatEffect()
    data class ShowToast(val message: String) : ChatEffect()
}
