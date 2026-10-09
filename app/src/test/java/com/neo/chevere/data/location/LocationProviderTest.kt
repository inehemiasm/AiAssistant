package com.neo.chevere.data.location

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.neo.chevere.core.DispatcherProvider
import kotlinx.coroutines.test.*
import kotlinx.coroutines.launch
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LocationProviderTest {
    private val manager = mock<LocationManager>()
    private var granted = true
    private var fine = false
    private lateinit var context: Context
    private lateinit var cache: RecentLocationCache

    @Before fun setup() {
        context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun getSystemService(name: String): Any? = if (name == Context.LOCATION_SERVICE) manager else super.getSystemService(name)
            override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
                if (granted && (permission == Manifest.permission.ACCESS_COARSE_LOCATION || fine))
                    PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
        }
        cache = RecentLocationCache(context)
        cache.clear()
    }
    @After fun cleanup() { cache.clear() }

    private fun fix(ageMinutes: Int = 0) = Location("test").apply {
        latitude = 40.5; longitude = -73.5
        time = System.currentTimeMillis() - ageMinutes * 60_000L
    }
    private fun provider(dispatcher: TestDispatcher) = LocationProvider(context, cache, object : DispatcherProvider {
        override val io = dispatcher
        override val main = dispatcher
        override val default = dispatcher
    })

    @Test fun recentFixSurvivesProviderRecreationAndDisabledServices() = runTest {
        cache.write(fix(10))
        val result = provider(StandardTestDispatcher(testScheduler)).resolveLocation() as LocationResolution.Available
        assertTrue(result.cached)
        assertEquals(40.5, result.location.latitude, 0.0)
        assertNotNull(RecentLocationCache(context).read())
    }

    @Test fun missingPermissionNeverUsesCacheAndClearsIt() = runTest {
        cache.write(fix())
        granted = false
        assertEquals(LocationResolution.PermissionRequired, provider(StandardTestDispatcher(testScheduler)).resolveLocation())
        assertNull(cache.read())
        verifyNoInteractions(manager)
    }

    @Test fun timeoutFallsBackAndRemovesListener() = runTest {
        cache.write(fix(10))
        whenever(manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)).thenReturn(true)
        val result = provider(StandardTestDispatcher(testScheduler)).resolveLocation() as LocationResolution.Available
        assertTrue(result.cached)
        assertTrue(testScheduler.currentTime >= 5_000)
        verify(manager).removeUpdates(any<LocationListener>())
    }

    @Test fun approximatePermissionCanAcquireAndRetainNetworkFix() = runTest {
        whenever(manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)).thenReturn(true)
        doAnswer { invocation ->
            invocation.getArgument<LocationListener>(3).onLocationChanged(fix())
            null
        }.whenever(manager).requestLocationUpdates(eq(LocationManager.NETWORK_PROVIDER), eq(0L), eq(0f), any<LocationListener>(), any<Looper>())
        val result = provider(StandardTestDispatcher(testScheduler)).resolveLocation() as LocationResolution.Available
        assertFalse(result.cached)
        assertNotNull(cache.read())
        verify(manager, never()).isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    @Test fun oldFutureInvalidAndCorruptCacheCannotSupplyCoordinates() = runTest {
        for (location in listOf(fix(31), fix(-1), fix().apply { latitude = 95.0 }, fix().apply { longitude = Double.NaN })) {
            cache.write(location)
            assertNull(cache.read())
        }
        java.io.File(context.noBackupFilesDir, "recent_weather_location.json").writeText("broken")
        assertEquals(LocationResolution.Unavailable, provider(StandardTestDispatcher(testScheduler)).resolveLocation())
    }

    @Test fun freshestSystemLocationIsCached() = runTest {
        whenever(manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)).thenReturn(fix(2))
        assertTrue((provider(StandardTestDispatcher(testScheduler)).resolveLocation() as LocationResolution.Available).cached)
        assertNotNull(cache.read())
    }
    @Test fun cancellationRemovesActiveListener() = runTest {
        whenever(manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)).thenReturn(true)
        val job = launch {
            provider(StandardTestDispatcher(testScheduler)).resolveLocation()
        }
        testScheduler.runCurrent()
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        verify(manager).removeUpdates(any<LocationListener>())
    }

    @Test fun retainedFixExpiresWithoutBeingRefreshedByReadingIt() {
        val location = fix()
        cache.write(location)
        assertNotNull(cache.read(location.time + 10 * 60_000))
        assertNull(cache.read(location.time + 31 * 60_000))
    }

    @Test fun gpsCanRecoverWhenNetworkAcquisitionFails() = runTest {
        fine = true
        whenever(manager.isProviderEnabled(any())).thenReturn(true)
        doThrow(IllegalArgumentException("network unavailable")).whenever(manager)
            .requestLocationUpdates(eq(LocationManager.NETWORK_PROVIDER), eq(0L), eq(0f), any<LocationListener>(), any<Looper>())
        doAnswer { invocation ->
            invocation.getArgument<LocationListener>(3).onLocationChanged(fix())
            null
        }.whenever(manager).requestLocationUpdates(eq(LocationManager.GPS_PROVIDER), eq(0L), eq(0f), any<LocationListener>(), any<Looper>())
        assertFalse((provider(StandardTestDispatcher(testScheduler)).resolveLocation() as LocationResolution.Available).cached)
        assertNotNull(cache.read())
    }

    @Test fun approximatePermissionCannotReusePreviouslyPreciseCache() = runTest {
        cache.write(fix(), precise = true)
        assertEquals(LocationResolution.Unavailable, provider(StandardTestDispatcher(testScheduler)).resolveLocation())
        verify(manager, never()).getLastKnownLocation(LocationManager.GPS_PROVIDER)
    }

}
