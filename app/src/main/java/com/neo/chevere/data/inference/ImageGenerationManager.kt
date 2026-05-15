package com.neo.chevere.data.inference

import com.neo.chevere.data.PreferenceManager
import com.neo.chevere.domain.ImageGenerationRequest
import com.neo.chevere.domain.ImageGenerationResult
import com.neo.chevere.domain.InstallStatus
import com.neo.chevere.domain.InstalledModel
import com.neo.chevere.domain.InstalledModelRegistry
import com.neo.chevere.domain.LoadResult
import com.neo.chevere.domain.ModelCapability
import com.neo.chevere.domain.ModelTaskType
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates image generation model selection, loading, and execution.
 */
@Singleton
class ImageGenerationManager @Inject constructor(
    private val engineFactory: ImageGenerationEngineFactory,
    private val installedModelRegistry: InstalledModelRegistry,
    private val preferenceManager: PreferenceManager
) {
    private val mutex = Mutex()
    private var loadedModelId: String? = null
    private var currentEngine: ImageGenerationEngine? = null

    /**
     * Returns true when at least one healthy image generation model is installed.
     */
    suspend fun isImageGenerationAvailable(): Boolean {
        return findImageGenerationModel() != null
    }

    /**
     * Generates an image using the active image generation model when available.
     */
    suspend fun generate(request: ImageGenerationRequest): ImageGenerationResult = mutex.withLock {
        val models = findImageGenerationModels()
        if (models.isEmpty()) {
            return ImageGenerationResult.Failure(
                "No compatible image generation model is installed. Download an ONNX Diffusion model from the Marketplace first."
            )
        }

        val loadErrors = mutableListOf<String>()
        models.forEach { model ->
            val engine = engineFactory.getEngine(model.runtime) ?: return@forEach

            if (loadedModelId != model.id) {
                currentEngine?.unload()
                currentEngine = null
                loadedModelId = null

                when (val loadResult = engine.load(model)) {
                    LoadResult.Success -> {
                        loadedModelId = model.id
                        currentEngine = engine
                    }

                    is LoadResult.Failure -> {
                        loadErrors += "${model.displayName}: ${loadResult.message}"
                        return@forEach
                    }
                }
            }

            return engine.generate(request)
        }

        val details = loadErrors.joinToString(separator = "\n")
        return ImageGenerationResult.Failure(
            "No compatible image generation model could be loaded. Download an ONNX Diffusion model from the Marketplace first." +
                    details.takeIf { it.isNotBlank() }?.let { "\n\nSkipped models:\n$it" }.orEmpty()
        )
    }

    /**
     * Releases the currently loaded image generation backend.
     */
    suspend fun unload() = mutex.withLock {
        currentEngine?.unload()
        currentEngine = null
        loadedModelId = null
    }

    private suspend fun findImageGenerationModel(): InstalledModel? {
        return findImageGenerationModels().firstOrNull()
    }

    private suspend fun findImageGenerationModels(): List<InstalledModel> {
        val models = installedModelRegistry.getInstalledModels()
            .filter { it.installStatus == InstallStatus.INSTALLED }
            .filter { engineFactory.getEngine(it.runtime) != null }
            .filter { model ->
                model.taskType == ModelTaskType.IMAGE_GENERATION ||
                        model.capabilities.contains(ModelCapability.IMAGE_GEN)
            }

        if (models.isEmpty()) return emptyList()

        val activeModelId = preferenceManager.selectedModelPreference.firstOrNull()
        val activeModel = models.find { it.id == activeModelId || it.fileName == activeModelId }
        return buildList {
            if (activeModel != null) add(activeModel)
            addAll(models.filter { it.id != activeModel?.id })
        }
    }
}
