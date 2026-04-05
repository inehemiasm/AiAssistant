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
import com.google.firebase.storage.FirebaseStorage
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val lastUpdateMs = AtomicLong(0L)
    private val throttleIntervalMs = 500L // 2 times per second
    private val notificationId = 1001
    private val channelId = "model_download_channel"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString("url") ?: return@withContext Result.failure(workDataOf("error" to "Missing URL"))
        val modelName = inputData.getString("modelName") ?: return@withContext Result.failure(workDataOf("error" to "Missing Name"))
        val targetFile = File(applicationContext.filesDir, modelName)

        // Promotion to foreground ensures the OS doesn't kill the download
        try {
            setForeground(getForegroundInfo())
        } catch (e: Exception) {
            Log.e("ModelDownloadWorker", "Failed to set foreground", e)
        }
        
        setProgress(workDataOf("progress" to 0))

        try {
            val downloadUrl = if (url.startsWith("gs://")) {
                FirebaseStorage.getInstance().getReferenceFromUrl(url).downloadUrl.await().toString()
            } else {
                url
            }

            val client = HttpClient(OkHttp)
            
            client.prepareGet(downloadUrl).execute { response ->
                if (!response.status.isSuccess()) {
                    throw IOException("HTTP error: ${response.status}")
                }

                val channel = response.bodyAsChannel()
                val totalBytes = response.contentLength() ?: 0L
                var bytesRead = 0L
                
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (!channel.isClosedForRead) {
                        val read = channel.readAvailable(buffer)
                        if (read == -1) break
                        
                        output.write(buffer, 0, read)
                        bytesRead += read
                        
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastUpdateMs.get() >= throttleIntervalMs && totalBytes > 0) {
                            val progress = (bytesRead * 100.0 / totalBytes).toInt()
                            // Corrected: Use suspending setProgress in CoroutineWorker
                            this@ModelDownloadWorker.setProgress(workDataOf("progress" to progress))
                            lastUpdateMs.set(currentTime)
                            Log.d("ModelDownloadWorker", "Syncing Core: $progress% ($bytesRead/$totalBytes)")
                        }
                    }
                }
            }
            
            Log.d("ModelDownloadWorker", "Download complete: $modelName")
            setProgress(workDataOf("progress" to 100))
            Result.success()
        } catch (e: Exception) {
            Log.e("ModelDownloadWorker", "Synthesis failed", e)
            val errorMsg = when {
                e.message?.contains("403") == true -> "Access Denied. Check Firebase Storage Rules."
                e is IOException -> "Uplink unstable. Check your network."
                else -> e.localizedMessage ?: "Unknown failure"
            }
            Result.failure(workDataOf("error" to errorMsg))
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Synthesizing Neural Core")
            .setContentText("Downloading model data...")
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Model Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
