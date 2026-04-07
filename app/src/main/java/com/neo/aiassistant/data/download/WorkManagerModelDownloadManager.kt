package com.neo.aiassistant.data.download

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.neo.aiassistant.data.ModelDownloadWorker
import com.neo.aiassistant.domain.DownloadProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages model downloads using WorkManager.
 */
@Singleton
class WorkManagerModelDownloadManager @Inject constructor(
    private val workManager: WorkManager
) {
    fun downloadModel(url: String, modelName: String): Flow<DownloadProgress> {
        val workRequest = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workDataOf("url" to url, "modelName" to modelName))
            .addTag(modelName)
            .build()
        
        workManager.enqueueUniqueWork(modelName, ExistingWorkPolicy.KEEP, workRequest)
        
        return workManager.getWorkInfoByIdFlow(workRequest.id).map { workInfo ->
            mapWorkInfoToDownloadProgress(workInfo)
        }
    }

    internal fun mapWorkInfoToDownloadProgress(workInfo: WorkInfo?): DownloadProgress {
        return when (workInfo?.state) {
            WorkInfo.State.RUNNING -> {
                val progress = workInfo.progress.getInt("progress", 0)
                DownloadProgress.Progress(progress)
            }
            WorkInfo.State.SUCCEEDED -> DownloadProgress.Finished
            WorkInfo.State.FAILED -> {
                val error = workInfo.outputData.getString("error") ?: "Download failed."
                DownloadProgress.Error(error)
            }
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadProgress.Progress(0)
            WorkInfo.State.CANCELLED -> DownloadProgress.Error("Download cancelled.")
            null -> DownloadProgress.Progress(0)
        }
    }
}
