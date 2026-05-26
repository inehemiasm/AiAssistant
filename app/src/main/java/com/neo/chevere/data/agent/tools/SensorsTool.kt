package com.neo.chevere.data.agent.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaRecorder
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.neo.chevere.core.NumberUtils
import com.neo.chevere.data.agent.AgentTool
import com.neo.chevere.data.agent.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.math.log10

/**
 * Reads local hardware and environment sensors, delivering ambient status to the agent.
 *
 * Sensors reported:
 * - Ambient room temperature
 * - Ambient light level (lux)
 * - Atmospheric pressure (hPa)
 * - Battery level and charging status
 * - Device internal (battery) temperature
 * - CPU thermal throttling status
 * - Ambient sound level (dB SPL estimate via microphone amplitude)
 */
class SensorsTool @Inject constructor(
    @param:ApplicationContext private val context: Context
) : AgentTool {
    override val name: String = "read_sensors"
    override val description: String = """
        Reads all real-time environment and hardware sensors from the device and reports them to the user.
        ALWAYS call this tool when the user asks about ANY of the following topics:

        SOUND / NOISE / AUDIO (use this tool — do NOT say you cannot hear):
          Trigger phrases (English): how loud is it, how noisy is it, how quiet is it, what's the noise level,
          how's the sound, how's the sound in this room, how's the sound in here, check the sound,
          is it noisy, is it loud, is it quiet, ambient noise, background noise, sound level,
          decibels, dB, noise pollution, how much noise, how silent, how peaceful.
          Spanish: ¿cuánto ruido hay?, ¿qué tan ruidoso está?, nivel de ruido, ¿está silencioso?,
          ¿cómo está el sonido?, ruido ambiental, decibeles.
          French: quel est le niveau sonore, c'est bruyant, c'est calme, niveau de bruit, bruit ambiant.
          Portuguese: como está o som aqui, nível de ruído, está barulhento, está silencioso.
          German: wie laut ist es hier, Lärmpegel, Geräuschpegel, ist es laut, ist es ruhig.
          Japanese: 音量はどれくらい, うるさいですか, 静かですか, 騒音レベル.

        LIGHT / BRIGHTNESS / DARKNESS:
          how bright is it, how dark is it, how's the light, how's the lighting, check the light,
          is it bright, is it dark, ambient light, light level, lux, luminosity,
          how much light, brightness in here, is it well-lit, is the room bright, is the room dark.
          Spanish: ¿qué tan brillante está?, nivel de luz, ¿está oscuro?, ¿hay buena luz?.
          French: quelle est la luminosité, c'est sombre, c'est clair, niveau de lumière.
          Portuguese: como está a luz aqui, está escuro, nível de luminosidade.
          German: wie hell ist es hier, wie dunkel ist es, Helligkeit, Lichtstärke.
          Japanese: どれくらい明るいですか, 暗いですか, 明るさ.

        TEMPERATURE / HOT / COLD:
          how hot is my room, how cold is it, room temperature, ambient temperature, what's the temperature,
          is it hot, is it cold, is it warm, how warm, thermal, heat in here, how hot is it in here.
          Spanish: ¿qué temperatura hace?, ¿hace calor?, ¿hace frío?, temperatura ambiente.
          French: quelle est la température, il fait chaud, il fait froid.
          Portuguese: qual é a temperatura aqui, está quente, está frio.
          German: wie warm ist es hier, wie kalt ist es, Raumtemperatur.
          Japanese: 部屋の温度はどれくらい, 暑いですか, 寒いですか.

        BATTERY / CHARGING / DEVICE STATUS:
          battery level, how's my battery, is my phone charging, battery percentage, charge status.

        PRESSURE / THERMALS / HARDWARE:
          atmospheric pressure, CPU temperature, device thermals, is the CPU throttling.
    """.trimIndent()
    override val inputSchema: String = "None. Returns all active sensor and system status values."

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val lightValue    = querySensorValue(Sensor.TYPE_LIGHT)
        val pressureValue = querySensorValue(Sensor.TYPE_PRESSURE)
        val ambientTemp   = querySensorValue(Sensor.TYPE_AMBIENT_TEMPERATURE)
        val batteryInfo   = getBatteryInfo()
        val batteryTemp   = getBatteryTemperature()
        val thermalStatus = getThermalStatus()
        val soundResult   = measureAmbientSoundLevel()

        val report = buildString {
            appendLine("Device Sensor Report:")
            appendLine("- Ambient Room Temperature: ${ambientTemp?.let { formatTemperature(it) } ?: "No hardware sensor"}")
            appendLine("- Ambient Light: ${lightValue?.let { "${formatFloat(it)} lux" } ?: "Not available or timeout"}")
            appendLine("- Atmospheric Pressure: ${pressureValue?.let { "${formatFloat(it)} hPa" } ?: "Not available or timeout"}")
            appendLine("- Battery Status: $batteryInfo")
            appendLine("- Device Internal Temperature: $batteryTemp")
            appendLine("- CPU Thermals: $thermalStatus")
            appendLine("- Ambient Sound Level: $soundResult")
        }

        ToolResult.Success(report)
    }

    /**
     * Measures ambient sound level using the device microphone.
     *
     * Samples [MediaRecorder] amplitude over a ~1 second window and converts the
     * raw amplitude value to a rough dB SPL estimate. Returns a human-readable
     * string with dB value and a qualitative label (e.g. "Quiet", "Moderate noise").
     *
     * Returns the sentinel string `"MIC_PERMISSION_REQUIRED"` if [Manifest.permission.RECORD_AUDIO]
     * has not been granted.
     */
    private suspend fun measureAmbientSoundLevel(): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return "MIC_PERMISSION_REQUIRED"
        }

        return withContext(Dispatchers.IO) {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            try {
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                // Write to a temp file; we only care about amplitude, not the recording
                val tmpFile = java.io.File(context.cacheDir, "sound_probe.3gp")
                recorder.setOutputFile(tmpFile.absolutePath)
                recorder.prepare()
                recorder.start()

                // Warm up, then sample amplitude over ~800 ms
                delay(200L)
                val samples = mutableListOf<Int>()
                repeat(4) {
                    delay(200L)
                    samples += recorder.maxAmplitude
                }

                recorder.stop()
                tmpFile.delete()

                val peak = samples.maxOrNull() ?: 0
                formatSoundLevel(peak)
            } catch (e: Exception) {
                "Measurement failed: ${e.message}"
            } finally {
                runCatching { recorder.release() }
            }
        }
    }

    /**
     * Converts a raw [MediaRecorder.maxAmplitude] value (0–32767) to a dB estimate
     * and returns a description with a qualitative noise label.
     *
     * The formula `20 * log10(amplitude / 32767.0)` gives dB *relative to full scale* (dBFS).
     * We offset by +90 to produce a rough dB SPL approximation suitable for qualitative guidance.
     */
    private fun formatSoundLevel(amplitude: Int): String {
        if (amplitude <= 0) return "Inaudible / Silence"
        val dbFs  = 20.0 * log10(amplitude / 32767.0)   // negative; 0 = full scale
        val dbSpl = (dbFs + 90.0).coerceIn(0.0, 120.0)  // rough SPL offset
        val label = when {
            dbSpl < 30  -> "Very quiet (near silence)"
            dbSpl < 45  -> "Quiet (library-level)"
            dbSpl < 60  -> "Moderate noise (normal conversation)"
            dbSpl < 75  -> "Loud (busy office / traffic)"
            dbSpl < 90  -> "Very loud (heavy machinery / concert)"
            else        -> "Extremely loud (potential hearing risk)"
        }
        val rounded = dbSpl.toInt()
        return "~$rounded dB SPL — $label"
    }

    private suspend fun querySensorValue(sensorType: Int): Float? = withTimeoutOrNull(500L) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return@withTimeoutOrNull null
        val sensor = sensorManager.getDefaultSensor(sensorType) ?: return@withTimeoutOrNull null
        val deferred = CompletableDeferred<Float>()

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.values.isNotEmpty()) {
                    deferred.complete(event.values[0])
                }
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

        try {
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST)
            deferred.await()
        } finally {
            sensorManager.unregisterListener(listener)
        }
    }

    private fun getBatteryInfo(): String {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return "Unknown"
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val chargePct = if (level >= 0 && scale > 0) (level * 100f / scale).toInt() else -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return "Level: ${if (chargePct >= 0) "$chargePct% (${NumberUtils.toWords(chargePct)} percent)" else "Unknown"}, Charging: $isCharging"
    }

    private fun getBatteryTemperature(): String {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return "Unknown"
        val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        if (tempTenths == -1) return "Unknown"
        val tempC = Math.round(tempTenths / 10f)
        val tempF = Math.round(tempC * 9f / 5f + 32f)
        return "$tempC (${NumberUtils.toWords(tempC)})°C ($tempF (${NumberUtils.toWords(tempF)})°F)"
    }

    private fun formatTemperature(celsius: Float): String {
        val c = Math.round(celsius)
        val f = Math.round(celsius * 9f / 5f + 32f)
        return "$c (${NumberUtils.toWords(c)})°C ($f (${NumberUtils.toWords(f)})°F)"
    }

    private fun formatFloat(value: Float): String {
        val rounded = Math.round(value)
        return "$rounded (${NumberUtils.toWords(rounded)})"
    }

    private fun getThermalStatus(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Api29Helper.getThermalStatus(context)
        } else {
            "Not supported on Android version < 10"
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private object Api29Helper {
        fun getThermalStatus(context: Context): String {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return "Unknown"
            return when (powerManager.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE      -> "Normal (None)"
                PowerManager.THERMAL_STATUS_LIGHT     -> "Light Throttling"
                PowerManager.THERMAL_STATUS_MODERATE  -> "Moderate Throttling"
                PowerManager.THERMAL_STATUS_SEVERE    -> "Severe Throttling"
                PowerManager.THERMAL_STATUS_CRITICAL  -> "Critical Throttling"
                PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency Shutdown Warning"
                PowerManager.THERMAL_STATUS_SHUTDOWN  -> "Device Shutdown Imminent"
                else                                  -> "Unknown"
            }
        }
    }
}
