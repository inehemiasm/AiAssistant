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
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.log10
import kotlin.math.sqrt

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
 * - Gyroscope (rotation rate on X/Y/Z axes in rad/s, angular magnitude, motion classification)
 * - Accelerometer (acceleration force on X/Y/Z axes in m/s², magnitude, motion classification)
 * - Compass Heading (azimuth angle in degrees and cardinal direction computed from accelerometer + magnetometer)
 * - Proximity Sensor (near/far status in cm)
 * - Device Posture / Placement (Face Up, Face Down, Portrait, Landscape, etc.)
 * - Spirit Level (Flatness tilt measurements in degrees)
 * - Metal / Magnetic Detection (magnetic field strength in uT and proximity to metal/magnets)
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
          Japanese: 部屋 of 温度はどれくらい, 暑いですか, 寒いですか.

        BATTERY / CHARGING / DEVICE STATUS:
          battery level, how's my battery, is my phone charging, battery percentage, charge status.

        PRESSURE / THERMALS / HARDWARE:
          atmospheric pressure, CPU temperature, device thermals, is the CPU throttling.

        GYROSCOPE / MOTION / STABILITY:
          Trigger phrases (English): gyroscope, am I walking, am I moving, is the phone still, is it shaking,
          check movement, device stability, orientation, rotation rate, are we moving.
          Spanish: giroscopio, ¿me estoy moviendo?, ¿estoy caminando?, ¿se mueve el teléfono?, ¿está quieto?, ¿está vibrando?, estabilidad, rotación.
          French: gyroscope, est-ce que je bouge, le téléphone bouge-t-il, est-il immobile, stabilité.
          Portuguese: giroscópio, estou me movendo, o celular está parado, estabilidade, rotação.
          German: Gyroskop, bewege ich mich, bewegt sich das Telefon, ist es stabil, Drehung.
          Japanese: ジャイロスコープ, 動いていますか, 歩いていますか, 揺れていますか, 回転, 安定性.

        COMPASS / DIRECTION / HEADING:
          Trigger phrases (English): compass, which way is north, what direction, am I facing east, azimuth, cardinal direction, heading, where is south, where is west.
          Spanish: brújula, ¿hacia dónde está el norte?, ¿qué dirección?, azimut, rumbo, dirección cardinal.
          French: boussole, où est le nord, quelle direction, cap, azimut.
          Portuguese: bússola, onde fica o norte, qual direção, rumo, azimute.
          German: Kompass, wo ist Norden, Himmelsrichtung, Ausrichtung, Azimut.
          Japanese: コンパス, 北はどっち, 方角, 方位, 東を向いていますか, 西はどちらですか.

        PROXIMITY / POCKET / TABLE DETECTION:
          Trigger phrases (English): proximity, is the phone in my pocket, is it face down, check proximity, pocket detection, is something close to the screen, is the phone covered.
          Spanish: proximidad, ¿está el teléfono en mi bolsillo?, ¿está boca abajo?, detector de bolsillo, sensor de proximidad.
          French: proximité, le téléphone est-il dans ma poche, est-il face contre terre, capteur de proximité.
          Portuguese: proximidade, o celular está no meu bolso, está virado para baixo, sensor de proximidade.
          German: Näherungssensor, ist das Handy in meiner Tasche, liegt es auf dem Display, Näherung.
          Japanese: 近接センサー, ポケットに入っていますか, うつ伏せですか, 近接.

        SPIRIT LEVEL / FLATNESS / SURFACE LEVEL:
          Trigger phrases (English): is this table flat, is the surface level, spirit level, bubble level, check flatness, is it straight, is the floor level, is it horizontal, is it plumb, level test.
          Spanish: ¿esta mesa está nivelada?, ¿está plano?, nivel de burbuja, ¿está recto?, ¿esta superficie es plana?.
          French: est-ce que cette table est plate, niveau à bulle, est-ce droit, surface plane.
          Portuguese: esta mesa está plana, nível de bolha, está reto, superfície plana.
          German: ist dieser Tisch gerade, Wasserwaage, ist die Oberfläche eben, gerade ausrichten.
          Japanese: このテーブルは水平ですか, 平らですか, 水準器, 水平器.

        METAL / MAGNET DETECTOR:
          Trigger phrases (English): metal detector, stud finder, is there metal nearby, magnetic detector, check for magnets, detect metal, is there a magnetic field, find studs, metal finder.
          Spanish: detector de metales, buscar metales, buscar imanes, detector magnético, buscador de vigas.
          French: détecteur de métaux, y a-t-il du métal, détecteur magnétique, chercheur de montants.
          Portuguese: detector de metal, encontrar metais, detector magnético, localizador de vigas.
          German: Metalldetektor, Metall finden, Magnetdetektor, Balkenfinder.
          Japanese: 金属探知機, 磁石を検出しますか, 金属探知, 下地探し.
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

        // Read advanced sensors (Gyroscope, Accelerometer, Magnetometer, Proximity)
        val gyroValues   = queryMultiAxisSensorValues(Sensor.TYPE_GYROSCOPE)
        val accelValues  = queryMultiAxisSensorValues(Sensor.TYPE_ACCELEROMETER)
        val magValues    = queryMultiAxisSensorValues(Sensor.TYPE_MAGNETIC_FIELD)
        val proximityVal = querySensorValue(Sensor.TYPE_PROXIMITY)

        val gyroReport   = formatGyroscope(gyroValues)
        val accelReport  = formatAccelerometer(accelValues)
        val compassReport = formatCompass(accelValues, magValues)
        val proximityReport = formatProximity(proximityVal)

        // Everyday helper reports
        val postureReport = formatPosture(accelValues)
        val flatnessReport = formatFlatness(accelValues)
        val metalReport = formatMetalDetector(magValues)

        val report = buildString {
            appendLine("Device Sensor Report:")
            val ambientReport = if (ambientTemp != null) {
                formatTemperature(ambientTemp)
            } else {
                "Not available (this device has no room temperature sensor)"
            }
            appendLine("- Ambient Room Temperature: $ambientReport")
            appendLine("- Ambient Light: ${lightValue?.let { "${formatFloat(it)} lux" } ?: "Not available or timeout"}")
            appendLine("- Atmospheric Pressure: ${pressureValue?.let { "${formatFloat(it)} hectopascals" } ?: "Not available or timeout"}")
            appendLine("- Battery Status: $batteryInfo")
            appendLine("- Battery Heat Level (Internal Device Temperature only): $batteryTemp")
            appendLine("- CPU Thermals: $thermalStatus")
            appendLine("- Ambient Sound Level: $soundResult")
            appendLine("- Gyroscope (Rotation): $gyroReport")
            appendLine("- Accelerometer (Motion): $accelReport")
            appendLine("- Compass Heading: $compassReport")
            appendLine("- Proximity Sensor: $proximityReport")
            appendLine("- Device Posture / Placement: $postureReport")
            appendLine("- Spirit Level (Flatness): $flatnessReport")
            appendLine("- Metal / Magnetic Detection: $metalReport")
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
        if (amplitude <= 0) return "Inaudible or Silence"
        val dbFs  = 20.0 * log10(amplitude / 32767.0)   // negative; 0 = full scale
        val dbSpl = (dbFs + 90.0).coerceIn(0.0, 120.0)  // rough SPL offset
        val label = when {
            dbSpl < 30  -> "Very quiet, near silence"
            dbSpl < 45  -> "Quiet, library level"
            dbSpl < 60  -> "Moderate noise, normal conversation"
            dbSpl < 75  -> "Loud, busy office or traffic"
            dbSpl < 90  -> "Very loud, heavy machinery or concert"
            else        -> "Extremely loud, potential hearing risk"
        }
        val rounded = dbSpl.toInt()
        val words = NumberUtils.toWords(rounded)
        return "around $words decibels SPL — $label"
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
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            deferred.await()
        } finally {
            sensorManager.unregisterListener(listener)
        }
    }

    private suspend fun queryMultiAxisSensorValues(sensorType: Int): FloatArray? = withTimeoutOrNull(500L) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return@withTimeoutOrNull null
        val sensor = sensorManager.getDefaultSensor(sensorType) ?: return@withTimeoutOrNull null
        val deferred = CompletableDeferred<FloatArray>()

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.values.isNotEmpty()) {
                    deferred.complete(event.values.clone())
                }
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

        try {
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            deferred.await()
        } finally {
            sensorManager.unregisterListener(listener)
        }
    }

    private fun formatGyroscope(values: FloatArray?): String {
        if (values == null || values.size < 3) return "Not available or timeout"
        val x = values[0]
        val y = values[1]
        val z = values[2]
        val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        val label = when {
            magnitude < 0.05f -> "Still, resting"
            magnitude < 0.3f  -> "Stable, slight tilt or rotation"
            magnitude < 1.5f  -> "Rotating, normal motion"
            else              -> "Rapid rotation or Shaking"
        }
        return "X: ${formatDecimal(x)}, Y: ${formatDecimal(y)}, Z: ${formatDecimal(z)} radians per second, with a magnitude of ${formatDecimal(magnitude)} radians per second — $label"
    }

    private fun formatAccelerometer(values: FloatArray?): String {
        if (values == null || values.size < 3) return "Not available or timeout"
        val x = values[0]
        val y = values[1]
        val z = values[2]
        val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        val deviation = abs(magnitude - 9.80665f)
        val label = when {
            deviation < 0.2f -> "Stationary"
            deviation < 1.5f -> "Held in hand or gentle motion"
            deviation < 5.0f -> "Walking or active movement"
            else             -> "Shaking or running or high acceleration"
        }
        return "X: ${formatDecimal(x)}, Y: ${formatDecimal(y)}, Z: ${formatDecimal(z)} meters per second squared, with a magnitude of ${formatDecimal(magnitude)} meters per second squared — $label"
    }

    private fun formatCompass(accel: FloatArray?, mag: FloatArray?): String {
        if (accel == null || mag == null || accel.size < 3 || mag.size < 3) {
            return "Not available (requires both accelerometer and magnetometer)"
        }
        val r = FloatArray(9)
        val i = FloatArray(9)
        val success = SensorManager.getRotationMatrix(r, i, accel, mag)
        return if (success) {
            val orientation = FloatArray(3)
            SensorManager.getOrientation(r, orientation)
            val azimuthRad = orientation[0]
            var azimuthDeg = Math.toDegrees(azimuthRad.toDouble()).toFloat()
            if (azimuthDeg < 0) {
                azimuthDeg += 360f
            }
            val direction = getCardinalDirection(azimuthDeg)
            val magStrength = sqrt((mag[0] * mag[0] + mag[1] * mag[1] + mag[2] * mag[2]).toDouble()).toFloat()
            val azimuthInt = Math.round(azimuthDeg)
            val azimuthWords = NumberUtils.toWords(azimuthInt)
            "$azimuthWords degrees, facing $direction, with a magnetic strength of ${formatDecimal(magStrength)} microtesla"
        } else {
            "Unable to compute orientation"
        }
    }

    private fun formatProximity(value: Float?): String {
        if (value == null) return "Not available or timeout"
        val label = if (value < 1.0f) "Near, object is close to the screen" else "Far"
        return "${formatDecimal(value)} centimeters — $label"
    }

    private fun formatPosture(values: FloatArray?): String {
        if (values == null || values.size < 3) return "Unknown, no accelerometer data"
        val x = values[0]
        val y = values[1]
        val z = values[2]
        return when {
            z > 8.5f  -> "Face Up, lying flat on its back"
            z < -8.5f -> "Face Down, lying flat on its screen"
            y > 8.5f  -> "Portrait Upright"
            y < -8.5f -> "Portrait Upside Down"
            x > 8.5f  -> "Landscape Left"
            x < -8.5f -> "Landscape Right"
            else      -> "Tilted or Diagonal Orientation"
        }
    }

    private fun formatFlatness(values: FloatArray?): String {
        if (values == null || values.size < 3) return "Not available or timeout"
        val x = values[0]
        val y = values[1]
        val z = values[2]
        val pitch = Math.toDegrees(atan2(-x.toDouble(), sqrt((y * y + z * z).toDouble()))).toFloat()
        val roll = Math.toDegrees(atan2(y.toDouble(), z.toDouble())).toFloat()
        val isFlat = abs(pitch) < 2.0f && abs(roll) < 2.0f
        val flatnessLabel = if (isFlat) "Level, flat surface detected" else "Tilted or Not flat"
        return "$flatnessLabel — Pitch forward or back tilt: ${formatDecimal(pitch)} degrees, Roll left or right tilt: ${formatDecimal(roll)} degrees"
    }

    private fun formatMetalDetector(values: FloatArray?): String {
        if (values == null || values.size < 3) return "Not available or timeout"
        val x = values[0]
        val y = values[1]
        val z = values[2]
        val magStrength = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        val magnetStatus = when {
            magStrength > 300f -> "Critical magnetic interference, strong magnetic source or magnet or metal extremely close"
            magStrength > 150f -> "High magnetic interference, possible metal object or magnetic field nearby"
            magStrength < 15f  -> "Low magnetic field, shielded or sensor anomaly"
            else               -> "Normal magnetic field, no major metal or magnet nearby"
        }
        return "Strength is ${formatDecimal(magStrength)} microtesla — $magnetStatus"
    }

    private fun getCardinalDirection(degrees: Float): String {
        return when {
            degrees >= 337.5 || degrees < 22.5 -> "North"
            degrees >= 22.5 && degrees < 67.5 -> "North-East"
            degrees >= 67.5 && degrees < 112.5 -> "East"
            degrees >= 112.5 && degrees < 157.5 -> "South-East"
            degrees >= 157.5 && degrees < 202.5 -> "South"
            degrees >= 202.5 && degrees < 247.5 -> "South-West"
            degrees >= 247.5 && degrees < 292.5 -> "West"
            degrees >= 292.5 && degrees < 337.5 -> "North-West"
            else -> "Unknown"
        }
    }

    private fun getBatteryInfo(): String {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return "Unknown"
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val chargePct = if (level >= 0 && scale > 0) (level * 100f / scale).toInt() else -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val levelStr = if (chargePct >= 0) {
            val words = NumberUtils.toWords(chargePct)
            "$words percent"
        } else "Unknown"
        return "Level: $levelStr, Charging: $isCharging"
    }

    private fun getBatteryTemperature(): String {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return "Unknown"
        val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        if (tempTenths == -1) return "Unknown"
        val tempC = Math.round(tempTenths / 10f)
        val tempF = Math.round(tempC * 9f / 5f + 32f)
        val cWords = NumberUtils.toWords(tempC)
        val fWords = NumberUtils.toWords(tempF)
        return "$cWords degrees Celsius, which is $fWords degrees Fahrenheit"
    }

    private fun formatTemperature(celsius: Float): String {
        val c = Math.round(celsius)
        val f = Math.round(celsius * 9f / 5f + 32f)
        val cWords = NumberUtils.toWords(c)
        val fWords = NumberUtils.toWords(f)
        return "$cWords degrees Celsius, which is $fWords degrees Fahrenheit"
    }

    private fun formatFloat(value: Float): String {
        val rounded = Math.round(value)
        return NumberUtils.toWords(rounded)
    }

    private fun floatToWords(value: Float): String {
        if (value.isNaN() || value.isInfinite()) return "unknown"
        val absoluteValue = abs(value)
        val rounded = String.format(java.util.Locale.US, "%.2f", absoluteValue)
        val parts = rounded.split(".")
        val integerPart = parts[0].toIntOrNull() ?: 0
        val fractionPart = parts.getOrNull(1) ?: "00"

        val integerWords = NumberUtils.toWords(integerPart)
        val fractionWords = fractionPart.map { digit ->
            when (digit) {
                '0' -> "zero"
                '1' -> "one"
                '2' -> "two"
                '3' -> "three"
                '4' -> "four"
                '5' -> "five"
                '6' -> "six"
                '7' -> "seven"
                '8' -> "eight"
                '9' -> "nine"
                else -> ""
            }
        }.joinToString(" ")

        val sign = if (value < 0) "minus " else ""
        return "$sign$integerWords point $fractionWords"
    }

    private fun formatDecimal(value: Float): String {
        return floatToWords(value)
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
                PowerManager.THERMAL_STATUS_NONE      -> "Normal"
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
