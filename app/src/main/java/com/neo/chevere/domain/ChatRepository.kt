package com.neo.chevere.domain

import android.net.Uri
import com.neo.chevere.data.agent.AgentState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for the Chat Repository.
 *
 * This repository is the main entry point for interacting with the AI assistant,
 * managing model lifecycle, chat history, and the agent execution loop.
 */
interface ChatRepository {
    /**
     * The current state of the AI Agent.
     */
    val agentState: StateFlow<AgentState>
    
    /**
     * Returns a flow of the initialization status of the AI engine.
     */
    fun getInitStatus(): Flow<InitializationStatus>
    
    /**
     * Checks if the current model/backend supports vision (image input).
     */
    fun isVisionSupported(): Boolean
    
    /**
     * Initializes the AI model from the given file path.
     */
    suspend fun initializeModel(modelPath: String): Result<Unit>
    
    /**
     * Sends a message to the AI and receives a response through the agent loop.
     */
    suspend fun sendMessage(prompt: String, imageUri: Uri? = null): Result<String>
    
    /**
     * Confirms a pending action requested by a tool.
     */
    suspend fun confirmAction(): Result<String>
    
    /**
     * Cancels a pending action requested by a tool.
     */
    suspend fun cancelAction(): Result<String>
    
    /**
     * Clears the current conversation history and resets the agent state.
     */
    suspend fun clearConversation()
    
    /**
     * Fetches a list of models available for download from the remote catalog.
     */
    suspend fun fetchAvailableModels(): Result<List<ModelEntry>>
    
    /**
     * Downloads a model from the given URL.
     */
    fun downloadModel(url: String, modelName: String, sha256: String? = null): Flow<DownloadProgress>
    
    /**
     * Returns a list of models currently installed on the device.
     */
    suspend fun getLocalModels(): List<InstalledModel>
    
    /**
     * Deletes an installed model.
     */
    suspend fun deleteModel(modelName: String): Boolean
    
    /**
     * Checks if a model file exists and is valid.
     */
    fun isModelValid(modelName: String): Boolean
}
