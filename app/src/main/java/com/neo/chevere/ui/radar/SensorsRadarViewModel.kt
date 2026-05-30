package com.neo.chevere.ui.radar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Which subset of sensors to activate for this screen session.
 * Opening in a focused mode registers only the required hardware listener,
 * reducing CPU load and preventing device heating.
 */
enum class SensorMode(val key: String) {
    /** All sensors — full radar dashboard. */
    ALL("all"),
    /** Magnetometer only — stud finder / metal detector. */
    STUD("stud"),
    /** Accelerometer only — spirit level / bubble level. */
    LEVEL("level"),
    /** Ambient light sensor only. */
    LIGHT("light"),
    /** Proximity sensor only. */
    PROXIMITY("proximity"),
    /** Sound level sensor (decibel meter). */
    SOUND("sound");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: ALL
    }
}

data class RadarUiState(
    val mode: SensorMode = SensorMode.ALL,
    val magneticMagnitude: Float = 0f,
    val magneticCalibrated: Float = 0f,
    val baseline: Float = 0f,
    val magneticHistory: List<Float> = emptyList(),
    val lightLevel: Float = -1f,
    val proximityDistance: Float = -1f,
    val proximityNear: Boolean = false,
    val pitch: Float = 0f,
    val roll: Float = 0f,
    val audioEnabled: Boolean = false,
    val vibrationEnabled: Boolean = false,
    val statusLabel: String = "INITIALIZING...",
    val soundDbSpl: Float = 0f,
    val soundHistory: List<Float> = emptyList(),
    val micPermissionGranted: Boolean = true
)

@HiltViewModel
class SensorsRadarViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val mode: SensorMode = SensorMode.from(savedStateHandle["mode"])

    private val _uiState = MutableStateFlow(RadarUiState(mode = mode))
    val uiState: StateFlow<RadarUiState> = _uiState.asStateFlow()

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private var toneGenerator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60)
    } catch (e: Exception) {
        null
    }

    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var audioJob: Job? = null
    private var vibrationJob: Job? = null

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                    val calibrated = abs(magnitude - _uiState.value.baseline)
                    _uiState.update { state ->
                        val history = (state.magneticHistory + calibrated).takeLast(60)
                        val status = when {
                            calibrated > 250f -> "TARGET ACQUIRED: CRITICAL DETECT"
                            calibrated > 100f -> "TARGET ACQUIRED: STRONG FIELD"
                            calibrated > 35f  -> "TARGET ACQUIRED: WEAK DETECT"
                            calibrated > 10f  -> "SIGNAL DETECTED"
                            else              -> "SCANNING FOR STUDS / METALS..."
                        }
                        state.copy(
                            magneticMagnitude = magnitude,
                            magneticCalibrated = calibrated,
                            magneticHistory = history,
                            statusLabel = status
                        )
                    }
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    val pitch = Math.toDegrees(atan2(-x.toDouble(), sqrt((y * y + z * z).toDouble()))).toFloat()
                    val roll = Math.toDegrees(atan2(y.toDouble(), z.toDouble())).toFloat()
                    _uiState.update { state ->
                        val status = if (abs(pitch) < 2.0f && abs(roll) < 2.0f) "LEVEL ✓" else
                            "TILT  P:${String.format("%.1f", pitch)}°  R:${String.format("%.1f", roll)}°"
                        state.copy(pitch = pitch, roll = roll, statusLabel = status)
                    }
                }
                Sensor.TYPE_LIGHT -> {
                    val light = event.values[0]
                    _uiState.update { state ->
                        val label = when {
                            light < 10f   -> "DARK (${light.toInt()} lx)"
                            light < 200f  -> "DIM (${light.toInt()} lx)"
                            light < 1000f -> "NORMAL (${light.toInt()} lx)"
                            else          -> "BRIGHT (${light.toInt()} lx)"
                        }
                        state.copy(lightLevel = light, statusLabel = label)
                    }
                }
                Sensor.TYPE_PROXIMITY -> {
                    val proximity = event.values[0]
                    val maxRange = event.sensor.maximumRange
                    val near = proximity < min(5f, maxRange)
                    _uiState.update { state ->
                        state.copy(
                            proximityDistance = proximity,
                            proximityNear = near,
                            statusLabel = if (near) "OBJECT DETECTED NEARBY" else "PATH CLEAR"
                        )
                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    init {
        _uiState.update { it.copy(statusLabel = initialStatusLabel(mode)) }
        val hasMic = checkMicPermission()
        _uiState.update { it.copy(micPermissionGranted = hasMic) }
        registerSensors()
        if (mode == SensorMode.ALL || mode == SensorMode.STUD) {
            startAudioFeedbackLoop()
            startVibrationFeedbackLoop()
        }
        if (mode == SensorMode.SOUND && hasMic) {
            startSoundMonitoring()
        }
    }

    private fun initialStatusLabel(mode: SensorMode) = when (mode) {
        SensorMode.ALL      -> "SCANNING FOR STUDS / METALS..."
        SensorMode.STUD     -> "SCANNING FOR STUDS / METALS..."
        SensorMode.LEVEL    -> "READING ORIENTATION..."
        SensorMode.LIGHT    -> "READING AMBIENT LIGHT..."
        SensorMode.PROXIMITY -> "READING PROXIMITY..."
        SensorMode.SOUND    -> "MONITORING AMBIENT NOISE..."
    }

    fun calibrate() {
        val currentMag = _uiState.value.magneticMagnitude
        _uiState.update { state ->
            state.copy(
                baseline = currentMag,
                magneticCalibrated = 0f,
                magneticHistory = emptyList(),
                statusLabel = "CALIBRATED TO ${String.format(java.util.Locale.US, "%.1f", currentMag)} uT"
            )
        }
    }

    fun resetCalibration() {
        _uiState.update { state ->
            state.copy(
                baseline = 0f,
                magneticCalibrated = state.magneticMagnitude,
                statusLabel = "CALIBRATION RESET"
            )
        }
    }

    fun toggleAudio(enabled: Boolean) {
        _uiState.update { it.copy(audioEnabled = enabled) }
    }

    fun toggleVibration(enabled: Boolean) {
        _uiState.update { it.copy(vibrationEnabled = enabled) }
    }

    private fun registerSensors() {
        val manager = sensorManager ?: return
        when (mode) {
            SensorMode.ALL -> {
                manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
                    ?.let { manager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI) }
                manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                    ?.let { manager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI) }
                manager.getDefaultSensor(Sensor.TYPE_LIGHT)
                    ?.let { manager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI) }
                manager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
                    ?.let { manager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI) }
            }
            SensorMode.STUD -> {
                manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
                    ?.let { manager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_FASTEST) }
            }
            SensorMode.LEVEL -> {
                manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                    ?.let { manager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME) }
            }
            SensorMode.LIGHT -> {
                manager.getDefaultSensor(Sensor.TYPE_LIGHT)
                    ?.let { manager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL) }
            }
            SensorMode.PROXIMITY -> {
                manager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
                    ?.let { manager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL) }
            }
            SensorMode.SOUND -> {
                // Sound mode uses MediaRecorder inside startSoundMonitoring, no standard Android Sensors are registered
            }
        }
    }

    private fun unregisterSensors() {
        sensorManager?.unregisterListener(sensorListener)
    }

    private fun startAudioFeedbackLoop() {
        audioJob?.cancel()
        audioJob = viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                val state = _uiState.value
                if (state.audioEnabled && state.magneticCalibrated > 12f) {
                    val strength = state.magneticCalibrated
                    val delayTime = max(60L, (1000L - (strength * 4.5f)).toLong())
                    val toneType = if (strength > 120f) ToneGenerator.TONE_CDMA_PIP else ToneGenerator.TONE_PROP_BEEP
                    try { toneGenerator?.startTone(toneType, 30) } catch (_: Exception) {}
                    delay(delayTime)
                } else {
                    delay(200L)
                }
            }
        }
    }

    private fun startVibrationFeedbackLoop() {
        vibrationJob?.cancel()
        vibrationJob = viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                val state = _uiState.value
                if (state.vibrationEnabled && state.magneticCalibrated > 35f) {
                    val delayTime = max(100L, (900L - (_uiState.value.magneticCalibrated * 4f)).toLong())
                    vibrate(30)
                    delay(delayTime)
                } else {
                    delay(200L)
                }
            }
        }
    }

    private fun vibrate(durationMs: Long) {
        try {
            val activeVibrator = vibrator ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activeVibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                activeVibrator.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }

    private fun checkMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    private var soundJob: Job? = null
    private var mediaRecorder: MediaRecorder? = null
    private var soundRecordFile: java.io.File? = null

    fun onMicPermissionGranted() {
        _uiState.update { it.copy(micPermissionGranted = true) }
        if (mode == SensorMode.SOUND) {
            startSoundMonitoring()
        }
    }

    private fun startSoundMonitoring() {
        soundJob?.cancel()
        releaseAudioRecorder()

        if (!checkMicPermission()) {
            _uiState.update { it.copy(micPermissionGranted = false) }
            return
        }

        soundJob = viewModelScope.launch(Dispatchers.Default) {
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
                
                val tmpFile = java.io.File(context.cacheDir, "sound_radar_probe.3gp")
                soundRecordFile = tmpFile
                recorder.setOutputFile(tmpFile.absolutePath)
                
                recorder.prepare()
                recorder.start()
                mediaRecorder = recorder

                _uiState.update { it.copy(statusLabel = "MONITORING AMBIENT NOISE...") }

                while (true) {
                    delay(150L)
                    val amplitude = recorder.maxAmplitude
                    val dbSpl = formatDbSpl(amplitude)
                    _uiState.update { state ->
                        val history = (state.soundHistory + dbSpl).takeLast(60)
                        val label = when {
                            dbSpl < 30f  -> "VERY QUIET (${dbSpl.toInt()} dB)"
                            dbSpl < 45f  -> "QUIET (${dbSpl.toInt()} dB)"
                            dbSpl < 60f  -> "MODERATE NOISE (${dbSpl.toInt()} dB)"
                            dbSpl < 75f  -> "LOUD (${dbSpl.toInt()} dB)"
                            dbSpl < 90f  -> "VERY LOUD (${dbSpl.toInt()} dB)"
                            else         -> "HEARING RISK (${dbSpl.toInt()} dB)"
                        }
                        state.copy(
                            soundDbSpl = dbSpl,
                            soundHistory = history,
                            statusLabel = label
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(statusLabel = "SOUND PROBE ERROR: ${e.message}") }
            } finally {
                runCatching { recorder.stop() }
                runCatching { recorder.release() }
                soundRecordFile?.delete()
                soundRecordFile = null
                if (mediaRecorder == recorder) {
                    mediaRecorder = null
                }
            }
        }
    }

    private fun formatDbSpl(amplitude: Int): Float {
        if (amplitude <= 0) return 0f
        val dbFs = 20.0 * kotlin.math.log10(amplitude / 32767.0)
        val dbSpl = (dbFs + 90.0).coerceIn(0.0, 120.0)
        return dbSpl.toFloat()
    }

    private fun releaseAudioRecorder() {
        soundJob?.cancel()
        soundJob = null
        val recorder = mediaRecorder
        if (recorder != null) {
            try {
                recorder.stop()
            } catch (_: Exception) {}
            try {
                recorder.release()
            } catch (_: Exception) {}
            mediaRecorder = null
        }
        soundRecordFile?.delete()
        soundRecordFile = null
    }

    override fun onCleared() {
        super.onCleared()
        unregisterSensors()
        releaseAudioRecorder()
        audioJob?.cancel()
        vibrationJob?.cancel()
        try { toneGenerator?.release() } catch (_: Exception) {}
    }
}
