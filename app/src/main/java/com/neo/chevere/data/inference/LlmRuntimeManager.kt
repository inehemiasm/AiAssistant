package com.neo.chevere.data.inference

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.neo.chevere.core.Constants
import com.neo.chevere.core.DispatcherProvider
import com.neo.chevere.domain.InitializationStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

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

            val backends = buildList {
                if (enableVision) {
                    add(Triple(Backend.GPU(), Backend.CPU(), "GPU + Vision"))
                    add(Triple(Backend.CPU(), Backend.CPU(), "CPU + Vision"))
                }
                add(Triple(Backend.CPU(), null, "Text-Only"))
            }

            var lastError: Throwable? = null

            for ((main, vision, label) in backends) {
                _initStatus.value = InitializationStatus.Initializing("ATTEMPTING: $label")
                val result = runCatching {
                    val config = EngineConfig(
                        modelPath = modelPath,
                        backend = main,
                        visionBackend = vision,
                        maxNumTokens = Constants.Inference.MAX_NUM_TOKENS,
                        cacheDir = neuralCache.absolutePath
                    )

                    engineWrapper.initialize(config)

                    _initStatus.value = InitializationStatus.Initializing("WARMING UP $label...")
                    performWarmup(vision != null)
                }

                if (result.isSuccess) {
                    Log.i("LlmRuntimeManager", "Successfully initialized with $label")
                    isVisionEnabled = vision != null
                    activeConversation = engineWrapper.createConversation()
                    isInitialized = true
                    _initStatus.value = InitializationStatus.Ready
                    return@withContext Result.success(Unit)
                }

                lastError = result.exceptionOrNull()
                Log.e("LlmRuntimeManager", "$label failed: ${lastError?.message}")
                engineWrapper.close()
                neuralCache.deleteRecursively()
                neuralCache.mkdirs()
            }

            val failureMessage = "Hardware incompatible or model corrupt."
            _initStatus.value = InitializationStatus.Failure(failureMessage, lastError)
            Result.failure(lastError ?: Exception(failureMessage))
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
        } catch (e: Exception) {
            Log.w("LlmRuntimeManager", "Warmup skipped: ${e.message}")
        } finally {
            warmupConv.close()
        }
    }

    suspend fun sendMessage(message: Message): Result<Message> =
        withContext(dispatcherProvider.default) {
            engineLock.withLock {
                val conversation = activeConversation ?: return@withContext Result.failure(
                    IllegalStateException("No active conversation")
                )
                runCatching { conversation.sendMessage(message) }
            }
        }

    suspend fun clearConversation() = withContext(dispatcherProvider.default) {
        engineLock.withLock {
            activeConversation?.close()
            activeConversation = if (isInitialized) engineWrapper.createConversation() else null
        }
    }

    fun close() {
        closeCurrentResources()
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
