package com.neo.chevere.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.neo.chevere.core.DispatcherProvider
import com.neo.chevere.data.location.RecentLocationCache.Companion.isUsable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Distinguishes denied access, fresh fixes, and explicitly qualified cached fallbacks. */
sealed interface LocationResolution {
    data class Available(val location: Location, val cached: Boolean) : LocationResolution
    data object PermissionRequired : LocationResolution
    data object Unavailable : LocationResolution
}

/** Acquires a bounded-time location fix and retains a recent fallback for local weather. */
@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cache: RecentLocationCache,
    private val dispatchers: DispatcherProvider
) {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    fun isPermissionGranted(): Boolean = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ||
        hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Never uses retained coordinates without current permission; old/future/invalid fixes are rejected. */
    suspend fun resolveLocation(): LocationResolution = withContext(dispatchers.io) {
        if (!isPermissionGranted()) {
            cache.clear()
            return@withContext LocationResolution.PermissionRequired
        }
        val now = System.currentTimeMillis()
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { provider ->
                (provider != LocationManager.GPS_PROVIDER || hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) &&
                    runCatching { manager?.isProviderEnabled(provider) == true }.getOrDefault(false)
            }
        val known = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { it != LocationManager.GPS_PROVIDER || hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) }
            .mapNotNull { provider -> runCatching { manager?.getLastKnownLocation(provider) }.getOrNull() }
            .filter { it.isUsable(now) }.maxByOrNull { it.time }
        val retained = cache.read(now, allowPrecise = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION))
        val fallback = listOfNotNull(known, retained).maxByOrNull { it.time }
        if (fallback != null && now - fallback.time <= FRESH_AGE_MS) {
            cache.write(fallback, now, precise = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION))
            return@withContext LocationResolution.Available(fallback, cached = true)
        }
        val fresh = if (providers.isEmpty()) null else
            withTimeoutOrNull(FIX_TIMEOUT_MS) { awaitFix(providers) }
        val validFresh = fresh?.takeIf { it.isUsable(System.currentTimeMillis()) }
        if (!isPermissionGranted()) {
            cache.clear()
            return@withContext LocationResolution.PermissionRequired
        }
        if (validFresh != null) {
            cache.write(validFresh, precise = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION))
            LocationResolution.Available(validFresh, cached = false)
        } else if (fallback != null && fallback.isUsable(System.currentTimeMillis())) {
            cache.write(fallback, precise = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION))
            LocationResolution.Available(fallback, cached = true)
        } else LocationResolution.Unavailable
    }

    private suspend fun awaitFix(providers: List<String>): Location? = suspendCancellableCoroutine { continuation ->
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (!location.isUsable(System.currentTimeMillis())) return
                runCatching { manager?.removeUpdates(this) }
                if (continuation.isActive) continuation.resume(location)
            }
            @Deprecated("Required for older Android location callbacks")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        continuation.invokeOnCancellation { runCatching { manager?.removeUpdates(listener) } }
        var registered = false
        for (provider in providers) {
            if (!continuation.isActive) break
            try {
                manager?.requestLocationUpdates(provider, 0L, 0f, listener, context.mainLooper)
                registered = true
            } catch (_: Exception) {
                // Another enabled provider may still return a usable fix.
            }
        }
        if (!registered && continuation.isActive) continuation.resume(null)
        if (!continuation.isActive) runCatching { manager?.removeUpdates(listener) }

    }

    companion object {
        private const val FRESH_AGE_MS = 5 * 60 * 1000L
        private const val FIX_TIMEOUT_MS = 5_000L
    }
}
