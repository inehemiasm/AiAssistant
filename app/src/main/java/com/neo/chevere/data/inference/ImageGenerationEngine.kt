package com.neo.chevere.data.inference

import com.neo.chevere.domain.ImageGenerationRequest
import com.neo.chevere.domain.ImageGenerationResult
import com.neo.chevere.domain.InstalledModel
import com.neo.chevere.domain.LoadResult

/**
 * Runtime contract for text-to-image generation backends.
 *
 * Implementations can wrap Qualcomm/QNN diffusion pipelines, a LiteRT
 * diffusion bundle, or a remote fallback while keeping the rest of the app
 * independent from backend details.
 */
interface ImageGenerationEngine {
    /**
     * Loads the installed image generation model into the backend.
     */
    suspend fun load(model: InstalledModel): LoadResult

    /**
     * Generates an image for [request] and returns a URI to the saved result.
     */
    suspend fun generate(request: ImageGenerationRequest): ImageGenerationResult

    /**
     * Releases runtime resources associated with the active image model.
     */
    suspend fun unload()
}
