package com.neo.chevere.data.download

import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.neo.chevere.core.Constants
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
        const val TAG_MODEL_DOWNLOAD = Constants.Download.TAG_MODEL_DOWNLOAD
    }

    /**
     * A global flow that tracks all active and recent model downloads.
     * This allows any part of the UI to observe progress for any model by its ID (fileName).
     */
    val allDownloadsProgress: Flow<Map<String, DownloadProgress>> =
        workManager.getWorkInfosByTagFlow(TAG_MODEL_DOWNLOAD).map { workInfos ->
            workInfos.mapNotNull { info ->
                val modelName = info.tags.firstOrNull {
                    it.startsWith(Constants.Download.TAG_MODEL_NAME_PREFIX)
                }?.removePrefix(Constants.Download.TAG_MODEL_NAME_PREFIX) ?: return@mapNotNull null

                modelName to mapWorkInfoToDownloadProgress(info)
            }.toMap()
        }

    /**
     * Initiates a model download task.
     */
    fun downloadModel(
        url: String,
        modelName: String,
        modelId: String = modelName.removeSuffix(Constants.ModelFiles.ZIP_EXTENSION),
        sha256: String? = null,
        repositoryFiles: List<String> = emptyList()
    ): Flow<DownloadProgress> {
        Log.d("DownloadManager", "Creating WorkManager request for $modelName from $url")

        val inputData = Data.Builder()
            .putString(Constants.Download.INPUT_URL, url)
            .putString(Constants.Download.INPUT_MODEL_NAME, modelName)
            .putString(Constants.Download.INPUT_MODEL_ID, modelId)
            .putStringArray(
                Constants.Download.INPUT_REPOSITORY_FILES,
                repositoryFiles.toTypedArray()
            )
            .apply {
                if (sha256 != null) {
                    putString(Constants.Download.INPUT_SHA256, sha256)
                }
            }
            .build()

        val workRequest = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(inputData)
            .addTag(TAG_MODEL_DOWNLOAD) // Generic tag for tracking all downloads
            .addTag("${Constants.Download.TAG_MODEL_NAME_PREFIX}$modelName") // Specific tag for this model
            .build()

        // Replace same-model work so a stale interrupted job cannot keep the download button pinned.
        workManager.enqueueUniqueWork(modelName, ExistingWorkPolicy.REPLACE, workRequest)

        // Return a flow specifically for this model's name
        return workManager.getWorkInfosForUniqueWorkFlow(modelName).map { list ->
            mapWorkInfoToDownloadProgress(list.firstOrNull())
        }
    }

    /**
     * Cancels an active download and lets WorkManager surface the cancelled state.
     */
    fun cancelDownload(modelName: String) {
        workManager.cancelUniqueWork(modelName)
    }

    internal fun mapWorkInfoToDownloadProgress(workInfo: WorkInfo?): DownloadProgress {
        return when (workInfo?.state) {
            WorkInfo.State.RUNNING -> {
                val progress = workInfo.progress.getInt(Constants.Download.PROGRESS, 0)
                DownloadProgress.Progress(progress)
            }

            WorkInfo.State.SUCCEEDED -> DownloadProgress.Finished
            WorkInfo.State.FAILED -> {
                val error = workInfo.outputData.getString(Constants.Download.OUTPUT_ERROR)
                    ?: "Download failed."
                DownloadProgress.Error(error)
            }

            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadProgress.Progress(0)
            WorkInfo.State.CANCELLED -> DownloadProgress.Error("Download cancelled.")
            null -> DownloadProgress.Progress(0)
        }
    }
}
