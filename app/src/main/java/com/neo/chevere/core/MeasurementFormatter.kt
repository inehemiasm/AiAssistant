package com.neo.chevere.core

import java.math.BigDecimal
import java.math.RoundingMode

/** Formats tool measurements as locale-independent digits, preserving signs and explicit units. */
object MeasurementFormatter {
    /** Non-finite or missing readings stay unavailable; rounding never invents a zero reading. */
    fun number(value: Double?, decimalPlaces: Int = 2): String {
        require(decimalPlaces in 0..6)
        if (value == null || !value.isFinite()) return "N/A"
        return BigDecimal.valueOf(value).setScale(decimalPlaces, RoundingMode.HALF_UP)
            .stripTrailingZeros().toPlainString()
    }

    /** Unit symbols attach to their values; other units remain separated by a space. */
    fun measurement(value: Double?, unit: String, decimalPlaces: Int = 2): String {
        val number = number(value, decimalPlaces)
        if (number == "N/A") return number
        val separator = if (unit.startsWith("°") || unit == "%") "" else " "
        return "$number$separator$unit"
    }

    /** Derives Fahrenheit from the original Celsius reading before display rounding. */
    fun temperature(celsius: Double?): String {
        if (celsius == null || !celsius.isFinite()) return "N/A"
        return "${measurement(celsius, "°C", 1)} (${measurement(celsius * 9 / 5 + 32, "°F", 1)})"
    }
}
