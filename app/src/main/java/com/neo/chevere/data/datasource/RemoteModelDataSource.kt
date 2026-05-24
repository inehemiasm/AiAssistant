package com.neo.chevere.data.datasource

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed class DownloadStatus {
    data class Progress(val percent: Int) : DownloadStatus()
    object Success : DownloadStatus()
}

/**
 * Interface for resolving and downloading model files from remote HTTP sources.
 */
interface RemoteModelDataSource {
    suspend fun getDownloadUrl(uri: String): String
    fun downloadToFile(url: String, file: File): Flow<DownloadStatus>
}

/**
 * Default implementation of [RemoteModelDataSource] using Ktor for direct HTTP downloads.
 *
 * Supports resumable downloads via the HTTP `Range` header. When [startFromByte] is greater than
 * zero the request includes `Range: bytes=<startFromByte>-` and the file is opened in append mode,
 * allowing an interrupted download to continue from where it left off without restarting.
 */
@Singleton
class DefaultRemoteModelDataSource @Inject constructor(
    private val httpClient: HttpClient
) : RemoteModelDataSource {

    override suspend fun getDownloadUrl(uri: String): String = uri

    override fun downloadToFile(url: String, file: File): Flow<DownloadStatus> =
        downloadToFile(url, file, startFromByte = 0L)

    /**
     * Downloads [url] into [file], resuming from [startFromByte] if non-zero.
     *
     * When resuming:
     * - Sends `Range: bytes=<startFromByte>-` so the server streams only the remaining bytes.
     * - Opens [file] in **append** mode so already-downloaded bytes are preserved.
     * - Reports progress as a percentage of the full file size (already-downloaded + remaining).
     */
    fun downloadToFile(
        url: String,
        file: File,
        startFromByte: Long
    ): Flow<DownloadStatus> = flow {
        val isResume = startFromByte > 0L
        httpClient.prepareGet(url) {
            if (isResume) {
                headers.append(HttpHeaders.Range, "bytes=$startFromByte-")
            }
        }.execute { response ->
            // 206 Partial Content is expected for resumed requests; 200 is fine for fresh ones.
            if (!response.status.isSuccess()) {
                throw IOException("HTTP error: ${response.status}")
            }

            // Check if we accidentally downloaded an HTML error page.
            val contentType = response.contentType()
            if (contentType != null &&
                contentType.contentType == ContentType.Text.Html.contentType &&
                contentType.contentSubtype == ContentType.Text.Html.contentSubtype
            ) {
                throw IOException("Received HTML instead of a model file. The download link might be expired or blocked.")
            }

            val channel = response.bodyAsChannel()
            // Content-Length from a Range response is the *remaining* bytes only.
            val remainingBytes = response.contentLength() ?: 0L
            val totalBytes = startFromByte + remainingBytes
            // Track bytes written in this session; start accounting from the resume offset.
            var bytesRead = startFromByte
            val buffer = ByteArray(128 * 1024)
            var lastEmittedProgress = if (isResume && totalBytes > 0) {
                (startFromByte * 100 / totalBytes).toInt()
            } else {
                -1
            }

            // Append when resuming so the already-downloaded prefix is kept.
            FileOutputStream(file, isResume).use { output ->
                while (!channel.isClosedForRead) {
                    val read = channel.readAvailable(buffer)
                    if (read == -1) break

                    output.write(buffer, 0, read)
                    bytesRead += read

                    if (totalBytes > 0) {
                        val progress = (bytesRead * 100.0 / totalBytes).toInt()
                        if (progress != lastEmittedProgress) {
                            emit(DownloadStatus.Progress(progress))
                            lastEmittedProgress = progress
                        }
                    }
                }
            }
        }
        emit(DownloadStatus.Success)
    }.flowOn(Dispatchers.IO)
}
