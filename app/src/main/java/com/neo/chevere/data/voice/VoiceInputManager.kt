package com.neo.chevere.data.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.neo.chevere.R
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
    /** Partial (in-progress) transcription update across continuous segments; text may still change. */
    data class Partial(val text: String) : VoiceResult()
    /** Final transcription; recording is done and this text is committed. */
    data class Final(val text: String) : VoiceResult()
    /** Normalized microphone RMS level in `0f..1f` for driving reactive UI waveform bars. */
    data class Amplitude(val normalized: Float) : VoiceResult()
    /** User explicitly cancelled the recording session without keeping the transcript. */
    data object Cancelled : VoiceResult()
    /** An error occurred during speech recognition. */
    data class Error(val message: String) : VoiceResult()
}

/**
 * Continuous offline-first voice listener built on Android's [SpeechRecognizer] that supports
 * Whisper-style multi-sentence voice notes across pauses, live RMS waveform telemetry, and
 * explicit Done/Cancel controls.
 *
 * Uses [RecognizerIntent.EXTRA_PREFER_OFFLINE] so recognition runs locally when an offline
 * model pack is installed, and automatically chains recognition segments while [isSessionActive]
 * remains true so natural pauses do not prematurely terminate a long voice note.
 */
@Singleton
class VoiceInputManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _results = Channel<VoiceResult>(Channel.UNLIMITED)

    /** Flow of [VoiceResult] events. Collect this in the ViewModel. */
    val results: Flow<VoiceResult> = _results.receiveAsFlow()

    private var recognizer: SpeechRecognizer? = null
    private var isSessionActive: Boolean = false
    private val committedSegments = mutableListOf<String>()
    private var latestSegmentPartial: String = ""

    /**
     * Starts a new continuous voice listening session.
     *
     * Must be called from the **main thread** (Android SpeechRecognizer requirement).
     */
    fun startListening() {
        stopAndDestroyRecognizer()
        committedSegments.clear()
        latestSegmentPartial = ""

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            isSessionActive = false
            _results.trySend(
                VoiceResult.Error(
                    context.getString(R.string.ui_speech_recognition_is_not_available_on_this_device)
                )
            )
            return
        }

        isSessionActive = true
        startRecognizerSegment()
    }

    /**
     * Finishes the active recognition session and commits all accumulated speech segments
     * as a [VoiceResult.Final] event.
     *
     * Must be called from the **main thread**.
     */
    fun stopListening() {
        val wasActive = isSessionActive
        isSessionActive = false
        try {
            recognizer?.stopListening()
        } catch (_: Exception) {
        }
        stopAndDestroyRecognizer()
        if (wasActive) {
            val finalTranscript = buildFullTranscript(latestSegmentPartial)
            committedSegments.clear()
            latestSegmentPartial = ""
            _results.trySend(VoiceResult.Final(finalTranscript))
        }
    }

    /**
     * Cancels the active recognition session immediately and discards any buffered text.
     *
     * Must be called from the **main thread**.
     */
    fun cancelListening() {
        isSessionActive = false
        committedSegments.clear()
        latestSegmentPartial = ""
        try {
            recognizer?.cancel()
        } catch (_: Exception) {
        }
        stopAndDestroyRecognizer()
        _results.trySend(VoiceResult.Cancelled)
    }

    private fun startRecognizerSegment() {
        if (!isSessionActive) return
        stopAndDestroyRecognizer()

        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { sr ->
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Timber.tag(TAG).d("Ready for speech segment")
                }

                override fun onBeginningOfSpeech() {
                    Timber.tag(TAG).d("Speech segment started")
                }

                override fun onRmsChanged(rmsdB: Float) {
                    if (!isSessionActive) return
                    // Typical SpeechRecognizer rmsdB ranges from -2f to +10f
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1.0f)
                    _results.trySend(VoiceResult.Amplitude(normalized))
                }

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    Timber.tag(TAG).d("Speech segment ended")
                }

                override fun onError(error: Int) {
                    if (!isSessionActive) return

                    // During continuous Whisper-style memo recording, a pause between sentences
                    // triggers ERROR_NO_MATCH or ERROR_SPEECH_TIMEOUT. Keep the session alive
                    // and start the next segment seamlessly.
                    if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    ) {
                        Timber.tag(TAG).d("Pause detected ($error); continuing listening session")
                        startRecognizerSegment()
                        return
                    }

                    isSessionActive = false
                    val existing = buildFullTranscript(latestSegmentPartial)
                    if (existing.isNotBlank()) {
                        committedSegments.clear()
                        latestSegmentPartial = ""
                        _results.trySend(VoiceResult.Final(existing))
                    } else {
                        val message = speechErrorToMessage(error)
                        Timber.tag(TAG).w("Recognition error $error: $message")
                        _results.trySend(VoiceResult.Error(message))
                    }
                }

                override fun onResults(results: Bundle?) {
                    val segment = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()

                    if (segment.isNotBlank()) {
                        committedSegments.add(segment)
                    }
                    latestSegmentPartial = ""
                    val full = buildFullTranscript("")
                    if (full.isNotBlank()) {
                        _results.trySend(VoiceResult.Partial(full))
                    }

                    if (isSessionActive) {
                        startRecognizerSegment()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    if (!isSessionActive) return
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (partial.isNotBlank()) {
                        latestSegmentPartial = partial
                        _results.trySend(VoiceResult.Partial(buildFullTranscript(partial)))
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
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                    2000L
                )
            }
            sr.startListening(intent)
        }
    }

    private fun buildFullTranscript(currentPartial: String): String {
        val base = committedSegments.joinToString(" ").trim()
        val tail = currentPartial.trim()
        return when {
            base.isEmpty() -> tail
            tail.isEmpty() -> base
            else -> "$base $tail"
        }
    }

    private fun stopAndDestroyRecognizer() {
        try {
            recognizer?.destroy()
        } catch (_: Exception) {
        }
        recognizer = null
    }

    private fun speechErrorToMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> context.getString(R.string.ui_audio_recording_error)
        SpeechRecognizer.ERROR_CLIENT -> context.getString(R.string.ui_client_side_error)
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> context.getString(R.string.microphone_permission_denied)
        SpeechRecognizer.ERROR_NETWORK -> context.getString(R.string.ui_network_error_during_speech_recognition)
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> context.getString(R.string.ui_network_timed_out)
        SpeechRecognizer.ERROR_NO_MATCH -> context.getString(R.string.ui_no_speech_was_recognized)
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> context.getString(R.string.ui_speech_recognizer_is_busy)
        SpeechRecognizer.ERROR_SERVER -> context.getString(R.string.ui_server_error_during_recognition)
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> context.getString(R.string.ui_no_speech_input_detected)
        else -> context.getString(R.string.ui_unknown_speech_recognition_error_1_s, error)
    }
}
