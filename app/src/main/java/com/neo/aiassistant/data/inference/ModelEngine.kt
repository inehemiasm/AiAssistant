package com.neo.aiassistant.data.inference

import com.neo.aiassistant.domain.InferenceRequest
import com.neo.aiassistant.domain.InferenceResult
import com.neo.aiassistant.domain.LoadResult
import com.neo.aiassistant.domain.LocalModel
import kotlinx.coroutines.flow.Flow

/**
 * Interface defining the requirements for an AI inference engine.
 *
 * Different runtimes (e.g., LiteRT, QNN) must implement this interface to be
 * compatible with the [InferenceManager].
 */
interface ModelEngine {
    /**
     * A [Flow] of strings representing the initialization status of the engine.
     */
    val initStatus: Flow<String>

    /**
     * Loads the specified model into the engine.
     *
     * @param model The model to be loaded.
     * @return [LoadResult.Success] if loaded correctly, or [LoadResult.Failure] otherwise.
     */
    suspend fun load(model: LocalModel): LoadResult

    /**
     * Executes an inference request and returns the result.
     *
     * @param request The request containing input data (text/image).
     * @return [InferenceResult.Success] with the output, or [InferenceResult.Failure] on error.
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
