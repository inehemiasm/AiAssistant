package com.neo.chevere.data.inference

import com.neo.chevere.core.Constants
import com.neo.chevere.domain.ImageGenerationRequest
import com.neo.chevere.domain.ImageGenerationResult
import com.neo.chevere.domain.InstalledModel
import com.neo.chevere.domain.LoadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Image-generation backend for Qualcomm AI Hub / QAIRT Stable Diffusion bundles.
 *
 * The current implementation validates and selects QAIRT model bundles, but the
 * native QAIRT/QNN invocation layer still needs to be linked before generation
 * can execute. This keeps runtime routing honest: Qualcomm bundles no longer go
 * through the failed MediaPipe backend, and the app can surface an actionable
 * runtime-missing error instead of a misleading model-format error.
 */
@Singleton
class QualcommImageGenerationEngine @Inject constructor() : ImageGenerationEngine {
    private var activeModel: InstalledModel? = null

    override suspend fun load(model: InstalledModel): LoadResult = withContext(Dispatchers.IO) {
        val modelDirectory = File(model.filePath)
        if (!modelDirectory.isDirectory) {
            return@withContext LoadResult.Failure("Qualcomm image generation model must be an extracted QAIRT directory.")
        }

        val missingFiles = Constants.ImageGeneration.QUALCOMM_REQUIRED_FILES.filterNot { fileName ->
            File(modelDirectory, fileName).isFile
        }
        if (missingFiles.isNotEmpty()) {
            return@withContext LoadResult.Failure(
                "Qualcomm image generation model is missing required files: ${missingFiles.joinToString(", ")}"
            )
        }

        activeModel = model
        LoadResult.Success
    }

    override suspend fun generate(request: ImageGenerationRequest): ImageGenerationResult {
        val model = activeModel
            ?: return ImageGenerationResult.Failure("Qualcomm image generation model is not loaded.")

        return ImageGenerationResult.Failure(
            "Qualcomm QAIRT image generation is selected for '${model.displayName}', but the native QAIRT/QNN runtime bridge is not bundled yet. Add the Qualcomm AI Runtime Android SDK and implement text_encoder -> unet denoising -> vae decode execution for this model bundle."
        )
    }

    override suspend fun unload() {
        activeModel = null
    }
}
