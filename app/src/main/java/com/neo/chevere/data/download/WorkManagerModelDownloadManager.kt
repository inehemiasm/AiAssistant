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
 */
@Singleton
class WorkManagerModelDownloadManager @Inject constructor(
    private val workManager: WorkManager
) {
    companion object {
        const val TAG_MODEL_DOWNLOAD = "MODEL_DOWNLOAD_TASK"
    }

    /**
     * A global flow that tracks all active and recent model downloads.
     * This allows any part of the UI to observe progress for any model by its ID (fileName).
     */
    val allDownloadsProgress: Flow<Map<String, DownloadProgress>> = 
        workManager.getWorkInfosByTagFlow(TAG_MODEL_DOWNLOAD).map { workInfos ->
            workInfos.associate { info ->
                // The model name is stored as a tag (excluding the generic one and class tags)
                val modelName = info.tags.find { 
                    it != TAG_MODEL_DOWNLOAD && 
                    !it.contains(".") && 
                    it != ModelDownloadWorker::class.java.name 
                } ?: "unknown"
                
                modelName to mapWorkInfoToDownloadProgress(info)
            }
        }

    /**
     * Initiates a model download task.
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
            .addTag(TAG_MODEL_DOWNLOAD) // Generic tag for tracking all downloads
            .addTag(modelName)          // Specific tag for this model
            .build()
        
        // Use KEEP policy so we don't restart an existing download for the same model
        workManager.enqueueUniqueWork(modelName, ExistingWorkPolicy.KEEP, workRequest)
        
        // Return a flow specifically for this model's name
        return workManager.getWorkInfosForUniqueWorkFlow(modelName).map { list ->
            mapWorkInfoToDownloadProgress(list.firstOrNull())
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
