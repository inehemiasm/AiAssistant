package com.neo.aiassistant.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.firebase.firestore.FirebaseFirestore
import com.neo.aiassistant.core.ImageUtils
import com.neo.aiassistant.domain.ChatRepository
import com.neo.aiassistant.domain.DownloadProgress
import com.neo.aiassistant.domain.ModelEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore
) : ChatRepository {
    private var engine: Engine? = null
    private var activeConversation: Conversation? = null
    private var isVisionEnabled = false
    private val workManager = WorkManager.getInstance(context)
    
    // Mutex to prevent concurrent initialization or message sending, 
    // which is the primary cause of "session already exists" errors.
    private val engineLock = Mutex()

    private val _initStatus = MutableSharedFlow<String>(replay = 1)
    override fun getInitStatus(): Flow<String> = _initStatus.asSharedFlow()

    override fun isVisionSupported(): Boolean = isVisionEnabled

    override suspend fun initializeModel(modelPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        engineLock.withLock {
            val modelFile = File(modelPath)
            if (!modelFile.exists() || modelFile.length() < 1024 * 1024) {
                return@withContext Result.failure(Exception("Model file missing or corrupted at: $modelPath"))
            }

            Log.d("ChatRepository", "--- STARTING ENGINE INITIALIZATION ---")
            _initStatus.emit("CLEANING UP PREVIOUS SESSION...")
            
            // Critical: Close everything before re-initializing
            closeCurrentResources()

            val neuralCache = File(context.cacheDir, "neural_cache")
            if (!neuralCache.exists()) neuralCache.mkdirs()

            // We attempt backends in order of preference: GPU Multimodal -> CPU Multimodal -> CPU Text
            val attempts = listOf(
                Triple(Backend.GPU(), Backend.CPU(), "GPU Acceleration (Vision Enabled)"),
                Triple(Backend.CPU(), Backend.CPU(), "CPU Mode (Vision Enabled)"),
                Triple(Backend.CPU(), null, "CPU Mode (Text-Only)")
            )

            var lastError: Throwable? = null

            for ((mainBackend, visionBackend, label) in attempts) {
                _initStatus.emit("CONFIGURING: $label")
                val result = runCatching {
                    val config = EngineConfig(
                        modelPath = modelPath,
                        backend = mainBackend,
                        visionBackend = visionBackend,
                        maxNumTokens = 4096,
                        cacheDir = neuralCache.absolutePath
                    )
                    
                    // LiteRT-LM 0.10.0 reflection hack for maxNumImages if not in constructor
                    if (visionBackend != null) {
                        try {
                            val field = config.javaClass.getDeclaredField("maxNumImages")
                            field.isAccessible = true
                            field.set(config, 1)
                        } catch (e: Exception) { /* ignore if field doesn't exist */ }
                    }

                    val newEngine = Engine(config)
                    newEngine.initialize()
                    
                    // Warmup is crucial for GPU kernel residency and initial latency
                    performWarmup(newEngine, visionBackend != null)
                    
                    newEngine
                }
                
                if (result.isSuccess) {
                    Log.i("ChatRepository", "Successfully initialized with: $label")
                    engine = result.getOrThrow()
                    isVisionEnabled = visionBackend != null
                    activeConversation = engine?.createConversation()
                    _initStatus.emit("SYSTEM READY")
                    return@withContext Result.success(Unit)
                }
                
                lastError = result.exceptionOrNull()
                Log.e("ChatRepository", "Backend $label failed: ${lastError?.message}")
                
                // Reset cache between attempts to ensure no corrupted artifacts interfere
                neuralCache.deleteRecursively()
                neuralCache.mkdirs()
            }

            _initStatus.emit("INITIALIZATION FAILED")
            Result.failure(lastError ?: Exception("Unable to initialize any inference backend."))
        }
    }

    private suspend fun performWarmup(tempEngine: Engine, vision: Boolean) {
        _initStatus.emit("WARMING UP NEURAL CORES...")
        val warmupConv = tempEngine.createConversation()
        try {
            if (vision) {
                val dummyBitmap = createBitmap(448, 448, Bitmap.Config.ARGB_8888)
                val bos = ByteArrayOutputStream()
                dummyBitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)
                val message = Message.user(
                    Contents.of(
                        Content.ImageBytes(bos.toByteArray()),
                        Content.Text("warmup")
                    )
                )
                warmupConv.sendMessage(message)
            } else {
                warmupConv.sendMessage("warmup")
            }
        } catch (e: Exception) {
            Log.w("ChatRepository", "Warmup failed, but continuing: ${e.message}")
        } finally {
            // Explicitly close the warmup conversation to free the session slot
            warmupConv.close()
        }
    }

    private fun closeCurrentResources() {
        try {
            activeConversation?.close()
        } catch (e: Exception) {
            Log.w("ChatRepository", "Error closing conversation: ${e.message}")
        } finally {
            activeConversation = null
        }

        try {
            engine?.close()
        } catch (e: Exception) {
            Log.w("ChatRepository", "Error closing engine: ${e.message}")
        } finally {
            engine = null
        }
        
        isVisionEnabled = false
    }

    override suspend fun sendMessage(prompt: String, imageUri: Uri?): Result<String> = withContext(Dispatchers.IO) {
        engineLock.withLock {
            runCatching {
                val conversation = activeConversation ?: throw IllegalStateException("No active conversation. Did you initialize the model?")
                
                val response = if (imageUri != null) {
                    if (!isVisionEnabled) {
                        throw IllegalStateException("Current backend does not support image input. Please switch to a vision-enabled model/backend.")
                    }
                    
                    val bitmap = ImageUtils.loadAndProcessImage(context, imageUri, 448)
                    val bos = ByteArrayOutputStream()
                    // PNG is safer for some backends, but JPEG is smaller. Using PNG as it's more standard for multimodal input in some versions.
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)
                    
                    val message = Message.user(
                        Contents.of(
                            Content.ImageBytes(bos.toByteArray()),
                            Content.Text(prompt)
                        )
                    )
                    conversation.sendMessage(message)
                } else {
                    conversation.sendMessage(prompt)
                }

                parseResponseText(response)
            }
        }
    }

    private fun parseResponseText(response: Message): String {
        return response.contents.contents.joinToString("") { content ->
            when (content) {
                is Content.Text -> content.text
                else -> {
                    // Fallback logic for extraction if the specific version's API differs slightly
                    val str = content.toString()
                    if (str.contains("text=")) {
                        str.substringAfter("text=").substringBeforeLast(")")
                    } else {
                        ""
                    }
                }
            }
        }.trim()
    }

    override suspend fun clearConversation() {
        engineLock.withLock {
            activeConversation?.close()
            activeConversation = engine?.createConversation()
        }
    }

    override suspend fun fetchAvailableModels(): Result<List<ModelEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val snapshot = firestore.collection("models").get().await()
            snapshot.toObjects(ModelEntry::class.java)
        }
    }

    override fun downloadModel(url: String, modelName: String): Flow<DownloadProgress> {
        val workRequest = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workDataOf("url" to url, "modelName" to modelName))
            .addTag(modelName)
            .build()
        workManager.enqueueUniqueWork(modelName, ExistingWorkPolicy.KEEP, workRequest)
        
        return workManager.getWorkInfoByIdFlow(workRequest.id).map { workInfo ->
            when (workInfo?.state) {
                WorkInfo.State.RUNNING -> {
                    val progress = workInfo.progress.getInt("progress", 0)
                    DownloadProgress.Progress(progress)
                }
                WorkInfo.State.SUCCEEDED -> DownloadProgress.Finished
                WorkInfo.State.FAILED -> {
                    val error = workInfo.outputData.getString("error") ?: "Download failed."
                    DownloadProgress.Error(error)
                }
                else -> DownloadProgress.Progress(0)
            }
        }
    }
}
