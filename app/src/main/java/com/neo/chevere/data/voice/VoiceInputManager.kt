package com.neo.chevere.data.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "VoiceInputManager"

/** Results emitted by [VoiceInputManager.results]. */
sealed class VoiceResult {
    /** Partial (in-progress) transcription update; text may still change. */
    data class Partial(val text: String) : VoiceResult()
    /** Final transcription; recording is done and this text is committed. */
    data class Final(val text: String) : VoiceResult()
    /** An error occurred during speech recognition. */
    data class Error(val message: String) : VoiceResult()
}

/**
 * A thin wrapper around Android's [SpeechRecognizer] that surfaces voice-input results as a
 * [Flow] of [VoiceResult] events.
 *
 * Uses [RecognizerIntent.EXTRA_PREFER_OFFLINE] so recognition runs fully on-device when a
 * language pack is installed. The system degrades gracefully to network recognition when no
 * offline pack is available, rather than failing hard.
 *
 * The [results] channel is a [Channel.UNLIMITED] broadcast so that collectors can process events
 * without back-pressure blocking the [RecognitionListener] callbacks (which arrive on the main
 * thread). A new [SpeechRecognizer] instance is created per [startListening] call so that the
 * recognizer state machine is always fresh, avoiding subtle restart bugs.
 */
@Singleton
class VoiceInputManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _results = Channel<VoiceResult>(Channel.UNLIMITED)

    /** Flow of [VoiceResult] events. Collect this in the ViewModel. */
    val results: Flow<VoiceResult> = _results.receiveAsFlow()

    private var recognizer: SpeechRecognizer? = null

    /**
     * Starts a new recognition session.
     *
     * Must be called from the **main thread** (Android SpeechRecognizer requirement).
     */
    fun startListening() {
        stopAndDestroyRecognizer()
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _results.trySend(VoiceResult.Error("Speech recognition is not available on this device."))
            return
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { sr ->
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Timber.tag(TAG).d("Ready for speech")
                }

                override fun onBeginningOfSpeech() {
                    Timber.tag(TAG).d("Speech started")
                }

                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    Timber.tag(TAG).d("Speech ended")
                }

                override fun onError(error: Int) {
                    val message = speechErrorToMessage(error)
                    Timber.tag(TAG).w("Recognition error $error: $message")
                    _results.trySend(VoiceResult.Error(message))
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val best = matches?.firstOrNull().orEmpty()
                    Timber.tag(TAG).d("Final result: \"$best\"")
                    _results.trySend(VoiceResult.Final(best))
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (partial.isNotBlank()) {
                        _results.trySend(VoiceResult.Partial(partial))
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            sr.startListening(intent)
        }
    }

    /**
     * Stops the active recognition session early.
     *
     * Must be called from the **main thread**.
     */
    fun stopListening() {
        recognizer?.stopListening()
        stopAndDestroyRecognizer()
    }

    private fun stopAndDestroyRecognizer() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun speechErrorToMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
        SpeechRecognizer.ERROR_CLIENT -> "Client-side error."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied."
        SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition."
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timed out."
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech was recognized."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy."
        SpeechRecognizer.ERROR_SERVER -> "Server error during recognition."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input detected."
        else -> "Unknown speech recognition error ($error)."
    }
}
