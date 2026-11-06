package com.neo.chevere.ui.marketplace

import com.neo.chevere.R
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

internal fun InstalledModel.isAudioCapable(): Boolean {
    if (ModelCapability.AUDIO in capabilities) return true
    val lower = "$displayName $fileName".lowercase()
    return "audio" in lower || "whisper" in lower || "omni" in lower || "speech" in lower || "parakeet" in lower
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

internal fun ModelEntry.getRamCompatibility(deviceRamGb: Double): Pair<RamCompatibility, Int> {
    if (deviceRamGb <= 0.0) return Pair(RamCompatibility.COMPATIBLE, R.string.ui_ram_check_n_a)
    if (sizeBytes <= 0) return Pair(RamCompatibility.COMPATIBLE, R.string.ui_compatible)

    // For image generation / diffusion models
    if (isImageGenerationModel) {
        return when {
            deviceRamGb >= 12.0 -> Pair(RamCompatibility.OPTIMAL, R.string.ui_optimal_12gb_ram)
            deviceRamGb >= 8.0 -> Pair(RamCompatibility.COMPATIBLE, R.string.ui_compatible_8gb_ram)
            deviceRamGb >= 6.0 -> Pair(RamCompatibility.WARNING, R.string.ui_tight_ram_8gb_rec)
            else -> Pair(RamCompatibility.RISK, R.string.ui_high_oom_risk_8gb_rec)
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
        deviceRamGb >= recommendedRam -> Pair(RamCompatibility.OPTIMAL, R.string.ui_optimal_ram)
        deviceRamGb >= minRequiredRam -> Pair(RamCompatibility.COMPATIBLE, R.string.ui_compatible)
        deviceRamGb >= (minRequiredRam - 2.0) -> Pair(RamCompatibility.WARNING, R.string.ui_tight_ram)
        else -> Pair(RamCompatibility.RISK, R.string.ui_high_oom_risk)
    }
}
