package com.neo.chevere.data.inference

import com.neo.chevere.domain.InferenceRequest
import com.neo.chevere.domain.InferenceResult
import com.neo.chevere.domain.InitializationStatus
import com.neo.chevere.domain.InstalledModel
import com.neo.chevere.domain.LoadResult
import kotlinx.coroutines.flow.Flow

/**
 * Interface defining the requirements for an AI inference engine.
 */
interface ModelEngine {
    /**
     * A [Flow] of the initialization status of the engine.
     */
    val initStatus: Flow<InitializationStatus>

    /**
     * Loads the specified model into the engine.
     */
    suspend fun load(model: InstalledModel): LoadResult

    /**
     * Executes an inference request and returns the result.
     */
    suspend fun generate(request: InferenceRequest): InferenceResult

    /**
     * Unloads the current model and releases engine resources.
     */
    suspend fun unload()

    /**
     * Clears the internal conversation history/context of the engine.
     */
    suspend fun clearConversation()

    /**
     * Returns `true` if this engine implementation supports vision tasks.
     */
    fun isVisionSupported(): Boolean
}
