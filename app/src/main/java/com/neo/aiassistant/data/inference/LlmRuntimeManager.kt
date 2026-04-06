package com.neo.aiassistant.data.inference

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the lifecycle of the LiteRT-LM engine and active conversation.
 * Handles hardware backends and initialization state.
 */
@Singleton
class LlmRuntimeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val messageFactory: MultimodalMessageFactory
) {
    private val engineLock = Mutex()
    private var engine: Engine? = null
    private var activeConversation: Conversation? = null
    private var isVisionEnabled = false

    private val _initStatus = MutableSharedFlow<String>(replay = 1)
    val initStatus: SharedFlow<String> = _initStatus.asSharedFlow()

    fun isVisionSupported(): Boolean = isVisionEnabled

    suspend fun initialize(modelPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        engineLock.withLock {
            _initStatus.emit("RESETTING RUNTIME...")
            closeCurrentResources()

            val neuralCache = File(context.cacheDir, "neural_cache").apply { if (!exists()) mkdirs() }

            val backends = listOf(
                Triple(Backend.GPU(), Backend.CPU(), "GPU + Vision"),
                Triple(Backend.CPU(), Backend.CPU(), "CPU + Vision"),
                Triple(Backend.CPU(), null, "Text-Only")
            )

            var lastError: Throwable? = null

            for ((main, vision, label) in backends) {
                _initStatus.emit("ATTEMPTING: $label")
                val result = runCatching {
                    val config = EngineConfig(
                        modelPath = modelPath,
                        backend = main,
                        visionBackend = vision,
                        maxNumTokens = 4096,
                        cacheDir = neuralCache.absolutePath
                    )
                    
                    // Apply maxNumImages restriction if applicable
                    if (vision != null) {
                        try {
                            val field = config.javaClass.getDeclaredField("maxNumImages")
                            field.isAccessible = true
                            field.set(config, 1)
                        } catch (e: Exception) { /* ignore */ }
                    }

                    val newEngine = Engine(config)
                    newEngine.initialize()
                    
                    _initStatus.emit("WARMING UP $label...")
                    performWarmup(newEngine, vision != null)
                    
                    newEngine
                }

                if (result.isSuccess) {
                    Log.i("LlmRuntimeManager", "Successfully initialized with $label")
                    engine = result.getOrThrow()
                    isVisionEnabled = vision != null
                    activeConversation = engine?.createConversation()
                    _initStatus.emit("READY")
                    return@withContext Result.success(Unit)
                }

                lastError = result.exceptionOrNull()
                Log.e("LlmRuntimeManager", "$label failed: ${lastError?.message}")
                neuralCache.deleteRecursively()
                neuralCache.mkdirs()
            }

            _initStatus.emit("INITIALIZATION FAILED")
            Result.failure(lastError ?: Exception("Hardware incompatible or model corrupt."))
        }
    }

    private suspend fun performWarmup(tempEngine: Engine, vision: Boolean) {
        val warmupConv = tempEngine.createConversation()
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

    suspend fun sendMessage(message: Message): Result<Message> = withContext(Dispatchers.IO) {
        engineLock.withLock {
            val conversation = activeConversation ?: return@withContext Result.failure(IllegalStateException("No active conversation"))
            runCatching { conversation.sendMessage(message) }
        }
    }

    suspend fun clearConversation() {
        engineLock.withLock {
            activeConversation?.close()
            activeConversation = engine?.createConversation()
        }
    }

    fun close() {
        closeCurrentResources()
    }

    private fun closeCurrentResources() {
        activeConversation?.close()
        activeConversation = null
        engine?.close()
        engine = null
        isVisionEnabled = false
    }
}
