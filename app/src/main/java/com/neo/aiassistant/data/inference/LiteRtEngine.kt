package com.neo.aiassistant.data.inference

import com.neo.aiassistant.domain.InferenceRequest
import com.neo.aiassistant.domain.InferenceResult
import com.neo.aiassistant.domain.LoadResult
import com.neo.aiassistant.domain.LocalModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An implementation of [ModelEngine] that uses the LiteRT (TensorFlow Lite) runtime.
 *
 * This engine handles model loading and inference using the [LlmRuntimeManager]
 * and supports multimodal inputs via [MultimodalMessageFactory].
 *
 * @property runtimeManager The low-level manager for the LiteRT runtime.
 * @property responseMapper Mapper to convert raw LLM responses into strings.
 * @property messageFactory Factory to create multimodal messages from prompts and images.
 */
@Singleton
class LiteRtEngine @Inject constructor(
    private val runtimeManager: LlmRuntimeManager,
    private val responseMapper: LlmResponseMapper,
    private val messageFactory: MultimodalMessageFactory
) : ModelEngine {

    /**
     * Returns the initialization status flow from the [runtimeManager].
     */
    override val initStatus: Flow<String> = runtimeManager.initStatus

    /**
     * Initializes the LiteRT runtime with the model at [model].filePath.
     *
     * @param model The model to load.
     * @return [LoadResult.Success] on success, or [LoadResult.Failure] with an error message.
     */
    override suspend fun load(model: LocalModel): LoadResult {
        return try {
            val result = runtimeManager.initialize(model.filePath)
            if (result.isSuccess) {
                LoadResult.Success
            } else {
                LoadResult.Failure(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        } catch (e: Exception) {
            LoadResult.Failure(e.message ?: "Exception during load", e)
        }
    }

    /**
     * Generates a response for the given [request] using the LiteRT engine.
     *
     * @param request The inference request.
     * @return [InferenceResult.Success] with the text or [InferenceResult.Failure] on error.
     */
    override suspend fun generate(request: InferenceRequest): InferenceResult {
        return try {
            val message = messageFactory.createMessage(request.prompt, request.imageUri)

            val result = runtimeManager.sendMessage(message)
            if (result.isSuccess) {
                val response = result.getOrThrow()
                InferenceResult.Success(responseMapper.mapToString(response))
            } else {
                InferenceResult.Failure(result.exceptionOrNull()?.message ?: "Inference failed")
            }
        } catch (e: Exception) {
            InferenceResult.Failure(e.message ?: "Exception during generation", e)
        }
    }

    /**
     * Closes the [runtimeManager] and releases resources.
     */
    override suspend fun unload() {
        runtimeManager.close()
    }

    /**
     * Clears the conversation history in the [runtimeManager].
     */
    override suspend fun clearConversation() {
        runtimeManager.clearConversation()
    }

    /**
     * Checks if vision tasks are supported by the [runtimeManager].
     */
    override fun isVisionSupported(): Boolean {
        return runtimeManager.isVisionSupported()
    }
}
