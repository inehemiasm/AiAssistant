package com.neo.chevere.data.inference

import com.google.ai.edge.litertlm.Message
import com.neo.chevere.core.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

class PredictionSessionTest {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun unloadingWaitsForNativeStreamCleanup() = runTest {
        val engine = mock<LlmEngineWrapper>()
        val dispatchers = mock<DispatcherProvider>()
        whenever(dispatchers.default).thenReturn(coroutineContext[kotlin.coroutines.ContinuationInterceptor] as kotlinx.coroutines.CoroutineDispatcher)
        val conversation = mock<ConversationWrapper>()
        val cleanup = kotlinx.coroutines.CompletableDeferred<Unit>()
        val message = Message.user("Hi")
        whenever(conversation.sendMessageAsync(message)).thenReturn(flow {
            try { kotlinx.coroutines.awaitCancellation() }
            finally { kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { cleanup.await() } }
        })
        val runtime = LlmRuntimeManager(mock(), mock(), engine, dispatchers)
        LlmRuntimeManager::class.java.getDeclaredField("activeConversation").apply { isAccessible = true }.set(runtime, conversation)
        val generation = launch {
            runtime.sendMessageAsync(message).collect {}
        }
        runCurrent()
        generation.cancel()
        val teardown = launch { runtime.close() }
        runCurrent()
        verify(engine, never()).close()
        assertFalse(teardown.isCompleted)
        cleanup.complete(Unit)
        runCurrent()
        generation.join()
        teardown.join()
        verify(conversation).close()
        verify(engine).close()
    }
    @Test fun predictionClosesPreviousSessionAndRestoresChatEvenOnCancellation() = runTest {
        for (cancel in listOf(false, true)) {
            val previous = mock<ConversationWrapper>()
            val prediction = mock<ConversationWrapper>()
            val restored = mock<ConversationWrapper>()
            val engine = mock<LlmEngineWrapper>()
            val dispatchers = mock<DispatcherProvider>()
            whenever(dispatchers.default).thenReturn(coroutineContext[kotlin.coroutines.ContinuationInterceptor] as kotlinx.coroutines.CoroutineDispatcher)
            whenever(engine.createConversation()).thenReturn(prediction, restored)
            val message = mock<Message>()
            whenever(prediction.sendMessageAsync(message)).thenReturn(if (cancel) flow { throw CancellationException("stop") } else flowOf(message))
            val runtime = LlmRuntimeManager(mock(), mock(), engine, dispatchers)
            LlmRuntimeManager::class.java.getDeclaredField("isInitialized").apply { isAccessible = true }.set(runtime, true)
            LlmRuntimeManager::class.java.getDeclaredField("activeConversation").apply { isAccessible = true }.set(runtime, previous)
            try {
                assertEquals(listOf(message), runtime.predictInIsolation(message).getOrThrow())
                assertFalse(cancel)
            } catch (_: CancellationException) { assertTrue(cancel) }
            inOrder(previous, engine, prediction).apply {
                verify(previous).close()
                verify(engine).createConversation()
                verify(prediction).sendMessageAsync(message)
                verify(prediction).close()
                verify(engine).createConversation()
            }
            assertSame(restored, LlmRuntimeManager::class.java.getDeclaredField("activeConversation").apply { isAccessible = true }.get(runtime))
        }
    }
}
