package com.neo.chevere.data.inference

import org.junit.Assert.*
import org.junit.Test

class ModelRuntimeProfileTest {
    @Test fun webGemmaNeverRequestsVisionOrCpuEvenWithStaleCapabilities() {
        for (vision in listOf(true, false)) {
            val attempts = ModelRuntimeProfile.backends("/models/gemma-4-E2B-it-web.litertlm", vision)
            assertEquals(listOf("GPU Text-Only"), attempts.map { it.third })
            assertNull(attempts.single().second)
        }
    }

    @Test fun sparkRemainsCpuOnlyAndGeneralModelsHaveTextOnlyGpuFallback() {
        assertEquals(listOf("CPU Text-Only"), ModelRuntimeProfile.backends("Spark-X2.6-4B_int8.litertlm", true).map { it.third })
        assertEquals(listOf("CPU Text-Only", "GPU Text-Only"), ModelRuntimeProfile.backends("other.litertlm", false).map { it.third })
    }
    @Test fun sparkUsesFullContextAndDisablesThinking() {
        val path = "/models/Spark-X2.6-4B_int8.litertlm"
        assertEquals(4096, ModelRuntimeProfile.contextSize(path))
        val config = ModelRuntimeProfile.conversationConfig(path)
        assertEquals(false, config.extraContext["enable_thinking"])
        assertEquals(1536, config.maxOutputToken)
        assertFalse(config.automaticToolCalling)
        assertNull(config.channels)
    }

    @Test fun generalModelsReserveContextBeyondTheResponseLimit() {
        for (path in listOf("/models/gemma.litertlm", "/models/gemma-4-E2B-it-web.litertlm", "/models/qwen.litertlm")) {
            assertEquals(4096, ModelRuntimeProfile.contextSize(path))
            val config = ModelRuntimeProfile.conversationConfig(path)
            assertEquals(1024, config.maxOutputToken)
            assertTrue(ModelRuntimeProfile.contextSize(path) > config.maxOutputToken!!)
            assertTrue(config.extraContext.isEmpty())
        }
    }
}
