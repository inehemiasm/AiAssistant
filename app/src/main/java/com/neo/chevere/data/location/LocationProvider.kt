package com.neo.chevere.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Handles checking permissions and retrieving location on device.
 */
@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    /**
     * Checks if location permissions are granted.
     */
    fun isPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Attempts to get the last known location from GPS or Network provider.
     */
    fun getLastKnownLocation(): Location? {
        if (locationManager == null || !isPermissionGranted()) return null

        try {
            val gpsLocation = if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            } else null

            val networkLocation = if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } else null

            if (gpsLocation != null && networkLocation != null) {
                return if (gpsLocation.time > networkLocation.time) gpsLocation else networkLocation
            }
            return gpsLocation ?: networkLocation
        } catch (e: SecurityException) {
            return null
        }
    }

    /**
     * Suspends until a location update is retrieved or returns the last known location.
     */
    suspend fun getCurrentLocation(): Location? {
        if (locationManager == null || !isPermissionGranted()) return null

        val lastKnown = getLastKnownLocation()
        // If last known is fresh (less than 5 minutes old), use it
        if (lastKnown != null && (System.currentTimeMillis() - lastKnown.time) < 5 * 60 * 1000) {
            return lastKnown
        }

        return suspendCancellableCoroutine { continuation ->
            var resumed = false
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (!resumed) {
                        resumed = true
                        continuation.resume(location)
                        try {
                            locationManager.removeUpdates(this)
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
                }

                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            try {
                val provider = when {
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                    else -> null
                }

                if (provider != null) {
                    locationManager.requestSingleUpdate(provider, listener, context.mainLooper)
                } else {
                    continuation.resume(lastKnown)
                    resumed = true
                }
            } catch (e: SecurityException) {
                continuation.resume(null)
                resumed = true
            }

            continuation.invokeOnCancellation {
                try {
                    locationManager.removeUpdates(listener)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }
}
