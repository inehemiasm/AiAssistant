package com.neo.chevere.data.inference.shared

import com.neo.chevere.domain.InstallStatus
import com.neo.chevere.domain.InstalledModel
import com.neo.chevere.domain.ModelCapability
import com.neo.chevere.domain.ModelFormat
import com.neo.chevere.domain.ModelRuntime
import com.neo.chevere.domain.ModelSource
import com.neo.chevere.domain.ModelTaskType
import com.neo.chevere.shared.domain.ModelCapability as SharedModelCapability
import com.neo.chevere.shared.domain.ModelFormat as SharedModelFormat
import com.neo.chevere.shared.domain.ModelRuntime as SharedModelRuntime
import com.neo.chevere.shared.domain.ModelTask as SharedModelTask
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidTextGenerationRuntimeTest {
    @Test
    fun cumulativeTextTracker_emitsOnlyNewText() {
        val tracker = CumulativeTextTracker()

        assertEquals("Hel", tracker.accept("Hel"))
        assertEquals("lo", tracker.accept("Hello"))
        assertEquals("", tracker.accept("Hello"))
        assertEquals("!", tracker.accept("Hello!"))
        assertEquals("Hello!", tracker.currentText)
    }

    @Test
    fun cumulativeTextTracker_recoversWhenSnapshotIsReplaced() {
        val tracker = CumulativeTextTracker()
        tracker.accept("Original")

        assertEquals("Replacement", tracker.accept("Replacement"))
        assertEquals("Replacement", tracker.currentText)
    }

    @Test
    fun installedLiteRtModel_mapsToPortableArtifact() {
        val artifact = InstalledModel(
            id = "gemma.litertlm",
            displayName = "Gemma",
            filePath = "/models/gemma.litertlm",
            fileName = "gemma.litertlm",
            source = ModelSource.LOCAL,
            format = ModelFormat.LITERTLM,
            runtime = ModelRuntime.LITERT,
            taskType = ModelTaskType.VISION_CHAT,
            capabilities = setOf(ModelCapability.TEXT, ModelCapability.VISION),
            installStatus = InstallStatus.INSTALLED
        ).toSharedArtifact()

        assertEquals(SharedModelRuntime.LITERT_LM, artifact.runtime)
        assertEquals(SharedModelFormat.LITERT_LM, artifact.format)
        assertEquals(SharedModelTask.VISION_LANGUAGE, artifact.task)
        assertEquals(
            setOf(
                SharedModelCapability.TEXT_INPUT,
                SharedModelCapability.TEXT_OUTPUT,
                SharedModelCapability.IMAGE_INPUT
            ),
            artifact.capabilities
        )
    }
}
