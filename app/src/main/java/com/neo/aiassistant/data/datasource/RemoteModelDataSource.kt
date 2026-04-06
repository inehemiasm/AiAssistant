package com.neo.aiassistant.data.datasource

import com.google.firebase.storage.FirebaseStorage
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
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
 * Interface for resolving and downloading model files from remote storage.
 */
interface RemoteModelDataSource {
    suspend fun getDownloadUrl(uri: String): String
    fun downloadToFile(url: String, file: File): Flow<DownloadStatus>
}

/**
 * Implementation of RemoteModelDataSource using Firebase Storage and Ktor.
 */
@Singleton
class FirebaseRemoteModelDataSource @Inject constructor(
    private val httpClient: HttpClient
) : RemoteModelDataSource {

    override suspend fun getDownloadUrl(uri: String): String = withContext(Dispatchers.IO) {
        if (uri.startsWith("gs://")) {
            FirebaseStorage.getInstance().getReferenceFromUrl(uri).downloadUrl.await().toString()
        } else {
            uri
        }
    }

    override fun downloadToFile(url: String, file: File): Flow<DownloadStatus> = flow {
        httpClient.prepareGet(url).execute { response ->
            if (!response.status.isSuccess()) {
                throw IOException("HTTP error: ${response.status}")
            }

            val channel = response.bodyAsChannel()
            val totalBytes = response.contentLength() ?: 0L
            var bytesRead = 0L
            val buffer = ByteArray(128 * 1024)

            FileOutputStream(file).use { output ->
                while (!channel.isClosedForRead) {
                    val read = channel.readAvailable(buffer)
                    if (read == -1) break

                    output.write(buffer, 0, read)
                    bytesRead += read

                    if (totalBytes > 0) {
                        val progress = (bytesRead * 100.0 / totalBytes).toInt()
                        emit(DownloadStatus.Progress(progress))
                    }
                }
            }
        }
        emit(DownloadStatus.Success)
    }.flowOn(Dispatchers.IO)
}
