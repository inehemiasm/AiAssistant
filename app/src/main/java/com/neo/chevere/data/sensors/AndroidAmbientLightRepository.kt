package com.neo.chevere.data.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.neo.chevere.domain.AmbientLightReading
import com.neo.chevere.domain.AmbientLightRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Reads illuminance only, without microphone access or unrelated sensor sampling. */
@Singleton
class AndroidAmbientLightRepository @Inject constructor(@ApplicationContext context: Context) : AmbientLightRepository {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    override suspend fun readLight(): AmbientLightReading {
        val sensorManager = manager ?: return AmbientLightReading.Unsupported
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) ?: return AmbientLightReading.Unsupported
        val reading = CompletableDeferred<Float>()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                event.values.firstOrNull()?.takeIf { it.isFinite() && it >= 0 }?.let { reading.complete(it) }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        return try {
            if (!sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)) {
                AmbientLightReading.Unavailable
            } else {
                withTimeoutOrNull(1_000L) { reading.await() }?.let { AmbientLightReading.Available(it) }
                    ?: AmbientLightReading.Unavailable
            }
        } catch (_: SecurityException) {
            AmbientLightReading.Unavailable
        } finally {
            sensorManager.unregisterListener(listener)
            reading.cancel()
        }
    }
}
