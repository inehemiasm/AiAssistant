package com.neo.chevere.data.inference

import com.neo.chevere.domain.ModelRuntime
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class ModelEngineFactory @Inject constructor(
    private val liteRtEngine: Provider<LiteRtEngine>,
    private val miniCpmEngine: Provider<com.neo.chevere.data.inference.minicpm.MiniCpmEngine>
) {
    fun getEngine(runtime: ModelRuntime): ModelEngine {
        return when (runtime) {
            ModelRuntime.LLAMA_CPP -> miniCpmEngine.get()
            ModelRuntime.LITERT -> liteRtEngine.get()
            ModelRuntime.IMAGE_GENERATOR -> throw UnsupportedOperationException("Image generation uses ImageGenerationManager")
            ModelRuntime.ONNX_DIFFUSION -> throw UnsupportedOperationException("Image generation uses ImageGenerationManager")
            ModelRuntime.UNKNOWN -> throw IllegalArgumentException("Unknown runtime")
        }
    }
}
