package com.neo.chevere.data.inference

import com.neo.chevere.domain.InferenceRequest
import com.neo.chevere.domain.InferenceResult
import com.neo.chevere.domain.InitializationStatus
import com.neo.chevere.domain.InstalledModel
import com.neo.chevere.domain.LoadResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiteRtEngine @Inject constructor(
    private val runtimeManager: LlmRuntimeManager,
    private val responseMapper: LlmResponseMapper,
    private val messageFactory: MultimodalMessageFactory
) : ModelEngine {
    override var lastUsage: com.neo.chevere.domain.InferenceUsage? = null
        private set

    override val initStatus: Flow<InitializationStatus> = runtimeManager.initStatus

    override suspend fun load(model: InstalledModel): LoadResult {
        return try {
            val result = runtimeManager.initialize(
                modelPath = model.filePath,
                enableVision = model.capabilities.contains(com.neo.chevere.domain.ModelCapability.VISION)
            )
            if (result.isSuccess) {
                LoadResult.Success
            } else {
                LoadResult.Failure(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadResult.Failure(e.message ?: "Exception during load", e)
        }
    }

    override suspend fun generate(request: InferenceRequest): InferenceResult {
        lastUsage = null
        runtimeManager.promptBudgetError(request.prompt, request.imageUri != null)?.let { return InferenceResult.Failure(it) }
        return try {
            val message = messageFactory.createMessage(request.prompt, request.imageUri)

            val result = runtimeManager.sendMessage(message)
            if (result.isSuccess) {
                lastUsage = runtimeManager.lastUsage
                val response = result.getOrThrow()
                InferenceResult.Success(responseMapper.mapToString(response))
            } else {
                InferenceResult.Failure(result.exceptionOrNull()?.message ?: "Inference failed")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            InferenceResult.Failure(e.message ?: "Exception during generation", e)
        }
    }

    override fun generateStream(request: InferenceRequest): Flow<InferenceResult> = flow {
        lastUsage = null
        runtimeManager.promptBudgetError(request.prompt, request.imageUri != null)?.let {
            emit(InferenceResult.Failure(it))
            return@flow
        }
        try {
            val message = messageFactory.createMessage(request.prompt, request.imageUri)
            var accumulatedText = ""
            var failed = false
            runtimeManager.sendMessageAsync(message).collect { result ->
                if (result.isSuccess) {
                    val response = result.getOrThrow()
                    val chunkText = responseMapper.mapToString(response, trim = false)
                    accumulatedText += chunkText
                    emit(InferenceResult.Success(accumulatedText))
                } else {
                    failed = true
                    emit(InferenceResult.Failure(result.exceptionOrNull()?.message ?: "Inference failed"))
                }
            }
            if (!failed) lastUsage = runtimeManager.lastUsage
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(InferenceResult.Failure(e.message ?: "Exception during generation", e))
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
