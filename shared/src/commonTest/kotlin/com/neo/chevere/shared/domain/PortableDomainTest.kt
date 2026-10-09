package com.neo.chevere.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PortableDomainTest {
    @Test
    fun modelArtifact_retainsPortableMetadata() {
        val model = ModelArtifact(
            id = "gemma.litertlm",
            displayName = "Gemma",
            location = ModelLocation.LocalPath("/models/gemma.litertlm"),
            runtime = ModelRuntime.LITERT_LM,
            format = ModelFormat.LITERT_LM,
            task = ModelTask.TEXT_GENERATION,
            capabilities = setOf(ModelCapability.TEXT_INPUT, ModelCapability.TEXT_OUTPUT)
        )

        assertEquals("gemma.litertlm", model.id)
        assertEquals(ModelRuntime.LITERT_LM, model.runtime)
    }

    @Test
    fun blankPlatformLocators_areRejected() {
        assertFailsWith<IllegalArgumentException> { ModelLocation.LocalPath(" ") }
        assertFailsWith<IllegalArgumentException> { MediaReference.Uri("") }
    }
}
