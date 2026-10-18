package com.neo.chevere.data.sensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import com.neo.chevere.domain.AmbientSoundReading
import com.neo.chevere.domain.AmbientSoundRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10

/**
 * Samples microphone amplitude over a ~1 second window using [MediaRecorder] and converts the peak
 * amplitude into an uncalibrated dB SPL estimate.
 */
@Singleton
class AndroidAmbientSoundRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) : AmbientSoundRepository {

    override suspend fun readAmbientSound(): AmbientSoundReading {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return AmbientSoundReading.PermissionRequired
        }

        return withContext(Dispatchers.IO) {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            val tmpFile = File(context.cacheDir, "sound_probe.3gp")

            try {
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                recorder.setOutputFile(tmpFile.absolutePath)
                recorder.prepare()
                recorder.start()

                // Prime maxAmplitude baseline (first call after start() always returns 0)
                recorder.maxAmplitude

                // Warm up and sample 4 windows over ~800 ms
                delay(200L)
                val samples = mutableListOf<Int>()
                repeat(4) {
                    delay(200L)
                    samples += recorder.maxAmplitude
                }

                runCatching { recorder.stop() }
                val peak = samples.maxOrNull() ?: 0
                if (peak <= 0) {
                    AmbientSoundReading.Silence
                } else {
                    val dbFs = 20.0 * log10(peak / 32767.0)
                    val dbSpl = (dbFs + 90.0).coerceIn(0.0, 120.0)
                    AmbientSoundReading.Available(dbSpl = dbSpl, peakAmplitude = peak)
                }
            } catch (e: SecurityException) {
                AmbientSoundReading.PermissionRequired
            } catch (e: Exception) {
                AmbientSoundReading.Unavailable(e.message)
            } finally {
                runCatching { recorder.release() }
                runCatching { tmpFile.delete() }
            }
        }
    }
}
