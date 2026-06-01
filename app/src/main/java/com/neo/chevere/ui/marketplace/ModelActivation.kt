package com.neo.chevere.ui.marketplace

import com.neo.chevere.domain.InstalledModel
import com.neo.chevere.domain.ModelCapability
import com.neo.chevere.domain.ModelEntry
import com.neo.chevere.domain.ModelTaskType

internal enum class ModelActivationCategory {
    CHAT,
    IMAGE_GENERATION
}

internal fun InstalledModel.activationCategory(): ModelActivationCategory =
    if (taskType == ModelTaskType.IMAGE_GENERATION || ModelCapability.IMAGE_GEN in capabilities) {
        ModelActivationCategory.IMAGE_GENERATION
    } else {
        ModelActivationCategory.CHAT
    }

internal fun ModelEntry.activationCategory(): ModelActivationCategory {
    return if (isImageGenerationModel) {
        ModelActivationCategory.IMAGE_GENERATION
    } else {
        ModelActivationCategory.CHAT
    }
}

internal fun InstalledModel.matchesEntry(entry: ModelEntry): Boolean =
    id == entry.effectiveInstalledId ||
            id == entry.effectiveFileName ||
            fileName == entry.effectiveFileName ||
            fileName == entry.effectiveInstalledId
