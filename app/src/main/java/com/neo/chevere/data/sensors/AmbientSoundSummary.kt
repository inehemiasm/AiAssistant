package com.neo.chevere.data.sensors

import android.content.Context
import com.neo.chevere.R
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

/** Renders app-authored sensor answers using the active Android locale. */
internal fun AmbientSoundReading.toChatSummary(context: Context): String = when (this) {
    is AmbientSoundReading.Available -> {
        val description = context.getString(when {
            dbSpl < 30 -> R.string.ui_very_quiet_near_silence
            dbSpl < 45 -> R.string.ui_quiet_library_level_b9f249
            dbSpl < 60 -> R.string.ui_moderate_noise_normal_conversation
            dbSpl < 75 -> R.string.ui_loud_busy_office_or_traffic
            dbSpl < 90 -> R.string.ui_very_loud_heavy_machinery_or_concert
            else -> R.string.ui_extremely_loud_potential_hearing_risk
        })
        context.getString(R.string.ui_background_sound_at_your_phone_1_s_uncalibrated_estimate_2_s, MeasurementFormatter.measurement(dbSpl.toDouble(), "dB SPL", 0), description)
    }
    AmbientSoundReading.Silence -> context.getString(R.string.ui_background_sound_at_your_phone_inaudible_or_near_silence_20_db_s)
    AmbientSoundReading.PermissionRequired -> context.getString(R.string.ui_microphone_permission_is_required_to_measure_the_ambient_sound_l_fae33e)
    is AmbientSoundReading.Unavailable -> context.getString(R.string.ui_could_not_measure_the_background_sound_level_right_now)
}
