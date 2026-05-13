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
import com.neo.chevere.core.Constants
import com.neo.chevere.data.datasource.DownloadStatus
import com.neo.chevere.data.datasource.RemoteModelDataSource
import com.neo.chevere.data.telemetry.AppTelemetry
import com.neo.chevere.data.telemetry.TelemetryConstants
import com.neo.chevere.domain.InstallStatus
import com.neo.chevere.domain.InstalledModelRegistry
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

private const val TAG = "ModelDownloadWorker"

/**
 * A [CoroutineWorker] responsible for downloading AI model files in the background.
 */
@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val remoteModelDataSource: RemoteModelDataSource,
    private val installedModelRegistry: InstalledModelRegistry,
    private val telemetry: AppTelemetry
) : CoroutineWorker(context, workerParams) {

    private val lastUpdateMs = AtomicLong(0L)
    private val throttleIntervalMs = 500L
    private val notificationId = 1001
    private val channelId = Constants.Download.NOTIFICATION_CHANNEL_ID

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val uri = inputData.getString(Constants.Download.INPUT_URL)
        val modelName = inputData.getString(Constants.Download.INPUT_MODEL_NAME)
        val modelId = inputData.getString(Constants.Download.INPUT_MODEL_ID)
            ?: modelName?.removeSuffix(Constants.ModelFiles.ZIP_EXTENSION)
        val expectedSha256 = inputData.getString(Constants.Download.INPUT_SHA256)
        
        Log.d(TAG, "Worker started. Name: $modelName, URL: $uri")

        if (uri == null || modelName == null || modelId == null) {
            return@withContext Result.failure(workDataOf(Constants.Download.OUTPUT_ERROR to "Missing metadata"))
        }
        
        if (!modelName.endsWith(Constants.ModelFiles.LITERTLM_EXTENSION) &&
            !modelName.endsWith(Constants.ModelFiles.BIN_EXTENSION) &&
            !modelName.endsWith(Constants.ModelFiles.ZIP_EXTENSION)
        ) {
             return@withContext Result.failure(workDataOf(Constants.Download.OUTPUT_ERROR to "Unsupported file type"))
        }

        val targetFile = File(applicationContext.filesDir, modelName)
        val tempFile = File(applicationContext.filesDir, "$modelName${Constants.ModelFiles.TEMP_EXTENSION}")
        val fileType = modelName.substringAfterLast('.', missingDelimiterValue = "directory")
        val startedAtMs = System.currentTimeMillis()
        telemetry.logModelDownloadStarted(modelId = modelId, fileType = fileType)
        
        try {
            setForeground(getForegroundInfo())
            
            // Mark as downloading in registry
            installedModelRegistry.updateInstallStatus(modelId, InstallStatus.DOWNLOADING)

            val downloadUrl = remoteModelDataSource.getDownloadUrl(uri)

            remoteModelDataSource.downloadToFile(downloadUrl, tempFile).collect { status ->
                coroutineContext.ensureActive()
                if (status is DownloadStatus.Progress) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastUpdateMs.get() >= throttleIntervalMs) {
                        setProgress(workDataOf(Constants.Download.PROGRESS to status.percent))
                        lastUpdateMs.set(currentTime)
                    }
                }
            }
            
            if (tempFile.exists() && tempFile.length() > Constants.ModelFiles.MIN_VALID_FILE_SIZE_BYTES) {
                // Phase 4: Integrity check
                installedModelRegistry.updateInstallStatus(modelId, InstallStatus.VERIFYING)
                
                if (expectedSha256 != null) {
                    val actualSha256 = calculateSha256(tempFile)
                    if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                        installedModelRegistry.updateInstallStatus(modelId, InstallStatus.CORRUPTED)
                        throw IOException("Checksum mismatch")
                    }
                }

                if (targetFile.exists()) targetFile.delete()

                if (modelName.endsWith(Constants.ModelFiles.ZIP_EXTENSION)) {
                    val targetDir = File(applicationContext.filesDir, modelId)
                    val tempDir = File(applicationContext.filesDir, "${targetDir.name}${Constants.ModelFiles.TEMP_DIRECTORY_EXTENSION}")
                    if (targetDir.exists()) targetDir.deleteRecursively()
                    if (tempDir.exists()) tempDir.deleteRecursively()
                    tempDir.mkdirs()

                    unzipModelBundle(tempFile, tempDir)
                    if (!isSupportedExtractedModelBundle(tempDir)) {
                        tempDir.deleteRecursively()
                        installedModelRegistry.updateInstallStatus(modelId, InstallStatus.FAILED)
                        throw IOException(
                            "Downloaded ZIP is not a supported model bundle. Missing ONNX or Qualcomm image-generation files."
                        )
                    }
                    if (!tempDir.renameTo(targetDir)) {
                        tempDir.deleteRecursively()
                        installedModelRegistry.updateInstallStatus(modelId, InstallStatus.FAILED)
                        throw IOException("Failed to finalize extracted image model")
                    }
                    tempFile.delete()
                    installedModelRegistry.updateInstallStatus(modelId, InstallStatus.INSTALLED)
                    setProgress(workDataOf(Constants.Download.PROGRESS to 100))
                    telemetry.logModelDownloadFinished(
                        modelId = modelId,
                        success = true,
                        durationMs = System.currentTimeMillis() - startedAtMs,
                        fileType = fileType
                    )
                    Result.success()
                } else if (tempFile.renameTo(targetFile)) {
                    installedModelRegistry.updateInstallStatus(modelId, InstallStatus.INSTALLED)
                    setProgress(workDataOf(Constants.Download.PROGRESS to 100))
                    telemetry.logModelDownloadFinished(
                        modelId = modelId,
                        success = true,
                        durationMs = System.currentTimeMillis() - startedAtMs,
                        fileType = fileType
                    )
                    Result.success()
                } else {
                    installedModelRegistry.updateInstallStatus(modelId, InstallStatus.FAILED)
                    throw IOException("Finalization failed")
                }
            } else {
                installedModelRegistry.updateInstallStatus(modelId, InstallStatus.FAILED)
                throw IOException("Empty file")
            }
        } catch (e: CancellationException) {
            Log.d(TAG, "Download canceled: ${e.message}")
            if (tempFile.exists()) tempFile.delete()
            telemetry.logModelDownloadFinished(
                modelId = modelId,
                success = false,
                durationMs = System.currentTimeMillis() - startedAtMs,
                fileType = fileType,
                errorType = "CancellationException"
            )
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}")
            if (tempFile.exists()) tempFile.delete()
            installedModelRegistry.updateInstallStatus(modelId, InstallStatus.FAILED)
            telemetry.logModelDownloadFinished(
                modelId = modelId,
                success = false,
                durationMs = System.currentTimeMillis() - startedAtMs,
                fileType = fileType,
                errorType = e::class.java.simpleName
            )
            telemetry.recordNonFatal(e, TelemetryConstants.Context.MODEL_DOWNLOAD)
            Result.failure(workDataOf(Constants.Download.OUTPUT_ERROR to (e.localizedMessage ?: Constants.Download.UNKNOWN_ERROR)))
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

    private fun isSupportedExtractedModelBundle(directory: File): Boolean {
        return hasRequiredFiles(directory, Constants.ImageGeneration.ONNX_REQUIRED_FILES) ||
            hasRequiredFiles(directory, Constants.ImageGeneration.QUALCOMM_REQUIRED_FILES)
    }

    private fun hasRequiredFiles(directory: File, requiredFiles: List<String>): Boolean {
        return requiredFiles.all { relativePath -> File(directory, relativePath).isFile }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(Constants.Download.NOTIFICATION_TITLE)
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
        val channel = NotificationChannel(channelId, Constants.Download.NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}
