package com.neo.chevere.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.neo.chevere.core.Constants
import com.neo.chevere.core.DispatcherProvider
import com.neo.chevere.data.agent.AgentOrchestrator
import com.neo.chevere.data.agent.AgentState
import com.neo.chevere.data.datasource.ModelCatalogDataSource
import com.neo.chevere.data.download.WorkManagerModelDownloadManager
import com.neo.chevere.data.inference.ImageGenerationManager
import com.neo.chevere.data.inference.InferenceManager
import com.neo.chevere.domain.ChatRepository
import com.neo.chevere.domain.DownloadProgress
import com.neo.chevere.domain.ImageGenerationRequest
import com.neo.chevere.domain.ImageGenerationResult
import com.neo.chevere.domain.InitializationStatus
import com.neo.chevere.domain.InstallStatus
import com.neo.chevere.domain.InstalledModel
import com.neo.chevere.domain.InstalledModelRegistry
import com.neo.chevere.domain.ModelCapability
import com.neo.chevere.domain.ModelEntry
import com.neo.chevere.domain.ModelFormat
import com.neo.chevere.domain.ModelRuntime
import com.neo.chevere.domain.ModelSource
import com.neo.chevere.domain.ModelTaskType
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
    private val imageGenerationManager: ImageGenerationManager,
    private val installedModelRegistry: InstalledModelRegistry,
    private val dispatcherProvider: DispatcherProvider
) : ChatRepository {

    override val agentState: StateFlow<AgentState> = agentOrchestrator.agentState

    override fun getInitStatus(): Flow<InitializationStatus> = inferenceManager.initStatus

    override fun isVisionSupported(): Boolean = inferenceManager.isVisionSupported()

    override suspend fun initializeModel(modelPath: String): Result<Unit> {
        if (modelPath.isBlank()) {
            return Result.failure(Exception("Model path is empty"))
        }

        val file = File(modelPath)
        if (!file.exists()) {
            return Result.failure(Exception("Model file not found at: $modelPath"))
        }

        val installedModel = classifyModel(file) ?: return Result.failure(
            Exception("Unsupported or invalid model format. Please use a .litertlm, .bin, or extracted image model directory.")
        )
        
        // Sync with registry
        installedModelRegistry.upsertInstalledModel(installedModel)

        if (installedModel.taskType == ModelTaskType.IMAGE_GENERATION) {
            agentOrchestrator.reset()
            return Result.success(Unit)
        }
        
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

    override suspend fun generateImage(request: ImageGenerationRequest): Result<ImageGenerationResult.Success> {
        return when (val result = imageGenerationManager.generate(request)) {
            is ImageGenerationResult.Success -> Result.success(result)
            is ImageGenerationResult.Failure -> Result.failure(result.throwable ?: Exception(result.message))
        }
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

    override val allDownloadsProgress: Flow<Map<String, DownloadProgress>>
        get() = downloadManager.allDownloadsProgress

    override suspend fun getLocalModels(): List<InstalledModel> = withContext(dispatcherProvider.io) {
        val filesDir = context.filesDir
        
        // 1. Scan for potential model files
        val potentialFiles = filesDir.listFiles { file ->
            (file.isFile && (file.name.endsWith(".litertlm") || file.name.endsWith(".bin") || file.name.endsWith(".zip"))) ||
                isImageGenerationDirectory(file)
        } ?: emptyArray()

        // 2. Process and validate found files locally (Avoid remote fetch for performance)
        val validatedModels = potentialFiles.mapNotNull { file ->
            if (file.name.endsWith(".tmp") || file.length() < 1024) {
                return@mapNotNull null
            }
            
            // Reclassify local files so stale registry rows from older image backends do not survive forever.
            val existing = installedModelRegistry.getInstalledModel(file.name)
            classifyModel(file, existing?.license)
        }
        
        // 3. Update the Registry
        val currentRegistry = installedModelRegistry.getInstalledModels()
        
        currentRegistry.forEach { registryModel ->
            val modelFile = File(registryModel.filePath)
            val refreshedModel = classifyModel(modelFile, registryModel.license)
            if (!modelFile.exists()) {
                Log.w(TAG, "Removing orphaned registry entry: ${registryModel.id}")
                installedModelRegistry.removeInstalledModel(registryModel.id)
            } else if (registryModel.runtime == ModelRuntime.IMAGE_GENERATOR) {
                Log.w(TAG, "Removing unsupported MediaPipe image-generator registry entry: ${registryModel.id}")
                installedModelRegistry.removeInstalledModel(registryModel.id)
            } else if (registryModel.taskType == ModelTaskType.IMAGE_GENERATION && refreshedModel == null) {
                Log.w(TAG, "Removing invalid image-generation registry entry: ${registryModel.id}")
                installedModelRegistry.removeInstalledModel(registryModel.id)
            } else if (refreshedModel != null && refreshedModel != registryModel) {
                installedModelRegistry.upsertInstalledModel(refreshedModel)
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
        if (!file.exists()) return null

        if (isOnnxDiffusionDirectory(file)) {
            return InstalledModel(
                id = file.name,
                displayName = file.nameWithoutExtension.replace("_", " ")
                    .replace("-", " ")
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() },
                filePath = file.absolutePath,
                fileName = file.name,
                source = ModelSource.LOCAL,
                format = ModelFormat.ONNX_DIFFUSION_BUNDLE,
                runtime = ModelRuntime.ONNX_DIFFUSION,
                taskType = ModelTaskType.IMAGE_GENERATION,
                capabilities = setOf(ModelCapability.IMAGE_GEN),
                installStatus = InstallStatus.INSTALLED,
                sizeBytes = file.walkTopDown().filter { it.isFile }.sumOf { it.length() },
                installedAt = file.lastModified(),
                license = license
            )
        }

        if (isQualcommImageGenerationDirectory(file)) {
            return InstalledModel(
                id = file.name,
                displayName = file.nameWithoutExtension.replace("_", " ")
                    .replace("-", " ")
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() },
                filePath = file.absolutePath,
                fileName = file.name,
                source = ModelSource.LOCAL,
                format = ModelFormat.QNN,
                runtime = ModelRuntime.QUALCOMM,
                taskType = ModelTaskType.IMAGE_GENERATION,
                capabilities = setOf(ModelCapability.IMAGE_GEN),
                installStatus = InstallStatus.INSTALLED,
                sizeBytes = file.walkTopDown().filter { it.isFile }.sumOf { it.length() },
                installedAt = file.lastModified(),
                license = license
            )
        }

        if (!file.isFile) return null

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
            val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
            if (deleted) {
                installedModelRegistry.removeInstalledModel(modelName)
            }
            return deleted
        } else {
            installedModelRegistry.removeInstalledModel(modelName)
        }
        return false
    }

    private fun isImageGenerationDirectory(file: File): Boolean {
        if (!file.isDirectory) return false
        if (file.name == "generated_images" || file.name == Constants.Inference.NEURAL_CACHE_DIR) return false
        val nameLooksLikeImageModel = file.name.contains("image", ignoreCase = true) ||
            file.name.contains("diffusion", ignoreCase = true) ||
            file.name.contains("stable", ignoreCase = true)
        return nameLooksLikeImageModel || isOnnxDiffusionDirectory(file) || isQualcommImageGenerationDirectory(file)
    }

    private fun isOnnxDiffusionDirectory(file: File): Boolean {
        if (!file.isDirectory) return false
        val requiredFiles = listOf(
            "text_encoder/model.ort",
            "tokenizer/vocab.json",
            "tokenizer/merges.txt",
            "unet/model.ort",
            "vae_decoder/model.ort"
        )
        return requiredFiles.all { relativePath -> File(file, relativePath).isFile }
    }

    private fun isQualcommImageGenerationDirectory(file: File): Boolean {
        if (!file.isDirectory) return false
        val requiredFiles = listOf(
            "metadata.json",
            "text_encoder.onnx",
            "text_encoder_qairt_context.bin",
            "unet.onnx",
            "unet_qairt_context.bin",
            "vae.onnx",
            "vae_qairt_context.bin"
        )
        return requiredFiles.all { fileName -> File(file, fileName).isFile }
    }
}
