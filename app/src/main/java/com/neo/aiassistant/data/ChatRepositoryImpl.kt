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
 * Implementation of [ChatRepository] that orchestrates between different data sources,
 * the inference runtime, and the Agent Orchestrator.
 *
 * This repository handles the high-level logic for model lifecycle and message processing,
 * delegating specific tasks to specialized managers like [InferenceManager] and [AgentOrchestrator].
 *
 * @property context The application context.
 * @property inferenceManager Manages the loading and execution of AI models.
 * @property modelCatalog Data source for fetching available models.
 * @property downloadManager Manages model downloads using WorkManager.
 * @property agentOrchestrator Orchestrates the agent's reasoning loop.
 * @property dispatcherProvider Provides coroutine dispatchers.
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

    /**
     * A [StateFlow] representing the current state of the AI agent.
     */
    override val agentState: StateFlow<AgentState> = agentOrchestrator.agentState

    /**
     * Returns a [Flow] of strings representing the status of model initialization.
     */
    override fun getInitStatus(): Flow<String> = inferenceManager.initStatus

    /**
     * Checks if the currently loaded model/backend supports vision.
     */
    override fun isVisionSupported(): Boolean = inferenceManager.isVisionSupported()

    /**
     * Initializes the AI model from the given [modelPath].
     *
     * @param modelPath The absolute path to the model file.
     * @return A [Result] indicating success or failure.
     */
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

    /**
     * Sends a message to the AI agent and returns the response.
     *
     * @param prompt The user's input message.
     * @param imageUri Optional URI of an image to be processed.
     * @return A [Result] containing the AI's response text.
     */
    override suspend fun sendMessage(prompt: String, imageUri: Uri?): Result<String> {
        if (imageUri != null && !isVisionSupported()) {
            return Result.failure(
                IllegalArgumentException("Vision is not supported by the current model/backend.")
            )
        }

        return agentOrchestrator.processUserRequest(prompt, imageUri)
    }

    /**
     * Clears the current conversation history and resets the agent.
     */
    override suspend fun clearConversation() {
        agentOrchestrator.reset()
        inferenceManager.clearConversation()
    }

    /**
     * Fetches the list of available models from the remote catalog.
     *
     * @return A [Result] containing the list of [ModelEntry] objects.
     */
    override suspend fun fetchAvailableModels(): Result<List<ModelEntry>> {
        return modelCatalog.fetchAvailableModels()
    }

    /**
     * Initiates a model download.
     *
     * @param url The URL of the model file.
     * @param modelName The name for the downloaded model.
     * @param sha256 Optional SHA-256 hash for verification.
     * @return A [Flow] of [DownloadProgress].
     */
    override fun downloadModel(url: String, modelName: String, sha256: String?): Flow<DownloadProgress> {
        return downloadManager.downloadModel(url, modelName, sha256)
    }

    /**
     * Scans local storage for compatible AI model files.
     *
     * @return A list of [LocalModel] objects found on device.
     */
    override suspend fun getLocalModels(): List<LocalModel> = withContext(dispatcherProvider.io) {
        val filesDir = context.filesDir
        filesDir.listFiles { file ->
            file.isFile && (file.name.endsWith(".litertlm") || file.name.endsWith(".bin"))
        }?.map { file ->
            classifyModel(file)
        } ?: emptyList()
    }

    /**
     * Verifies if a model file exists and is not empty.
     *
     * @param modelName The name of the model file to check.
     * @return `true` if valid, `false` otherwise.
     */
    override fun isModelValid(modelName: String): Boolean {
        val file = File(context.filesDir, modelName)
        return file.exists() && file.length() > 0
    }

    /**
     * Classifies a raw model [file] into a structured [LocalModel] object.
     *
     * @param file The model file on disk.
     * @return A [LocalModel] representing the file and its capabilities.
     */
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

    /**
     * Deletes the specified model file from local storage.
     *
     * @param modelName The name of the model to delete.
     * @return `true` if deleted successfully, `false` otherwise.
     */
    override fun deleteModel(modelName: String): Boolean {
        val file = File(context.filesDir, modelName)
        if (file.exists()) {
            return file.delete()
        }
        return false
    }
}
