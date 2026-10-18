package com.neo.chevere.data.sensors

import com.neo.chevere.core.MeasurementFormatter
import com.neo.chevere.domain.AmbientSoundReading

/** Converts a domain ambient sound reading into a factual chat summary or sensor report line. */
internal fun AmbientSoundReading.toChatSummary(): String = when (this) {
    is AmbientSoundReading.Available -> {
        val description = soundDescription(dbSpl)
        "Background sound at your phone: **${MeasurementFormatter.measurement(dbSpl, "dB SPL", 0)}** (uncalibrated estimate) — $description."
    }
    AmbientSoundReading.Silence ->
        "Background sound at your phone: **Inaudible or near silence** (< 20 dB SPL)."
    AmbientSoundReading.PermissionRequired ->
        "Microphone permission is required to measure the ambient sound level."
    is AmbientSoundReading.Unavailable ->
        "Could not measure the background sound level right now${reason?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()}."
}

internal fun AmbientSoundReading.toSensorReportLine(): String = when (this) {
    is AmbientSoundReading.Available -> {
        val rounded = dbSpl.toInt()
        "approximately $rounded dB SPL (uncalibrated estimate) — ${soundDescription(dbSpl).replaceFirstChar { it.uppercase() }}"
    }
    AmbientSoundReading.Silence -> "Inaudible or Silence"
    AmbientSoundReading.PermissionRequired -> "MIC_PERMISSION_REQUIRED"
    is AmbientSoundReading.Unavailable -> "Measurement failed: ${reason ?: "unavailable"}"
}

private fun soundDescription(dbSpl: Double): String = when {
    dbSpl < 30.0 -> "very quiet, near silence"
    dbSpl < 45.0 -> "quiet, library level"
    dbSpl < 60.0 -> "moderate noise, normal conversation"
    dbSpl < 75.0 -> "loud, busy office or traffic"
    dbSpl < 90.0 -> "very loud, heavy machinery or concert"
    else -> "extremely loud, potential hearing risk"
}
