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
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.firebase.firestore.FirebaseFirestore
import com.neo.aiassistant.core.ImageUtils
import com.neo.aiassistant.domain.ChatRepository
import com.neo.aiassistant.domain.DownloadProgress
import com.neo.aiassistant.domain.ModelEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore
) : ChatRepository {
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var isVisionEnabled = false
    private val workManager = WorkManager.getInstance(context)

    override fun isVisionSupported(): Boolean = isVisionEnabled

    override suspend fun initializeModel(modelPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        val modelFile = File(modelPath)
        if (!modelFile.exists() || modelFile.length() < 1024 * 1024) {
            return@withContext Result.failure(Exception("Neural core missing or corrupted."))
        }

        Log.d("ChatRepository", "--- INITIALIZING NEURAL ENGINE ---")
        
        val neuralCache = File(context.cacheDir, "neural_cache")
        if (!neuralCache.exists()) neuralCache.mkdirs()

        val attempts = listOf(
            Triple(Backend.GPU(), Backend.GPU(), "Multimodal GPU"),
            Triple(Backend.CPU(), Backend.CPU(), "Multimodal CPU"),
            Triple(Backend.CPU(), null, "Text-Only Mode")
        )

        var lastError: Throwable? = null

        for ((mainBackend, visionBackend, label) in attempts) {
            val result = runCatching {
                val engineConfig = EngineConfig(
                    modelPath = modelPath,
                    backend = mainBackend,
                    visionBackend = visionBackend,
                    maxNumTokens = 4096,
                    cacheDir = neuralCache.absolutePath
                )
                
                if (visionBackend != null) {
                    try {
                        val field = engineConfig.javaClass.getDeclaredField("maxNumImages")
                        field.isAccessible = true
                        field.set(engineConfig, 1)
                    } catch (e: Exception) {
                        Log.w("ChatRepository", "Could not set maxNumImages: ${e.message}")
                    }
                }
                
                val newEngine = Engine(engineConfig)
                newEngine.initialize()
                
                val probeConv = newEngine.createConversation()
                
                if (visionBackend != null) {
                    val dummyBitmap = createBitmap(448, 448, Bitmap.Config.ARGB_8888)
                    probeConv.sendMessage(
                        "<image>\nwarmup", 
                        mapOf("images" to listOf(dummyBitmap))
                    )
                } else {
                    probeConv.sendMessage("warmup")
                }
                
                engine = newEngine
                conversation = probeConv
                isVisionEnabled = visionBackend != null
            }
            
            if (result.isSuccess) {
                Log.i("ChatRepository", "Engine online: $label")
                return@withContext Result.success(Unit)
            }
            
            lastError = result.exceptionOrNull()
            Log.e("ChatRepository", "$label failed: ${lastError?.message}")
            neuralCache.deleteRecursively()
            neuralCache.mkdirs()
        }

        Result.failure(lastError ?: Exception("Hardware incompatible."))
    }

    override suspend fun sendMessage(prompt: String, imageUri: Uri?): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val activeConversation = conversation ?: throw Exception("Engine not ready.")
            val extraContext = mutableMapOf<String, Any>()
            
            val finalPrompt = if (isVisionEnabled && imageUri != null) {
                val bitmap = ImageUtils.loadAndProcessImage(context, imageUri, 448)
                extraContext["images"] = listOf(bitmap)
                "<image>\n$prompt"
            } else {
                prompt
            }

            val response = activeConversation.sendMessage(finalPrompt, extraContext)

            response.contents.contents.joinToString("") { part ->
                val str = part.toString()
                if (str.contains("text=")) {
                    str.substringAfter("text=").substringBeforeLast(")")
                } else {
                    str
                }
            }.trim()
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
                WorkInfo.State.RUNNING -> DownloadProgress.Progress(workInfo.progress.getInt("progress", 0))
                WorkInfo.State.SUCCEEDED -> DownloadProgress.Finished
                WorkInfo.State.FAILED -> DownloadProgress.Error(workInfo.outputData.getString("error") ?: "Download interrupted")
                else -> DownloadProgress.Progress(0)
            }
        }
    }
}
