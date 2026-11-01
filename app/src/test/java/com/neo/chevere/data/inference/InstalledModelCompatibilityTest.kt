package com.neo.chevere.data.inference

import com.neo.chevere.data.datasource.local.InstalledModelEntity
import com.neo.chevere.data.datasource.local.toDomain
import com.neo.chevere.domain.*
import org.junit.Assert.*
import org.junit.Test

class InstalledModelCompatibilityTest {
    @Test fun persistedWebGemmaLosesIncorrectVisionWithoutLosingTheFileOrHealth() {
        val entity = InstalledModelEntity(
            id = "web-gemma", displayName = "Old catalog name",
            filePath = "/models/gemma-4-E2B-it-web.litertlm",
            fileName = "gemma-4-E2B-it-web.litertlm",
            source = ModelSource.HUGGING_FACE, format = ModelFormat.LITERTLM,
            runtime = ModelRuntime.LITERT, taskType = ModelTaskType.VISION_CHAT,
            capabilities = "TEXT,VISION", installStatus = InstallStatus.INSTALLED,
            sizeBytes = 2008432640L, checksum = null, installedAt = 1L,
        )
        val model = entity.toDomain()
        assertEquals(setOf(ModelCapability.TEXT), model.capabilities)
        assertEquals(ModelTaskType.CHAT, model.taskType)
        assertEquals(entity.filePath, model.filePath)
        assertEquals(InstallStatus.INSTALLED, model.installStatus)
        assertTrue(entity.copy(fileName = "gemma-4-E2B-it.litertlm").toDomain().capabilities.contains(ModelCapability.VISION))
    }
}
