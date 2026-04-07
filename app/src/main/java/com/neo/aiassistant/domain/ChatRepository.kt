package com.neo.aiassistant.domain

import android.net.Uri
import com.neo.aiassistant.data.agent.AgentState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ChatRepository {
    val agentState: StateFlow<AgentState>
    fun getInitStatus(): Flow<String>
    suspend fun initializeModel(modelPath: String): Result<Unit>
    suspend fun sendMessage(prompt: String, imageUri: Uri? = null): Result<String>
    suspend fun clearConversation()
    fun downloadModel(url: String, modelName: String): Flow<DownloadProgress>
    suspend fun fetchAvailableModels(): Result<List<ModelEntry>>
    fun isVisionSupported(): Boolean
    fun getLocalModels(): List<LocalModel>
    fun deleteModel(modelName: String): Boolean
}

data class ModelEntry(
    val name: String = "",
    val url: String = "",
    val description: String = "",
    val provider: String = "Firebase",
    val sizeBytes: Long = 0,
    val runtimeType: String = "LiteRT"
)

data class LocalModel(
    val name: String,
    val path: String,
    val sizeBytes: Long
)

sealed class DownloadProgress {
    data class Progress(val percent: Int) : DownloadProgress()
    object Finished : DownloadProgress()
    data class Error(val message: String) : DownloadProgress()
}
