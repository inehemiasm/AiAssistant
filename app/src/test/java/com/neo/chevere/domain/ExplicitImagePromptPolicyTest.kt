package com.neo.chevere.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplicitImagePromptPolicyTest {

    private val policy = ExplicitImagePromptPolicy()

    @Test
    fun requiresAgeVerification_forExplicitImageRequest() {
        assertTrue(policy.requiresAgeVerification("Create an image of a nude adult portrait"))
    }

    @Test
    fun requiresAgeVerification_ignoresExplicitTextThatIsNotImageGeneration() {
        assertFalse(policy.requiresAgeVerification("Explain what the word nude means"))
    }

    @Test
    fun requiresAgeVerification_allowsNormalImageRequest() {
        assertFalse(policy.requiresAgeVerification("Generate an image of a white wolf"))
    }
}
