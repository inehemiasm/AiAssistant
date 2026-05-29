package com.neo.chevere.ui.radar

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
    PROXIMITY("proximity");

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
    val statusLabel: String = "INITIALIZING..."
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
        registerSensors()
        if (mode == SensorMode.ALL || mode == SensorMode.STUD) {
            startAudioFeedbackLoop()
            startVibrationFeedbackLoop()
        }
    }

    private fun initialStatusLabel(mode: SensorMode) = when (mode) {
        SensorMode.ALL      -> "SCANNING FOR STUDS / METALS..."
        SensorMode.STUD     -> "SCANNING FOR STUDS / METALS..."
        SensorMode.LEVEL    -> "READING ORIENTATION..."
        SensorMode.LIGHT    -> "READING AMBIENT LIGHT..."
        SensorMode.PROXIMITY -> "READING PROXIMITY..."
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

    override fun onCleared() {
        super.onCleared()
        unregisterSensors()
        audioJob?.cancel()
        vibrationJob?.cancel()
        try { toneGenerator?.release() } catch (_: Exception) {}
    }
}
