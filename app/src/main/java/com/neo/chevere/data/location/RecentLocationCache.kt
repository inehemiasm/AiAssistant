package com.neo.chevere.data.location

import android.content.Context
import android.location.Location
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Keeps one recent fix in private, non-backed-up storage for transient acquisition failures. */
@Singleton
class RecentLocationCache @Inject constructor(@ApplicationContext context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "recent_weather_location.json"))

    @Serializable
    private data class FixDto(val latitude: Double, val longitude: Double, val time: Long, val precise: Boolean = true)

    @Synchronized
    fun read(now: Long = System.currentTimeMillis(), allowPrecise: Boolean = true): Location? = try {
        val fix = Json.decodeFromString<FixDto>(file.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
        if (fix.precise && !allowPrecise) null else Location("chevere-cache").apply {
            latitude = fix.latitude
            longitude = fix.longitude
            time = fix.time
        }.takeIf { it.isUsable(now) }
    } catch (_: Exception) { null }

    @Synchronized
    fun write(location: Location, now: Long = System.currentTimeMillis(), precise: Boolean = false) {
        if (!location.isUsable(now)) return
        var output: java.io.FileOutputStream? = null
        try {
            output = file.startWrite()
            output.write(Json.encodeToString(FixDto(location.latitude, location.longitude, location.time, precise)).toByteArray())
            file.finishWrite(output)
        } catch (_: Exception) {
            output?.let { file.failWrite(it) }
        }
    }

    /** Revoked permission invalidates our retained fix, including on the next lookup. */
    @Synchronized
    fun clear() { file.delete() }

    companion object {
        const val MAX_AGE_MS = 30 * 60 * 1000L
        internal fun Location.isUsable(now: Long): Boolean =
            latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 &&
                longitude in -180.0..180.0 && time > 0 && now >= time && now - time <= MAX_AGE_MS
    }
}
