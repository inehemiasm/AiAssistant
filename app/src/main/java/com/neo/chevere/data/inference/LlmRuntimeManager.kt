package com.neo.chevere.data.inference

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.neo.chevere.core.Constants
import com.neo.chevere.core.DispatcherProvider
import com.neo.chevere.domain.InitializationStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LlmRuntimeManager"

/**
 * Manages the lifecycle of the LiteRT-LM engine and active conversation.
 */
@Singleton
class LlmRuntimeManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val messageFactory: MultimodalMessageFactory,
    private val engineWrapper: LlmEngineWrapper,
    private val dispatcherProvider: DispatcherProvider
) {
    private val engineLock = Mutex()
    private var activeConversation: ConversationWrapper? = null
    private var isVisionEnabled = false
    private var isInitialized = false

    private val _initStatus =
        MutableStateFlow<InitializationStatus>(InitializationStatus.Uninitialized)
    val initStatus: StateFlow<InitializationStatus> = _initStatus.asStateFlow()

    fun isVisionSupported(): Boolean = isVisionEnabled

    suspend fun initialize(
        modelPath: String,
        enableVision: Boolean
    ): Result<Unit> = withContext(dispatcherProvider.default) {
        engineLock.withLock {
            _initStatus.value = InitializationStatus.Initializing("RESETTING RUNTIME...")
            closeCurrentResources()

            val neuralCache = File(context.cacheDir, Constants.Inference.NEURAL_CACHE_DIR).apply {
                if (!exists()) mkdirs()
            }

            val backends = ModelRuntimeProfile.backends(modelPath, enableVision)

            var lastError: Throwable? = null

            for ((main, vision, label) in backends) {
                _initStatus.value = InitializationStatus.Initializing("ATTEMPTING: $label")
                val result = runCatching {
                    val config = EngineConfig(
                        modelPath = modelPath,
                        backend = main,
                        visionBackend = vision,
                        maxNumTokens = ModelRuntimeProfile.contextSize(modelPath),
                        cacheDir = neuralCache.absolutePath
                    )

                    engineWrapper.initialize(config)

                    _initStatus.value = InitializationStatus.Initializing("WARMING UP $label...")
                    performWarmup(vision != null)
                    activeConversation = engineWrapper.createConversation()
                }

                if (result.isSuccess) {
                    Timber.tag(TAG).i("Successfully initialized with $label")
                    isVisionEnabled = vision != null
                    isInitialized = true
                    _initStatus.value = InitializationStatus.Ready
                    return@withContext Result.success(Unit)
                }

                lastError = result.exceptionOrNull()
                if (lastError is CancellationException) {
                    engineWrapper.close()
                    throw lastError
                }
                Timber.tag(TAG).e("$label failed: ${lastError?.message}")
                engineWrapper.close()
                neuralCache.deleteRecursively()
                neuralCache.mkdirs()
            }

            val failureMessage = if (ModelRuntimeProfile.isTextOnlyGpu(modelPath)) {
                "This text-only Gemma variant requires a compatible GPU. Try the standard Gemma 4 E2B model if GPU loading fails."
            } else {
                "Model loading failed. Check that this model variant supports your device and runtime, or try another model."
            }
            _initStatus.value = InitializationStatus.Failure(failureMessage, lastError)
            Result.failure(IllegalStateException(failureMessage, lastError))
        }
    }

    private suspend fun performWarmup(vision: Boolean) {
        val warmupConv = engineWrapper.createConversation()
        try {
            val message = if (vision) {
                messageFactory.createWarmupMessage()
            } else {
                messageFactory.createTextMessage("warmup")
            }
            warmupConv.sendMessage(message)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).w("Warmup skipped: ${e.message}")
        } finally {
            warmupConv.close()
        }
    }

    /** Predicts UI follow-ups without changing the active chat conversation. */
    suspend fun predictInIsolation(message: Message): Result<List<Message>> = withContext(dispatcherProvider.default) {
        engineLock.withLock {
            if (!isInitialized) return@withContext Result.failure(IllegalStateException("No model loaded"))
            // LiteRT supports one native session. ConversationContextManager owns retained chat memory.
            activeConversation?.close()
            activeConversation = null
            var prediction: ConversationWrapper? = null
            try {
                val conversation = engineWrapper.createConversation()
                prediction = conversation
                Result.success(conversation.sendMessageAsync(message).take(128).toList())
            } finally {
                try { prediction?.close() } finally {
                    activeConversation = if (isInitialized) engineWrapper.createConversation() else null
                }
            }
        }
    }

    suspend fun sendMessage(message: Message): Result<Message> =
        withContext(dispatcherProvider.default) {
            engineLock.withLock {
                val conversation = activeConversation ?: return@withContext Result.failure(
                    IllegalStateException("No active conversation")
                )
                try {
                    Result.success(conversation.sendMessage(message))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }

    fun sendMessageAsync(message: Message): Flow<Result<Message>> = flow {
        engineLock.withLock {
            val conversation = activeConversation ?: run {
                emit(Result.failure(IllegalStateException("No active conversation")))
                return@withLock
            }
            try {
                conversation.sendMessageAsync(message).collect { chunk ->
                    emit(Result.success(chunk))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emit(Result.failure(e))
            }
        }
    }

    suspend fun clearConversation() = withContext(dispatcherProvider.default) {
        engineLock.withLock {
            activeConversation?.close()
            activeConversation = if (isInitialized) engineWrapper.createConversation() else null
        }
    }

    /** Waits for in-flight prediction/inference cleanup before releasing native memory. */
    suspend fun close() = withContext(dispatcherProvider.default) {
        engineLock.withLock { closeCurrentResources() }
    }

    private fun closeCurrentResources() {
        activeConversation?.close()
        activeConversation = null
        engineWrapper.close()
        isVisionEnabled = false
        isInitialized = false
        _initStatus.value = InitializationStatus.Uninitialized
    }
}
