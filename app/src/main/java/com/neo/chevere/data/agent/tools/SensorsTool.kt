package com.neo.chevere.data.agent.tools

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.PowerManager
import com.neo.chevere.core.NumberUtils
import com.neo.chevere.data.agent.AgentTool
import com.neo.chevere.data.agent.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Reads local hardware and environment sensors, delivering ambient status to the agent.
 */
class SensorsTool @Inject constructor(
    @param:ApplicationContext private val context: Context
) : AgentTool {
    override val name: String = "read_sensors"
    override val description: String =
        "Queries device environment and hardware sensors to check how hot/cold the room is or how bright it is. Includes ambient room temperature, device internal temperature, ambient light level (lux) / brightness, atmospheric pressure (hPa), battery level, charging status, and CPU thermal throttling status."
    override val inputSchema: String = "None. Returns all active sensor and system status values."

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val lightValue = querySensorValue(Sensor.TYPE_LIGHT)
        val pressureValue = querySensorValue(Sensor.TYPE_PRESSURE)
        val ambientTemp = querySensorValue(Sensor.TYPE_AMBIENT_TEMPERATURE)
        val batteryInfo = getBatteryInfo()
        val batteryTemp = getBatteryTemperature()
        val thermalStatus = getThermalStatus()

        val report = buildString {
            appendLine("Device Sensor Report:")
            appendLine("- Ambient Room Temperature: ${ambientTemp?.let { formatTemperature(it) } ?: "No hardware sensor"}")
            appendLine("- Ambient Light: ${lightValue?.let { "${formatFloat(it)} lux" } ?: "Not available or timeout"}")
            appendLine("- Atmospheric Pressure: ${pressureValue?.let { "${formatFloat(it)} hPa" } ?: "Not available or timeout"}")
            appendLine("- Battery Status: $batteryInfo")
            appendLine("- Device Internal Temperature: $batteryTemp")
            appendLine("- CPU Thermals: $thermalStatus")
        }

        ToolResult.Success(report)
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
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            Api29Helper.getThermalStatus(context)
        } else {
            "Not supported on Android version < 10"
        }
    }

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.Q)
    private object Api29Helper {
        fun getThermalStatus(context: Context): String {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return "Unknown"
            return when (powerManager.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "Normal (None)"
                PowerManager.THERMAL_STATUS_LIGHT -> "Light Throttling"
                PowerManager.THERMAL_STATUS_MODERATE -> "Moderate Throttling"
                PowerManager.THERMAL_STATUS_SEVERE -> "Severe Throttling"
                PowerManager.THERMAL_STATUS_CRITICAL -> "Critical Throttling"
                PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency Shutdown Warning"
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "Device Shutdown Imminent"
                else -> "Unknown"
            }
        }
    }
}
