package com.neo.chevere.data.inference

import com.neo.chevere.domain.ModelRuntime
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class ModelEngineFactory @Inject constructor(
    private val liteRtEngine: Provider<LiteRtEngine>
) {
    fun getEngine(runtime: ModelRuntime): ModelEngine {
        return when (runtime) {
            ModelRuntime.LITERT -> liteRtEngine.get()
            ModelRuntime.QUALCOMM -> throw UnsupportedOperationException("Qualcomm runtime not yet implemented")
            ModelRuntime.UNKNOWN -> throw IllegalArgumentException("Unknown runtime")
        }
    }
}
