package com.neo.chevere.data.datasource

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
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
 */
@Singleton
class DefaultRemoteModelDataSource @Inject constructor(
    private val httpClient: HttpClient
) : RemoteModelDataSource {

    override suspend fun getDownloadUrl(uri: String): String = uri

    override fun downloadToFile(url: String, file: File): Flow<DownloadStatus> = flow {
        httpClient.prepareGet(url).execute { response ->
            if (!response.status.isSuccess()) {
                throw IOException("HTTP error: ${response.status}")
            }

            // Check if we accidentally downloaded an HTML error page from a blocked or expired link.
            val contentType = response.contentType()
            if (contentType != null && contentType.contentType == ContentType.Text.Html.contentType && contentType.contentSubtype == ContentType.Text.Html.contentSubtype) {
                throw IOException("Received HTML instead of a model file. The download link might be expired or blocked.")
            }

            val channel = response.bodyAsChannel()
            val totalBytes = response.contentLength() ?: 0L
            var bytesRead = 0L
            val buffer = ByteArray(128 * 1024)

            var lastEmittedProgress = -1

            FileOutputStream(file).use { output ->
                while (!channel.isClosedForRead) {
                    val read = channel.readAvailable(buffer)
                    if (read == -1) break

                    output.write(buffer, 0, read)
                    bytesRead += read

                    if (totalBytes > 0) {
                        val progress = (bytesRead * 100.0 / totalBytes).toInt()
                        // Only emit if progress has actually changed to avoid overwhelming the flow
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
