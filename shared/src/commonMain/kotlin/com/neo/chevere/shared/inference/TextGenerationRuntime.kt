package com.neo.chevere.shared.inference

import com.neo.chevere.shared.domain.MediaReference
import com.neo.chevere.shared.domain.ModelArtifact
import kotlinx.coroutines.flow.Flow

/** Shared contract implemented by each platform's local text model runtime. */
interface TextGenerationRuntime {
    val state: Flow<ModelRuntimeState>

    suspend fun load(model: ModelArtifact): ModelLoadResult

    fun generate(request: TextGenerationRequest): Flow<TextGenerationEvent>

    suspend fun resetConversation()

    suspend fun unload()
}

data class TextGenerationRequest(
    val prompt: String,
    val image: MediaReference? = null
)

sealed interface TextGenerationEvent {
    /** A newly generated fragment, not the full accumulated response. */
    data class Delta(val text: String) : TextGenerationEvent

    data class Completed(val fullText: String) : TextGenerationEvent

    data class Failed(val failure: RuntimeFailure) : TextGenerationEvent
}

sealed interface ModelRuntimeState {
    data object Idle : ModelRuntimeState

    data class Loading(val message: String) : ModelRuntimeState

    data class Ready(val modelId: String?) : ModelRuntimeState

    data class Failed(val failure: RuntimeFailure) : ModelRuntimeState
}

sealed interface ModelLoadResult {
    data object Success : ModelLoadResult

    data class Failed(val failure: RuntimeFailure) : ModelLoadResult
}

data class RuntimeFailure(
    val code: RuntimeFailureCode,
    val message: String,
    val isRecoverable: Boolean = false
)

enum class RuntimeFailureCode {
    MODEL_NOT_FOUND,
    UNSUPPORTED_MODEL,
    LOAD_FAILED,
    NO_MODEL_LOADED,
    GENERATION_FAILED,
    UNKNOWN
}
