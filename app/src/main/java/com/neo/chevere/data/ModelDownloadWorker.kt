package com.neo.chevere.data

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
import com.neo.chevere.data.datasource.DownloadStatus
import com.neo.chevere.data.datasource.RemoteModelDataSource
import com.neo.chevere.domain.InstallStatus
import com.neo.chevere.domain.InstalledModelRegistry
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipInputStream

private const val TAG = "ModelDownloadWorker"

/**
 * A [CoroutineWorker] responsible for downloading AI model files in the background.
 */
@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val remoteModelDataSource: RemoteModelDataSource,
    private val installedModelRegistry: InstalledModelRegistry
) : CoroutineWorker(context, workerParams) {

    private val lastUpdateMs = AtomicLong(0L)
    private val throttleIntervalMs = 500L
    private val notificationId = 1001
    private val channelId = "model_download_channel"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val uri = inputData.getString("url")
        val modelName = inputData.getString("modelName")
        val expectedSha256 = inputData.getString("sha256")
        
        Log.d(TAG, "Worker started. Name: $modelName, URL: $uri")

        if (uri == null || modelName == null) {
            return@withContext Result.failure(workDataOf("error" to "Missing metadata"))
        }
        
        if (!modelName.endsWith(".litertlm") && !modelName.endsWith(".bin") && !modelName.endsWith(".zip")) {
             return@withContext Result.failure(workDataOf("error" to "Unsupported file type"))
        }

        val targetFile = File(applicationContext.filesDir, modelName)
        val tempFile = File(applicationContext.filesDir, "$modelName.tmp")
        
        try {
            setForeground(getForegroundInfo())
            
            // Mark as downloading in registry
            installedModelRegistry.updateInstallStatus(modelName, InstallStatus.DOWNLOADING)

            val downloadUrl = remoteModelDataSource.getDownloadUrl(uri)

            remoteModelDataSource.downloadToFile(downloadUrl, tempFile).collect { status ->
                if (status is DownloadStatus.Progress) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastUpdateMs.get() >= throttleIntervalMs) {
                        setProgress(workDataOf("progress" to status.percent))
                        lastUpdateMs.set(currentTime)
                    }
                }
            }
            
            if (tempFile.exists() && tempFile.length() > 1024) {
                // Phase 4: Integrity check
                installedModelRegistry.updateInstallStatus(modelName, InstallStatus.VERIFYING)
                
                if (expectedSha256 != null) {
                    val actualSha256 = calculateSha256(tempFile)
                    if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                        installedModelRegistry.updateInstallStatus(modelName, InstallStatus.CORRUPTED)
                        throw IOException("Checksum mismatch")
                    }
                }

                if (targetFile.exists()) targetFile.delete()

                if (modelName.endsWith(".zip")) {
                    val targetDir = File(applicationContext.filesDir, modelName.removeSuffix(".zip"))
                    val tempDir = File(applicationContext.filesDir, "${targetDir.name}.tmpdir")
                    if (targetDir.exists()) targetDir.deleteRecursively()
                    if (tempDir.exists()) tempDir.deleteRecursively()
                    tempDir.mkdirs()

                    unzipModelBundle(tempFile, tempDir)
                    if (!tempDir.renameTo(targetDir)) {
                        tempDir.deleteRecursively()
                        installedModelRegistry.updateInstallStatus(modelName, InstallStatus.FAILED)
                        throw IOException("Failed to finalize extracted image model")
                    }
                    tempFile.delete()
                    installedModelRegistry.updateInstallStatus(modelName, InstallStatus.INSTALLED)
                    setProgress(workDataOf("progress" to 100))
                    Result.success()
                } else if (tempFile.renameTo(targetFile)) {
                    installedModelRegistry.updateInstallStatus(modelName, InstallStatus.INSTALLED)
                    setProgress(workDataOf("progress" to 100))
                    Result.success()
                } else {
                    installedModelRegistry.updateInstallStatus(modelName, InstallStatus.FAILED)
                    throw IOException("Finalization failed")
                }
            } else {
                installedModelRegistry.updateInstallStatus(modelName, InstallStatus.FAILED)
                throw IOException("Empty file")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}")
            if (tempFile.exists()) tempFile.delete()
            installedModelRegistry.updateInstallStatus(modelName, InstallStatus.FAILED)
            Result.failure(workDataOf("error" to (e.localizedMessage ?: "Unknown error")))
        }
    }

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

    private fun unzipModelBundle(zipFile: File, targetDir: File) {
        val canonicalTarget = targetDir.canonicalFile
        ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val outputFile = File(canonicalTarget, entry.name).canonicalFile
                if (!outputFile.path.startsWith(canonicalTarget.path + File.separator)) {
                    throw IOException("Unsafe zip entry: ${entry.name}")
                }

                if (entry.isDirectory) {
                    outputFile.mkdirs()
                } else {
                    outputFile.parentFile?.mkdirs()
                    outputFile.outputStream().buffered().use { output ->
                        zip.copyTo(output)
                    }
                }

                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Downloading Neural Core")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(channelId, "Model Downloads", NotificationManager.IMPORTANCE_LOW)
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}
