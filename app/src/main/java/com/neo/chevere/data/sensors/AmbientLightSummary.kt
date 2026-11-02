package com.neo.chevere.data.sensors

import android.content.Context
import com.neo.chevere.R
import com.neo.chevere.core.MeasurementFormatter
import com.neo.chevere.domain.AmbientLightReading

/** Converts a domain reading into a factual chat summary; never substitutes an image or invented value. */
internal fun AmbientLightReading.toChatSummary(): String = when (this) {
    is AmbientLightReading.Available -> if (!lux.isFinite() || lux < 0) {
        "Could not get a valid light reading. Try again with the phone's light sensor uncovered."
    } else {
        val description = when {
            lux < 10 -> "very low light"
            lux < 50 -> "dim light"
            lux < 200 -> "low indoor light"
            lux < 500 -> "moderate indoor light"
            lux < 1_000 -> "bright light"
            else -> "strong light"
        }
        "Light at your phone: **${MeasurementFormatter.measurement(lux.toDouble(), "lux", 1)}** — $description."
    }
    AmbientLightReading.Unsupported -> "This device does not have an ambient light sensor, so I cannot measure the room's light level."
    AmbientLightReading.Unavailable -> "Could not get a light reading. Try again with the phone's light sensor uncovered."
}

/** Renders app-authored sensor answers using the active Android locale. */
internal fun AmbientLightReading.toChatSummary(context: Context): String = when (this) {
    is AmbientLightReading.Available -> {
        if (!lux.isFinite() || lux < 0) context.getString(R.string.ui_could_not_get_a_valid_light_reading_try_again_with_the_phone_s_l)
        else {
        val description = context.getString(when {
            lux < 10 -> R.string.ui_very_low_light
            lux < 50 -> R.string.ui_dim_light
            lux < 200 -> R.string.ui_low_indoor_light
            lux < 500 -> R.string.ui_moderate_indoor_light
            lux < 1000 -> R.string.ui_bright_light
            else -> R.string.ui_strong_light
        })
        context.getString(R.string.ui_light_at_your_phone_1_s_2_s, MeasurementFormatter.measurement(lux.toDouble(), "lux", 1), description)
        }
    }
    AmbientLightReading.Unsupported -> context.getString(R.string.ui_this_device_does_not_have_an_ambient_light_sensor_so_i_cannot_me)
    AmbientLightReading.Unavailable -> context.getString(R.string.ui_could_not_get_a_light_reading_try_again_with_the_phone_s_light_s)
}
