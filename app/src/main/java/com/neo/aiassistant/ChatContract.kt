package com.neo.aiassistant

import android.net.Uri
import com.neo.aiassistant.core.UiEffect
import com.neo.aiassistant.core.UiIntent
import com.neo.aiassistant.core.UiState
import com.neo.aiassistant.domain.ChatMessage

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isReady: Boolean = false,
    val error: String? = null,
    val availableModels: Map<String, String> = emptyMap(),
    val selectedModel: String = "gemma-4-E4B-it.litertlm",
    val downloadProgress: Int? = null,
    val isDownloading: Boolean = false,
    val isFetchingModels: Boolean = false
) : UiState

sealed class ChatIntent : UiIntent {
    data class Initialize(val modelPath: String) : ChatIntent()
    data class SendMessage(val text: String, val imageUri: Uri? = null) : ChatIntent()
    data class SwitchModel(val modelName: String, val baseDir: String) : ChatIntent()
    data class DownloadModel(val modelName: String, val baseDir: String) : ChatIntent()
    object FetchModels : ChatIntent()
    object ClearError : ChatIntent()
}

sealed class ChatEffect : UiEffect {
    object ScrollToBottom : ChatEffect()
    data class ShowToast(val message: String) : ChatEffect()
}
