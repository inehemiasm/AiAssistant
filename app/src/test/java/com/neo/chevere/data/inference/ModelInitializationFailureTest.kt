package com.neo.chevere.data.inference

import android.content.Context
import com.google.ai.edge.litertlm.Message
import com.neo.chevere.core.DispatcherProvider
import com.neo.chevere.domain.InitializationStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.*
import kotlin.coroutines.ContinuationInterceptor

class ModelInitializationFailureTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun chatSessionCreationFailureReturnsActionableFailureAndClosesEngine() = runTest {
        val context = mock<Context>()
        whenever(context.cacheDir).thenReturn(temporaryFolder.root)
        val dispatchers = mock<DispatcherProvider>()
        whenever(dispatchers.default).thenReturn(coroutineContext[ContinuationInterceptor] as CoroutineDispatcher)
        val factory = mock<MultimodalMessageFactory>()
        val message = Message.user("warmup")
        whenever(factory.createTextMessage("warmup")).thenReturn(message)
        val warmup = mock<ConversationWrapper>()
        whenever(warmup.sendMessage(message)).thenReturn(Message.model("Ready"))
        val engine = mock<LlmEngineWrapper>()
        whenever(engine.createConversation()).thenReturn(warmup).thenThrow(IllegalStateException("session failed"))
        val runtime = LlmRuntimeManager(context, factory, engine, dispatchers)

        val result = runtime.initialize("/models/gemma-4-E2B-it-web.litertlm", true)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("compatible GPU"))
        assertTrue(runtime.initStatus.value is InitializationStatus.Failure)
        verify(warmup).close()
        verify(engine, times(2)).close()
        assertFalse(runtime.isVisionSupported())
    }
}
