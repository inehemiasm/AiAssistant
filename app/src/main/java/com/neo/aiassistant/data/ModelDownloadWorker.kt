package com.neo.aiassistant.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.neo.aiassistant.data.datasource.DownloadStatus
import com.neo.aiassistant.data.datasource.RemoteModelDataSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val remoteModelDataSource: RemoteModelDataSource
) : CoroutineWorker(context, workerParams) {

    private val lastUpdateMs = AtomicLong(0L)
    private val throttleIntervalMs = 500L
    private val notificationId = 1001
    private val channelId = "model_download_channel"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val uri = inputData.getString("url") ?: return@withContext Result.failure(workDataOf("error" to "Missing URL"))
        val modelName = inputData.getString("modelName") ?: return@withContext Result.failure(workDataOf("error" to "Missing Name"))
        
        val targetFile = File(applicationContext.filesDir, modelName)
        val tempFile = File(applicationContext.filesDir, "$modelName.tmp")

        try {
            setForeground(getForegroundInfo())
        } catch (e: Exception) {
            Log.e("ModelDownloadWorker", "Foreground promotion failed", e)
        }
        
        setProgress(workDataOf("progress" to 0))

        try {
            val downloadUrl = remoteModelDataSource.getDownloadUrl(uri)

            // Flow-based download for cleaner progress updates and decoupling
            remoteModelDataSource.downloadToFile(downloadUrl, tempFile).collect { status ->
                if (status is DownloadStatus.Progress) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastUpdateMs.get() >= throttleIntervalMs) {
                        setProgress(workDataOf("progress" to status.percent))
                        lastUpdateMs.set(currentTime)
                    }
                }
            }
            
            if (tempFile.exists() && tempFile.length() > 0) {
                if (targetFile.exists()) targetFile.delete()
                if (tempFile.renameTo(targetFile)) {
                    Log.d("ModelDownloadWorker", "Successfully downloaded: $modelName")
                    setProgress(workDataOf("progress" to 100))
                    Result.success()
                } else {
                    throw IOException("Failed to finalize model core file")
                }
            } else {
                throw IOException("Downloaded core file is empty or missing")
            }
        } catch (e: Exception) {
            Log.e("ModelDownloadWorker", "Model download failed", e)
            if (tempFile.exists()) tempFile.delete()
            val errorMsg = e.localizedMessage ?: "Unknown download failure"
            Result.failure(workDataOf("error" to errorMsg))
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Downloading Neural Core")
            .setContentText("Acquiring model data...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            channelId,
            "Model Downloads",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}
