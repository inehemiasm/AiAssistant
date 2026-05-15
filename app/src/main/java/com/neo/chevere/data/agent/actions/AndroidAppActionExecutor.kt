package com.neo.chevere.data.agent.actions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import androidx.core.net.toUri
import com.neo.chevere.core.Constants
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
    @param:ApplicationContext private val context: Context
) : AndroidAppActionExecutor {

    override fun execute(request: AppActionRequest): AppActionResult {
        Log.i(TAG, "Executing action request: ${request::class.simpleName}")
        return try {
            when (request) {
                is CopyToClipboardRequest -> copyToClipboard(request.text)
                is ShareTextRequest -> shareText(request.text)
                is OpenUrlRequest -> openUrl(request.url)
                is OpenMapsRequest -> openMaps(request.query)
                is DraftEmailRequest -> draftEmail(request)
                is CreateCalendarEventRequest -> createCalendarEvent(request)
                is SearchAppsRequest -> searchApps(request)
                is LaunchAppRequest -> launchApp(request.packageName)
                is LaunchAppByNameRequest -> launchAppByName(request.name)
                is ListAppsRequest -> listInstalledApps()
                is GetAppCapabilitiesRequest -> getAppCapabilities(request.appName)
                is OpenDeepLinkRequest -> openDeepLink(request)
                is PickImageRequest -> AppActionResult.Error("Pick Image not implemented yet via Intent in this layer")
                else -> AppActionResult.Error("Unknown action request type")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing action: ${request::class.simpleName}", e)
            AppActionResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun copyToClipboard(text: String): AppActionResult {
        val sanitizedText = sanitizeInput(text)
        if (sanitizedText.isEmpty()) return AppActionResult.Error("Text is empty")

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Assistant Copy", sanitizedText)
        clipboard.setPrimaryClip(clip)
        Log.d(TAG, "Successfully copied to clipboard: ${sanitizedText.take(20)}...")
        return AppActionResult.Success("Copied to clipboard")
    }

    private fun shareText(text: String): AppActionResult {
        val sanitizedText = sanitizeInput(text)
        if (sanitizedText.isEmpty()) return AppActionResult.Error("Text is empty")

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, sanitizedText)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Share via").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        return AppActionResult.Success("Share sheet opened")
    }

    private fun openUrl(url: String): AppActionResult {
        val sanitizedUrl = sanitizeInput(url)
        if (sanitizedUrl.isEmpty()) return AppActionResult.Error("URL is empty")

        val normalizedUrl = if (!sanitizedUrl.startsWith(Constants.AppActions.HTTP_SCHEME) &&
            !sanitizedUrl.startsWith(Constants.AppActions.HTTPS_SCHEME)
        ) {
            "${Constants.AppActions.HTTPS_SCHEME}$sanitizedUrl"
        } else {
            sanitizedUrl
        }

        Log.d(TAG, "Normalizing URL: '$url' -> '$normalizedUrl'")

        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalizedUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (intent.resolveActivity(context.packageManager) != null || isCommonBrowserPresent()) {
                context.startActivity(intent)
                AppActionResult.Success("Opened URL: $normalizedUrl")
            } else {
                Log.w(TAG, "No activity found to handle URL: $normalizedUrl")
                AppActionResult.Error("No browser application found to open this URL")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open URL: $normalizedUrl", e)
            AppActionResult.Error("Invalid URL format: $normalizedUrl")
        }
    }

    private fun sanitizeInput(input: String): String {
        return input.trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .trim()
    }

    private fun isCommonBrowserPresent(): Boolean {
        return try {
            val browserIntent = Intent(Intent.ACTION_VIEW, "https://www.google.com".toUri())
            context.packageManager.queryIntentActivities(browserIntent, 0).isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    private fun openMaps(query: String): AppActionResult {
        val sanitizedQuery = sanitizeInput(query)
        if (sanitizedQuery.isEmpty()) return AppActionResult.Error("Search query is empty")

        val gmmIntentUri =
            "${Constants.AppActions.MAPS_QUERY_URI}${Uri.encode(sanitizedQuery)}".toUri()
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
            setPackage(Constants.AppActions.GOOGLE_MAPS_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return if (mapIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(mapIntent)
            AppActionResult.Success("Maps opened for: $sanitizedQuery")
        } else {
            mapIntent.setPackage(null)
            if (mapIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(mapIntent)
                AppActionResult.Success("Maps opened for: $sanitizedQuery")
            } else {
                AppActionResult.Error("Google Maps or a compatible maps app is not installed")
            }
        }
    }

    private fun draftEmail(request: DraftEmailRequest): AppActionResult {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Constants.AppActions.MAILTO_SCHEME.toUri()
            request.to?.let { putExtra(Intent.EXTRA_EMAIL, arrayOf(sanitizeInput(it))) }
            request.subject?.let { putExtra(Intent.EXTRA_SUBJECT, sanitizeInput(it)) }
            request.body?.let { putExtra(Intent.EXTRA_TEXT, sanitizeInput(it)) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            AppActionResult.Success("Email draft opened")
        } else {
            AppActionResult.Error("No email application found")
        }
    }

    private fun createCalendarEvent(request: CreateCalendarEventRequest): AppActionResult {
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, sanitizeInput(request.title))
            .apply {
                request.startTime?.let { putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
                request.location?.let {
                    putExtra(
                        CalendarContract.Events.EVENT_LOCATION,
                        sanitizeInput(it)
                    )
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        return if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            AppActionResult.Success("Calendar event screen opened")
        } else {
            AppActionResult.Error("No calendar application found")
        }
    }

    private fun searchApps(request: SearchAppsRequest): AppActionResult {
        val intent = Intent(request.action).apply {
            request.mimeType?.let { type = it }
        }

        val packageManager = context.packageManager
        val activities = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)

        if (activities.isEmpty()) {
            return AppActionResult.Success("No apps found for action: ${request.action}")
        }

        val appList = activities.joinToString("\n") { resolveInfo ->
            val label = resolveInfo.loadLabel(packageManager)
            val pkg = resolveInfo.activityInfo.packageName
            "- $label ($pkg)"
        }

        return AppActionResult.Success("Found the following apps:\n$appList")
    }

    private fun listInstalledApps(): AppActionResult {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps = pm.queryIntentActivities(mainIntent, 0)
        if (apps.isEmpty()) {
            return AppActionResult.Error("No apps with launcher icons found. Check app permissions.")
        }

        val appList = apps
            .map { it.loadLabel(pm).toString() }
            .distinct()
            .sorted()
            .joinToString(", ")

        return AppActionResult.Success("The following apps are installed: $appList")
    }

    private fun getAppCapabilities(appName: String): AppActionResult {
        val sanitizedName = sanitizeInput(appName).lowercase()
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps = pm.queryIntentActivities(mainIntent, 0)
        val match = apps.find {
            it.loadLabel(pm).toString().lowercase().contains(sanitizedName) ||
                    it.activityInfo.packageName.lowercase().contains(sanitizedName)
        } ?: return AppActionResult.Error("Could not find an app named '$appName'")

        val packageName = match.activityInfo.packageName
        val label = match.loadLabel(pm).toString()

        val capabilities = mutableListOf<String>()

        // Define more specific intents to check for accurate capability detection
        val specificIntents = listOf(
            Intent(Intent.ACTION_SEND).apply { type = "text/plain" } to "Sharing text or files",
            Intent(
                Intent.ACTION_VIEW,
                "${Constants.AppActions.HTTPS_SCHEME}example.com".toUri()
            ) to "Opening web links (HTTP/HTTPS)",
            Intent(Intent.ACTION_VIEW, "geo:0,0".toUri()) to "Handling map/location requests",
            Intent(
                Intent.ACTION_SENDTO,
                (Constants.AppActions.MAILTO_SCHEME + "test@example.com").toUri()
            ) to "Sending emails",
            Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
            } to "Creating calendar events",
            Intent(Intent.ACTION_DIAL, "tel:123".toUri()) to "Making calls",
            Intent(Intent.ACTION_PICK).apply { type = "image/*" } to "Picking images/files"
        )

        specificIntents.forEach { (intent, description) ->
            intent.setPackage(packageName)
            if (pm.queryIntentActivities(intent, 0).isNotEmpty()) {
                capabilities.add(description)
            }
        }

        val capabilitiesStr = if (capabilities.isEmpty()) {
            "No common public actions detected, but it can be launched normally."
        } else {
            capabilities.distinct().joinToString("\n- ", prefix = "- ")
        }

        return AppActionResult.Success(
            "App: $label\n" +
                    "Package: $packageName\n" +
                    "Capabilities:\n$capabilitiesStr"
        )
    }

    private fun launchApp(packageName: String): AppActionResult {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        return if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            AppActionResult.Success("Launched app: $packageName")
        } else {
            AppActionResult.Error("Could not find launch intent for package: $packageName")
        }
    }

    private fun launchAppByName(name: String): AppActionResult {
        val sanitizedName = sanitizeInput(name).lowercase()
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps = pm.queryIntentActivities(mainIntent, 0)

        // Try exact match first, then contains
        var match = apps.find { it.loadLabel(pm).toString().lowercase() == sanitizedName }
        if (match == null) {
            match = apps.find { it.loadLabel(pm).toString().lowercase().contains(sanitizedName) }
        }

        if (match == null) {
            match = apps.find { it.activityInfo.packageName.lowercase().contains(sanitizedName) }
        }

        return if (match != null) {
            launchApp(match.activityInfo.packageName)
        } else {
            AppActionResult.Error("Could not find an app named '$name' on your device.")
        }
    }

    private fun openDeepLink(request: OpenDeepLinkRequest): AppActionResult {
        val uriString = sanitizeInput(request.uri)
        if (uriString.isBlank()) {
            return AppActionResult.Error("Missing required parameter: uri")
        }

        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addCategory(Intent.CATEGORY_BROWSABLE)
                request.packageName?.takeIf { it.isNotBlank() }?.let {
                    setPackage(sanitizeInput(it))
                }
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                AppActionResult.Success("Opened deep link: $uriString")
            } else {
                AppActionResult.Error("No app found that can handle this deep link: $uriString")
            }
        } catch (t: Throwable) {
            AppActionResult.Error("Failed to open deep link: ${t.message}")
        }
    }
}
