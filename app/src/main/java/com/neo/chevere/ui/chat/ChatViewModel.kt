package com.neo.chevere.ui.chat

import com.neo.chevere.domain.AssistantResponse
import com.neo.chevere.domain.TaskRepository
import com.neo.chevere.domain.TaskStatus
import com.neo.chevere.ui.tasks.toUiModel
import com.neo.chevere.domain.AgentUiContent
import com.neo.chevere.ui.chat.a2ui.TaskChecklistData
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.catch
import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import com.neo.chevere.BuildConfig
import com.neo.chevere.core.BaseViewModel
import com.neo.chevere.core.Constants
import com.neo.chevere.core.DispatcherProvider
import com.neo.chevere.data.PreferenceManager
import com.neo.chevere.data.report.AiContentReportRepository
import com.neo.chevere.data.telemetry.AppTelemetry
import com.neo.chevere.data.telemetry.TelemetryConstants
import com.neo.chevere.data.voice.VoiceInputManager
import com.neo.chevere.data.voice.VoiceResult
import com.neo.chevere.data.agent.AgentState
import com.neo.chevere.domain.ChatHistoryRepository
import com.neo.chevere.domain.ChatMessage
import com.neo.chevere.domain.ChatRepository
import com.neo.chevere.domain.ContactsPermissionException
import com.neo.chevere.domain.CalendarPermissionException
import com.neo.chevere.domain.MicPermissionException
import com.neo.chevere.domain.ExplicitImagePromptDecision
import com.neo.chevere.domain.ExplicitImagePromptPolicy
import com.neo.chevere.domain.ImageAspectRatio
import com.neo.chevere.domain.ImageGenerationRequest
import com.neo.chevere.domain.ImageGenerationResult
import com.neo.chevere.domain.InitializationStatus
import com.neo.chevere.domain.InitializeChatUseCase
import kotlinx.coroutines.flow.first
import com.neo.chevere.domain.ModelCapability
import com.neo.chevere.domain.ModelTaskType
import com.neo.chevere.domain.LocationPermissionException
import com.neo.chevere.domain.SendMessageUseCase
import com.neo.chevere.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.time.DateTimeException
import java.time.LocalDate
import java.time.Period
import javax.inject.Inject
import kotlin.system.measureTimeMillis

private const val TAG = "ChatViewModel"

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val application: Application,
    private val repository: ChatRepository,
    private val initializeChatUseCase: InitializeChatUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val preferenceManager: PreferenceManager,
    private val dispatcherProvider: DispatcherProvider,
    private val telemetry: AppTelemetry,
    private val voiceInputManager: VoiceInputManager,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val taskRepository: TaskRepository,
    private val aiContentReportRepository: AiContentReportRepository,
    private val documentRepository: com.neo.chevere.domain.DocumentRepository,
    private val suggestionRepository: com.neo.chevere.domain.ChatSuggestionRepository,
    private val documentFolderRepository: com.neo.chevere.domain.DocumentFolderRepository,
) : BaseViewModel<ChatState, ChatIntent, ChatEffect>(application, ChatState()) {

    private val explicitImagePromptPolicy = ExplicitImagePromptPolicy()

    override fun shouldLogStateChange(oldState: ChatState, newState: ChatState): Boolean {
        val cleanOld = oldState.copy(inputText = "", streamingText = "", documentFolders = DocumentFoldersState.Closed)
        val cleanNew = newState.copy(inputText = "", streamingText = "", documentFolders = DocumentFoldersState.Closed)
        return cleanOld != cleanNew
    }
    private val intentMutex = Mutex()
    private var initJob: Job? = null
    private var imageGenerationJob: Job? = null
    private var responseJob: Job? = null
    private var voiceTimerJob: Job? = null
    private var lastUserMessage: String? = null
    /** ID of the currently active Room session; null until the first message is sent. */
    private var currentSessionId: Long? = null

    init {
        // 1. Observe global initialization status from the repository
        observeInitStatus()
        viewModelScope.launch {
            repository.allDownloadsProgress.collectLatest { downloads ->
                val active = downloads.entries.firstOrNull { it.value is com.neo.chevere.domain.DownloadProgress.Progress }
                val failed = downloads.entries.firstOrNull { it.value is com.neo.chevere.domain.DownloadProgress.Error }
                val status = when {
                    active != null -> DownloadStatusUi.Active(active.key, (active.value as com.neo.chevere.domain.DownloadProgress.Progress).percent.coerceIn(0, 100))
                    failed != null -> DownloadStatusUi.Failed(failed.key, (failed.value as com.neo.chevere.domain.DownloadProgress.Error).message)
                    else -> DownloadStatusUi.None
                }
                setState { copy(downloadStatus = status) }
            }
        }

        // 2. Observe agent state for tool executions
        observeAgentStatus()

        // 3. React to model selection changes (Source of Truth)
        observeSelectedModel()

        // 4. Observe active partial response for token streaming
        observeActivePartialResponse()

        // 5. Initial load of local models metadata
        viewModelScope.launch {
            updateLocalModels()
        }

        // 6. Observe voice recognition results
        observeVoiceResults()

        // 7. Observe history sessions for the bottom sheet
        observeHistorySessions()
        viewModelScope.launch {
            taskRepository.observeTasks().catch { error ->
                if (error is CancellationException) throw error
                setState { copy(taskChecklistData = TaskChecklistData.Unavailable) }
            }.collect { tasks ->
                setState {
                    val pending = (taskChecklistData as? TaskChecklistData.Ready)?.updatingIds.orEmpty()
                    copy(taskChecklistData = TaskChecklistData.Ready(tasks.map { it.toUiModel() }).withUpdatingIds(pending))
                }
            }
        }

        observeSuggestions()
    }

    private fun observeActivePartialResponse() {
        viewModelScope.launch {
            repository.activePartialResponse.collectLatest { partialText ->
                setState { copy(streamingText = partialText) }
            }
        }
    }

    private fun observeVoiceResults() {
        viewModelScope.launch(dispatcherProvider.main) {
            voiceInputManager.results.collect { result ->
                when (result) {
                    is VoiceResult.Partial -> setState { copy(inputText = result.text) }
                    is VoiceResult.Amplitude -> {
                        setState {
                            val currentListening = voiceRecordingState as? VoiceRecordingState.Listening
                            if (currentListening != null) {
                                copy(voiceRecordingState = currentListening.copy(amplitude = result.normalized))
                            } else {
                                this
                            }
                        }
                    }
                    is VoiceResult.Final -> {
                        voiceTimerJob?.cancel()
                        voiceTimerJob = null
                        val clean = result.text.trim()
                        setState {
                            copy(
                                inputText = clean,
                                isListening = false,
                                voiceRecordingState = if (clean.isNotBlank()) {
                                    VoiceRecordingState.Recorded(clean)
                                } else {
                                    VoiceRecordingState.Idle
                                }
                            )
                        }
                    }
                    is VoiceResult.Cancelled -> {
                        voiceTimerJob?.cancel()
                        voiceTimerJob = null
                        setState {
                            copy(
                                inputText = "",
                                isListening = false,
                                voiceRecordingState = VoiceRecordingState.Idle
                            )
                        }
                    }
                    is VoiceResult.Error -> {
                        voiceTimerJob?.cancel()
                        voiceTimerJob = null
                        setState {
                            copy(
                                isListening = false,
                                voiceRecordingState = VoiceRecordingState.Idle
                            )
                        }
                        if (result.message.isNotBlank()) {
                            sendEffect { ChatEffect.ShowVoiceError(result.message) }
                        }
                    }
                }
            }
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
                    val models = withContext(dispatcherProvider.io) { repository.getLocalModels() }
                    val selected = models.find {
                        it.id == savedModel ||
                                it.fileName == savedModel ||
                                it.id == savedModel.removeSuffix(Constants.ModelFiles.ZIP_EXTENSION) ||
                                it.fileName == savedModel.removeSuffix(Constants.ModelFiles.ZIP_EXTENSION)
                    }
                    setState { copy(localModels = models) }

                    if (selected?.isImageGenerationModel() == true) {
                        val chatModel = models.firstOrNull { it.isHealthy && it.isChatModel() }
                        if (chatModel != null) {
                            withContext(dispatcherProvider.io) {
                                preferenceManager.updateSelectedModel(chatModel.id)
                            }
                        } else {
                            setState {
                                copy(
                                    selectedModel = "",
                                    runtimeState = RuntimeState.Uninitialized
                                )
                            }
                        }
                        return@collectLatest
                    }

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
                Timber.tag(TAG).d("Init status update: $status")
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
        if (intent is ChatIntent.CancelGeneration || intent is ChatIntent.StopResponse) {
            stopActiveResponse()
            return
        }

        intentMutex.withLock {
            when (intent) {
                is ChatIntent.SetTaskCompleted -> setTaskCompleted(intent)
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
                    setState {
                        copy(
                            messages = emptyList(),
                            runtimeState = RuntimeState.Uninitialized
                        )
                    }
                }

                ChatIntent.ClearError -> setState {
                    copy(
                        runtimeState = if (currentState.runtimeState is RuntimeState.Error) RuntimeState.Uninitialized else currentState.runtimeState,
                        sendState = SendState.Idle
                    )
                }

                ChatIntent.ClearConversation -> clearConversation()
                is ChatIntent.UpdateInputText -> setState {
                    copy(
                        inputText = intent.text,
                        voiceRecordingState = if (intent.text.isBlank() && voiceRecordingState is VoiceRecordingState.Recorded) {
                            VoiceRecordingState.Idle
                        } else {
                            voiceRecordingState
                        }
                    )
                }
                is ChatIntent.ToggleDocumentExcerpt -> {
                    val evidence = currentState.messages.getOrNull(intent.messageIndex)?.documentEvidence as? com.neo.chevere.domain.DocumentEvidence.Matches
                    if (evidence?.excerpts?.getOrNull(intent.excerptIndex) != null) {
                        val key = intent.messageIndex to intent.excerptIndex
                        setState { copy(expandedEvidence = if (key in expandedEvidence) expandedEvidence - key else expandedEvidence + key) }
                    }
                }
                ChatIntent.RetryModelLoad -> {
                    val selected = repository.getLocalModels().firstOrNull { it.id == currentState.selectedModel || it.fileName == currentState.selectedModel }
                    if (selected != null) initModel(selected.filePath)
                    else setState { copy(runtimeState = RuntimeState.Uninitialized, selectedModel = "") }
                }
                ChatIntent.OpenDocumentFolders -> updateDocumentFolders()
                ChatIntent.CloseDocumentFolders -> setState { copy(documentFolders = DocumentFoldersState.Closed) }
                is ChatIntent.IndexDocumentFolder -> {
                    setState { copy(documentFolders = DocumentFoldersState.Open(
                        (documentFolders as? DocumentFoldersState.Open)?.folders.orEmpty(), FolderOperation.Working)) }
                    val message = when (val result = documentFolderRepository.indexFolder(intent.reference)) {
                        is com.neo.chevere.domain.FolderIndexResult.Indexed -> application.getString(R.string.folder_indexed, result.files, result.skipped)
                        is com.neo.chevere.domain.FolderIndexResult.Failed -> result.reason
                    }
                    updateDocumentFolders(message)
                }
                is ChatIntent.DisconnectDocumentFolder -> {
                    val folders = (currentState.documentFolders as? DocumentFoldersState.Open)?.folders.orEmpty()
                    if (folders.any { it.reference == intent.reference }) {
                        setState { copy(documentFolders = DocumentFoldersState.Open(folders, FolderOperation.Working)) }
                        val message = when (val result = documentFolderRepository.disconnectFolder(intent.reference)) {
                            com.neo.chevere.domain.FolderDisconnectResult.Disconnected -> application.getString(R.string.folder_disconnected)
                            is com.neo.chevere.domain.FolderDisconnectResult.Failed -> result.reason
                        }
                        updateDocumentFolders(message)
                    }
                }
                is ChatIntent.ImportDocument -> {
                    sendEffect { ChatEffect.ShowToast(getApplication<android.app.Application>().getString(R.string.ui_indexing_document)) }
                    when (val result = documentRepository.importDocument(intent.reference)) {
                        is com.neo.chevere.domain.DocumentImportResult.Imported -> {
                            setState { copy(inputText = "/docs Summarize ${result.name}") }
                            sendEffect { ChatEffect.ShowToast(getApplication<android.app.Application>().getString(R.string.ui_indexed_1_s_2_s_chunks, result.name, result.chunks)) }
                        }
                        is com.neo.chevere.domain.DocumentImportResult.Failed -> sendEffect { ChatEffect.ShowToast(result.reason) }
                    }
                }
                is ChatIntent.SelectImage -> setState {
                    copy(
                        selectedImageUri = intent.uri,
                        suggestions = emptyList()
                    )
                }
                is ChatIntent.SetTempCameraUri -> setState { copy(tempCameraUri = intent.uri) }
                ChatIntent.ConfirmAction -> confirmAction()
                ChatIntent.CancelAction -> cancelAction()
                ChatIntent.CancelGeneration -> cancelImageGeneration()
                ChatIntent.StopResponse -> stopActiveResponse()
                is ChatIntent.SubmitBirthdate -> submitBirthdate(
                    intent.year,
                    intent.month,
                    intent.day
                )

                ChatIntent.DismissAgeVerification -> dismissAgeVerification()
                is ChatIntent.ToggleExplicitImageMask -> toggleExplicitImageMask(intent.messageIndex)
                is ChatIntent.ShareMessage -> shareMessage(intent.messageIndex)
                is ChatIntent.OpenReportDialog -> openReportDialog(intent.messageIndex)
                ChatIntent.DismissReportDialog -> setState { copy(reportDialogMessageIndex = null) }
                is ChatIntent.SubmitReport -> submitReport(intent.reason, intent.details)
                is ChatIntent.SaveImage -> saveImage(intent.messageIndex)
                is ChatIntent.ReadMessageAloud -> readMessageAloud(intent.messageIndex)
                ChatIntent.RetryLastMessage -> {
                    if (currentState.isLoading) return@withLock
                    val query = lastUserMessage
                    if (query != null) {
                        sendMessage(query, null)
                    }
                }

                ChatIntent.StartVoiceInput -> startVoiceInput()
                ChatIntent.StopVoiceInput -> stopVoiceInput()
                ChatIntent.CancelVoiceInput -> cancelVoiceInput()
                is ChatIntent.VoiceInputResult -> {
                    voiceTimerJob?.cancel()
                    voiceTimerJob = null
                    val clean = intent.text.trim()
                    setState {
                        copy(
                            inputText = clean,
                            isListening = false,
                            voiceRecordingState = if (clean.isNotBlank()) {
                                VoiceRecordingState.Recorded(clean)
                            } else {
                                VoiceRecordingState.Idle
                            }
                        )
                    }
                }
                is ChatIntent.RunVoiceMemoAction -> runVoiceMemoAction(intent.action)
                // ── Chat History ─────────────────────────────────────────────
                ChatIntent.NewConversation -> newConversation()
                is ChatIntent.LoadSession -> loadSession(intent.sessionId)
                is ChatIntent.DeleteSession -> deleteSession(intent.sessionId)
                is ChatIntent.RenameSession -> renameSession(intent.sessionId, intent.newTitle)
                ChatIntent.Resume -> {
                    if (currentState.runtimeState is RuntimeState.Uninitialized && currentState.selectedModel.isNotEmpty()) {
                        val modelFile = File(application.filesDir, currentState.selectedModel)
                        if (modelFile.exists()) {
                            initModel(modelFile.absolutePath)
                        }
                    }
                }
            }
        }
    }

    private suspend fun updateDocumentFolders(message: String? = null) {
        try {
            val folders = documentFolderRepository.getFolders().map { DocumentFolderUiModel(it.reference, it.name) }
            setState { copy(documentFolders = DocumentFoldersState.Open(folders, message = message)) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            setState { copy(documentFolders = DocumentFoldersState.Open(message = application.getString(R.string.folder_index_failed))) }
        }
    }

    private fun AgentState.toOutcome(): com.neo.chevere.domain.AgentOutcome? = when (this) {
        is AgentState.Completed -> com.neo.chevere.domain.AgentOutcome.ANSWERED
        is AgentState.Incomplete -> com.neo.chevere.domain.AgentOutcome.INCOMPLETE
        is AgentState.Cancelled -> com.neo.chevere.domain.AgentOutcome.CANCELLED
        is AgentState.Error -> com.neo.chevere.domain.AgentOutcome.FAILED
        else -> null
    }

    private fun cancelActiveWorkSilently() {
        responseJob?.cancel()
        responseJob = null
        imageGenerationJob?.cancel()
        imageGenerationJob = null
        repository.resetAgentState()
        setState { copy(sendState = SendState.Idle) }
    }

    private fun clearConversation() {
        cancelActiveWorkSilently()
        viewModelScope.launch {
            withContext(dispatcherProvider.io) {
                repository.clearConversation()
            }
            // Detach from the current session; the session stays in history
            currentSessionId = null
            setState {
                copy(
                    messages = emptyList(),
                    currentSessionId = null,
                    selectedImageUri = null,
                    suggestions = emptyList()
                )
            }
        }
    }

    // ── Chat History helpers ──────────────────────────────────────────────────

    private fun observeHistorySessions() {
        viewModelScope.launch {
            chatHistoryRepository.getAllSessions().collectLatest { sessions ->
                setState { copy(historySessions = sessions) }
            }
        }
    }

    /**
     * Ensures a Room session exists for the current conversation. If this is the
     * first message of a new session, creates the session row and caches the ID.
     */
    private suspend fun ensureSessionCreated(firstUserText: String): Long {
        currentSessionId?.let { return it }
        val id = withContext(dispatcherProvider.io) {
            chatHistoryRepository.createSession(
                firstUserMessage = firstUserText,
                modelName = currentState.selectedModel
            )
        }
        currentSessionId = id
        setState { copy(currentSessionId = id) }
        return id
    }

    /** Persists a completed message to the active session (fire-and-forget). */
    private fun persistMessage(message: ChatMessage) {
        val sessionId = currentSessionId ?: return
        viewModelScope.launch(dispatcherProvider.io) {
            chatHistoryRepository.appendMessage(sessionId, message)
        }
    }

    private fun newConversation() {
        cancelActiveWorkSilently()
        viewModelScope.launch {
            withContext(dispatcherProvider.io) {
                repository.clearConversation()
            }
            currentSessionId = null
            setState {
                copy(
                    messages = emptyList(),
                    currentSessionId = null,
                    inputText = "",
                    selectedImageUri = null,
                    suggestions = emptyList()
                )
            }
            sendEffect { ChatEffect.CloseHistorySheet }
        }
    }

    private fun loadSession(sessionId: Long) {
        if (currentState.isAiBusy) return
        viewModelScope.launch {
            val messages = withContext(dispatcherProvider.io) {
                chatHistoryRepository.getMessages(sessionId)
            }
            // Rebuild context manager from the loaded session
            withContext(dispatcherProvider.io) {
                repository.clearConversation()
            }
            currentSessionId = sessionId
            setState {
                copy(
                    messages = messages,
                    currentSessionId = sessionId,
                    selectedImageUri = null,
                    suggestions = emptyList()
                )
            }
            sendEffect { ChatEffect.ScrollToBottom }
            sendEffect { ChatEffect.CloseHistorySheet }
        }
    }

    private fun deleteSession(sessionId: Long) {
        viewModelScope.launch(dispatcherProvider.io) {
            chatHistoryRepository.deleteSession(sessionId)
            // If we just deleted the active session, start fresh
            if (currentSessionId == sessionId) {
                currentSessionId = null
                setState { copy(messages = emptyList(), currentSessionId = null) }
            }
        }
    }

    private fun renameSession(sessionId: Long, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch(dispatcherProvider.io) {
            chatHistoryRepository.renameSession(sessionId, newTitle)
        }
    }

    private suspend fun initModel(modelPath: String) {
        initJob?.cancel()
        initJob = viewModelScope.launch {
            sendEffect { ChatEffect.HideKeyboard }
            // State will be updated to Initializing/Ready via observeInitStatus flow
            withContext(dispatcherProvider.default) {
                val modelId = File(modelPath).name
                telemetry.setActiveModel(modelId)
                telemetry.logModelInitStarted(modelId)
                var result: Result<Unit>? = null
                val time = measureTimeMillis {
                    result = initializeChatUseCase(modelPath)
                }
                result
                    ?.onSuccess {
                        telemetry.logModelInitFinished(modelId, success = true, durationMs = time)
                    }
                    ?.onFailure { throwable ->
                        telemetry.logModelInitFinished(
                            modelId = modelId,
                            success = false,
                            durationMs = time,
                            errorType = throwable::class.java.simpleName
                        )
                        telemetry.recordNonFatal(throwable, TelemetryConstants.Context.MODEL_INIT)
                    }
            }
        }
    }

    private suspend fun sendMessage(text: String, imageUri: Uri?) {
        if (currentState.isWaitingForConfirmation) return
        sendEffect { ChatEffect.HideKeyboard }
        val promptText = text.ifBlank {
            if (imageUri != null) getApplication<android.app.Application>().getString(R.string.suggestion_describe_image) else text
        }
        lastUserMessage = promptText
        val userMsg = ChatMessage(promptText, isUser = true, imageUri = imageUri?.toString())
        // Ensure a Room session exists and persist the user message before dispatching
        ensureSessionCreated(promptText)
        persistMessage(userMsg)
        setState {
            copy(
                messages = messages + userMsg,
                sendState = SendState.Sending,
                inputText = "",
                selectedImageUri = null,
                voiceRecordingState = VoiceRecordingState.Idle,
                suggestions = emptyList()
            )
        }
        sendEffect { ChatEffect.ScrollToBottom }

        if (imageUri != null) {
            telemetry.logChatTurnStarted(hasImage = true, promptLength = promptText.length)
            responseJob?.cancel()
            responseJob = viewModelScope.launch {
                processAgentTurn(hasImage = true, inputText = promptText) {
                    withContext(dispatcherProvider.default) {
                        sendMessageUseCase(promptText, imageUri)
                    }
                }
            }
            return
        }

        // Sensor slash commands → navigate to Sensor Radar screen in the requested mode
        val trimmedLower = promptText.trim().lowercase()
        if (trimmedLower == "/voice" || trimmedLower == "/memo") {
            setState { copy(messages = messages.dropLast(1), sendState = SendState.Idle, inputText = "") }
            startVoiceInput()
            return
        }
        val sensorMode: String? = when {
            trimmedLower == "/sensors" || trimmedLower.startsWith("/sensors ") -> "all"
            trimmedLower == "/stud" || trimmedLower.startsWith("/stud ")   -> "stud"
            trimmedLower == "/metal" || trimmedLower.startsWith("/metal ") -> "stud"
            trimmedLower == "/level" || trimmedLower.startsWith("/level ")                     -> "level"
            trimmedLower == "/spiritlevel" || trimmedLower.startsWith("/spiritlevel ") -> "level"
            trimmedLower == "/light" || trimmedLower.startsWith("/light ") -> "light"
            trimmedLower == "/proximity" || trimmedLower.startsWith("/proximity ") -> "proximity"
            trimmedLower == "/sound" || trimmedLower.startsWith("/sound ") -> "sound"
            trimmedLower == "/noise" || trimmedLower.startsWith("/noise ") -> "sound"
            else -> null
        }
        if (sensorMode != null) {
            // Remove the user message we just appended — it's a silent nav command
            setState { copy(messages = messages.dropLast(1), sendState = SendState.Idle, inputText = "") }
            sendEffect { ChatEffect.NavigateToRadar(mode = sensorMode) }
            return
        }

        telemetry.logChatTurnStarted(hasImage = false, promptLength = promptText.length)
        responseJob?.cancel()
        responseJob = viewModelScope.launch {
            processAgentTurn(hasImage = false, inputText = promptText) {
                withContext(dispatcherProvider.default) {
                    sendMessageUseCase(promptText, imageUri = null)
                }
            }
        }
    }

    private suspend fun submitBirthdate(year: Int, month: Int, day: Int) {
        val request = currentState.ageVerificationRequest ?: return
        if (!BuildConfig.DEBUG) {
            setState { copy(ageVerificationRequest = null, sendState = SendState.Idle) }
            appendAssistantMessage(
                getApplication<android.app.Application>().getString(R.string.capability_image),
                modelName = "CHEVERE AI"
            )
            return
        }

        val birthdate = try {
            LocalDate.of(year, month, day)
        } catch (_: DateTimeException) {
            sendEffect { ChatEffect.ShowToast(getApplication<android.app.Application>().getString(R.string.ui_enter_a_valid_birthdate)) }
            return
        }

        if (birthdate.isAfter(LocalDate.now())) {
            sendEffect { ChatEffect.ShowToast(getApplication<android.app.Application>().getString(R.string.ui_enter_a_valid_birthdate)) }
            return
        }

        setState { copy(ageVerificationRequest = null) }
        val age = Period.between(birthdate, LocalDate.now()).years
        val message = if (age < 18) {
            getApplication<android.app.Application>().getString(R.string.ui_you_must_be_18_or_older_to_request_age_restricted_image_content)
        } else {
            when (val decision = explicitImagePromptPolicy.evaluate(request.prompt)) {
                ExplicitImagePromptDecision.Allow -> null
                is ExplicitImagePromptDecision.Block -> decision.message
            }
        }
        if (message != null) {
            appendAssistantMessage(message, modelName = "CHEVERE AI")
            return
        }

        val imageCommand = parseImageCommand(request.prompt)
        startImageGenerationTurn(
            prompt = imageCommand?.prompt ?: request.prompt,
            conditionImageUri = request.imageUri,
            maskExplicitImage = true
        )
    }

    private suspend fun dismissAgeVerification() {
        setState { copy(ageVerificationRequest = null, sendState = SendState.Idle) }
        appendAssistantMessage(getApplication<android.app.Application>().getString(R.string.ui_age_verification_was_canceled), modelName = "CHEVERE AI")
    }

    private suspend fun appendAssistantMessage(
        text: String,
        modelName: String? = currentState.selectedModel
    ) {
        setState {
            copy(
                messages = messages + ChatMessage(
                    text = text,
                    isUser = false,
                    modelName = modelName
                ),
                sendState = SendState.Idle
            )
        }
        sendEffect { ChatEffect.ScrollToBottom }
    }

    private suspend fun promptForImageModelDownload() {
        appendAssistantMessage(
            text = getApplication<android.app.Application>().getString(R.string.ui_image_generation_needs_a_local_image_model_first_download_an_ima),
            modelName = "CHEVERE AI"
        )
        sendEffect { ChatEffect.ShowImageModelDownloadPrompt }
    }

    /**
     * Parses explicit slash commands that should bypass the chat agent and call
     * the installed image-generation backend directly.
     */
    private fun parseImageCommand(text: String): ImageCommand? {
        if (!BuildConfig.DEBUG) return null
        val trimmed = text.trim()
        val command = imageCommandPrefixes.firstOrNull { prefix ->
            trimmed.startsWith(prefix, ignoreCase = true)
        } ?: return null

        val prompt = trimmed.substring(command.length).trim()
        return prompt.takeIf { it.isNotBlank() }?.let(::ImageCommand)
    }

    private fun hasInstalledImageGenerationModel(): Boolean =
        currentState.localModels.any {
            it.isHealthy && it.isImageGenerationModel()
        }

    private fun looksLikeImageGenerationRequest(text: String): Boolean {
        val normalized = text.lowercase()
        val words = normalized.split(Regex("[\\s,\\.\\?!]+"))
        val hasSpecificImageVerb = words.any { it in listOf("draw", "paint", "imagine", "depict", "sketch") }
        if (hasSpecificImageVerb) return true

        val hasImageNoun = imageRequestNouns.any { it in normalized }
        val hasCreateVerb = imageRequestVerbs.any { it in normalized }
        if (hasImageNoun && hasCreateVerb) return true

        // Also match visual prompts like "create a wolf..." or "generate a sunset..."
        val isVisualCreation = (normalized.contains("create a") || normalized.contains("generate a") || normalized.contains("make a") || normalized.contains("render a")) &&
                !normalized.contains("event") && !normalized.contains("calendar") && !normalized.contains("email") &&
                !normalized.contains("code") && !normalized.contains("file") && !normalized.contains("text") && !normalized.contains("app") &&
                !normalized.contains("playlist") && !normalized.contains("reminder") && !normalized.contains("alarm") &&
                !normalized.contains("timer") && !normalized.contains("note") &&
                !normalized.contains("invoice") && !normalized.contains("receipt") && !normalized.contains("bill")

        return isVisualCreation
    }

    /**
     * Reveals or hides a generated explicit image without changing the stored
     * image file. Non-explicit messages ignore the toggle.
     */
    private fun toggleExplicitImageMask(messageIndex: Int) {
        val updatedMessages = currentState.messages.mapIndexed { index, message ->
            if (index == messageIndex && message.isExplicitImage) {
                message.copy(isImageMasked = !message.isImageMasked)
            } else {
                message
            }
        }
        setState { copy(messages = updatedMessages) }
    }

    private fun startImageGenerationTurn(
        prompt: String,
        conditionImageUri: Uri?,
        maskExplicitImage: Boolean = false
    ) {
        imageGenerationJob?.cancel()
        telemetry.logImageGenerationStarted(
            hasConditionImage = conditionImageUri != null,
            isExplicit = maskExplicitImage
        )
        setState { copy(sendState = SendState.GeneratingImage) }
        imageGenerationJob = viewModelScope.launch {
            processImageGenerationTurn(
                prompt = prompt,
                conditionImageUri = conditionImageUri,
                maskExplicitImage = maskExplicitImage
            )
        }
    }

    private suspend fun cancelImageGeneration() {
        imageGenerationJob?.cancel()
        imageGenerationJob = null
        setState { copy(sendState = SendState.Idle) }
        appendAssistantMessage(getApplication<android.app.Application>().getString(R.string.ui_image_generation_canceled), modelName = "CHEVERE AI")
    }

    private suspend fun stopActiveResponse() {
        val hadActiveWork = responseJob?.isActive == true || imageGenerationJob?.isActive == true
        telemetry.logStopRequested(hadActiveWork)
        responseJob?.cancelAndJoin()
        responseJob = null
        imageGenerationJob?.cancel()
        imageGenerationJob = null
        repository.resetAgentState()
        setState { copy(sendState = SendState.Idle) }
        if (hadActiveWork) {
            val message = ChatMessage(application.getString(R.string.ui_response_stopped), isUser = false,
                modelName = "CHEVERE AI", agentOutcome = com.neo.chevere.domain.AgentOutcome.CANCELLED)
            setState { copy(messages = messages + message, streamingText = "") }
            persistMessage(message)
        }
    }

    /**
     * Starts a voice input session. If RECORD_AUDIO permission has not been granted,
     * emits [ChatEffect.RequestMicPermission] so the UI can trigger the system dialog.
     * SpeechRecognizer must be controlled from the main thread.
     */
    private fun startVoiceInput() {
        if (currentState.isLoading || currentState.isListening) return
        if (ContextCompat.checkSelfPermission(
                application,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            viewModelScope.launch {
                sendEffect { ChatEffect.RequestMicPermission }
            }
            return
        }

        voiceTimerJob?.cancel()
        viewModelScope.launch(dispatcherProvider.main) {
            setState {
                copy(
                    isListening = true,
                    inputText = "",
                    voiceRecordingState = VoiceRecordingState.Listening(amplitude = 0.12f, elapsedSeconds = 0)
                )
            }
            voiceInputManager.startListening()
        }
        voiceTimerJob = viewModelScope.launch(dispatcherProvider.main) {
            var seconds = 0
            while (true) {
                kotlinx.coroutines.delay(1000L)
                seconds += 1
                val listening = currentState.voiceRecordingState as? VoiceRecordingState.Listening ?: break
                setState {
                    copy(voiceRecordingState = listening.copy(elapsedSeconds = seconds))
                }
            }
        }
    }

    /** Stops any active voice input session and commits the transcript. */
    private fun stopVoiceInput() {
        voiceTimerJob?.cancel()
        voiceTimerJob = null
        viewModelScope.launch(dispatcherProvider.main) {
            voiceInputManager.stopListening()
            val clean = currentState.inputText.trim()
            setState {
                copy(
                    isListening = false,
                    voiceRecordingState = if (clean.isNotBlank()) {
                        VoiceRecordingState.Recorded(clean)
                    } else {
                        VoiceRecordingState.Idle
                    }
                )
            }
        }
    }

    /** Cancels the active voice input session and discards any partial transcript. */
    private fun cancelVoiceInput() {
        voiceTimerJob?.cancel()
        voiceTimerJob = null
        viewModelScope.launch(dispatcherProvider.main) {
            voiceInputManager.cancelListening()
            setState {
                copy(
                    isListening = false,
                    inputText = "",
                    voiceRecordingState = VoiceRecordingState.Idle
                )
            }
        }
    }

    /** Executes a one-tap Whisper-style action on the captured voice memo. */
    private suspend fun runVoiceMemoAction(action: VoiceMemoAction) {
        val transcript = currentState.inputText.trim().ifBlank {
            (currentState.voiceRecordingState as? VoiceRecordingState.Recorded)?.transcript.orEmpty().trim()
        }
        if (transcript.isBlank()) {
            setState { copy(voiceRecordingState = VoiceRecordingState.Idle) }
            return
        }

        setState { copy(voiceRecordingState = VoiceRecordingState.Idle) }
        when (action) {
            VoiceMemoAction.SUMMARIZE -> {
                if (currentState.isLoading) return
                sendMessage("Summarize this voice note clearly:\n\n$transcript", null)
            }
            VoiceMemoAction.EXTRACT_TASKS -> {
                if (currentState.isLoading) return
                sendMessage("Extract actionable tasks from this voice note:\n\n$transcript", null)
            }
            VoiceMemoAction.SAVE_TO_DOCS -> {
                val noteName = "voice-note-${System.currentTimeMillis()}.md"
                when (val result = documentRepository.saveTextDocument(noteName, transcript)) {
                    is com.neo.chevere.domain.DocumentImportResult.Imported -> {
                        setState { copy(inputText = "/docs Summarize ${result.name}") }
                        sendEffect {
                            ChatEffect.ShowToast(
                                application.getString(R.string.voice_memo_saved_toast, result.name, result.chunks)
                            )
                        }
                    }
                    is com.neo.chevere.domain.DocumentImportResult.Failed -> {
                        sendEffect { ChatEffect.ShowToast(result.reason) }
                    }
                }
            }
        }
    }

    /**
     * Opens the platform share sheet for an assistant response without sending
     * anything automatically.
     */
    private fun shareMessage(messageIndex: Int) {
        val message = currentState.messages.getOrNull(messageIndex) ?: return
        if (message.isUser) return

        val shareText = buildString {
            appendLine(getApplication<android.app.Application>().getString(R.string.ui_chevere_ai_response))
            message.modelName?.let { modelName ->
                appendLine(getApplication<android.app.Application>().getString(R.string.ui_generated_by_1_s, modelName))
            }
            appendLine()
            appendLine(message.text)
            appendLine()
            appendLine("---")
            appendLine(getApplication<android.app.Application>().getString(R.string.ui_shared_via_chevere_ai))
        }

        sendEffect {
            ChatEffect.ShareMessage(
                title = getApplication<android.app.Application>().getString(R.string.ui_share_ai_response),
                text = shareText,
                imageUri = message.imageUri?.toUri()
            )
        }
    }

    private fun openReportDialog(messageIndex: Int) {
        val message = currentState.messages.getOrNull(messageIndex) ?: return
        if (message.isUser) return
        setState { copy(reportDialogMessageIndex = messageIndex) }
    }

    private fun submitReport(reason: String, details: String) {
        val messageIndex = currentState.reportDialogMessageIndex ?: return
        val message = currentState.messages.getOrNull(messageIndex) ?: run {
            setState { copy(reportDialogMessageIndex = null) }
            return
        }

        setState { copy(reportDialogMessageIndex = null) }
        viewModelScope.launch(dispatcherProvider.io) {
            val result = aiContentReportRepository.submitReport(
                messageText = message.text,
                modelName = message.modelName ?: currentState.selectedModel.ifBlank { "unknown" },
                reason = reason,
                additionalDetails = details
            )
            sendEffect {
                ChatEffect.ShowToast(application.getString(
                    if (result.isSuccess) R.string.report_submitted_toast else R.string.report_failed_toast
                ))
            }
        }
    }

    /**
     * Requests that the UI save an image-bearing assistant message to user-visible storage.
     */
    private fun saveImage(messageIndex: Int) {
        val message = currentState.messages.getOrNull(messageIndex) ?: return
        val imageUri = message.imageUri ?: return
        if (message.isExplicitImage && message.isImageMasked) {
            viewModelScope.launch {
                sendEffect { ChatEffect.ShowToast(getApplication<android.app.Application>().getString(R.string.ui_reveal_the_image_before_saving_it)) }
            }
            return
        }

        viewModelScope.launch {
            sendEffect { ChatEffect.SaveImage(Uri.parse(imageUri)) }
        }
    }

    /**
     * Requests native text-to-speech playback for an assistant response.
     */
    private fun readMessageAloud(messageIndex: Int) {
        val message = currentState.messages.getOrNull(messageIndex) ?: return
        if (message.isUser || message.text.isBlank()) return

        viewModelScope.launch {
            sendEffect { ChatEffect.ReadMessageAloud(messageIndex, message.text) }
        }
    }

    private suspend fun confirmAction() {
        sendEffect { ChatEffect.HideKeyboard }
        responseJob?.cancel()
        responseJob = viewModelScope.launch {
            processAgentTurn(hasImage = false, inputText = lastUserMessage.orEmpty()) {
                withContext(dispatcherProvider.default) {
                    repository.confirmAction()
                }
            }
        }
    }

    private suspend fun cancelAction() {
        sendEffect { ChatEffect.HideKeyboard }
        responseJob?.cancel()
        responseJob = viewModelScope.launch {
            processAgentTurn(hasImage = false, inputText = lastUserMessage.orEmpty()) {
                withContext(dispatcherProvider.default) {
                    repository.cancelAction()
                }
            }
        }
    }

    private suspend fun processAgentTurn(
        hasImage: Boolean,
        inputText: String,
        action: suspend () -> Result<AssistantResponse>
    ) {
        var result: Result<AssistantResponse>? = null
        val time = measureTimeMillis {
            result = try {
                action()
            } catch (_: CancellationException) {
                setState { copy(sendState = SendState.Idle) }
                return
            }
        }

        val currentAgentState = repository.agentState.value
        // Keep the pending callback and dialog alive until the user confirms or cancels.
        if (currentAgentState !is AgentState.WaitingForConfirmation) {
            repository.resetAgentState()
        }

        val response = result
            ?.onSuccess {
                telemetry.logChatTurnFinished(hasImage, success = currentAgentState is AgentState.Completed || currentAgentState is AgentState.Idle, durationMs = time)
            }
            ?.onFailure { e ->
                telemetry.logChatTurnFinished(
                    hasImage = hasImage,
                    success = false,
                    durationMs = time,
                    errorType = e::class.java.simpleName
                )
                telemetry.recordNonFatal(e, TelemetryConstants.Context.CHAT_TURN)
                val errorMsg = when (e) {
                    is LocationPermissionException -> {
                        sendEffect { ChatEffect.RequestLocationPermission }
                        getApplication<android.app.Application>().getString(R.string.ui_location_permission_is_required_to_get_local_weather_automatical)
                    }
                    is ContactsPermissionException -> {
                        sendEffect { ChatEffect.RequestContactsPermission }
                        getApplication<android.app.Application>().getString(R.string.ui_contacts_permission_is_required_to_look_up_people_on_this_device)
                    }
                    is CalendarPermissionException -> {
                        sendEffect { ChatEffect.RequestCalendarPermission }
                        getApplication<android.app.Application>().getString(R.string.ui_calendar_permission_is_required_to_read_calendar_events_on_this_)
                    }
                    is MicPermissionException -> {
                        sendEffect { ChatEffect.RequestMicPermission }
                        getApplication<android.app.Application>().getString(R.string.ui_microphone_permission_is_required_to_measure_the_ambient_sound_l_9f5b1d)
                    }
                    else -> e.message ?: getApplication<android.app.Application>().getString(R.string.ui_action_failed)
                }
                val aiMsg = ChatMessage(
                    text = errorMsg,
                    isUser = false,
                    inferenceTimeMs = time,
                    inputTokenCount = null,
                    outputTokenCount = null,
                    modelName = "CHEVERE AI",
                    agentState = currentAgentState.takeIf { it !is AgentState.Idle },
                    agentOutcome = currentAgentState.toOutcome() ?: com.neo.chevere.domain.AgentOutcome.FAILED,
                )
                setState { copy(messages = messages + aiMsg, sendState = SendState.Error(errorMsg)) }
                persistMessage(aiMsg)
                sendEffect { ChatEffect.ScrollToBottom }
                return
            }
            ?.getOrNull()
            ?: run {
                setState { copy(sendState = SendState.Idle) }
                return
            }

        val responseText = response.text
        val imagePayload = parseGeneratedImagePayload(responseText)
        val aiMsg = ChatMessage(
            text = imagePayload?.caption ?: responseText,
            agentUiContent = response.agentUiContent,
            isUser = false,
            inferenceTimeMs = time,
            inputTokenCount = response.usage?.inputTokens,
            outputTokenCount = response.usage?.outputTokens,
            tokenCountKind = com.neo.chevere.domain.TokenCountKind.MEASURED,
            documentEvidence = response.documentEvidence,
            agentOutcome = currentAgentState.toOutcome(),
            imageUri = imagePayload?.imageUri,
            modelName = currentState.selectedModel.replace(
                Constants.ModelFiles.LITERTLM_EXTENSION,
                ""
            ).uppercase(),
            agentState = currentAgentState.takeIf { it !is AgentState.Idle }
        )
        responseJob = null
        setState { copy(messages = messages + aiMsg, sendState = SendState.Idle, streamingText = "") }
        persistMessage(aiMsg)
        sendEffect { ChatEffect.ScrollToBottom }
    }

    /** Revalidates task membership at the MVI boundary and updates only its status in Room. */
    private suspend fun setTaskCompleted(intent: ChatIntent.SetTaskCompleted) {
        val surface = currentState.messages.asSequence()
            .filter { !it.isUser }
            .mapNotNull { it.agentUiContent as? AgentUiContent.TaskChecklist }
            .firstOrNull { it.surfaceId == intent.surfaceId } ?: return
        if (intent.taskId !in surface.taskIds) return
        val data = currentState.taskChecklistData as? TaskChecklistData.Ready ?: return
        if (intent.taskId in data.updatingIds) return
        setState { copy(taskChecklistData = data.withUpdatingIds(data.updatingIds + intent.taskId)) }
        try {
            val status = if (intent.completed) TaskStatus.COMPLETED else TaskStatus.PENDING
            taskRepository.setTaskStatus(intent.taskId, status)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            sendEffect { ChatEffect.ShowToast(application.getString(R.string.a2ui_update_failed)) }
        } finally {
            setState {
                val latest = taskChecklistData as? TaskChecklistData.Ready
                copy(taskChecklistData = latest?.withUpdatingIds(latest.updatingIds - intent.taskId)
                    ?: taskChecklistData)
            }
        }
    }

    private suspend fun processImageGenerationTurn(
        prompt: String,
        conditionImageUri: Uri?,
        maskExplicitImage: Boolean = false
    ) {
        var wasCanceled = false
        var result: Result<ImageGenerationResult.Success>? = null
        val time = measureTimeMillis {
            result = try {
                withContext(dispatcherProvider.default) {
                    val defaultRatioStr = preferenceManager.defaultImageAspectRatioPreference.first()
                    val defaultRatio = ImageAspectRatio.fromString(defaultRatioStr)
                    val defaultSteps = preferenceManager.defaultImageStepsPreference.first()
                    val defaultGuidanceScale = preferenceManager.defaultImageGuidanceScalePreference.first()
                    val defaultNegativePrompt = preferenceManager.defaultImageNegativePromptPreference.first()

                    repository.generateImage(
                        ImageGenerationRequest(
                            prompt = prompt,
                            conditionImageUri = conditionImageUri,
                            width = defaultRatio.pixelWidth,
                            height = defaultRatio.pixelHeight,
                            steps = defaultSteps,
                            guidanceScale = defaultGuidanceScale,
                            negativePrompt = defaultNegativePrompt.takeIf { it.isNotBlank() }
                        )
                    )
                }
            } catch (e: CancellationException) {
                wasCanceled = true
                return@measureTimeMillis
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        if (wasCanceled) {
            setState { copy(sendState = SendState.Idle) }
            return
        }

        val generation = result
            ?.onFailure { e ->
                telemetry.logImageGenerationFinished(
                    success = false,
                    durationMs = time,
                    isExplicit = maskExplicitImage,
                    errorType = e::class.java.simpleName
                )
                telemetry.recordNonFatal(e, TelemetryConstants.Context.IMAGE_GENERATION)
                setState {
                    copy(
                        sendState = SendState.Error(
                            e.message ?: getApplication<android.app.Application>().getString(R.string.ui_image_generation_failed)
                        )
                    )
                }
                return
            }
            ?.getOrNull()
            ?: run {
                setState { copy(sendState = SendState.Idle) }
                return
            }

        val aiMsg = ChatMessage(
            text = getApplication<android.app.Application>().getString(R.string.ui_generated_image_for_1_s, generation.prompt),
            isUser = false,
            inferenceTimeMs = time,
            inputTokenCount = estimateTokenCount(prompt),
            outputTokenCount = estimateTokenCount(getApplication<android.app.Application>().getString(R.string.ui_generated_image_for_1_s, generation.prompt)),
            imageUri = generation.imageUri.toString(),
            modelName = currentState.selectedModel.replace(Constants.ModelFiles.ZIP_EXTENSION, "")
                .uppercase(),
            isExplicitImage = maskExplicitImage,
            isImageMasked = maskExplicitImage
        )
        imageGenerationJob = null
        telemetry.logImageGenerationFinished(
            success = true,
            durationMs = time,
            isExplicit = maskExplicitImage
        )
        setState { copy(messages = messages + aiMsg, sendState = SendState.Idle) }
        persistMessage(aiMsg)
        sendEffect { ChatEffect.ScrollToBottom }
    }

    private fun parseGeneratedImagePayload(text: String): GeneratedImagePayload? {
        if (!text.startsWith(Constants.Agent.IMAGE_GENERATION_RESULT_PREFIX)) return null

        val fields = text.split(Constants.Agent.IMAGE_GENERATION_RESULT_SEPARATOR)
            .drop(1)
            .mapNotNull { part ->
                val index = part.indexOf("=")
                if (index <= 0) null else part.substring(0, index) to part.substring(index + 1)
            }
            .toMap()

        val uri = fields["uri"] ?: return null
        val prompt = fields["prompt"].orEmpty()
        return GeneratedImagePayload(
            imageUri = uri,
            caption = if (prompt.isBlank()) getApplication<android.app.Application>().getString(R.string.ui_generated_image) else getApplication<android.app.Application>().getString(R.string.ui_generated_image_for_1_s, prompt)
        )
    }

    private fun estimateTokenCount(text: String): Int =
        (text.length / APPROX_CHARS_PER_TOKEN).toInt().coerceAtLeast(if (text.isBlank()) 0 else 1)

    private fun com.neo.chevere.domain.InstalledModel.isHealthy(): Boolean =
        installStatus == com.neo.chevere.domain.InstallStatus.INSTALLED

    private data class GeneratedImagePayload(
        val imageUri: String,
        val caption: String
    )

    /**
     * Parsed direct image-generation command.
     */
    private data class ImageCommand(val prompt: String)

    /** Cancels obsolete predictions when the conversation, task, or model changes. */
    private fun observeSuggestions() {
        viewModelScope.launch {
            uiState.map { state ->
                SuggestionContext(state.messages.takeLast(4), state.selectedImageUri != null,
                    state.currentSessionId, state.selectedModel,
                    state.isReady && !state.isAiBusy && !state.isWaitingForConfirmation && state.inputText.isBlank())
            }.distinctUntilChanged().collectLatest { context ->
                setState { copy(suggestions = emptyList()) }
                if (!context.available || (context.messages.isEmpty() && !context.hasImage)) return@collectLatest
                kotlinx.coroutines.delay(500)
                val predictions = withContext(dispatcherProvider.default) {
                    suggestionRepository.predict(context.messages, context.hasImage)
                }
                setState { copy(suggestions = predictions) }
            }
        }
    }

    /** Presentation snapshot used to discard predictions from superseded tasks. */
    private data class SuggestionContext(
        val messages: List<ChatMessage>, val hasImage: Boolean, val sessionId: Long?,
        val model: String, val available: Boolean
    )

    private companion object {
        const val APPROX_CHARS_PER_TOKEN = 4.0
        val imageCommandPrefixes = Constants.Commands.IMAGE_GENERATION
        val imageRequestVerbs = listOf("create", "generate", "make", "draw", "render", "paint")
        val imageRequestNouns =
            listOf("image", "picture", "photo", "art", "illustration", "portrait")
    }
}

private fun com.neo.chevere.domain.InstalledModel.isImageGenerationModel(): Boolean =
    taskType == ModelTaskType.IMAGE_GENERATION || ModelCapability.IMAGE_GEN in capabilities

private fun com.neo.chevere.domain.InstalledModel.isChatModel(): Boolean =
    !isImageGenerationModel()
