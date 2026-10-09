package com.neo.chevere.shared.chat

import com.neo.chevere.shared.inference.RuntimeFailure
import com.neo.chevere.shared.inference.RuntimeFailureCode
import com.neo.chevere.shared.inference.TextGenerationEvent
import com.neo.chevere.shared.inference.TextGenerationRequest
import com.neo.chevere.shared.inference.TextGenerationRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.takeWhile

/** Runs one bounded-context turn using the platform runtime; context is supplied by the caller. */
class GenerateChatTurn(private val runtime: TextGenerationRuntime) {
    /** Resets runtime history, accumulates deltas, and requires an explicit terminal result. */
    operator fun invoke(request: TextGenerationRequest): Flow<ChatTurnUpdate> = flow {
        var terminal = false
        val text = StringBuilder()
        runtime.resetConversation()
        runtime.generate(request).takeWhile { event ->
            when (event) {
                is TextGenerationEvent.Delta -> {
                    text.append(event.text)
                    emit(ChatTurnUpdate.Partial(text.toString()))
                }
                is TextGenerationEvent.Completed -> {
                    terminal = true
                    if (event.fullText.isEmpty()) {
                        emit(ChatTurnUpdate.Failed(RuntimeFailure(
                            RuntimeFailureCode.GENERATION_FAILED, "No output generated"
                        )))
                    } else emit(ChatTurnUpdate.Completed(event.fullText))
                }
                is TextGenerationEvent.Failed -> {
                    terminal = true
                    emit(ChatTurnUpdate.Failed(event.failure))
                }
            }
            !terminal
        }.collect {}
        if (!terminal) emit(ChatTurnUpdate.Failed(RuntimeFailure(
            RuntimeFailureCode.GENERATION_FAILED, "Generation ended without a result"
        )))
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(ChatTurnUpdate.Failed(RuntimeFailure(
            RuntimeFailureCode.GENERATION_FAILED, error.message ?: "Generation failed"
        )))
    }
}

/** Renderer-independent progress and terminal state for a single chat turn. */
sealed interface ChatTurnUpdate {
    data class Partial(val text: String) : ChatTurnUpdate
    data class Completed(val text: String) : ChatTurnUpdate
    data class Failed(val failure: RuntimeFailure) : ChatTurnUpdate
}
