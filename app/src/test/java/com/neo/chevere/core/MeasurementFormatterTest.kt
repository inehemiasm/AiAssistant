package com.neo.chevere.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class MeasurementFormatterTest {
    @Test fun digitsRetainSignsDecimalsAndCompactUnits() {
        assertEquals("58°F", MeasurementFormatter.measurement(58.0, "°F", 0))
        assertEquals("83%", MeasurementFormatter.measurement(83.0, "%", 0))
        assertEquals("12 km/h", MeasurementFormatter.measurement(12.0, "km/h", 0))
        assertEquals("-2.35 m/s²", MeasurementFormatter.measurement(-2.345, "m/s²"))
        assertEquals("0", MeasurementFormatter.number(-0.001))
        assertEquals("1000000000", MeasurementFormatter.number(1e9))
    }

    @Test fun unavailableAndInvalidReadingsDoNotBecomeZero() {
        for (value in listOf(null, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertEquals("N/A", MeasurementFormatter.measurement(value, "°C"))
            assertEquals("N/A", MeasurementFormatter.temperature(value))
        }
    }

    @Test fun convertsTemperatureBeforeRounding() {
        assertEquals("36.6°C (97.9°F)", MeasurementFormatter.temperature(36.6))
        assertEquals("-2.5°C (27.5°F)", MeasurementFormatter.temperature(-2.5))
    }

    @Test fun toolNumbersRemainUnambiguousAcrossLocales() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("1013.25 hPa", MeasurementFormatter.measurement(1013.25, "hPa"))
        } finally { Locale.setDefault(original) }
    }
}
