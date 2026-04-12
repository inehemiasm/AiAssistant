package com.neo.aiassistant.data.agent.actions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AppActionExecutor"

/**
 * Interface for executing Android-specific app actions.
 */
interface AndroidAppActionExecutor {
    fun execute(request: AppActionRequest): AppActionResult
}

/**
 * Default implementation of [AndroidAppActionExecutor] using standard Android Intents and APIs.
 */
@Singleton
class DefaultAndroidAppActionExecutor @Inject constructor(
    @ApplicationContext private val context: Context
) : AndroidAppActionExecutor {

    override fun execute(request: AppActionRequest): AppActionResult {
        return try {
            when (request) {
                is CopyToClipboardRequest -> copyToClipboard(request.text)
                is ShareTextRequest -> shareText(request.text)
                is OpenUrlRequest -> openUrl(request.url)
                is OpenMapsRequest -> openMaps(request.query)
                is DraftEmailRequest -> draftEmail(request)
                is CreateCalendarEventRequest -> createCalendarEvent(request)
                is PickImageRequest -> AppActionResult.Error("Pick Image not implemented yet via Intent in this layer")
                else -> AppActionResult.Error("Unknown action request type")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing action: ${request::class.simpleName}", e)
            AppActionResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun copyToClipboard(text: String): AppActionResult {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Assistant Copy", text)
        clipboard.setPrimaryClip(clip)
        return AppActionResult.Success("Copied to clipboard")
    }

    private fun shareText(text: String): AppActionResult {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Share via").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        return AppActionResult.Success("Share sheet opened")
    }

    private fun openUrl(url: String): AppActionResult {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return AppActionResult.Success("URL opened in browser")
    }

    private fun openMaps(query: String): AppActionResult {
        val gmmIntentUri = Uri.parse("geo:0,0?q=${Uri.encode(query)}")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(mapIntent)
        return AppActionResult.Success("Maps opened for query: $query")
    }

    private fun draftEmail(request: DraftEmailRequest): AppActionResult {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            request.to?.let { putExtra(Intent.EXTRA_EMAIL, arrayOf(it)) }
            request.subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
            request.body?.let { putExtra(Intent.EXTRA_TEXT, it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return AppActionResult.Success("Email draft opened")
    }

    private fun createCalendarEvent(request: CreateCalendarEventRequest): AppActionResult {
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, request.title)
            .apply {
                request.startTime?.let { putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
                request.location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(intent)
        return AppActionResult.Success("Calendar event creation screen opened")
    }
}
