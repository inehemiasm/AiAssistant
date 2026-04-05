package com.neo.aiassistant.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
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
import com.google.firebase.firestore.FirebaseFirestoreSettings
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
    @ApplicationContext private val context: Context
) : ChatRepository {
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var isVisionEnabled = false

    private val firestore = FirebaseFirestore.getInstance().apply {
        firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
    }
    private val workManager = WorkManager.getInstance(context)

    override fun isVisionSupported(): Boolean = isVisionEnabled

    override suspend fun initializeModel(modelPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            return@withContext Result.failure(Exception("Neural core not found: $modelPath"))
        }

        val attempts = listOf(
            // Tier 1: Universal CPU Multimodal (Safe-High)
            Triple(Backend.CPU(), Backend.CPU(), "Multimodal CPU"),
            // Tier 2: Pure Text Mode (Guaranteed stability)
            Triple(Backend.CPU(), null, "Pure Text Mode"),
            // Tier 3: GPU Accelerated (Performance fallback)
            Triple(Backend.GPU(), Backend.CPU(), "GPU Accelerated")
        )

        var lastError: Throwable? = null

        for ((mainBackend, visionBackend, label) in attempts) {
            val result = initializeWithConfig(modelPath, mainBackend, visionBackend)
            if (result.isSuccess) {
                Log.i("ChatRepository", "Neural Engine synchronized using: $label")
                isVisionEnabled = visionBackend != null
                return@withContext Result.success(Unit)
            }
            lastError = result.exceptionOrNull()
            Log.e("ChatRepository", "Hardware probe failed for $label: ${lastError?.message}")
        }

        Result.failure(lastError ?: Exception("Hardware is incompatible with current neural core."))
    }

    private suspend fun initializeWithConfig(modelPath: String, backend: Backend, visionBackend: Backend?): Result<Unit> {
        return runCatching {
            // We must explicitly allow at least one image in the config for the vision pipeline to activate
            val engineConfig = EngineConfig(
                modelPath = modelPath,
                backend = backend,
                visionBackend = visionBackend,
                maxNumTokens = 4096
            )
            val newEngine = Engine(engineConfig)
            newEngine.initialize()
            
            val probeConv = newEngine.createConversation()
            
            try {
                if (visionBackend != null) {
                    // Test Vision Pipeline with a 1x1 dummy pixel
                    val dummyBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                    probeConv.sendMessage("check", mapOf("images" to listOf(dummyBitmap)))
                } else {
                    probeConv.sendMessage("check")
                }
            } catch (e: Throwable) {
                throw Exception("Hardware Sanity Check Failed: ${e.message}")
            }
            
            engine = newEngine
            conversation = probeConv
            Unit
        }
    }

    override suspend fun sendMessage(prompt: String, imageUri: Uri?): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val activeConversation = conversation ?: throw Exception("Neural core not initialized.")
            val extraContext = mutableMapOf<String, Any>()
            
            if (isVisionEnabled && imageUri != null) {
                val bitmap = loadScaledBitmap(imageUri, 1024)
                extraContext["images"] = listOf(bitmap)
            } else if (imageUri != null) {
                return@runCatching sendMessage("[IMAGE REMOVED: Vision Incompatible] $prompt", null).getOrThrow()
            }

            val response = activeConversation.sendMessage(prompt, extraContext)
            
            val responseText = response.contents.contents.joinToString("") { part ->
                val str = part.toString()
                if (str.contains("text=")) {
                    str.substringAfter("text=").substringBeforeLast(")")
                } else {
                    str
                }
            }.trim().removeSurrounding("[", "]")
            
            if (responseText.isBlank()) {
                throw Exception("Synthesis returned empty data.")
            }
            responseText
        }
    }

    private fun loadScaledBitmap(uri: Uri, maxSize: Int): Bitmap {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
        
        val width = bitmap.width
        val height = bitmap.height
        val ratio = width.toFloat() / height.toFloat()
        
        var finalWidth = width
        var finalHeight = height
        
        if (width > maxSize || height > maxSize) {
            if (ratio > 1) {
                finalWidth = maxSize
                finalHeight = (maxSize / ratio).toInt()
            } else {
                finalHeight = maxSize
                finalWidth = (maxSize * ratio).toInt()
            }
        }
        
        return Bitmap.createScaledBitmap(bitmap, finalWidth, finalHeight, true)
            .copy(Bitmap.Config.ARGB_8888, false)
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
