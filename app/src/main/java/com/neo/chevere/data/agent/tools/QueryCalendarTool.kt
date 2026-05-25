package com.neo.chevere.data.agent.tools

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.neo.chevere.data.agent.AgentTool
import com.neo.chevere.data.agent.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Searches local calendar events so the agent can report them to the user.
 */
class QueryCalendarTool @Inject constructor(
    @param:ApplicationContext private val context: Context
) : AgentTool {
    override val name: String = "query_calendar"
    override val description: String =
        "Queries local calendar events on the device for a given time frame. Use this to find scheduled meetings, appointments, or agenda details."
    override val inputSchema: String =
        "timeRangeDays: Number of days to search ahead (default 7). action: 'query' (default)."

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext ToolResult.Error("CALENDAR_PERMISSION_REQUIRED")
        }

        val timeRangeDays = args["timeRangeDays"]?.toIntOrNull()?.coerceIn(1, 30) ?: 7
        val events = fetchCalendarEvents(timeRangeDays)
        
        if (events.isEmpty()) {
            ToolResult.Success("No calendar events scheduled for the next $timeRangeDays days.")
        } else {
            val summary = events.joinToString("\n") { event ->
                "- ${event.title} at ${event.dateTime}${if (event.location.isNotBlank()) " (Location: ${event.location})" else ""}"
            }
            ToolResult.Success("Calendar events for the next $timeRangeDays days:\n$summary")
        }
    }

    private fun fetchCalendarEvents(days: Int): List<CalendarEvent> {
        val resolver = context.contentResolver
        val startMillis = System.currentTimeMillis()
        val endMillis = startMillis + (days * 24 * 60 * 60 * 1000L)

        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startMillis)
        ContentUris.appendId(builder, endMillis)

        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_LOCATION
        )

        val selection = "${CalendarContract.Instances.VISIBLE} = 1"
        val sortOrder = "${CalendarContract.Instances.BEGIN} ASC"

        val eventsList = mutableListOf<CalendarEvent>()
        val formatter = SimpleDateFormat("EEE, MMM d, h:mm a", Locale.getDefault())

        resolver.query(builder.build(), projection, selection, null, sortOrder)?.use { cursor ->
            val titleIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val beginIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val endIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val locIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)

            while (cursor.moveToNext() && eventsList.size < 20) {
                val title = cursor.getString(titleIdx).orEmpty().ifBlank { "Untitled Event" }
                val begin = cursor.getLong(beginIdx)
                val location = cursor.getString(locIdx).orEmpty()
                val dateTime = formatter.format(Date(begin))
                eventsList.add(CalendarEvent(title, begin, dateTime, location))
            }
        }

        return eventsList
    }

    private data class CalendarEvent(
        val title: String,
        val beginTime: Long,
        val dateTime: String,
        val location: String
    )
}
