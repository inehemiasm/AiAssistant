package com.neo.aiassistant.data

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ChatRepository {
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private val firestore = FirebaseFirestore.getInstance().apply {
        firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
    }
    private val workManager = WorkManager.getInstance(context)

    override suspend fun initializeModel(modelPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val engineConfig = EngineConfig(modelPath = modelPath)
            engine = Engine(engineConfig)
            engine?.initialize()
            conversation = engine?.createConversation()
            Unit
        }
    }

    override suspend fun sendMessage(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val response = conversation?.sendMessage(prompt)
            response?.contents?.contents?.toString() ?: throw Exception("Empty response from model")
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
                    val errorMsg = workInfo.outputData.getString("error") ?: "Download failed"
                    DownloadProgress.Error(errorMsg)
                }
                else -> DownloadProgress.Progress(0)
            }
        }
    }
}
