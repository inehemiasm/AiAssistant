package com.neo.chevere.data.sensors

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
