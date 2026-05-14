package com.neo.chevere.data.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRequestRouterTest {
    private val router = ChatRequestRouter()

    @Test
    fun capabilityOverview_returnsStaticResponseAndDoesNotUseAgent() {
        val prompt = "What can you do?"

        assertNotNull(router.capabilityResponseFor(prompt))
        assertFalse(router.shouldUseAgent(prompt))
    }

    @Test
    fun imageCapabilityQuestion_doesNotUseAgent() {
        val prompt = "Can you generate images?"

        assertNotNull(router.capabilityResponseFor(prompt))
        assertFalse(router.shouldUseAgent(prompt))
    }

    @Test
    fun concreteImageRequest_usesAgent() {
        val prompt = "Generate an image of a neon robot"

        assertNull(router.capabilityResponseFor(prompt))
        assertTrue(router.shouldUseAgent(prompt))
    }

    @Test
    fun directChatPrompt_includesCapabilityContext() {
        val prompt = router.buildDirectChatPrompt("CURRENT USER REQUEST:\nhello")

        assertTrue(prompt.contains("You are Chevere AI"))
        assertTrue(prompt.endsWith("CURRENT USER REQUEST:\nhello"))
    }
}
