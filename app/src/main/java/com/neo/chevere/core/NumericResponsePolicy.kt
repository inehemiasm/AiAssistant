package com.neo.chevere.core

/** Shared response guidance for direct chat and agent observations. */
object NumericResponsePolicy {
    const val INSTRUCTION = "Use digits for quantities and measurements (58°F, 83%, 12 km/h, -2.5°C), unless the user asks for words. Keep each value with its unit and label. Preserve observed values, signs, units, and estimate, unavailable, and cached-location qualifiers; do not invent readings, swap high/low values, or convert units unless requested. Prefer short labeled lines for multiple measurements."
}
