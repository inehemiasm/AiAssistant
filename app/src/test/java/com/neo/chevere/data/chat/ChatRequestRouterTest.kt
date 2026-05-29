package com.neo.chevere.data.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRequestRouterTest {
    private val router = ChatRequestRouter()

    @Test
    fun capabilityOverview_returnsStaticResponseAndDoesNotUseAgent() {
        val prompt = "What can you do?"

        val response = router.capabilityResponseFor(prompt)

        assertNotNull(response)
        assertTrue(response?.contains("live device sensor readings") == true)
        assertTrue(response?.contains("stud finder") == true)
        assertFalse(router.shouldUseAgent(prompt))
    }

    @Test
    fun imageCapabilityQuestion_doesNotUseAgent() {
        val prompt = "Can you generate images?"

        assertNotNull(router.capabilityResponseFor(prompt))
        assertFalse(router.shouldUseAgent(prompt))
    }

    @Test
    fun sensorCapabilityQuestion_doesNotUseAgent() {
        val prompt = "Can you detect metal?"

        val response = router.capabilityResponseFor(prompt)

        assertNotNull(response)
        assertTrue(response?.contains("/stud") == true)
        assertTrue(response?.contains("ambient light") == true)
        assertFalse(router.shouldUseAgent(prompt))
    }

    @Test
    fun concreteImageRequest_usesAgent() {
        val prompt = "Generate an image of a neon robot"

        assertNull(router.capabilityResponseFor(prompt))
        assertTrue(router.shouldUseAgent(prompt))
    }

    @Test
    fun visualImageRequestWithoutImageNoun_usesAgent() {
        val prompt = "create a wolf walking next to a man under the moon in cyberpunk city environment"

        assertNull(router.capabilityResponseFor(prompt))
        assertTrue(router.shouldUseAgent(prompt))
    }

    @Test
    fun politeImageRequestWithCanYouPrefix_usesAgent() {
        val prompt = "Can you generate a white wolf walking alongside a man in the city"

        assertNull(router.capabilityResponseFor(prompt))
        assertTrue(router.shouldUseAgent(prompt))
    }

    @Test
    fun directChatPrompt_includesCapabilityContext() {
        val prompt = router.buildDirectChatPrompt("CURRENT USER REQUEST:\nhello")

        assertTrue(prompt.contains("You are Chevere AI"))
        assertTrue(prompt.endsWith("CURRENT USER REQUEST:\nhello"))
    }

    @Test
    fun visionChatPrompt_instructsModelToAnalyzeAttachment() {
        val prompt =
            router.buildVisionChatPrompt("CURRENT USER REQUEST:\nWhat can you tell me about this image")

        assertTrue(prompt.contains("attached image"))
        assertTrue(prompt.contains("Do not generate"))
        assertTrue(prompt.endsWith("What can you tell me about this image"))
    }

    @Test
    fun taskRegistryRequests_useAgent() {
        val prompts = listOf(
            "add a task to book a hotel",
            "create task walk the dog",
            "show my todo list",
            "list my tasks",
            "complete task 3",
            "remind me to buy groceries",
            "remember to call mom"
        )
        for (prompt in prompts) {
            assertTrue("Should route '$prompt' to agent", router.shouldUseAgent(prompt))
        }
    }

    @Test
    fun nonTaskRequests_doNotUseAgentForTasks() {
        val prompts = listOf(
            "what is task management?",
            "how do I code a task scheduler in Kotlin",
            "hello there"
        )
        for (prompt in prompts) {
            assertFalse("Should not route '$prompt' to agent", router.shouldUseAgent(prompt))
        }
    }

    @Test
    fun sensorRequests_useAgent() {
        val prompts = listOf(
            "how hot is my room",
            "Can you tell me how hot is my room?",
            "is it hot in my room?",
            "what is the ambient temperature?",
            "read my device sensors",
            "battery status",
            "tell me the CPU thermal status",
            "how bright is the room",
            "check the barometer pressure",
            "hows the light in my room",
            "how is the light in my room",
            "light level in here"
        )
        for (prompt in prompts) {
            assertTrue("Should route '$prompt' to agent", router.shouldUseAgent(prompt))
        }
    }

    @Test
    fun sensorRequests_multilingual_useAgent() {
        val multilingualPrompts = mapOf(
            "¿qué temperatura hace en mi cuarto?" to RoutingCategory.SENSORS, // Spanish
            "temperatura de la habitación" to RoutingCategory.SENSORS, // Spanish
            "temperatura do quarto" to RoutingCategory.SENSORS, // Portuguese
            "como está a bateria?" to RoutingCategory.SENSORS, // Portuguese
            "fait-il chaud dans ma chambre?" to RoutingCategory.SENSORS, // French
            "température ambiante" to RoutingCategory.SENSORS, // French
            "wie warm ist mein zimmer" to RoutingCategory.SENSORS, // German
            "raumtemperatur" to RoutingCategory.SENSORS, // German
            "cómo está la luz" to RoutingCategory.SENSORS, // Spanish
            "comment est la lumiere" to RoutingCategory.SENSORS, // French
            "wie ist das licht" to RoutingCategory.SENSORS, // German
            "北はどっちですか" to RoutingCategory.SENSORS // Japanese
        )
        for ((prompt, expectedCategory) in multilingualPrompts) {
            val category = router.classifyRequest(prompt)
            assertEquals(
                "Prompt '$prompt' should be classified as $expectedCategory but was $category",
                expectedCategory,
                category
            )
            assertTrue("Should route '$prompt' to agent", router.shouldUseAgent(prompt))
        }
    }

    @Test
    fun classifyRequest_returnsCorrectCategory() {
        assertEquals(RoutingCategory.IMAGE_GENERATION, router.classifyRequest("Draw a cute kitten"))
        assertEquals(RoutingCategory.LIVE_INFORMATION, router.classifyRequest("what is the weather today?"))
        assertEquals(RoutingCategory.TASK_REGISTRY, router.classifyRequest("add a task to walk the dog"))
        assertEquals(RoutingCategory.DIRECT_CHAT, router.classifyRequest("hello how are you?"))
    }
}
