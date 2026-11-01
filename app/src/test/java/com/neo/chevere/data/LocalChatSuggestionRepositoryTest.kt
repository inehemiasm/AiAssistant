package com.neo.chevere.data

import com.google.ai.edge.litertlm.Message
import com.neo.chevere.data.inference.LlmRuntimeManager
import com.neo.chevere.data.inference.LlmResponseMapper
import com.neo.chevere.data.inference.MultimodalMessageFactory
import com.neo.chevere.domain.ChatMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

class LocalChatSuggestionRepositoryTest {
    @Test fun modelOutputIsBoundedDeduplicatedAndToolSyntaxRejected() {
        assertEquals(listOf("Show the warranty terms", "Compare the two options"), parsePredictedSuggestions(
            "1. Show the warranty terms\n- show the warranty terms\n[TOOL_CALL: delete_file]\nNONE\n${"x".repeat(100)}\nCompare the two options"))
        assertTrue(parsePredictedSuggestions("NONE").isEmpty())
    }

    @Test fun emptyConversationDoesNotRunInference() = runTest {
        val runtime = mock<LlmRuntimeManager>()
        val repository = LocalChatSuggestionRepository(runtime, mock(), mock())
        assertTrue(repository.predict(emptyList(), false).isEmpty())
        verifyNoInteractions(runtime)
    }

    @Test fun predictionsUseBoundedRecentContextAndIsolatedRuntime() = runTest {
        val runtime = mock<LlmRuntimeManager>()
        val factory = mock<MultimodalMessageFactory>()
        val mapper = mock<LlmResponseMapper>()
        val request = mock<Message>()
        val response = mock<Message>()
        whenever(factory.createTextMessage(any())).thenReturn(request)
        whenever(runtime.predictInIsolation(request)).thenReturn(Result.success(listOf(response)))
        whenever(mapper.mapToString(response, trim = false)).thenReturn("Compare warranty coverage\nShow exclusions")
        val context = (1..8).map { ChatMessage(text = "turn$it " + "x".repeat(2000), isUser = it % 2 == 0) }
        val predictions = LocalChatSuggestionRepository(runtime, factory, mapper).predict(context, false)
        assertEquals(2, predictions.size)
        val prompt = argumentCaptor<String>()
        verify(factory).createTextMessage(prompt.capture())
        assertTrue(prompt.firstValue.contains("turn8"))
        assertFalse(prompt.firstValue.contains("turn1"))
        assertTrue(prompt.firstValue.length < 5000)
        verify(runtime, never()).clearConversation()
    }
}
