package com.neo.aiassistant.data

import android.content.Context
import android.net.Uri
import com.neo.aiassistant.core.DispatcherProvider
import com.neo.aiassistant.data.agent.AgentOrchestrator
import com.neo.aiassistant.data.agent.AgentState
import com.neo.aiassistant.data.datasource.ModelCatalogDataSource
import com.neo.aiassistant.data.download.WorkManagerModelDownloadManager
import com.neo.aiassistant.data.inference.InferenceManager
import com.neo.aiassistant.domain.ChatRepository
import com.neo.aiassistant.domain.DownloadProgress
import com.neo.aiassistant.domain.LocalModel
import com.neo.aiassistant.domain.ModelCapability
import com.neo.aiassistant.domain.ModelEntry
import com.neo.aiassistant.domain.ModelFormat
import com.neo.aiassistant.domain.ModelRuntime
import com.neo.aiassistant.domain.ModelSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ChatRepository that orchestrates between different data sources,
 * the inference runtime, and the Agent Orchestrator.
 */
@Singleton
class ChatRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val inferenceManager: InferenceManager,
    private val modelCatalog: ModelCatalogDataSource,
    private val downloadManager: WorkManagerModelDownloadManager,
    private val agentOrchestrator: AgentOrchestrator,
    private val dispatcherProvider: DispatcherProvider
) : ChatRepository {

    override val agentState: StateFlow<AgentState> = agentOrchestrator.agentState

    override fun getInitStatus(): Flow<String> = inferenceManager.initStatus

    override fun isVisionSupported(): Boolean = inferenceManager.isVisionSupported()

    override suspend fun initializeModel(modelPath: String): Result<Unit> {
        val file = File(modelPath)
        if (!file.exists()) return Result.failure(Exception("Model file not found"))
        
        val localModel = classifyModel(file)
        agentOrchestrator.reset()
        
        return when (val result = inferenceManager.loadModel(localModel)) {
            com.neo.aiassistant.domain.LoadResult.Success -> Result.success(Unit)
            is com.neo.aiassistant.domain.LoadResult.Failure -> Result.failure(result.throwable ?: Exception(result.message))
        }
    }

    override suspend fun sendMessage(prompt: String, imageUri: Uri?): Result<String> {
        if (imageUri != null && !isVisionSupported()) {
            return Result.failure(
                IllegalArgumentException("Vision is not supported by the current model/backend.")
            )
        }

        return agentOrchestrator.processUserRequest(prompt, imageUri)
    }

    override suspend fun clearConversation() {
        agentOrchestrator.reset()
        inferenceManager.clearConversation()
    }

    override suspend fun fetchAvailableModels(): Result<List<ModelEntry>> {
        return modelCatalog.fetchAvailableModels()
    }

    override fun downloadModel(url: String, modelName: String, sha256: String?): Flow<DownloadProgress> {
        return downloadManager.downloadModel(url, modelName, sha256)
    }

    override suspend fun getLocalModels(): List<LocalModel> = withContext(dispatcherProvider.io) {
        val filesDir = context.filesDir
        filesDir.listFiles { file ->
            file.isFile && (file.name.endsWith(".litertlm") || file.name.endsWith(".bin"))
        }?.map { file ->
            classifyModel(file)
        } ?: emptyList()
    }

    /**
     * Check if a model file exists and is valid
     */
    override fun isModelValid(modelName: String): Boolean {
        val file = File(context.filesDir, modelName)
        return file.exists() && file.length() > 0
    }

    private fun classifyModel(file: File): LocalModel {
        val extension = file.extension.lowercase()
        val (format, runtime) = when (extension) {
            "litertlm" -> ModelFormat.LITERTLM to ModelRuntime.LITERT
            else -> ModelFormat.UNKNOWN to ModelRuntime.UNKNOWN
        }

        val capabilities = mutableSetOf(ModelCapability.TEXT)
        if (file.name.contains("vision", ignoreCase = true) || 
            file.name.contains("multimodal", ignoreCase = true)) {
            capabilities.add(ModelCapability.VISION)
        }

        return LocalModel(
            id = file.name,
            displayName = file.nameWithoutExtension.replace("_", " ")
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() },
            filePath = file.absolutePath,
            fileName = file.name,
            source = ModelSource.LOCAL,
            format = format,
            runtime = runtime,
            capabilities = capabilities,
            sizeBytes = file.length()
        )
    }

    override fun deleteModel(modelName: String): Boolean {
        val file = File(context.filesDir, modelName)
        if (file.exists()) {
            return file.delete()
        }
        return false
    }
}
