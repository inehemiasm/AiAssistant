package com.neo.chevere.data.inference

import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NativeStreamLifecycleTest {
    @Test fun cancellationAndChunkCapWaitForNativeTerminalCallbackBeforeClose() = runTest {
        for (truncate in listOf(false, true)) {
            val native = mock<Conversation>()
            lateinit var callback: MessageCallback
            doAnswer { invocation ->
                callback = invocation.getArgument(1)
                null
            }.whenever(native).sendMessageAsync(
                any<Message>(), any<MessageCallback>(), any(), anyOrNull(),
                anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(),
            )
            val wrapper = RealConversationWrapper(native)
            val job = launch {
                val stream = wrapper.sendMessageAsync(Message.user("Predict"))
                if (truncate) stream.take(1).collect() else stream.collect()
            }
            runCurrent()
            if (truncate) callback.onMessage(Message.model("Draft")) else job.cancel()
            runCurrent()
            verify(native).cancelProcess()
            assertFalse("Native cleanup must remain pending until its terminal callback", job.isCompleted)
            verify(native, never()).close()
            callback.onError(CancellationException("Stopped"))
            runCurrent()
            job.join()
            wrapper.close()
            verify(native).close()
        }
    }

    @Test fun completedStreamDoesNotCancelNativeGeneration() = runTest {
        val native = mock<Conversation>()
        doAnswer { invocation ->
            val callback = invocation.getArgument<MessageCallback>(1)
            callback.onMessage(Message.model("Answer"))
            callback.onDone()
            null
        }.whenever(native).sendMessageAsync(
            any<Message>(), any<MessageCallback>(), any(), anyOrNull(),
            anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(),
        )
        RealConversationWrapper(native).sendMessageAsync(Message.user("Hi")).collect()
        verify(native, never()).cancelProcess()
    }
}
