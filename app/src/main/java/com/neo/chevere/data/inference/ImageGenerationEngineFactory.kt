package com.neo.chevere.data.inference

import com.neo.chevere.domain.ModelRuntime
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Selects the image generation runtime for an installed model.
 */
@Singleton
class ImageGenerationEngineFactory @Inject constructor(
    private val onnxLocalDiffusionEngine: Provider<OnnxLocalDiffusionEngine>
) {
    /**
     * Returns an [ImageGenerationEngine] for [runtime], or `null` when the app
     * does not have a compatible backend implementation.
     */
    fun getEngine(runtime: ModelRuntime): ImageGenerationEngine? {
        return when (runtime) {
            ModelRuntime.ONNX_DIFFUSION -> onnxLocalDiffusionEngine.get()
            ModelRuntime.IMAGE_GENERATOR,
            ModelRuntime.LITERT,
            ModelRuntime.UNKNOWN -> null
        }
    }
}
