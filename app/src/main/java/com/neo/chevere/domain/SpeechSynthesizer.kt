package com.neo.chevere.domain

/**
 * Speaks assistant text aloud. Implementations wrap a platform text-to-speech engine and live in
 * the data layer so presentation code only depends on this contract.
 */
interface SpeechSynthesizer {
    /**
     * Speaks [text] and suspends until playback finishes, is interrupted, or fails.
     * Cancelling the calling coroutine stops playback.
     */
    suspend fun speak(text: String): SpeechOutcome

    /** Immediately stops any in-progress playback. Safe to call when idle. */
    fun stop()
}

/** Terminal result of a [SpeechSynthesizer.speak] call. */
sealed interface SpeechOutcome {
    /** All text was spoken. */
    data object Completed : SpeechOutcome

    /** Playback was stopped before finishing (explicit stop or coroutine cancellation). */
    data object Interrupted : SpeechOutcome

    /** No usable text-to-speech engine or voice is available on this device. */
    data object Unavailable : SpeechOutcome
}
