package com.neo.chevere.ui.radar

import com.neo.chevere.R
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
enum class SensorMode(val key: String, @androidx.annotation.StringRes val shortLabelRes: Int) {
    /** All sensors — full radar dashboard. */
    ALL("all", R.string.ui_all),
    /** Magnetometer only — stud finder / metal detector. */
    STUD("stud", R.string.ui_stud),
    /** Sound level sensor (decibel meter). */
    SOUND("sound", R.string.ui_sound),
    /** Accelerometer only — spirit level / bubble level. */
    LEVEL("level", R.string.ui_level_f481cc),
    /** Ambient light sensor only. */
    LIGHT("light", R.string.ui_light),
    /** Proximity sensor only. */
    PROXIMITY("proximity", R.string.ui_proximity_795c24);

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
    val statusLabel: String = "",
    val soundDbSpl: Float = 0f,
    val soundHistory: List<Float> = emptyList(),
    val micPermissionGranted: Boolean = true
)

@HiltViewModel
class SensorsRadarViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private var activeMode: SensorMode = SensorMode.from(savedStateHandle["mode"])
    private var isForeground: Boolean = true

    private val _uiState = MutableStateFlow(RadarUiState(mode = activeMode))
    val uiState: StateFlow<RadarUiState> = _uiState.asStateFlow()

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private var toneGenerator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60)
    } catch (_: Exception) {
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
    private var soundJob: Job? = null
    private var mediaRecorder: MediaRecorder? = null
    private var soundRecordFile: java.io.File? = null

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
                        val next = state.copy(
                            magneticMagnitude = magnitude,
                            magneticCalibrated = calibrated,
                            magneticHistory = history
                        )
                        next.copy(statusLabel = computeStatusLabel(next))
                    }
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    val rawPitch = Math.toDegrees(atan2(-x.toDouble(), sqrt((y * y + z * z).toDouble()))).toFloat()
                    val rawRoll = Math.toDegrees(atan2(y.toDouble(), z.toDouble())).toFloat()
                    _uiState.update { state ->
                        // Apply gentle low-pass smoothing to prevent high-frequency bubble jitter
                        val smoothedPitch = if (state.pitch == 0f) rawPitch else state.pitch * 0.75f + rawPitch * 0.25f
                        val smoothedRoll = if (state.roll == 0f) rawRoll else state.roll * 0.75f + rawRoll * 0.25f
                        val next = state.copy(pitch = smoothedPitch, roll = smoothedRoll)
                        next.copy(statusLabel = computeStatusLabel(next))
                    }
                }
                Sensor.TYPE_LIGHT -> {
                    val light = event.values[0]
                    _uiState.update { state ->
                        val next = state.copy(lightLevel = light)
                        next.copy(statusLabel = computeStatusLabel(next))
                    }
                }
                Sensor.TYPE_PROXIMITY -> {
                    val proximity = event.values[0]
                    val maxRange = event.sensor.maximumRange
                    val near = proximity < min(5f, maxRange)
                    _uiState.update { state ->
                        val next = state.copy(
                            proximityDistance = proximity,
                            proximityNear = near
                        )
                        next.copy(statusLabel = computeStatusLabel(next))
                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    init {
        val hasMic = checkMicPermission()
        _uiState.update { state ->
            val next = state.copy(mode = activeMode, micPermissionGranted = hasMic)
            next.copy(statusLabel = computeStatusLabel(next))
        }
        startActiveModePipelines()
    }

    /**
     * Switches the active sensor mode in-place and reconfigures hardware listeners
     * so only the needed sensors are active.
     */
    fun selectMode(newMode: SensorMode) {
        if (activeMode == newMode) return
        activeMode = newMode
        val hasMic = checkMicPermission()
        _uiState.update { state ->
            val next = state.copy(mode = newMode, micPermissionGranted = hasMic)
            next.copy(statusLabel = computeStatusLabel(next))
        }
        if (isForeground) {
            startActiveModePipelines()
        }
    }

    /** Resumes hardware sensor and microphone listeners when the screen enters the foreground. */
    fun onResume() {
        isForeground = true
        val hasMic = checkMicPermission()
        _uiState.update { state ->
            val next = state.copy(micPermissionGranted = hasMic)
            next.copy(statusLabel = computeStatusLabel(next))
        }
        startActiveModePipelines()
    }

    /** Pauses hardware sensor and microphone listeners when the screen leaves the foreground. */
    fun onPause() {
        isForeground = false
        stopAllPipelines()
    }

    private fun startActiveModePipelines() {
        unregisterSensors()
        audioJob?.cancel()
        vibrationJob?.cancel()
        registerSensors(activeMode)

        if (activeMode == SensorMode.ALL || activeMode == SensorMode.STUD) {
            startAudioFeedbackLoop()
            startVibrationFeedbackLoop()
        }

        if (activeMode == SensorMode.SOUND || activeMode == SensorMode.ALL) {
            if (checkMicPermission()) {
                startSoundMonitoring()
            } else {
                releaseAudioRecorder()
            }
        } else {
            releaseAudioRecorder()
        }
    }

    private fun stopAllPipelines() {
        unregisterSensors()
        releaseAudioRecorder()
        audioJob?.cancel()
        vibrationJob?.cancel()
    }

    /**
     * Computes a stable, non-flickering status banner label for the current mode.
     * In [SensorMode.ALL], priority alerts surface first without individual sensors
     * overwriting each other on every frame.
     */
    private fun computeStatusLabel(state: RadarUiState): String = when (state.mode) {
        SensorMode.ALL -> when {
            state.magneticCalibrated > 250f -> context.getString(R.string.ui_target_acquired_critical_field)
            state.magneticCalibrated > 100f -> context.getString(R.string.ui_target_acquired_strong_field)
            state.magneticCalibrated > 35f  -> context.getString(R.string.ui_magnetic_anomaly_detected)
            state.proximityNear             -> context.getString(R.string.ui_proximity_alert_object_nearby)
            state.soundDbSpl > 75f          -> context.getString(R.string.ui_noise_alert_loud_environment)
            else                            -> context.getString(R.string.ui_multi_sensor_telemetry_active)
        }
        SensorMode.STUD -> when {
            state.magneticCalibrated > 250f -> context.getString(R.string.ui_target_acquired_critical_detect)
            state.magneticCalibrated > 100f -> context.getString(R.string.ui_target_acquired_strong_field)
            state.magneticCalibrated > 35f  -> context.getString(R.string.ui_target_acquired_weak_detect)
            state.magneticCalibrated > 10f  -> context.getString(R.string.ui_signal_detected)
            else                            -> context.getString(R.string.ui_scanning_for_studs_metals)
        }
        SensorMode.LEVEL -> {
            if (abs(state.pitch) < 2.0f && abs(state.roll) < 2.0f) {
                context.getString(R.string.ui_surface_level)
            } else {
                context.getString(R.string.ui_surface_tilted)
            }
        }
        SensorMode.LIGHT -> when {
            state.lightLevel < 0f    -> context.getString(R.string.ui_reading_ambient_light)
            state.lightLevel < 10f   -> context.getString(R.string.ui_environment_dark)
            state.lightLevel < 200f  -> context.getString(R.string.ui_environment_dim)
            state.lightLevel < 1000f -> context.getString(R.string.ui_environment_normal_indoor)
            else                     -> context.getString(R.string.ui_environment_bright)
        }
        SensorMode.PROXIMITY -> {
            if (state.proximityNear) context.getString(R.string.ui_object_detected_nearby) else context.getString(R.string.ui_path_clear)
        }
        SensorMode.SOUND -> when {
            !state.micPermissionGranted -> context.getString(R.string.ui_microphone_permission_required)
            state.soundDbSpl < 30f      -> context.getString(R.string.ui_very_quiet_environment)
            state.soundDbSpl < 45f      -> context.getString(R.string.ui_quiet_library_level)
            state.soundDbSpl < 60f      -> context.getString(R.string.ui_moderate_ambient_noise)
            state.soundDbSpl < 75f      -> context.getString(R.string.ui_loud_environment)
            state.soundDbSpl < 90f      -> context.getString(R.string.ui_very_loud_environment)
            else                        -> context.getString(R.string.ui_extreme_noise_hearing_risk)
        }
    }

    fun calibrate() {
        val currentMag = _uiState.value.magneticMagnitude
        _uiState.update { state ->
            val next = state.copy(
                baseline = currentMag,
                magneticCalibrated = 0f,
                magneticHistory = emptyList()
            )
            next.copy(statusLabel = computeStatusLabel(next))
        }
    }

    fun resetCalibration() {
        _uiState.update { state ->
            val next = state.copy(
                baseline = 0f,
                magneticCalibrated = state.magneticMagnitude
            )
            next.copy(statusLabel = computeStatusLabel(next))
        }
    }

    fun toggleAudio(enabled: Boolean) {
        _uiState.update { it.copy(audioEnabled = enabled) }
    }

    fun toggleVibration(enabled: Boolean) {
        _uiState.update { it.copy(vibrationEnabled = enabled) }
    }

    private fun registerSensors(targetMode: SensorMode) {
        val manager = sensorManager ?: return
        when (targetMode) {
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
                // Sound mode uses MediaRecorder inside startSoundMonitoring
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

    fun onMicPermissionGranted() {
        _uiState.update { state ->
            val next = state.copy(micPermissionGranted = true)
            next.copy(statusLabel = computeStatusLabel(next))
        }
        if (isForeground && (activeMode == SensorMode.SOUND || activeMode == SensorMode.ALL)) {
            startSoundMonitoring()
        }
    }

    private fun startSoundMonitoring() {
        if (soundJob?.isActive == true && mediaRecorder != null) return
        soundJob?.cancel()
        releaseAudioRecorder()

        if (!checkMicPermission()) {
            _uiState.update { state ->
                val next = state.copy(micPermissionGranted = false)
                next.copy(statusLabel = computeStatusLabel(next))
            }
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
                recorder.maxAmplitude // Prime baseline so first sample window is accurate
                mediaRecorder = recorder

                while (true) {
                    delay(180L)
                    val amplitude = recorder.maxAmplitude
                    val rawDbSpl = formatDbSpl(amplitude)
                    _uiState.update { state ->
                        val smoothedDb = if (state.soundDbSpl == 0f) {
                            rawDbSpl
                        } else {
                            state.soundDbSpl * 0.65f + rawDbSpl * 0.35f
                        }
                        val history = (state.soundHistory + smoothedDb).takeLast(60)
                        val next = state.copy(
                            soundDbSpl = smoothedDb,
                            soundHistory = history
                        )
                        next.copy(statusLabel = computeStatusLabel(next))
                    }
                }
            } catch (e: Exception) {
                if (activeMode == SensorMode.SOUND) {
                    _uiState.update { it.copy(statusLabel = context.getString(R.string.ui_sound_probe_error_1_s, e.message)) }
                }
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
        stopAllPipelines()
        try { toneGenerator?.release() } catch (_: Exception) {}
    }
}
