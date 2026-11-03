package com.neo.chevere.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.neo.chevere.R
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
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
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipInputStream

private const val TAG = "ModelDownloadWorker"
private const val MAX_AUTO_RETRIES = 3

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
        val repositoryFiles = inputData.getStringArray(Constants.Download.INPUT_REPOSITORY_FILES)
            ?.filter { it.isNotBlank() }
            .orEmpty()

        Timber.tag(TAG).d("Worker started. Name: $modelName, URL: $uri")

        if (uri == null || modelName == null || modelId == null) {
            return@withContext Result.failure(workDataOf(Constants.Download.OUTPUT_ERROR to applicationContext.getString(R.string.ui_missing_metadata)))
        }

        if (modelName != com.neo.chevere.data.inference.minicpm.MiniCpmBundle.ID &&
            !modelName.endsWith(Constants.ModelFiles.LITERTLM_EXTENSION) &&
            !modelName.endsWith(Constants.ModelFiles.BIN_EXTENSION) &&
            !modelName.endsWith(Constants.ModelFiles.ZIP_EXTENSION)
        ) {
            return@withContext Result.failure(workDataOf(Constants.Download.OUTPUT_ERROR to applicationContext.getString(R.string.ui_unsupported_file_type)))
        }

        val targetFile = File(applicationContext.filesDir, modelName)
        val tempFile =
            File(applicationContext.filesDir, "$modelName${Constants.ModelFiles.TEMP_EXTENSION}")
        val fileType = modelName.substringAfterLast('.', missingDelimiterValue = "directory")
        val startedAtMs = System.currentTimeMillis()
        telemetry.logModelDownloadStarted(modelId = modelId, fileType = fileType)

        try {
            setForeground(getForegroundInfo())

            // Mark as downloading in registry
            installedModelRegistry.updateInstallStatus(modelId, InstallStatus.DOWNLOADING)

            if (modelName == com.neo.chevere.data.inference.minicpm.MiniCpmBundle.ID) {
                require(modelId == modelName)
                downloadMiniCpmBundle(modelId, modelName)
                installedModelRegistry.updateInstallStatus(modelId, InstallStatus.INSTALLED)
                setProgress(workDataOf(Constants.Download.PROGRESS to 100))
                telemetry.logModelDownloadFinished(modelId, true, System.currentTimeMillis() - startedAtMs, "repository")
                return@withContext Result.success()
            }
            if (repositoryFiles.isNotEmpty()) {
                val targetDir = File(applicationContext.filesDir, modelId)
                val tempDir = File(
                    applicationContext.filesDir,
                    "${targetDir.name}${Constants.ModelFiles.TEMP_DIRECTORY_EXTENSION}"
                )
                if (targetDir.exists()) targetDir.deleteRecursively()
                if (tempDir.exists()) tempDir.deleteRecursively()
                tempDir.mkdirs()

                downloadRepositoryBundle(repositoryFiles, tempDir, modelName)

                installedModelRegistry.updateInstallStatus(modelId, InstallStatus.VERIFYING)
                if (!isSupportedExtractedModelBundle(tempDir)) {
                    tempDir.deleteRecursively()
                    installedModelRegistry.updateInstallStatus(modelId, InstallStatus.FAILED)
                    throw IOException(applicationContext.getString(R.string.ui_downloaded_repository_is_not_a_supported_image_model_bundle))
                }
                if (!tempDir.renameTo(targetDir)) {
                    tempDir.deleteRecursively()
                    installedModelRegistry.updateInstallStatus(modelId, InstallStatus.FAILED)
                    throw IOException(applicationContext.getString(R.string.ui_failed_to_finalize_downloaded_image_model))
                }
                installedModelRegistry.updateInstallStatus(modelId, InstallStatus.INSTALLED)
                setProgress(workDataOf(Constants.Download.PROGRESS to 100))
                telemetry.logModelDownloadFinished(
                    modelId = modelId,
                    success = true,
                    durationMs = System.currentTimeMillis() - startedAtMs,
                    fileType = "repository"
                )
                return@withContext Result.success()
            }

            val downloadUrl = remoteModelDataSource.getDownloadUrl(uri)

            // Resume from wherever the temp file left off (0 = fresh start).
            val resumeOffset = if (tempFile.exists()) tempFile.length() else 0L
            if (resumeOffset > 0L) {
                Timber.tag(TAG).i("Resuming download of $modelName from byte $resumeOffset")
            }

            // Call downloadToFile using native overload on interface
            val downloadFlow = remoteModelDataSource.downloadToFile(downloadUrl, tempFile, resumeOffset)
            downloadFlow.collect { status ->
                kotlin.coroutines.coroutineContext.ensureActive()
                emitDownloadProgress(status, modelName)
            }

            if (tempFile.exists() && tempFile.length() > Constants.ModelFiles.MIN_VALID_FILE_SIZE_BYTES) {
                // Phase 4: Integrity check
                installedModelRegistry.updateInstallStatus(modelId, InstallStatus.VERIFYING)

                if (expectedSha256 != null) {
                    val actualSha256 = calculateSha256(tempFile)
                    if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                        installedModelRegistry.updateInstallStatus(modelId, InstallStatus.CORRUPTED)
                        throw IOException(applicationContext.getString(R.string.ui_checksum_mismatch))
                    }
                }

                if (targetFile.exists()) targetFile.delete()

                if (modelName.endsWith(Constants.ModelFiles.ZIP_EXTENSION)) {
                    val targetDir = File(applicationContext.filesDir, modelId)
                    val tempDir = File(
                        applicationContext.filesDir,
                        "${targetDir.name}${Constants.ModelFiles.TEMP_DIRECTORY_EXTENSION}"
                    )
                    if (targetDir.exists()) targetDir.deleteRecursively()
                    if (tempDir.exists()) tempDir.deleteRecursively()
                    tempDir.mkdirs()

                    unzipModelBundle(tempFile, tempDir)
                    if (!isSupportedExtractedModelBundle(tempDir)) {
                        tempDir.deleteRecursively()
                        installedModelRegistry.updateInstallStatus(modelId, InstallStatus.FAILED)
                        throw IOException(
                            applicationContext.getString(R.string.ui_downloaded_zip_is_not_a_supported_model_bundle_missing_required_)
                        )
                    }
                    if (!tempDir.renameTo(targetDir)) {
                        tempDir.deleteRecursively()
                        installedModelRegistry.updateInstallStatus(modelId, InstallStatus.FAILED)
                        throw IOException(applicationContext.getString(R.string.ui_failed_to_finalize_extracted_image_model))
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
                    throw IOException(applicationContext.getString(R.string.ui_finalization_failed))
                }
            } else {
                installedModelRegistry.updateInstallStatus(modelId, InstallStatus.FAILED)
                throw IOException(applicationContext.getString(R.string.ui_empty_file))
            }
        } catch (e: CancellationException) {
            // User explicitly canceled; discard the partial file so a fresh start is guaranteed.
            Timber.tag(TAG).d("Download canceled by user: ${e.message}")
            if (tempFile.exists()) tempFile.delete()
            File(
                applicationContext.filesDir,
                "${modelId}${Constants.ModelFiles.TEMP_DIRECTORY_EXTENSION}"
            ).deleteRecursively()
            telemetry.logModelDownloadFinished(
                modelId = modelId,
                success = false,
                durationMs = System.currentTimeMillis() - startedAtMs,
                fileType = fileType,
                errorType = "CancellationException"
            )
            throw e
        } catch (e: Exception) {
            // Transient failure (network drop, timeout, etc.).
            // Keep the temp file so the next run can resume from where it left off.
            val attemptsMade = runAttemptCount  // 0-based; 0 = first attempt
            val willRetry = attemptsMade < MAX_AUTO_RETRIES
            Timber.tag(TAG).e(
                e,
                "Download failed (attempt ${attemptsMade + 1}/${
                    MAX_AUTO_RETRIES + 1
                }). ${if (willRetry) "Will retry; keeping partial file (${tempFile.length()} bytes)." else "Max retries reached."}"
            )
            if (!willRetry) {
                // Max retries exhausted; clean up and report permanent failure.
                // Keep the partial files on standard network/transient failures. Discard only on corruption.
                val isCorruption = e.message?.contains(applicationContext.getString(R.string.ui_checksum_mismatch)) == true ||
                        e.message?.contains("ZIP") == true ||
                        e.message?.contains("extracted") == true ||
                        e.message?.contains("finalize") == true
                if (isCorruption) {
                    if (tempFile.exists()) tempFile.delete()
                    File(
                        applicationContext.filesDir,
                        "${modelId}${Constants.ModelFiles.TEMP_DIRECTORY_EXTENSION}"
                    ).deleteRecursively()
                }
                installedModelRegistry.updateInstallStatus(modelId, InstallStatus.FAILED)
                telemetry.logModelDownloadFinished(
                    modelId = modelId,
                    success = false,
                    durationMs = System.currentTimeMillis() - startedAtMs,
                    fileType = fileType,
                    errorType = e::class.java.simpleName
                )
                telemetry.recordNonFatal(e, TelemetryConstants.Context.MODEL_DOWNLOAD)
                Result.failure(
                    workDataOf(
                        Constants.Download.OUTPUT_ERROR to (e.localizedMessage
                            ?: Constants.Download.UNKNOWN_ERROR)
                    )
                )
            } else {
                // Keep the partial temp file and let WorkManager retry with exponential back-off.
                Result.retry()
            }
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
                    throw IOException(applicationContext.getString(R.string.ui_unsafe_zip_entry_1_s, entry.name))
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

    private suspend fun downloadMiniCpmBundle(modelId: String, modelName: String) {
        val bundle = com.neo.chevere.data.inference.minicpm.MiniCpmBundle
        val manifest = bundle.manifest(applicationContext)
        val target = File(applicationContext.filesDir, modelId)
        if (bundle.isComplete(target, manifest)) return
        // Retain verified and partial files between network retries; never delete a healthy install.
        require(!target.exists())
        val temp = File(applicationContext.filesDir, modelId + Constants.ModelFiles.TEMP_DIRECTORY_EXTENSION)
        temp.mkdirs()
        val remaining = manifest.files.sumOf { entry ->
            (entry.sizeBytes - File(temp, entry.path).length()).coerceAtLeast(0L)
        }
        if (temp.usableSpace < remaining + 512L * 1024 * 1024) {
            throw IOException(applicationContext.getString(R.string.ui_minicpm_storage_required))
        }
        var completed = 0L
        val total = manifest.files.sumOf { it.sizeBytes }
        manifest.files.forEach { entry ->
            kotlin.coroutines.coroutineContext.ensureActive()
            val file = File(temp, entry.path).canonicalFile
            require(file.path.startsWith(temp.canonicalPath + File.separator))
            file.parentFile?.mkdirs()
            if (file.length() > entry.sizeBytes) file.delete()
            if (!file.isFile || file.length() != entry.sizeBytes) {
                val url = remoteModelDataSource.getDownloadUrl(entry.url)
                remoteModelDataSource.downloadToFile(url, file, file.length()).collect { progress ->
                    kotlin.coroutines.coroutineContext.ensureActive()
                    if (progress is DownloadStatus.Progress) {
                        val bytes = completed + entry.sizeBytes * progress.percent / 100
                        emitDownloadProgress(DownloadStatus.Progress((bytes * 100 / total).toInt().coerceIn(0, 99)), modelName)
                    }
                }
            }
            if (file.length() != entry.sizeBytes || !calculateSha256(file).equals(entry.sha256, true)) {
                file.delete()
                throw IOException(applicationContext.getString(R.string.ui_checksum_mismatch))
            }
            completed += entry.sizeBytes
        }
        installedModelRegistry.updateInstallStatus(modelId, InstallStatus.VERIFYING)
        check(bundle.isComplete(temp, manifest))
        check(temp.renameTo(target))
    }

    private suspend fun downloadRepositoryBundle(
        repositoryFiles: List<String>,
        targetDir: File,
        modelName: String
    ) {
        repositoryFiles.forEachIndexed { index, spec ->
            kotlin.coroutines.coroutineContext.ensureActive()
            val repositoryFile = RepositoryFileSpec.parse(spec)
            val destination = File(targetDir, repositoryFile.relativePath).canonicalFile
            val canonicalTarget = targetDir.canonicalFile
            if (!destination.path.startsWith(canonicalTarget.path + File.separator)) {
                throw IOException(applicationContext.getString(R.string.ui_unsafe_repository_file_path_1_s, repositoryFile.relativePath))
            }
            destination.parentFile?.mkdirs()

            val url = remoteModelDataSource.getDownloadUrl(repositoryFile.url)
            val resumeOffset = if (destination.exists()) destination.length() else 0L
            val downloadFlow = remoteModelDataSource.downloadToFile(url, destination, resumeOffset)
            downloadFlow.collect { status ->
                kotlin.coroutines.coroutineContext.ensureActive()
                if (status is DownloadStatus.Progress) {
                    val aggregateProgress = ((index * 100) + status.percent) / repositoryFiles.size
                    emitDownloadProgress(DownloadStatus.Progress(aggregateProgress.coerceIn(0, 99)), modelName)
                }
            }
        }
    }

    private suspend fun emitDownloadProgress(status: DownloadStatus, modelName: String) {
        if (status is DownloadStatus.Progress) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastUpdateMs.get() >= throttleIntervalMs) {
                setProgress(workDataOf(Constants.Download.PROGRESS to status.percent))
                setForeground(getForegroundInfoHelper(status.percent, modelName))
                lastUpdateMs.set(currentTime)
            }
        }
    }

    private fun isSupportedExtractedModelBundle(directory: File): Boolean {
        // Find if any subdirectory (up to 3 deep) contains the required ONNX files
        val root = findModelRoot(directory)
        return root != null
    }

    private fun findModelRoot(directory: File): File? {
        if (!directory.isDirectory) return null
        if (hasRequiredFiles(directory)) return directory

        return directory.walkTopDown().maxDepth(3).filter { it.isDirectory }
            .find { hasRequiredFiles(it) }
    }

    private fun hasRequiredFiles(directory: File): Boolean {
        return Constants.ImageGeneration.ONNX_REQUIRED_FILES.all { relativePath ->
            File(directory, relativePath).isFile ||
                    File(directory, relativePath.replace(".ort", ".onnx")).isFile
        }
    }

    private data class RepositoryFileSpec(
        val url: String,
        val relativePath: String
    ) {
        companion object {
            fun parse(value: String): RepositoryFileSpec {
                val parts = value.split("|", limit = 2)
                val url = parts[0].trim()
                val relativePath = parts.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
                    ?: inferRelativePath(url)
                return RepositoryFileSpec(url, relativePath)
            }

            private fun inferRelativePath(url: String): String {
                val pathAfterResolve = url.substringAfter("/resolve/", missingDelimiterValue = url)
                    .substringAfter("/")
                return URLDecoder.decode(pathAfterResolve, StandardCharsets.UTF_8.name())
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val modelName = inputData.getString(Constants.Download.INPUT_MODEL_NAME) ?: applicationContext.getString(R.string.notification_model_fallback)
        return getForegroundInfoHelper(0, modelName)
    }

    private fun getForegroundInfoHelper(progress: Int, modelName: String): ForegroundInfo {
        createNotificationChannel()
        val cancelIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(applicationContext.getString(R.string.notification_downloading_model, modelName))
            .setContentText(applicationContext.getString(R.string.notification_download_progress, progress))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, applicationContext.getString(R.string.cancel_button), cancelIntent)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            channelId,
            Constants.Download.NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        )
        val manager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}
