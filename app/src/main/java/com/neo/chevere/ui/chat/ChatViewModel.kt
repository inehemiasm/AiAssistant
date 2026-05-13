package com.neo.chevere.ui.chat

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.neo.chevere.BuildConfig
import com.neo.chevere.core.BaseViewModel
import com.neo.chevere.core.Constants
import com.neo.chevere.core.DispatcherProvider
import com.neo.chevere.data.PreferenceManager
import com.neo.chevere.data.telemetry.AppTelemetry
import com.neo.chevere.data.telemetry.TelemetryConstants
import com.neo.chevere.domain.ChatMessage
import com.neo.chevere.domain.ExplicitImagePromptDecision
import com.neo.chevere.domain.ExplicitImagePromptPolicy
import com.neo.chevere.domain.ChatRepository
import com.neo.chevere.domain.ImageGenerationRequest
import com.neo.chevere.domain.ImageGenerationResult
import com.neo.chevere.domain.InitializationStatus
import com.neo.chevere.domain.InitializeChatUseCase
import com.neo.chevere.domain.ModelCapability
import com.neo.chevere.domain.ModelTaskType
import com.neo.chevere.domain.SendMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.DateTimeException
import java.time.LocalDate
import java.time.Period
import javax.inject.Inject
import kotlin.system.measureTimeMillis

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val application: Application,
    private val repository: ChatRepository,
    private val initializeChatUseCase: InitializeChatUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val preferenceManager: PreferenceManager,
    private val dispatcherProvider: DispatcherProvider,
    private val telemetry: AppTelemetry
) : BaseViewModel<ChatState, ChatIntent, ChatEffect>(application, ChatState()) {

    private val explicitImagePromptPolicy = ExplicitImagePromptPolicy()
    private val intentMutex = Mutex()
    private var initJob: Job? = null
    private var imageGenerationJob: Job? = null
    private var responseJob: Job? = null

    init {
        // 1. Observe global initialization status from the repository
        observeInitStatus()
        
        // 2. Observe agent state for tool executions
        observeAgentStatus()
        
        // 3. React to model selection changes (Source of Truth)
        observeSelectedModel()
        
        // 4. Initial load of local models metadata
        viewModelScope.launch {
            updateLocalModels()
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
                    val selected = models.find { it.id == savedModel || it.fileName == savedModel }
                    setState { copy(localModels = models) }

                    if (selected?.isImageGenerationModel() == true) {
                        val chatModel = models.firstOrNull { it.isHealthy && it.isChatModel() }
                        if (chatModel != null) {
                            withContext(dispatcherProvider.io) {
                                preferenceManager.updateSelectedModel(chatModel.id)
                            }
                        } else {
                            setState { copy(selectedModel = "", runtimeState = RuntimeState.Uninitialized) }
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
                Log.d("ChatViewModel", "Init status update: $status")
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
                    setState { copy(messages = emptyList(), runtimeState = RuntimeState.Uninitialized) }
                }
                ChatIntent.ClearError -> setState { copy(
                    runtimeState = if (currentState.runtimeState is RuntimeState.Error) RuntimeState.Uninitialized else currentState.runtimeState,
                    sendState = SendState.Idle
                ) }
                ChatIntent.ClearConversation -> clearConversation()
                is ChatIntent.UpdateInputText -> setState { copy(inputText = intent.text) }
                is ChatIntent.SelectImage -> setState { copy(selectedImageUri = intent.uri) }
                is ChatIntent.SetTempCameraUri -> setState { copy(tempCameraUri = intent.uri) }
                ChatIntent.ConfirmAction -> confirmAction()
                ChatIntent.CancelAction -> cancelAction()
                ChatIntent.CancelGeneration -> cancelImageGeneration()
                ChatIntent.StopResponse -> stopActiveResponse()
                is ChatIntent.SubmitBirthdate -> submitBirthdate(intent.year, intent.month, intent.day)
                ChatIntent.DismissAgeVerification -> dismissAgeVerification()
                is ChatIntent.ToggleExplicitImageMask -> toggleExplicitImageMask(intent.messageIndex)
                is ChatIntent.ShareMessage -> shareMessage(intent.messageIndex)
            }
        }
    }

    private fun clearConversation() {
        viewModelScope.launch {
            withContext(dispatcherProvider.io) {
                repository.clearConversation()
            }
            setState { copy(messages = emptyList()) }
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
        sendEffect { ChatEffect.HideKeyboard }
        val promptText = text.ifBlank {
            if (imageUri != null) "Describe this image." else text
        }
        val userMsg = ChatMessage(promptText, isUser = true, imageUri = imageUri?.toString())
        setState { copy(messages = messages + userMsg, sendState = SendState.Sending, inputText = "", selectedImageUri = null) }
        sendEffect { ChatEffect.ScrollToBottom }

        if (explicitImagePromptPolicy.requiresAgeVerification(promptText)) {
            if (!BuildConfig.DEBUG) {
                appendAssistantMessage(Constants.ContentPolicy.EXPLICIT_RELEASE_BLOCK_MESSAGE, modelName = "CHEVERE AI")
                return
            }

            setState {
                copy(
                    ageVerificationRequest = AgeVerificationRequest(promptText, imageUri),
                    sendState = SendState.Idle
                )
            }
            return
        }

        val imageCommand = parseImageCommand(promptText)
        if (imageUri == null && (imageCommand != null || looksLikeImageGenerationRequest(promptText)) && !hasInstalledImageGenerationModel()) {
            promptForImageModelDownload()
            return
        }

        if (imageUri == null && imageCommand != null) {
            startImageGenerationTurn(imageCommand.prompt, imageUri)
            return
        }

        telemetry.logChatTurnStarted(hasImage = imageUri != null, promptLength = promptText.length)
        responseJob?.cancel()
        responseJob = viewModelScope.launch {
            processAgentTurn(hasImage = imageUri != null) {
                withContext(dispatcherProvider.default) {
                    sendMessageUseCase(promptText, imageUri)
                }
            }
        }
    }

    private suspend fun submitBirthdate(year: Int, month: Int, day: Int) {
        val request = currentState.ageVerificationRequest ?: return
        if (!BuildConfig.DEBUG) {
            setState { copy(ageVerificationRequest = null, sendState = SendState.Idle) }
            appendAssistantMessage(Constants.ContentPolicy.EXPLICIT_RELEASE_BLOCK_MESSAGE, modelName = "CHEVERE AI")
            return
        }

        val birthdate = try {
            LocalDate.of(year, month, day)
        } catch (_: DateTimeException) {
            sendEffect { ChatEffect.ShowToast("Enter a valid birthdate.") }
            return
        }

        if (birthdate.isAfter(LocalDate.now())) {
            sendEffect { ChatEffect.ShowToast("Enter a valid birthdate.") }
            return
        }

        setState { copy(ageVerificationRequest = null) }
        val age = Period.between(birthdate, LocalDate.now()).years
        val message = if (age < 18) {
            "You must be 18 or older to request age-restricted image content."
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
        appendAssistantMessage("Age verification was canceled.", modelName = "CHEVERE AI")
    }

    private suspend fun appendAssistantMessage(text: String, modelName: String? = currentState.selectedModel) {
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
            text = "Image generation needs a local image model first. Download an image generation model from Models, then try this prompt again.",
            modelName = "CHEVERE AI"
        )
        sendEffect { ChatEffect.ShowImageModelDownloadPrompt }
    }

    /**
     * Parses explicit slash commands that should bypass the chat agent and call
     * the installed image-generation backend directly.
     */
    private fun parseImageCommand(text: String): ImageCommand? {
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
        val hasImageNoun = imageRequestNouns.any { it in normalized }
        val hasCreateVerb = imageRequestVerbs.any { it in normalized }
        return hasImageNoun && hasCreateVerb
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
        appendAssistantMessage("Image generation canceled.", modelName = "CHEVERE AI")
    }

    private suspend fun stopActiveResponse() {
        val hadActiveWork = responseJob?.isActive == true || imageGenerationJob?.isActive == true
        telemetry.logStopRequested(hadActiveWork)
        responseJob?.cancel()
        responseJob = null
        imageGenerationJob?.cancel()
        imageGenerationJob = null
        setState { copy(sendState = SendState.Idle) }
        if (hadActiveWork) {
            appendAssistantMessage("Stopped.", modelName = "CHEVERE AI")
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
            appendLine("Chevere AI response")
            message.modelName?.let { modelName -> appendLine("Model: $modelName") }
            message.imageUri?.let { imageUri -> appendLine("Image: $imageUri") }
            appendLine()
            appendLine(message.text)
        }

        sendEffect {
            ChatEffect.ShareText(
                title = "Share Chevere AI response",
                text = shareText
            )
        }
    }

    private suspend fun confirmAction() {
        sendEffect { ChatEffect.HideKeyboard }
        responseJob?.cancel()
        responseJob = viewModelScope.launch {
            processAgentTurn(hasImage = false) {
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
            processAgentTurn(hasImage = false) {
                withContext(dispatcherProvider.default) {
                    repository.cancelAction()
                }
            }
        }
    }

    private suspend fun processAgentTurn(hasImage: Boolean, action: suspend () -> Result<String>) {
        var result: Result<String>? = null
        val time = measureTimeMillis {
            result = try {
                action()
            } catch (_: CancellationException) {
                setState { copy(sendState = SendState.Idle) }
                return
            }
        }

        val responseText = result
            ?.onSuccess {
                telemetry.logChatTurnFinished(hasImage, success = true, durationMs = time)
            }
            ?.onFailure { e ->
                telemetry.logChatTurnFinished(
                    hasImage = hasImage,
                    success = false,
                    durationMs = time,
                    errorType = e::class.java.simpleName
                )
                telemetry.recordNonFatal(e, TelemetryConstants.Context.CHAT_TURN)
                appendAssistantMessage(e.message ?: "Action failed", modelName = "CHEVERE AI")
                return
            }
            ?.getOrNull()
            ?: run {
                setState { copy(sendState = SendState.Idle) }
                return
            }

        val imagePayload = parseGeneratedImagePayload(responseText)
        val aiMsg = ChatMessage(
            text = imagePayload?.caption ?: responseText,
            isUser = false,
            inferenceTimeMs = time,
            imageUri = imagePayload?.imageUri,
            modelName = currentState.selectedModel.replace(Constants.ModelFiles.LITERTLM_EXTENSION, "").uppercase()
        )
        responseJob = null
        setState { copy(messages = messages + aiMsg, sendState = SendState.Idle) }
        sendEffect { ChatEffect.ScrollToBottom }
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
                    repository.generateImage(
                        ImageGenerationRequest(
                            prompt = prompt,
                            conditionImageUri = conditionImageUri
                        )
                    )
                }
            } catch (_: CancellationException) {
                wasCanceled = true
                return@measureTimeMillis
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
                setState { copy(sendState = SendState.Error(e.message ?: "Image generation failed")) }
                return
            }
            ?.getOrNull()
            ?: run {
                setState { copy(sendState = SendState.Idle) }
                return
            }

        val aiMsg = ChatMessage(
            text = "Generated image for: ${generation.prompt}",
            isUser = false,
            inferenceTimeMs = time,
            imageUri = generation.imageUri.toString(),
            modelName = currentState.selectedModel.replace(Constants.ModelFiles.ZIP_EXTENSION, "").uppercase(),
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
            caption = if (prompt.isBlank()) "Generated image" else "Generated image for: $prompt"
        )
    }

    private data class GeneratedImagePayload(
        val imageUri: String,
        val caption: String
    )

    /**
     * Parsed direct image-generation command.
     */
    private data class ImageCommand(val prompt: String)

    private companion object {
        val imageCommandPrefixes = Constants.Commands.IMAGE_GENERATION
        val imageRequestVerbs = listOf("create", "generate", "make", "draw", "render", "paint")
        val imageRequestNouns = listOf("image", "picture", "photo", "art", "illustration", "portrait")
    }
}

private fun com.neo.chevere.domain.InstalledModel.isImageGenerationModel(): Boolean =
    taskType == ModelTaskType.IMAGE_GENERATION || ModelCapability.IMAGE_GEN in capabilities

private fun com.neo.chevere.domain.InstalledModel.isChatModel(): Boolean =
    !isImageGenerationModel()
