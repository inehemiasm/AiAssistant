package com.neo.aiassistant.data.inference

import com.neo.aiassistant.domain.InferenceRequest
import com.neo.aiassistant.domain.InferenceResult
import com.neo.aiassistant.domain.LoadResult
import com.neo.aiassistant.domain.InstalledModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An implementation of [ModelEngine] that uses the LiteRT (TensorFlow Lite) runtime.
 */
@Singleton
class LiteRtEngine @Inject constructor(
    private val runtimeManager: LlmRuntimeManager,
    private val responseMapper: LlmResponseMapper,
    private val messageFactory: MultimodalMessageFactory
) : ModelEngine {

    override val initStatus: Flow<String> = runtimeManager.initStatus

    override suspend fun load(model: InstalledModel): LoadResult {
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

    override suspend fun unload() {
        runtimeManager.close()
    }

    override suspend fun clearConversation() {
        runtimeManager.clearConversation()
    }

    override fun isVisionSupported(): Boolean {
        return runtimeManager.isVisionSupported()
    }
}
