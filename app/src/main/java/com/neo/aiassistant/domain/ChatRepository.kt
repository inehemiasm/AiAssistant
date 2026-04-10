package com.neo.aiassistant.domain

import android.net.Uri
import com.neo.aiassistant.data.agent.AgentState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface for managing chat interactions and AI models.
 *
 * This repository handles model initialization, message sending, model downloads,
 * and retrieval of available models.
 */
interface ChatRepository {
    /**
     * A [StateFlow] representing the current state of the AI agent.
     */
    val agentState: StateFlow<AgentState>

    /**
     * Returns a [Flow] of strings representing the status of model initialization.
     */
    fun getInitStatus(): Flow<String>

    /**
     * Initializes the AI model from the given file path.
     *
     * @param modelPath The absolute path to the model file.
     * @return A [Result] indicating success or failure.
     */
    suspend fun initializeModel(modelPath: String): Result<Unit>

    /**
     * Sends a message to the AI agent and returns the response.
     *
     * @param prompt The user's input message.
     * @param imageUri Optional URI of an image to be processed with the message.
     * @return A [Result] containing the AI's response text or an error.
     */
    suspend fun sendMessage(prompt: String, imageUri: Uri? = null): Result<String>

    /**
     * Clears the current conversation history.
     */
    suspend fun clearConversation()

    /**
     * Downloads an AI model from the specified URL.
     *
     * @param url The URL of the model file.
     * @param modelName The name to assign to the downloaded model.
     * @param sha256 Optional SHA-256 hash for verifying the downloaded file.
     * @return A [Flow] of [DownloadProgress] updates.
     */
    fun downloadModel(url: String, modelName: String, sha256: String? = null): Flow<DownloadProgress>

    /**
     * Fetches the list of available models from the catalog.
     *
     * @return A [Result] containing the list of [ModelEntry] objects or an error.
     */
    suspend fun fetchAvailableModels(): Result<List<ModelEntry>>

    /**
     * Checks if the currently loaded model supports vision (image processing).
     */
    fun isVisionSupported(): Boolean

    /**
     * Returns a list of all AI models stored locally on the device.
     */
    suspend fun getLocalModels(): List<LocalModel>

    /**
     * Checks if a model file with the given name exists and is valid.
     */
    fun isModelValid(modelName: String): Boolean

    /**
     * Deletes the specified model from local storage.
     *
     * @param modelName The name of the model to delete.
     * @return `true` if the model was successfully deleted, `false` otherwise.
     */
    fun deleteModel(modelName: String): Boolean
}

/**
 * Represents an entry for an AI model in the model catalog.
 *
 * @property name The name of the model.
 * @property url The download URL for the model.
 * @property description A brief description of the model's capabilities.
 * @property provider The provider of the model (e.g., "Firebase").
 * @property sizeBytes The size of the model file in bytes.
 * @property runtimeType The required runtime for the model (e.g., "LiteRT").
 * @property sha256 Optional SHA-256 hash for file verification.
 * @property fileName The name of the file on disk.
 * @property supportsVision Whether the model supports vision tasks.
 */
data class ModelEntry(
    val name: String = "",
    val url: String = "",
    val description: String = "",
    val provider: String = "Firebase",
    val sizeBytes: Long = 0,
    val runtimeType: String = "LiteRT",
    val sha256: String? = null,
    val fileName: String? = null,
    val supportsVision: Boolean = false
) {
    /**
     * The effective file name to use for this model, derived from [fileName] or [name].
     */
    val effectiveFileName: String get() = fileName ?: name.replace(" ", "_").lowercase() + ".litertlm"
}

/**
 * Represents the progress of a model download operation.
 */
sealed class DownloadProgress {
    /**
     * Indicates that the download is in progress.
     * @property percent The percentage of the download completed (0-100).
     */
    data class Progress(val percent: Int) : DownloadProgress()
    
    /** Indicates that the download has finished successfully. */
    object Finished : DownloadProgress()
    
    /** 
     * Indicates that an error occurred during the download.
     * @property message A description of the error.
     */
    data class Error(val message: String) : DownloadProgress()
}
