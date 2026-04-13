package com.neo.chevere.data.download

import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.neo.chevere.data.ModelDownloadWorker
import com.neo.chevere.domain.DownloadProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the background downloading of AI models using Android's WorkManager.
 *
 * This manager handles enqueuing download tasks and mapping the WorkManager's
 * state and progress updates back to a domain-level [DownloadProgress] flow.
 *
 * @property workManager The system's WorkManager instance used to schedule tasks.
 */
@Singleton
class WorkManagerModelDownloadManager @Inject constructor(
    private val workManager: WorkManager
) {
    /**
     * Initiates a model download task.
     *
     * @param url The URL from which to download the model file.
     * @param modelName A unique name for the model, also used as the unique work name.
     * @param sha256 Optional SHA-256 hash to verify the integrity of the downloaded file.
     * @return A [Flow] of [DownloadProgress] updates for the initiated download.
     */
    fun downloadModel(url: String, modelName: String, sha256: String? = null): Flow<DownloadProgress> {
        Log.d("DownloadManager", "Creating WorkManager request for $modelName from $url")
        
        val inputData = Data.Builder()
            .putString("url", url)
            .putString("modelName", modelName)
            .apply {
                if (sha256 != null) {
                    putString("sha256", sha256)
                }
            }
            .build()

        val workRequest = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(inputData)
            .addTag(modelName)
            .build()
        
        val enqueueResult = workManager.enqueueUniqueWork(modelName, ExistingWorkPolicy.KEEP, workRequest)
        Log.d("DownloadManager", "WorkManager enqueue result for $modelName: ${enqueueResult.result}")
        
        return workManager.getWorkInfoByIdFlow(workRequest.id).map { workInfo ->
            val progress = mapWorkInfoToDownloadProgress(workInfo)
            Log.v("DownloadManager", "WorkInfo status for $modelName: ${workInfo?.state}, progress: $progress")
            progress
        }
    }

    /**
     * Maps the internal WorkManager [WorkInfo] to the domain-level [DownloadProgress].
     *
     * @param workInfo The information about the background work.
     * @return The corresponding [DownloadProgress] state.
     */
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
