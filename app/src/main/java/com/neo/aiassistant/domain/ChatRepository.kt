package com.neo.aiassistant.domain

import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    suspend fun initializeModel(modelPath: String): Result<Unit>
    suspend fun sendMessage(prompt: String): Result<String>
    fun downloadModel(url: String, modelName: String): Flow<DownloadProgress>
    suspend fun fetchAvailableModels(): Result<List<ModelEntry>>
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
