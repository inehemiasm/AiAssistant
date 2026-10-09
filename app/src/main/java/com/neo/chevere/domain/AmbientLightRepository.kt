package com.neo.chevere.domain

/** A measured illuminance at the phone, without platform sensor types. */
sealed interface AmbientLightReading {
    data class Available(val lux: Float) : AmbientLightReading
    data object Unsupported : AmbientLightReading
    data object Unavailable : AmbientLightReading
}

/** Acquires one current light reading; implementations own hardware access and timeouts. */
interface AmbientLightRepository {
    suspend fun readLight(): AmbientLightReading
}
