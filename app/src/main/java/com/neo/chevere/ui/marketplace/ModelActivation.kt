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

internal enum class RamCompatibility {
    OPTIMAL,
    COMPATIBLE,
    WARNING,
    RISK
}

internal fun ModelEntry.getRamCompatibility(deviceRamGb: Double): Pair<RamCompatibility, String> {
    if (deviceRamGb <= 0.0) return Pair(RamCompatibility.COMPATIBLE, "RAM Check N/A")
    if (sizeBytes <= 0) return Pair(RamCompatibility.COMPATIBLE, "Compatible")

    // For image generation / diffusion models
    if (isImageGenerationModel) {
        return when {
            deviceRamGb >= 12.0 -> Pair(RamCompatibility.OPTIMAL, "Optimal (12GB+ RAM)")
            deviceRamGb >= 8.0 -> Pair(RamCompatibility.COMPATIBLE, "Compatible (8GB+ RAM)")
            deviceRamGb >= 6.0 -> Pair(RamCompatibility.WARNING, "Tight RAM (8GB+ Rec.)")
            else -> Pair(RamCompatibility.RISK, "High OOM Risk (8GB+ Rec.)")
        }
    }

    // For LLMs
    val sizeGb = sizeBytes / (1024.0 * 1024.0 * 1024.0)

    val minRequiredRam = when {
        sizeGb > 4.5 -> 12.0  // e.g. Gemma 4 E4B (5.16 GB)
        sizeGb > 2.2 -> 8.0   // e.g. Gemma 4 E2B (2.76 GB)
        sizeGb > 1.2 -> 6.0   // e.g. Gemma 2 2B (1.65 GB), Qwen 2.5 1.5B (1.56 GB)
        else -> 4.0           // e.g. Gemma 3 1B IT (0.95 GB)
    }

    val recommendedRam = when {
        sizeGb > 4.5 -> 16.0
        sizeGb > 2.2 -> 12.0
        sizeGb > 1.2 -> 8.0
        else -> 6.0
    }

    return when {
        deviceRamGb >= recommendedRam -> Pair(RamCompatibility.OPTIMAL, "Optimal RAM")
        deviceRamGb >= minRequiredRam -> Pair(RamCompatibility.COMPATIBLE, "Compatible")
        deviceRamGb >= (minRequiredRam - 2.0) -> Pair(RamCompatibility.WARNING, "Tight RAM")
        else -> Pair(RamCompatibility.RISK, "High OOM Risk")
    }
}
