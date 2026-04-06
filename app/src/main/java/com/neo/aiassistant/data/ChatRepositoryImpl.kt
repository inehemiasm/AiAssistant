package com.neo.aiassistant.data

import android.net.Uri
import com.neo.aiassistant.data.agent.AgentOrchestrator
import com.neo.aiassistant.data.agent.AgentState
import com.neo.aiassistant.data.datasource.ModelCatalogDataSource
import com.neo.aiassistant.data.download.WorkManagerModelDownloadManager
import com.neo.aiassistant.data.inference.LlmResponseMapper
import com.neo.aiassistant.data.inference.LlmRuntimeManager
import com.neo.aiassistant.data.inference.MultimodalMessageFactory
import com.neo.aiassistant.domain.ChatRepository
import com.neo.aiassistant.domain.DownloadProgress
import com.neo.aiassistant.domain.ModelEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ChatRepository that orchestrates between different data sources,
 * the inference runtime, and the Agent Orchestrator.
 */
@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val runtimeManager: LlmRuntimeManager,
    private val messageFactory: MultimodalMessageFactory,
    private val responseMapper: LlmResponseMapper,
    private val modelCatalog: ModelCatalogDataSource,
    private val downloadManager: WorkManagerModelDownloadManager,
    private val agentOrchestrator: AgentOrchestrator
) : ChatRepository {

    override val agentState: StateFlow<AgentState> = agentOrchestrator.agentState

    override fun getInitStatus(): Flow<String> = runtimeManager.initStatus

    override fun isVisionSupported(): Boolean = runtimeManager.isVisionSupported()

    override suspend fun initializeModel(modelPath: String): Result<Unit> {
        agentOrchestrator.reset()
        return runtimeManager.initialize(modelPath)
    }

    override suspend fun sendMessage(prompt: String, imageUri: Uri?): Result<String> {
        if (imageUri != null && !runtimeManager.isVisionSupported()) {
            return Result.failure(
                IllegalArgumentException("Vision is not supported by the current model/backend.")
            )
        }

        // We use the AgentOrchestrator to handle the request.
        // It internally handles the loop if tools are triggered,
        // or returns a direct response if not.
        return agentOrchestrator.processUserRequest(prompt, imageUri)
    }

    override suspend fun clearConversation() {
        agentOrchestrator.reset()
        runtimeManager.clearConversation()
    }

    override suspend fun fetchAvailableModels(): Result<List<ModelEntry>> {
        return modelCatalog.fetchAvailableModels()
    }

    override fun downloadModel(url: String, modelName: String): Flow<DownloadProgress> {
        return downloadManager.downloadModel(url, modelName)
    }
}
