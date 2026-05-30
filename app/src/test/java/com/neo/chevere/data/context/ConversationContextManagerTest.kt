package com.neo.chevere.data.context

import com.neo.chevere.core.Constants
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationContextManagerTest {

    @Test
    fun buildPrompt_withoutHistory_returnsCurrentPromptOnly() {
        val manager = ConversationContextManager()

        val prompt = manager.buildPrompt("What should I do next?")

        assertTrue(prompt == "What should I do next?")
    }

    @Test
    fun buildPrompt_withHistory_includesMemoryRecentAndCurrentRequest() {
        val manager = ConversationContextManager()
        repeat(4) { index ->
            manager.recordExchange("question $index", null, "answer $index")
        }

        val prompt = manager.buildPrompt("final question")

        assertTrue(prompt.contains(Constants.ContextWindow.CONTEXT_INSTRUCTION))
        assertTrue(prompt.contains(Constants.ContextWindow.MEMORY_HEADER))
        assertTrue(prompt.contains(Constants.ContextWindow.RECENT_HEADER))
        assertTrue(prompt.contains(Constants.ContextWindow.CURRENT_REQUEST_HEADER))
        assertTrue(prompt.contains(Constants.ContextWindow.CURRENT_REQUEST_INSTRUCTION))
        assertTrue(prompt.contains("final question"))
    }

    @Test
    fun buildPrompt_placesCurrentRequestAfterRetainedContext() {
        val manager = ConversationContextManager()
        manager.recordExchange(
            "Can you give me a quick quiz about Kotlin?",
            null,
            "Would you like to continue the quiz, return to the roadmap, or summarize text?"
        )
        manager.recordExchange(
            "For 1 would nullable check at compile time. 2 data classes add equals.",
            null,
            "That's a great start. Would you like to do something else?"
        )

        val prompt = manager.buildPrompt("Can you grade my answers?")

        assertTrue(
            prompt.indexOf(Constants.ContextWindow.CURRENT_REQUEST_HEADER) >
                    prompt.indexOf(Constants.ContextWindow.RECENT_HEADER)
        )
        assertTrue(prompt.endsWith("Can you grade my answers?"))
    }

    @Test
    fun buildContextPrefix_marksHistoryAsBackgroundOnly() {
        val manager = ConversationContextManager()
        manager.recordExchange("old question", null, "old answer")

        val context = manager.buildContextPrefix()

        assertTrue(context.startsWith(Constants.ContextWindow.CONTEXT_INSTRUCTION))
        assertFalse(context.contains(Constants.ContextWindow.CURRENT_REQUEST_HEADER))
    }

    @Test
    fun clear_removesRetainedContext() {
        val manager = ConversationContextManager()
        manager.recordExchange("remember this", null, "stored")

        manager.clear()

        val prompt = manager.buildPrompt("fresh")
        assertFalse(prompt.contains("remember this"))
        assertTrue(prompt == "fresh")
    }

    @Test
    fun getMemoryTurnsTranscript_returnsTranscriptOfOldTurns() {
        val manager = ConversationContextManager()
        manager.recordExchange("q0", null, "a0")
        manager.recordExchange("q1", null, "a1")
        manager.recordExchange("q2", null, "a2")

        val transcript = manager.getMemoryTurnsTranscript()
        assertTrue(transcript != null)
        assertTrue(transcript!!.contains("User: q0"))
        assertTrue(transcript.contains("Assistant: a0"))
        assertFalse(transcript.contains("q2"))
    }

    @Test
    fun buildPrompt_whenPersistentMemorySet_usesPersistentMemory() {
        val manager = ConversationContextManager()
        manager.recordExchange("q0", null, "a0")
        manager.recordExchange("q1", null, "a1")
        manager.recordExchange("q2", null, "a2")

        manager.persistentMemory = "This is a custom persistent memory summary."
        val prompt = manager.buildPrompt("current request")

        assertTrue(prompt.contains("This is a custom persistent memory summary."))
        assertFalse(prompt.contains("User: q0"))
    }
}
