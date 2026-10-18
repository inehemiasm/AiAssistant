package com.neo.chevere.data.inference

import com.neo.chevere.domain.ModelRuntime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Selects the image generation runtime for an installed model.
 *
 * Local diffusion inference lives in the standalone `:image-generation` module
 * and is currently disabled in `:app`.
 */
@Singleton
class ImageGenerationEngineFactory @Inject constructor() {
    /**
     * Returns an [ImageGenerationEngine] for [runtime], or `null` when the app
     * does not have an active image generation backend linked.
     */
    fun getEngine(runtime: ModelRuntime): ImageGenerationEngine? {
        return when (runtime) {
            ModelRuntime.ONNX_DIFFUSION,
            ModelRuntime.IMAGE_GENERATOR,
            ModelRuntime.LITERT,
            ModelRuntime.UNKNOWN -> null
        }
    }
}
