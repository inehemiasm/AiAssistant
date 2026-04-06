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
    
    private val _initStatus = MutableSharedFlow<String>(replay = 1)
    override fun getInitStatus(): Flow<String> = _initStatus.asSharedFlow()

    override fun isVisionSupported(): Boolean = isVisionEnabled

    override suspend fun initializeModel(modelPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        val modelFile = File(modelPath)
        if (!modelFile.exists() || modelFile.length() < 1024 * 1024) {
            return@withContext Result.failure(Exception("Neural core missing or corrupted."))
        }

        Log.d("ChatRepository", "--- INITIALIZING NEURAL ENGINE ---")
        _initStatus.emit("RESETTING ENGINE...")

        clearConversation()
        engine?.close()
        engine = null
        
        val neuralCache = File(context.cacheDir, "neural_cache")
        if (!neuralCache.exists()) neuralCache.mkdirs()

        val attempts = listOf(
            Triple(Backend.GPU(), Backend.CPU(), "GPU + Vision CPU"),
            Triple(Backend.CPU(), Backend.CPU(), "CPU + Vision CPU"),
            Triple(Backend.CPU(), null, "Text-Only Mode")
        )

        var lastError: Throwable? = null

        for ((mainBackend, visionBackend, label) in attempts) {
            _initStatus.emit("ATTEMPTING: $label")
            val result = runCatching {
                initializeWithConfig(modelPath, mainBackend, visionBackend, neuralCache.absolutePath)
            }
            
            if (result.isSuccess) {
                Log.i("ChatRepository", "Engine online: $label")
                engine = result.getOrNull()
                isVisionEnabled = visionBackend != null
                activeConversation = engine?.createConversation()
                _initStatus.emit("SYSTEM READY")
                return@withContext Result.success(Unit)
            }
            
            lastError = result.exceptionOrNull()
            Log.e("ChatRepository", "$label failed: ${lastError?.message}")
            _initStatus.emit("HARDWARE FALLBACK...")
            neuralCache.deleteRecursively()
            neuralCache.mkdirs()
        }

        _initStatus.emit("INITIALIZATION FAILED")
        Result.failure(lastError ?: Exception("Hardware incompatible."))
    }

    private suspend fun initializeWithConfig(
        modelPath: String,
        mainBackend: Backend,
        visionBackend: Backend?,
        cacheDirPath: String
    ): Engine {
        val engineConfig = EngineConfig(
            modelPath = modelPath,
            backend = mainBackend,
            visionBackend = visionBackend,
            maxNumTokens = 4096,
            cacheDir = cacheDirPath
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
        
        _initStatus.emit("ALLOCATING NEURAL MEMORY...")
        val tempEngine = Engine(engineConfig)
        try {
            tempEngine.initialize()
            
            _initStatus.emit("WARMING UP VISION CORE...")
            val probeConv = tempEngine.createConversation()
            try {
                if (visionBackend != null) {
                    val dummyBitmap = createBitmap(448, 448, Bitmap.Config.ARGB_8888)
                    val bos = ByteArrayOutputStream()
                    dummyBitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)
                    val imageBytes = bos.toByteArray()
                    
                    val message = Message.user(
                        Contents.of(
                            Content.ImageBytes(imageBytes),
                            Content.Text("warmup")
                        )
                    )
                    probeConv.sendMessage(message)
                } else {
                    probeConv.sendMessage("warmup")
                }
            } finally {
                probeConv.close()
            }
            
            return tempEngine
        } catch (e: Exception) {
            tempEngine.close()
            throw e
        }
    }

    override suspend fun sendMessage(prompt: String, imageUri: Uri?): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val currentConv = activeConversation ?: throw Exception("Conversation not initialized.")
            
            val response = if (imageUri != null) {
                if (!isVisionEnabled) {
                    throw Exception("Image provided but vision is disabled in current backend.")
                }
                val bitmap = ImageUtils.loadAndProcessImage(context, imageUri, 448)
                val bos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, bos)
                val imageBytes = bos.toByteArray()
                
                val message = Message.user(
                    Contents.of(
                        Content.ImageBytes(imageBytes),
                        Content.Text(prompt)
                    )
                )
                currentConv.sendMessage(message)
            } else {
                currentConv.sendMessage(prompt)
            }

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

    override suspend fun clearConversation() {
        activeConversation?.close()
        activeConversation = engine?.createConversation()
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
