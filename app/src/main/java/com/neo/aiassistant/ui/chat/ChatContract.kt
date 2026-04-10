package com.neo.aiassistant.ui.chat

import android.net.Uri
import com.neo.aiassistant.core.UiEffect
import com.neo.aiassistant.core.UiIntent
import com.neo.aiassistant.core.UiState
import com.neo.aiassistant.data.agent.AgentState
import com.neo.aiassistant.domain.ChatMessage
import com.neo.aiassistant.domain.LocalModel

/**
 * Represents the current state of the AI engine runtime.
 */
sealed interface RuntimeState {
    /** The engine is not yet initialized. */
    data object Uninitialized : RuntimeState
    /** The engine is currently initializing. @property message A status message. */
    data class Initializing(val message: String) : RuntimeState
    /** The engine is ready to process requests. */
    data object Ready : RuntimeState
    /** The engine encountered an error during initialization. @property message The error message. */
    data class Error(val message: String) : RuntimeState
}

/**
 * Represents the state of a message sending operation.
 */
sealed interface SendState {
    /** No message is currently being sent. */
    data object Idle : SendState
    /** A message is currently being processed by the AI. */
    data object Sending : SendState
    /** An error occurred while sending the message. @property message The error message. */
    data class Error(val message: String) : SendState
}

/**
 * The UI state for the Chat screen.
 *
 * @property messages The list of messages in the conversation.
 * @property localModels The list of AI models available locally.
 * @property selectedModel The identifier of the currently selected model.
 * @property runtimeState The initialization state of the AI engine.
 * @property sendState The state of the current message sending operation.
 * @property agentState The state of the AI agent (e.g., planning, executing).
 * @property inputText The current text in the chat input field.
 * @property selectedImageUri The URI of an image selected to be sent with the next message.
 * @property tempCameraUri Temporary URI for capturing an image from the camera.
 */
data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val localModels: List<LocalModel> = emptyList(),
    val selectedModel: String = "",
    val runtimeState: RuntimeState = RuntimeState.Uninitialized,
    val sendState: SendState = SendState.Idle,
    val agentState: AgentState = AgentState.Idle,
    val inputText: String = "",
    val selectedImageUri: Uri? = null,
    val tempCameraUri: Uri? = null
) : UiState {
    /** `true` if the AI engine is ready to process requests. */
    val isReady: Boolean get() = runtimeState is RuntimeState.Ready
    
    /** `true` if any asynchronous operation (initialization, sending, agent work) is in progress. */
    val isLoading: Boolean get() = runtimeState is RuntimeState.Initializing || 
            sendState is SendState.Sending || 
            (agentState is AgentState.Planning || agentState is AgentState.ExecutingTool)
    
    /** A user-friendly message describing the current loading operation, if any. */
    val loadingMessage: String? get() = when {
        agentState is AgentState.Planning -> "PLANNING..."
        agentState is AgentState.ExecutingTool -> "EXECUTING: ${agentState.toolName.uppercase()}"
        sendState is SendState.Sending -> "THINKING..."
        runtimeState is RuntimeState.Initializing -> runtimeState.message
        else -> null
    }

    /** The current error message from any part of the chat system, if any. */
    val error: String? get() = when {
        runtimeState is RuntimeState.Error -> runtimeState.message
        sendState is SendState.Error -> sendState.message
        agentState is AgentState.Error -> agentState.message
        else -> null
    }
}

/**
 * User intents (actions) that can be performed on the Chat screen.
 */
sealed class ChatIntent : UiIntent {
    /** Initialize the model at the given path. */
    data class Initialize(val modelPath: String) : ChatIntent()
    /** Send a message with optional image. */
    data class SendMessage(val text: String, val imageUri: Uri? = null) : ChatIntent()
    /** Switch to a different AI model. */
    data class SwitchModel(val modelName: String, val baseDir: String) : ChatIntent()
    /** Clear any active error state. */
    data object ClearError : ChatIntent()
    /** Clear the conversation history. */
    data object ClearConversation : ChatIntent()
    /** Update the text in the input field. */
    data class UpdateInputText(val text: String) : ChatIntent()
    /** Select an image from the gallery. */
    data class SelectImage(val uri: Uri?) : ChatIntent()
    /** Set the temporary camera URI. */
    data class SetTempCameraUri(val uri: Uri?) : ChatIntent()
}

/**
 * One-time side effects that can be triggered from the Chat ViewModel.
 */
sealed class ChatEffect : UiEffect {
    /** Request the chat list to scroll to the bottom. */
    data object ScrollToBottom : ChatEffect()
    /** Request to show a toast message. @property message The message to show. */
    data class ShowToast(val message: String) : ChatEffect()
}
