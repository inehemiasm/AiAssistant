package com.neo.aiassistant

import android.net.Uri
import com.neo.aiassistant.core.UiEffect
import com.neo.aiassistant.core.UiIntent
import com.neo.aiassistant.core.UiState
import com.neo.aiassistant.domain.ChatMessage

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
    val availableModels: Map<String, String> = emptyMap(),
    val selectedModel: String = "gemma-4-E4B-it.litertlm",
    val runtimeState: RuntimeState = RuntimeState.Uninitialized,
    val sendState: SendState = SendState.Idle,
    val downloadState: DownloadState = DownloadState.Idle,
    val catalogState: CatalogState = CatalogState.Idle
) : UiState

sealed class ChatIntent : UiIntent {
    data class Initialize(val modelPath: String) : ChatIntent()
    data class SendMessage(val text: String, val imageUri: Uri? = null) : ChatIntent()
    data class SwitchModel(val modelName: String, val baseDir: String) : ChatIntent()
    data class DownloadModel(val modelName: String, val baseDir: String) : ChatIntent()
    data object FetchModels : ChatIntent()
    data object ClearError : ChatIntent()
    data object ClearConversation : ChatIntent()
}

sealed class ChatEffect : UiEffect {
    data object ScrollToBottom : ChatEffect()
    data class ShowToast(val message: String) : ChatEffect()
}
