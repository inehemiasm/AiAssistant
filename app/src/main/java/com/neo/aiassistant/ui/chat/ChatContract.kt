package com.neo.aiassistant.ui.chat

import android.net.Uri
import com.neo.aiassistant.core.UiEffect
import com.neo.aiassistant.core.UiIntent
import com.neo.aiassistant.core.UiState
import com.neo.aiassistant.data.agent.AgentState
import com.neo.aiassistant.domain.ChatMessage
import com.neo.aiassistant.domain.InstalledModel

/**
 * Represents the current state of the AI engine runtime.
 */
sealed interface RuntimeState {
    data object Uninitialized : RuntimeState
    data class Initializing(val message: String) : RuntimeState
    data object Ready : RuntimeState
    data class Error(val message: String) : RuntimeState
}

/**
 * Represents the state of a message sending operation.
 */
sealed interface SendState {
    data object Idle : SendState
    data object Sending : SendState
    data class Error(val message: String) : SendState
}

/**
 * The UI state for the Chat screen.
 */
data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val localModels: List<InstalledModel> = emptyList(),
    val selectedModel: String = "",
    val runtimeState: RuntimeState = RuntimeState.Uninitialized,
    val sendState: SendState = SendState.Idle,
    val agentState: AgentState = AgentState.Idle,
    val inputText: String = "",
    val selectedImageUri: Uri? = null,
    val tempCameraUri: Uri? = null
) : UiState {
    val isReady: Boolean get() = runtimeState is RuntimeState.Ready
    
    val isLoading: Boolean get() = runtimeState is RuntimeState.Initializing || 
            sendState is SendState.Sending || 
            (agentState is AgentState.Planning || agentState is AgentState.ExecutingTool)
    
    val loadingMessage: String? get() = when {
        agentState is AgentState.Planning -> "PLANNING..."
        agentState is AgentState.ExecutingTool -> "EXECUTING: ${agentState.toolName.uppercase()}"
        sendState is SendState.Sending -> "THINKING..."
        runtimeState is RuntimeState.Initializing -> runtimeState.message
        else -> null
    }

    val error: String? get() = when {
        runtimeState is RuntimeState.Error -> runtimeState.message
        sendState is SendState.Error -> sendState.message
        agentState is AgentState.Error -> agentState.message
        else -> null
    }

    val isWaitingForConfirmation: Boolean get() = agentState is AgentState.WaitingForConfirmation
    val confirmationMessage: String? get() = (agentState as? AgentState.WaitingForConfirmation)?.message
}

sealed class ChatIntent : UiIntent {
    data class Initialize(val modelPath: String) : ChatIntent()
    data class SendMessage(val text: String, val imageUri: Uri? = null) : ChatIntent()
    data class SwitchModel(val modelName: String, val baseDir: String) : ChatIntent()
    data object ClearError : ChatIntent()
    data object ClearConversation : ChatIntent()
    data class UpdateInputText(val text: String) : ChatIntent()
    data class SelectImage(val uri: Uri?) : ChatIntent()
    data class SetTempCameraUri(val uri: Uri?) : ChatIntent()
    data object ConfirmAction : ChatIntent()
    data object CancelAction : ChatIntent()
}

sealed class ChatEffect : UiEffect {
    data object ScrollToBottom : ChatEffect()
    data class ShowToast(val message: String) : ChatEffect()
    data object HideKeyboard : ChatEffect()
}
