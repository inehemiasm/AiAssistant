package com.neo.chevere.data.sensors

import android.content.*
import android.hardware.*
import androidx.test.core.app.ApplicationProvider
import com.neo.chevere.domain.AmbientLightReading
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AndroidAmbientLightRepositoryTest {
    private val manager = mock<SensorManager>()
    private fun repository(): AndroidAmbientLightRepository = AndroidAmbientLightRepository(
        object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun getSystemService(name: String): Any? = if (name == Context.SENSOR_SERVICE) manager else super.getSystemService(name)
        }
    )

    @Test fun missingSensorReturnsUnsupported() = runTest {
        assertEquals(AmbientLightReading.Unsupported, repository().readLight())
    }

    @Test fun actualLuxReadingIsReturnedAndListenerIsRemoved() = runTest {
        val sensor = mock<Sensor>()
        whenever(manager.getDefaultSensor(Sensor.TYPE_LIGHT)).thenReturn(sensor)
        doAnswer { invocation ->
            val event = SensorEvent::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType).apply { isAccessible = true }.newInstance(1)
            event.values[0] = 125.5f
            invocation.getArgument<SensorEventListener>(0).onSensorChanged(event)
            true
        }.whenever(manager).registerListener(any<SensorEventListener>(), eq(sensor), any())
        assertEquals(AmbientLightReading.Available(125.5f), repository().readLight())
        verify(manager).unregisterListener(any<SensorEventListener>())
    }

    @Test fun timeoutAndCancellationRemoveSensorListener() = runTest {
        whenever(manager.getDefaultSensor(Sensor.TYPE_LIGHT)).thenReturn(mock())
        whenever(manager.registerListener(any<SensorEventListener>(), any<Sensor>(), any())).thenReturn(true)
        assertEquals(AmbientLightReading.Unavailable, repository().readLight())
        val job = launch { repository().readLight() }
        testScheduler.runCurrent()
        job.cancel(); job.join()
        verify(manager, times(2)).unregisterListener(any<SensorEventListener>())
    }

    @Test fun summariesKeepZeroAndRejectInvalidReadings() {
        assertTrue(AmbientLightReading.Available(0f).toChatSummary().contains("0 lux"))
        for (value in listOf(-1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertFalse(AmbientLightReading.Available(value).toChatSummary().contains("lux"))
        }
        assertFalse(AmbientLightReading.Unavailable.toChatSummary().contains("image"))
    }
}
