package com.neo.chevere.domain

/** A measured ambient sound level (in dB SPL) at the device microphone, without platform types. */
sealed interface AmbientSoundReading {
    data class Available(val dbSpl: Double, val peakAmplitude: Int) : AmbientSoundReading
    data object Silence : AmbientSoundReading
    data object PermissionRequired : AmbientSoundReading
    data class Unavailable(val reason: String? = null) : AmbientSoundReading
}

/** Acquires one short ambient sound reading from the microphone; implementations own hardware and permission checks. */
interface AmbientSoundRepository {
    suspend fun readAmbientSound(): AmbientSoundReading
}
