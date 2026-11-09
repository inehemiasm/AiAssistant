package com.neo.chevere.ui.chat

import com.neo.chevere.ui.chat.a2ui.TaskChecklistData
import com.neo.chevere.ui.chat.a2ui.TaskChecklistUiModel
import com.neo.chevere.ui.chat.a2ui.toUiModel
import com.neo.chevere.domain.AgentUiContent
import android.net.Uri
import com.neo.chevere.core.Constants
import com.neo.chevere.core.UiEffect
import com.neo.chevere.core.UiIntent
import com.neo.chevere.core.UiState
import com.neo.chevere.data.agent.AgentState
import com.neo.chevere.domain.ChatMessage
import com.neo.chevere.domain.ConversationSession
import com.neo.chevere.domain.InstalledModel

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
    data object GeneratingImage : SendState
    data class Error(val message: String) : SendState
}

/**
 * Represents the state of the composer action button.
 */
sealed interface ComposerActionButtonState {
    data object Disabled : ComposerActionButtonState
    data object Send : ComposerActionButtonState
    data object Stop : ComposerActionButtonState
    /** Composer is empty and a model is ready: the button starts hands-free conversation mode. */
    data object HandsFree : ComposerActionButtonState
}

/**
 * Hands-free conversation loop: listen → think → speak → listen again, until the user says an
 * exit phrase, taps End, leaves the screen, or stays silent for several turns.
 */
sealed interface HandsFreeState {
    /** Hands-free mode is not running. */
    data object Off : HandsFreeState

    /** Waiting for the user to grant microphone access before the loop can start. */
    data object AwaitingPermission : HandsFreeState

    /** Microphone is open for the user's next utterance. */
    data class Listening(val amplitude: Float = 0.1f) : HandsFreeState

    /** The utterance was submitted and the assistant is generating a reply. */
    data object Thinking : HandsFreeState

    /** The assistant reply is being read aloud. */
    data object Speaking : HandsFreeState

    /** True while the conversation loop is running (excludes [Off] and [AwaitingPermission]). */
    val isActive: Boolean get() = this is Listening || this is Thinking || this is Speaking
}

/**
 * Explicit MVI state for the Whisper-style continuous voice listener and post-recording memo bar.
 */
sealed interface VoiceRecordingState {
    data object Idle : VoiceRecordingState
    data class Listening(
        val amplitude: Float = 0.1f,
        val elapsedSeconds: Int = 0
    ) : VoiceRecordingState
    data class Recorded(
        val transcript: String
    ) : VoiceRecordingState
}

/**
 * One-tap post-recording actions available after finishing a voice memo.
 */
enum class VoiceMemoAction {
    SUMMARIZE,
    EXTRACT_TASKS,
    SAVE_TO_DOCS
}

/**
 * The UI state for the Chat screen.
 */
data class ChatState(
    val downloadStatus: DownloadStatusUi = DownloadStatusUi.None,
    val expandedEvidence: Set<Pair<Int, Int>> = emptySet(),
    val documentFolders: DocumentFoldersState = DocumentFoldersState.Closed,
    val messages: List<ChatMessage> = emptyList(),
    val localModels: List<InstalledModel> = emptyList(),
    val selectedModel: String = "",
    val runtimeState: RuntimeState = RuntimeState.Uninitialized,
    val sendState: SendState = SendState.Idle,
    val agentState: AgentState = AgentState.Idle,
    val inputText: String = "",
    val selectedImageUri: Uri? = null,
    val tempCameraUri: Uri? = null,
    val ageVerificationRequest: AgeVerificationRequest? = null,
    val streamingText: String = "",
    /** Explicit state for the continuous voice listener and post-recording memo actions. */
    val voiceRecordingState: VoiceRecordingState = VoiceRecordingState.Idle,
    /** Hands-free conversation loop state; see [HandsFreeState]. */
    val handsFree: HandsFreeState = HandsFreeState.Off,
    /** The ID of the currently active persisted session, or null for a brand-new unsaved session. */
    val currentSessionId: Long? = null,
    /** History sessions shown in the history bottom sheet. */
    val historySessions: List<ConversationSession> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val taskChecklistData: TaskChecklistData = TaskChecklistData.Loading,
    /** Index of the assistant message currently being reported via ReportAiContentDialog, or null. */
    val reportDialogMessageIndex: Int? = null
) : UiState {
    val responseDetails: Map<Int, ResponseDetailsUi> get() = messages.mapIndexed { index, message ->
        index to message.toDetailsUi(expandedEvidence.filter { it.first == index }.map { it.second }.toSet())
    }.toMap()

    val modelStatus: ChatModelStatus get() = when {
        runtimeState is RuntimeState.Initializing -> ChatModelStatus.Loading(runtimeState.message)
        runtimeState is RuntimeState.Error -> ChatModelStatus.Failed(runtimeState.message, ModelRecovery.RELOAD)
        isWaitingForConfirmation -> ChatModelStatus.AwaitingConfirmation
        sendState is SendState.Error -> ChatModelStatus.Failed(sendState.message, ModelRecovery.RETRY_RESPONSE)
        isAiBusy -> ChatModelStatus.Generating
        runtimeState is RuntimeState.Ready -> ChatModelStatus.Ready(
            localModels.firstOrNull { it.id == selectedModel || it.fileName == selectedModel }?.displayName ?: selectedModel)
        downloadStatus is DownloadStatusUi.Active -> ChatModelStatus.Downloading(downloadStatus.name, downloadStatus.percent)
        downloadStatus is DownloadStatusUi.Failed -> ChatModelStatus.Failed(downloadStatus.reason, ModelRecovery.OPEN_MODELS)
        else -> ChatModelStatus.NeedsModel
    }
    /** Maps structured domain message content to renderer-facing presentation metadata. */
    val taskChecklists: Map<Int, TaskChecklistUiModel>
        get() = messages.mapIndexedNotNull { index, message ->
            val content = message.agentUiContent as? AgentUiContent.TaskChecklist
            if (!message.isUser && content != null) index to content.toUiModel() else null
        }.toMap()

    val isReady: Boolean get() = runtimeState is RuntimeState.Ready

    /**
     * True while the microphone is actively recording for speech-to-text. Derived from
     * [voiceRecordingState] so the listening flag can never drift from the visible voice UI.
     */
    val isListening: Boolean get() = voiceRecordingState is VoiceRecordingState.Listening

    val isLoading: Boolean
        get() = runtimeState is RuntimeState.Initializing ||
                sendState is SendState.Sending ||
                sendState is SendState.GeneratingImage ||
                (agentState is AgentState.Planning || agentState is AgentState.ExecutingTool)

    val isAiBusy: Boolean
        get() = sendState is SendState.Sending ||
                sendState is SendState.GeneratingImage ||
                agentState is AgentState.Planning ||
                agentState is AgentState.ExecutingTool

    val composerActionButtonState: ComposerActionButtonState
        get() = when {
            isWaitingForConfirmation -> ComposerActionButtonState.Disabled
            isAiBusy -> ComposerActionButtonState.Stop
            handsFree.isActive -> ComposerActionButtonState.Disabled
            isReady && !isLoading && (inputText.isNotBlank() || selectedImageUri != null) -> ComposerActionButtonState.Send
            isReady && !isLoading && !isListening && voiceRecordingState is VoiceRecordingState.Idle ->
                ComposerActionButtonState.HandsFree
            else -> ComposerActionButtonState.Disabled
        }

    val loadingMessage: String?
        get() = when {
            streamingText.isNotEmpty() -> "GENERATING..."
            agentState is AgentState.Planning -> Constants.UiStatus.PLANNING
            agentState is AgentState.ExecutingTool -> {
                if (agentState.toolName == Constants.Agent.IMAGE_GENERATION_TOOL_NAME) {
                    Constants.UiStatus.GENERATING_IMAGE
                } else {
                    "${Constants.UiStatus.EXECUTING_PREFIX}${agentState.toolName.uppercase()}"
                }
            }
            sendState is SendState.GeneratingImage -> Constants.UiStatus.GENERATING_IMAGE
            sendState is SendState.Sending -> Constants.UiStatus.THINKING
            runtimeState is RuntimeState.Initializing -> runtimeState.message
            else -> null
        }

    val error: String?
        get() = when {
            runtimeState is RuntimeState.Error -> runtimeState.message
            sendState is SendState.Error -> sendState.message
            agentState is AgentState.Error -> agentState.message
            else -> null
        }

    val isWaitingForConfirmation: Boolean get() = agentState is AgentState.WaitingForConfirmation
    val confirmationMessage: String? get() = (agentState as? AgentState.WaitingForConfirmation)?.message
}

sealed class ChatIntent : UiIntent {
    data class ToggleDocumentExcerpt(val messageIndex: Int, val excerptIndex: Int) : ChatIntent()
    data object RetryModelLoad : ChatIntent()
    data object OpenDocumentFolders : ChatIntent()
    data object CloseDocumentFolders : ChatIntent()
    data class IndexDocumentFolder(val reference: String) : ChatIntent()
    data class DisconnectDocumentFolder(val reference: String) : ChatIntent()
    data class Initialize(val modelPath: String) : ChatIntent()
    data class SendMessage(val text: String, val imageUri: Uri? = null) : ChatIntent()
    data class SwitchModel(val modelName: String, val baseDir: String) : ChatIntent()
    data object ClearError : ChatIntent()
    data object ClearConversation : ChatIntent()
    data class UpdateInputText(val text: String) : ChatIntent()
    /** Imports a user-selected text document into the local library. */
    data class ImportDocument(val reference: String) : ChatIntent()
    data class SelectImage(val uri: Uri?) : ChatIntent()
    data class SetTempCameraUri(val uri: Uri?) : ChatIntent()
    data object ConfirmAction : ChatIntent()
    data object CancelAction : ChatIntent()
    data object CancelGeneration : ChatIntent()
    data object StopResponse : ChatIntent()
    data class SubmitBirthdate(val year: Int, val month: Int, val day: Int) : ChatIntent()
    data object DismissAgeVerification : ChatIntent()
    data class ToggleExplicitImageMask(val messageIndex: Int) : ChatIntent()
    data class ShareMessage(val messageIndex: Int) : ChatIntent()
    data class OpenReportDialog(val messageIndex: Int) : ChatIntent()
    data object DismissReportDialog : ChatIntent()
    data class SubmitReport(val reason: String, val details: String) : ChatIntent()
    data class SaveImage(val messageIndex: Int) : ChatIntent()
    data class ReadMessageAloud(val messageIndex: Int) : ChatIntent()
    data object RetryLastMessage : ChatIntent()
    /** Start offline speech-to-text recording. */
    data object StartVoiceInput : ChatIntent()
    /** Stop the active speech-to-text recording session and commit the transcript. */
    data object StopVoiceInput : ChatIntent()
    /** Cancel the active speech-to-text recording session and discard any buffered text. */
    data object CancelVoiceInput : ChatIntent()
    /** Deliver a completed STT transcript back to the input field. */
    data class VoiceInputResult(val text: String) : ChatIntent()
    /** Execute a one-tap post-recording action on the captured voice memo. */
    data class RunVoiceMemoAction(val action: VoiceMemoAction) : ChatIntent()
    /** Start the hands-free listen → answer aloud → listen loop. */
    data object StartHandsFree : ChatIntent()
    /** End hands-free mode, stopping the microphone and any speech playback. */
    data object StopHandsFree : ChatIntent()
    // ── Chat History ─────────────────────────────────────────────────────────
    /** Starts a fresh, unsaved conversation (the current session is kept in history). */
    data object NewConversation : ChatIntent()
    /** Loads a previously saved session from history into the chat screen. */
    data class LoadSession(val sessionId: Long) : ChatIntent()
    /** Permanently deletes a session from history. */
    data class DeleteSession(val sessionId: Long) : ChatIntent()
    /** Renames a session's display title. */
    data class RenameSession(val sessionId: Long, val newTitle: String) : ChatIntent()
    /** Triggers reload of model on resume if needed. */
    data object Resume : ChatIntent()
    /** A validated user interaction on a task in a currently displayed agent surface. */
    data class SetTaskCompleted(val surfaceId: String, val taskId: Int, val completed: Boolean) : ChatIntent()
}

/** Presentation-only models for folder management; provider types stay in the data layer. */
data class DocumentFolderUiModel(val reference: String, val name: String)

sealed interface FolderOperation {
    data object Idle : FolderOperation
    data object Working : FolderOperation
}

sealed interface DocumentFoldersState {
    data object Closed : DocumentFoldersState
    data class Open(
        val folders: List<DocumentFolderUiModel> = emptyList(),
        val operation: FolderOperation = FolderOperation.Idle,
        val message: String? = null,
    ) : DocumentFoldersState {
        val canInteract: Boolean get() = operation == FolderOperation.Idle
    }
}

sealed class ChatEffect : UiEffect {
    data object ScrollToBottom : ChatEffect()
    data class ShowToast(val message: String) : ChatEffect()
    data class ShareMessage(val title: String, val text: String, val imageUri: Uri? = null) :
        ChatEffect()

    data class SaveImage(val imageUri: Uri) : ChatEffect()
    data class ReadMessageAloud(val messageIndex: Int, val text: String) : ChatEffect()
    data object ShowImageModelDownloadPrompt : ChatEffect()
    data object HideKeyboard : ChatEffect()
    /** Navigate to the Sensor Radar / Stud Finder utility screen with an optional mode. */
    data class NavigateToRadar(val mode: String = "all") : ChatEffect()
    data object RequestLocationPermission : ChatEffect()
    /** Request the READ_CONTACTS permission from the user. */
    data object RequestContactsPermission : ChatEffect()
    /** Request the RECORD_AUDIO permission from the user. */
    data object RequestMicPermission : ChatEffect()
    /** Request the READ_CALENDAR and WRITE_CALENDAR permissions. */
    data object RequestCalendarPermission : ChatEffect()
    /** Show an error that occurred during voice input. */
    data class ShowVoiceError(val message: String) : ChatEffect()
    /** Dismiss the chat history bottom sheet (e.g. after a session is loaded). */
    data object CloseHistorySheet : ChatEffect()
}

/**
 * Pending age-gated image prompt waiting for a birthdate dialog result.
 */
data class AgeVerificationRequest(
    val prompt: String,
    val imageUri: Uri?
)
