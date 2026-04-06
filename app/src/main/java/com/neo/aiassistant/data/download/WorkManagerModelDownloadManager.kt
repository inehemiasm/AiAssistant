package com.neo.aiassistant.data.download

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.neo.aiassistant.data.ModelDownloadWorker
import com.neo.aiassistant.domain.DownloadProgress
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages model downloads using WorkManager.
 */
@Singleton
class WorkManagerModelDownloadManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val workManager = WorkManager.getInstance(context)

    fun downloadModel(url: String, modelName: String): Flow<DownloadProgress> {
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
