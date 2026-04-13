package com.neo.chevere.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.neo.chevere.core.DispatcherProvider
import com.neo.chevere.data.agent.AgentOrchestrator
import com.neo.chevere.data.agent.AgentState
import com.neo.chevere.data.datasource.ModelCatalogDataSource
import com.neo.chevere.data.download.WorkManagerModelDownloadManager
import com.neo.chevere.data.inference.InferenceManager
import com.neo.chevere.domain.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ChatRepositoryImpl"

/**
 * Implementation of [ChatRepository] that orchestrates between different data sources,
 * the inference runtime, and the Agent Orchestrator.
 */
@Singleton
class ChatRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val inferenceManager: InferenceManager,
    private val modelCatalog: ModelCatalogDataSource,
    private val downloadManager: WorkManagerModelDownloadManager,
    private val agentOrchestrator: AgentOrchestrator,
    private val installedModelRegistry: InstalledModelRegistry,
    private val dispatcherProvider: DispatcherProvider
) : ChatRepository {

    override val agentState: StateFlow<AgentState> = agentOrchestrator.agentState

    override fun getInitStatus(): Flow<String> = inferenceManager.initStatus

    override fun isVisionSupported(): Boolean = inferenceManager.isVisionSupported()

    override suspend fun initializeModel(modelPath: String): Result<Unit> {
        if (modelPath.isBlank()) {
            return Result.failure(Exception("Model path is empty"))
        }

        val file = File(modelPath)
        if (!file.exists()) {
            return Result.failure(Exception("Model file not found at: $modelPath"))
        }
        
        if (!file.isFile) {
            return Result.failure(Exception("Path is a directory, not a valid model file: ${file.name}"))
        }
        
        val installedModel = classifyModel(file) ?: return Result.failure(
            Exception("Unsupported or invalid model format. Please ensure the file has a .litertlm or .bin extension.")
        )
        
        // Sync with registry
        installedModelRegistry.upsertInstalledModel(installedModel)
        
        agentOrchestrator.reset()
        
        return when (val result = inferenceManager.loadModel(installedModel)) {
            com.neo.chevere.domain.LoadResult.Success -> Result.success(Unit)
            is com.neo.chevere.domain.LoadResult.Failure -> Result.failure(result.throwable ?: Exception(result.message))
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

    override suspend fun confirmAction(): Result<String> {
        return agentOrchestrator.confirmAction()
    }

    override suspend fun cancelAction(): Result<String> {
        return agentOrchestrator.cancelAction()
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

    override suspend fun getLocalModels(): List<InstalledModel> = withContext(dispatcherProvider.io) {
        val filesDir = context.filesDir
        
        // 1. Scan for potential model files
        val potentialFiles = filesDir.listFiles { file ->
            file.isFile && (file.name.endsWith(".litertlm") || file.name.endsWith(".bin"))
        } ?: emptyArray()

        // 2. Process and validate found files locally (Avoid remote fetch for performance)
        val validatedModels = potentialFiles.mapNotNull { file ->
            if (file.name.endsWith(".tmp") || file.length() < 1024) {
                return@mapNotNull null
            }
            
            // Try to find in existing registry first to preserve license/metadata
            val existing = installedModelRegistry.getInstalledModel(file.name)
            if (existing != null && File(existing.filePath).exists()) {
                existing
            } else {
                classifyModel(file)
            }
        }
        
        // 3. Update the Registry
        val currentRegistry = installedModelRegistry.getInstalledModels()
        
        currentRegistry.forEach { registryModel ->
            if (!File(registryModel.filePath).exists()) {
                Log.w(TAG, "Removing orphaned registry entry: ${registryModel.id}")
                installedModelRegistry.removeInstalledModel(registryModel.id)
            }
        }

        validatedModels.forEach { model ->
            installedModelRegistry.upsertInstalledModel(model)
        }
        
        installedModelRegistry.getInstalledModels()
    }

    override fun isModelValid(modelName: String): Boolean {
        if (modelName.isBlank()) return false
        val file = File(context.filesDir, modelName)
        return file.exists() && file.isFile && file.length() > 1024 * 1024
    }

    private fun classifyModel(file: File, license: String? = null): InstalledModel? {
        if (!file.exists() || !file.isFile) return null

        val extension = file.extension.lowercase()
        val (format, runtime) = when (extension) {
            "litertlm" -> ModelFormat.LITERTLM to ModelRuntime.LITERT
            "bin" -> ModelFormat.BIN to ModelRuntime.LITERT
            else -> return null
        }

        val capabilities = mutableSetOf(ModelCapability.TEXT)
        var taskType = ModelTaskType.CHAT
        
        if (file.name.contains("vision", ignoreCase = true) || 
            file.name.contains("multimodal", ignoreCase = true)) {
            capabilities.add(ModelCapability.VISION)
            taskType = ModelTaskType.VISION_CHAT
        }

        return InstalledModel(
            id = file.name,
            displayName = file.nameWithoutExtension.replace("_", " ")
                .replace("-", " ")
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() },
            filePath = file.absolutePath,
            fileName = file.name,
            source = ModelSource.LOCAL,
            format = format,
            runtime = runtime,
            taskType = taskType,
            capabilities = capabilities,
            installStatus = InstallStatus.INSTALLED,
            sizeBytes = file.length(),
            installedAt = file.lastModified(),
            license = license
        )
    }

    override suspend fun deleteModel(modelName: String): Boolean {
        if (modelName.isBlank()) return false
        val file = File(context.filesDir, modelName)
        if (file.exists()) {
            val deleted = file.delete()
            if (deleted) {
                installedModelRegistry.removeInstalledModel(modelName)
            }
            return deleted
        } else {
            installedModelRegistry.removeInstalledModel(modelName)
        }
        return false
    }
}
