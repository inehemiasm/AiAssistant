package com.neo.chevere.shared.chat

import com.neo.chevere.shared.domain.*
import com.neo.chevere.shared.inference.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class GenerateChatTurnTest {
    private class Runtime : TextGenerationRuntime {
        override val state = flowOf<ModelRuntimeState>(ModelRuntimeState.Idle)
        var resets = 0
        var request: TextGenerationRequest? = null
        var events: Flow<TextGenerationEvent> = emptyFlow()
        override suspend fun load(model: ModelArtifact) = ModelLoadResult.Success
        override suspend fun resetConversation() { resets++ }
        override suspend fun unload() {}
        override fun generate(request: TextGenerationRequest): Flow<TextGenerationEvent> {
            check(resets > 0)
            this.request = request
            return events
        }
    }

    @Test fun accumulatesDeltasAndUsesAuthoritativeCompletion() = runTest {
        val runtime = Runtime().apply { events = flowOf(
            TextGenerationEvent.Delta("Hel"), TextGenerationEvent.Delta("lo"),
            TextGenerationEvent.Completed("Hello!"), TextGenerationEvent.Delta("ignored")
        ) }
        val request = TextGenerationRequest("bounded prompt", MediaReference.Uri("content://image"))
        assertEquals(listOf(ChatTurnUpdate.Partial("Hel"), ChatTurnUpdate.Partial("Hello"),
            ChatTurnUpdate.Completed("Hello!")), GenerateChatTurn(runtime)(request).toList())
        assertEquals(request, runtime.request)
        GenerateChatTurn(runtime)(request).toList()
        assertEquals(2, runtime.resets)
    }

    @Test fun failureStopsCollectionWithoutCompletingPartialOutput() = runTest {
        val failure = RuntimeFailure(RuntimeFailureCode.NO_MODEL_LOADED, "Load a model")
        val runtime = Runtime().apply { events = flowOf(TextGenerationEvent.Delta("partial"),
            TextGenerationEvent.Failed(failure), TextGenerationEvent.Completed("ignored")) }
        assertEquals(listOf(ChatTurnUpdate.Partial("partial"), ChatTurnUpdate.Failed(failure)),
            GenerateChatTurn(runtime)(TextGenerationRequest("hello")).toList())
    }

    @Test fun missingOrEmptyTerminalOutputFails() = runTest {
        for (events in listOf(emptyFlow(), flowOf(TextGenerationEvent.Delta("partial")),
            flowOf(TextGenerationEvent.Completed("")))) {
            val runtime = Runtime().apply { this.events = events }
            assertIs<ChatTurnUpdate.Failed>(GenerateChatTurn(runtime)(TextGenerationRequest("hi")).toList().last())
        }
    }

    @Test fun runtimeExceptionsBecomeFailuresAndCancellationPropagates() = runTest {
        val runtime = Runtime().apply { events = flow { throw IllegalStateException("broken") } }
        val result = GenerateChatTurn(runtime)(TextGenerationRequest("hi")).toList().single()
        assertEquals("broken", assertIs<ChatTurnUpdate.Failed>(result).failure.message)
        runtime.events = flow { throw CancellationException("stop") }
        assertFailsWith<CancellationException> { GenerateChatTurn(runtime)(TextGenerationRequest("hi")).toList() }
    }

    @Test fun collectorExceptionsPropagate() = runTest {
        val runtime = Runtime().apply { events = flowOf(TextGenerationEvent.Delta("hello")) }
        assertFailsWith<IllegalArgumentException> {
            GenerateChatTurn(runtime)(TextGenerationRequest("hi")).collect { throw IllegalArgumentException("consumer") }
        }
    }
}
