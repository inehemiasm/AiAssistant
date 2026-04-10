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
import com.neo.aiassistant.R
import com.neo.aiassistant.data.datasource.DownloadStatus
import com.neo.aiassistant.data.datasource.RemoteModelDataSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

/**
 * A [CoroutineWorker] responsible for downloading AI model files in the background.
 *
 * This worker handles:
 * 1. Resolving the download URL via [RemoteModelDataSource].
 * 2. Downloading the file to a temporary location.
 * 3. Providing progress updates via [setProgress].
 * 4. Verifying the integrity of the downloaded file using SHA-256 (if provided).
 * 5. Finalizing the download by moving the file to its target location.
 * 6. Running as a foreground service to ensure completion.
 *
 * @property remoteModelDataSource Data source used to resolve URLs and perform the download.
 */
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

    /**
     * Executes the download work on a background dispatcher.
     */
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val uri = inputData.getString("url")
        val modelName = inputData.getString("modelName")
        val expectedSha256 = inputData.getString("sha256")
        
        Log.d("ModelDownloadWorker", "Worker started. Name: $modelName, URL: $uri")

        if (uri == null) return@withContext Result.failure(workDataOf("error" to "Missing URL")).also { Log.e("ModelDownloadWorker", "Failure: Missing URL") }
        if (modelName == null) return@withContext Result.failure(workDataOf("error" to "Missing Name")).also { Log.e("ModelDownloadWorker", "Failure: Missing Name") }
        
        // Ensure we only download .litertlm files for this feature
        if (!modelName.endsWith(".litertlm") && !modelName.endsWith(".bin")) {
             Log.e("ModelDownloadWorker", "Failure: Unsupported file type $modelName")
             return@withContext Result.failure(workDataOf("error" to "Unsupported model file type"))
        }

        val targetFile = File(applicationContext.filesDir, modelName)
        val tempFile = File(applicationContext.filesDir, "$modelName.tmp")
        
        Log.d("ModelDownloadWorker", "Target file: ${targetFile.absolutePath}")

        try {
            setForeground(getForegroundInfo())
        } catch (e: Exception) {
            Log.e("ModelDownloadWorker", "Foreground promotion failed", e)
        }
        
        setProgress(workDataOf("progress" to 0))

        try {
            Log.d("ModelDownloadWorker", "Resolving download URL for $uri")
            val downloadUrl = remoteModelDataSource.getDownloadUrl(uri)
            Log.d("ModelDownloadWorker", "Resolved URL: $downloadUrl")

            // Flow-based download for cleaner progress updates and decoupling
            Log.d("ModelDownloadWorker", "Beginning network download to ${tempFile.absolutePath}")
            remoteModelDataSource.downloadToFile(downloadUrl, tempFile).collect { status ->
                if (status is DownloadStatus.Progress) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastUpdateMs.get() >= throttleIntervalMs) {
                        Log.v("ModelDownloadWorker", "Download progress for $modelName: ${status.percent}%")
                        setProgress(workDataOf("progress" to status.percent))
                        lastUpdateMs.set(currentTime)
                    }
                }
            }
            
            if (tempFile.exists() && tempFile.length() > 0) {
                Log.d("ModelDownloadWorker", "Download complete. Size: ${tempFile.length()} bytes")
                // Checksum validation
                if (expectedSha256 != null) {
                    Log.d("ModelDownloadWorker", "Verifying checksum (SHA-256: $expectedSha256)")
                    val actualSha256 = calculateSha256(tempFile)
                    if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                        Log.e("ModelDownloadWorker", "Checksum mismatch! Actual: $actualSha256")
                        throw IOException("Checksum mismatch. Expected: $expectedSha256, Actual: $actualSha256")
                    }
                    Log.d("ModelDownloadWorker", "Checksum verified.")
                }

                if (targetFile.exists()) {
                    Log.d("ModelDownloadWorker", "Removing existing file before rename")
                    targetFile.delete()
                }
                
                if (tempFile.renameTo(targetFile)) {
                    Log.i("ModelDownloadWorker", "Successfully finalized model: $modelName")
                    setProgress(workDataOf("progress" to 100))
                    Result.success()
                } else {
                    Log.e("ModelDownloadWorker", "Failed to rename temp file to target")
                    throw IOException("Failed to finalize model core file")
                }
            } else {
                Log.e("ModelDownloadWorker", "Temp file is empty or missing after download")
                throw IOException("Downloaded core file is empty or missing")
            }
        } catch (e: Exception) {
            Log.e("ModelDownloadWorker", "Model download failed with exception", e)
            if (tempFile.exists()) tempFile.delete()
            val errorMsg = e.localizedMessage ?: "Unknown download failure"
            Result.failure(workDataOf("error" to errorMsg))
        }
    }

    /**
     * Calculates the SHA-256 checksum of a file.
     *
     * @param file The file to check.
     * @return The hex string representation of the checksum.
     */
    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Creates and returns the [ForegroundInfo] required to run the worker in the foreground.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(applicationContext.getString(R.string.downloading_neural_core))
            .setContentText(applicationContext.getString(R.string.acquiring_model_data))
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

    /**
     * Creates the notification channel for download progress notifications.
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            channelId,
            applicationContext.getString(R.string.model_downloads_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}
