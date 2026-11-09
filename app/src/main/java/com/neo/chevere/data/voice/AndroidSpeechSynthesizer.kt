package com.neo.chevere.data.voice

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.neo.chevere.domain.SpeechOutcome
import com.neo.chevere.domain.SpeechSynthesizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private const val TAG = "AndroidSpeechSynthesizer"

/** Conservative chunk size below `TextToSpeech.getMaxSpeechInputLength()` (4000 on AOSP). */
private const val MAX_CHUNK_CHARS = 3000

/**
 * [SpeechSynthesizer] backed by Android's [TextToSpeech] engine.
 *
 * The engine is created lazily on first use and kept for the process lifetime. Long replies are
 * split on sentence boundaries and spoken sequentially; cancelling the caller stops playback.
 * Speech is synthesised by the device's installed TTS provider, which may use network voices
 * depending on the user's system settings.
 */
@Singleton
class AndroidSpeechSynthesizer @Inject constructor(
    @ApplicationContext private val context: Context
) : SpeechSynthesizer {

    private val initMutex = Mutex()
    private var engine: TextToSpeech? = null
    private val pending = ConcurrentHashMap<String, CancellableContinuation<SpeechOutcome>>()

    override suspend fun speak(text: String): SpeechOutcome {
        val clean = text.trim()
        if (clean.isEmpty()) return SpeechOutcome.Completed
        val tts = obtainEngine() ?: return SpeechOutcome.Unavailable
        for (chunk in chunk(clean)) {
            val outcome = speakChunk(tts, chunk)
            if (outcome != SpeechOutcome.Completed) return outcome
        }
        return SpeechOutcome.Completed
    }

    override fun stop() {
        runCatching { engine?.stop() }
        pending.keys.toList().forEach { id ->
            pending.remove(id)?.let { if (it.isActive) it.resume(SpeechOutcome.Interrupted) }
        }
    }

    private suspend fun speakChunk(tts: TextToSpeech, chunk: String): SpeechOutcome =
        suspendCancellableCoroutine { continuation ->
            val id = UUID.randomUUID().toString()
            pending[id] = continuation
            continuation.invokeOnCancellation {
                pending.remove(id)
                runCatching { tts.stop() }
            }
            val status = tts.speak(chunk, TextToSpeech.QUEUE_ADD, null, id)
            if (status != TextToSpeech.SUCCESS) {
                pending.remove(id)
                if (continuation.isActive) continuation.resume(SpeechOutcome.Unavailable)
            }
        }

    private suspend fun obtainEngine(): TextToSpeech? = initMutex.withLock {
        engine?.let { return@withLock it }
        val created = try {
            createEngine()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            Timber.tag(TAG).w("TTS initialisation failed: %s", error::class.java.simpleName)
            null
        }
        engine = created
        created
    }

    private suspend fun createEngine(): TextToSpeech? = suspendCancellableCoroutine { continuation ->
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context) { status ->
            val tts = instance
            if (status != TextToSpeech.SUCCESS || tts == null) {
                runCatching { tts?.shutdown() }
                if (continuation.isActive) continuation.resume(null)
                return@TextToSpeech
            }
            val languageStatus = tts.setLanguage(Locale.getDefault())
            if (languageStatus == TextToSpeech.LANG_MISSING_DATA ||
                languageStatus == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                tts.setLanguage(Locale.US)
            }
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            tts.setOnUtteranceProgressListener(progressListener)
            if (continuation.isActive) continuation.resume(tts) else tts.shutdown()
        }
        continuation.invokeOnCancellation { runCatching { instance?.shutdown() } }
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) = finish(utteranceId, SpeechOutcome.Completed)

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = finish(utteranceId, SpeechOutcome.Unavailable)

        override fun onError(utteranceId: String?, errorCode: Int) =
            finish(utteranceId, SpeechOutcome.Unavailable)

        override fun onStop(utteranceId: String?, interrupted: Boolean) =
            finish(utteranceId, SpeechOutcome.Interrupted)
    }

    private fun finish(utteranceId: String?, outcome: SpeechOutcome) {
        val continuation = utteranceId?.let { pending.remove(it) } ?: return
        if (continuation.isActive) continuation.resume(outcome)
    }

    /** Splits [text] into sentence-aligned chunks no longer than [MAX_CHUNK_CHARS]. */
    private fun chunk(text: String): List<String> {
        if (text.length <= MAX_CHUNK_CHARS) return listOf(text)
        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        for (sentence in sentences) {
            if (current.length + sentence.length + 1 > MAX_CHUNK_CHARS && current.isNotEmpty()) {
                chunks += current.toString()
                current.clear()
            }
            if (sentence.length > MAX_CHUNK_CHARS) {
                sentence.chunked(MAX_CHUNK_CHARS).forEach { chunks += it }
            } else {
                if (current.isNotEmpty()) current.append(' ')
                current.append(sentence)
            }
        }
        if (current.isNotEmpty()) chunks += current.toString()
        return chunks
    }
}
