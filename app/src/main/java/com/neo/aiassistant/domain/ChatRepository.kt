package com.neo.aiassistant.domain

import android.net.Uri
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getInitStatus(): Flow<String>
    suspend fun initializeModel(modelPath: String): Result<Unit>
    suspend fun sendMessage(prompt: String, imageUri: Uri? = null): Result<String>
    fun downloadModel(url: String, modelName: String): Flow<DownloadProgress>
    suspend fun fetchAvailableModels(): Result<List<ModelEntry>>
    fun isVisionSupported(): Boolean
}

data class ModelEntry(
    val name: String = "",
    val url: String = ""
)

sealed class DownloadProgress {
    data class Progress(val percent: Int) : DownloadProgress()
    object Finished : DownloadProgress()
    data class Error(val message: String) : DownloadProgress()
}
