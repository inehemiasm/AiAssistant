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
import com.neo.chevere.domain.InferenceRequest
import com.neo.chevere.domain.InferenceResult
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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
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
            Exception(
                "Unsupported or invalid model format. Please use a ${Constants.ModelFiles.LITERTLM_EXTENSION}, " +
                    "${Constants.ModelFiles.BIN_EXTENSION}, or extracted image model directory."
            )
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

        if (imageUri != null) {
            return when (val result = inferenceManager.generate(InferenceRequest(prompt, imageUri))) {
                is InferenceResult.Success -> Result.success(result.text)
                is InferenceResult.Failure -> Result.failure(
                    result.throwable ?: Exception(result.message)
                )
            }
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

    override fun downloadModel(model: ModelEntry): Flow<DownloadProgress> {
        val downloadFileName = model.effectiveFileName
        val installedId = downloadFileName.installedModelId()
        val targetFile = File(context.filesDir, installedId)
        val placeholder = model.toPendingInstalledModel(
            id = installedId,
            filePath = targetFile.absolutePath
        )
        return flow {
            installedModelRegistry.upsertInstalledModel(placeholder)
            emitAll(
                downloadManager.downloadModel(
                    url = model.url,
                    modelName = downloadFileName,
                    modelId = installedId,
                    sha256 = model.sha256
                )
            )
        }
    }

    override suspend fun cancelModelDownload(modelName: String) {
        val matchingModel = installedModelRegistry.getInstalledModels().firstOrNull {
            it.id == modelName || it.fileName == modelName
        }
        downloadManager.cancelDownload(matchingModel?.fileName ?: modelName)
    }

    override val allDownloadsProgress: Flow<Map<String, DownloadProgress>>
        get() = downloadManager.allDownloadsProgress

    override suspend fun getLocalModels(): List<InstalledModel> = withContext(dispatcherProvider.io) {
        val filesDir = context.filesDir
        
        // 1. Scan for potential model files
        val potentialFiles = filesDir.listFiles { file ->
            (file.isFile && (
                file.name.endsWith(Constants.ModelFiles.LITERTLM_EXTENSION) ||
                    file.name.endsWith(Constants.ModelFiles.BIN_EXTENSION) ||
                    file.name.endsWith(Constants.ModelFiles.ZIP_EXTENSION)
                )) ||
                isImageGenerationDirectory(file)
        } ?: emptyArray()

        // 2. Process and validate found files locally (Avoid remote fetch for performance)
        val validatedModels = potentialFiles.mapNotNull { file ->
            if (file.name.endsWith(Constants.ModelFiles.TEMP_EXTENSION) ||
                (file.isFile && file.length() < Constants.ModelFiles.MIN_VALID_FILE_SIZE_BYTES)
            ) {
                return@mapNotNull null
            }
            
            // Reclassify local files so stale registry rows from older image backends do not survive forever.
            val existing = installedModelRegistry.getInstalledModel(file.name)
            classifyModel(file, existing)
        }
        
        // 3. Update the Registry
        val currentRegistry = installedModelRegistry.getInstalledModels()
        
        currentRegistry.forEach { registryModel ->
            val modelFile = File(registryModel.filePath)
            val refreshedModel = classifyModel(modelFile, registryModel)
            if (!modelFile.exists()) {
                if (registryModel.installStatus.shouldRemainWithoutFile()) {
                    return@forEach
                }
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

    private fun classifyModel(file: File, existing: InstalledModel? = null): InstalledModel? {
        if (!file.exists()) return null

        if (isOnnxDiffusionDirectory(file)) {
            return InstalledModel(
                id = file.name,
                displayName = file.nameWithoutExtension.replace("_", " ")
                    .replace("-", " ")
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() },
                filePath = file.absolutePath,
                fileName = file.name,
                source = existing?.source ?: ModelSource.LOCAL,
                format = ModelFormat.ONNX_DIFFUSION_BUNDLE,
                runtime = ModelRuntime.ONNX_DIFFUSION,
                taskType = ModelTaskType.IMAGE_GENERATION,
                capabilities = setOf(ModelCapability.IMAGE_GEN),
                installStatus = InstallStatus.INSTALLED,
                sizeBytes = file.walkTopDown().filter { it.isFile }.sumOf { it.length() },
                checksum = existing?.checksum,
                installedAt = file.lastModified(),
                license = existing?.license
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
                source = existing?.source ?: ModelSource.LOCAL,
                format = ModelFormat.QNN,
                runtime = ModelRuntime.QUALCOMM,
                taskType = ModelTaskType.IMAGE_GENERATION,
                capabilities = setOf(ModelCapability.IMAGE_GEN),
                installStatus = InstallStatus.INSTALLED,
                sizeBytes = file.walkTopDown().filter { it.isFile }.sumOf { it.length() },
                checksum = existing?.checksum,
                installedAt = file.lastModified(),
                license = existing?.license
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
            file.name.contains("multimodal", ignoreCase = true) ||
            file.name.contains("gemma-4", ignoreCase = true)
        ) {
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
            source = existing?.source ?: ModelSource.LOCAL,
            format = format,
            runtime = runtime,
            taskType = taskType,
            capabilities = capabilities,
            installStatus = InstallStatus.INSTALLED,
            sizeBytes = file.length(),
            checksum = existing?.checksum,
            installedAt = file.lastModified(),
            license = existing?.license
        )
    }

    private fun String.installedModelId(): String = removeSuffix(Constants.ModelFiles.ZIP_EXTENSION)

    private fun ModelEntry.toPendingInstalledModel(id: String, filePath: String): InstalledModel {
        val (format, runtime, taskType, capabilities) = when {
            effectiveFileName.endsWith(Constants.ModelFiles.ZIP_EXTENSION, ignoreCase = true) &&
                runtimeType.contains("ONNX", ignoreCase = true) -> Tuple4(
                    ModelFormat.ONNX_DIFFUSION_BUNDLE,
                    ModelRuntime.ONNX_DIFFUSION,
                    ModelTaskType.IMAGE_GENERATION,
                    setOf(ModelCapability.IMAGE_GEN)
                )
            effectiveFileName.endsWith(Constants.ModelFiles.ZIP_EXTENSION, ignoreCase = true) &&
                runtimeType.contains("Qualcomm", ignoreCase = true) -> Tuple4(
                    ModelFormat.QNN,
                    ModelRuntime.QUALCOMM,
                    ModelTaskType.IMAGE_GENERATION,
                    setOf(ModelCapability.IMAGE_GEN)
                )
            effectiveFileName.endsWith(Constants.ModelFiles.LITERTLM_EXTENSION, ignoreCase = true) -> Tuple4(
                ModelFormat.LITERTLM,
                ModelRuntime.LITERT,
                if (supportsVision) ModelTaskType.VISION_CHAT else ModelTaskType.CHAT,
                if (supportsVision) setOf(ModelCapability.TEXT, ModelCapability.VISION) else setOf(ModelCapability.TEXT)
            )
            else -> Tuple4(
                ModelFormat.BIN,
                ModelRuntime.LITERT,
                if (supportsVision) ModelTaskType.VISION_CHAT else ModelTaskType.CHAT,
                if (supportsVision) setOf(ModelCapability.TEXT, ModelCapability.VISION) else setOf(ModelCapability.TEXT)
            )
        }
        return InstalledModel(
            id = id,
            displayName = name,
            filePath = filePath,
            fileName = id,
            source = provider.toModelSource(),
            format = format,
            runtime = runtime,
            taskType = taskType,
            capabilities = capabilities,
            installStatus = InstallStatus.DOWNLOADING,
            sizeBytes = sizeBytes.takeIf { it > 0 },
            checksum = sha256,
            installedAt = null,
            license = license
        )
    }

    private fun String.toModelSource(): ModelSource {
        return when {
            equals("Hugging Face", ignoreCase = true) || equals("HF Hub", ignoreCase = true) -> ModelSource.HUGGING_FACE
            equals("Firebase", ignoreCase = true) || equals("Firestore", ignoreCase = true) -> ModelSource.FIREBASE
            else -> ModelSource.UNKNOWN
        }
    }

    private fun InstallStatus.shouldRemainWithoutFile(): Boolean {
        return this == InstallStatus.DOWNLOADING ||
            this == InstallStatus.VERIFYING ||
            this == InstallStatus.FAILED ||
            this == InstallStatus.CORRUPTED
    }

    private data class Tuple4<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )

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
        if (file.name == Constants.ImageGeneration.GENERATED_IMAGES_DIRECTORY ||
            file.name == Constants.Inference.NEURAL_CACHE_DIR
        ) return false
        val nameLooksLikeImageModel = file.name.contains("image", ignoreCase = true) ||
            file.name.contains("diffusion", ignoreCase = true) ||
            file.name.contains("stable", ignoreCase = true)
        return nameLooksLikeImageModel || isOnnxDiffusionDirectory(file) || isQualcommImageGenerationDirectory(file)
    }

    private fun isOnnxDiffusionDirectory(file: File): Boolean {
        if (!file.isDirectory) return false
        return Constants.ImageGeneration.ONNX_REQUIRED_FILES.all { relativePath -> File(file, relativePath).isFile }
    }

    private fun isQualcommImageGenerationDirectory(file: File): Boolean {
        if (!file.isDirectory) return false
        return Constants.ImageGeneration.QUALCOMM_REQUIRED_FILES.all { fileName -> File(file, fileName).isFile }
    }
}
