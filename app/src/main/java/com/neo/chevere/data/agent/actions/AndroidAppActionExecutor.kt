package com.neo.chevere.data.agent.actions

import com.neo.chevere.R
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.Settings
import androidx.core.net.toUri
import com.neo.chevere.core.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
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
        Timber.tag(TAG).i("Executing action request: ${request::class.simpleName}")
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
                is DeviceControlRequest -> controlDevice(request)
                is SetAlarmRequest -> setAlarm(request)
                is SetTimerRequest -> setTimer(request)
                is ShowAlarmsRequest -> showAlarms()
                is PickImageRequest -> AppActionResult.Error("Pick Image not implemented yet via Intent in this layer")
                else -> AppActionResult.Error("Unknown action request type")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error executing action: ${request::class.simpleName}")
            AppActionResult.Error(e.message ?: context.getString(R.string.ui_unknown_error))
        }
    }

    private fun copyToClipboard(text: String): AppActionResult {
        val sanitizedText = sanitizeInput(text)
        if (sanitizedText.isEmpty()) return AppActionResult.Error(context.getString(R.string.ui_text_is_empty))

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(context.getString(R.string.ui_assistant_copy), sanitizedText)
        clipboard.setPrimaryClip(clip)
        Timber.tag(TAG).d("Successfully copied to clipboard: ${sanitizedText.take(20)}...")
        return AppActionResult.Success(context.getString(R.string.ui_copied_to_clipboard))
    }

    private fun shareText(text: String): AppActionResult {
        val sanitizedText = sanitizeInput(text)
        if (sanitizedText.isEmpty()) return AppActionResult.Error(context.getString(R.string.ui_text_is_empty))

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, sanitizedText)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, context.getString(R.string.ui_share_via)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        return AppActionResult.Success(context.getString(R.string.ui_share_sheet_opened))
    }

    private fun openUrl(url: String): AppActionResult {
        val sanitizedUrl = sanitizeInput(url)
        if (sanitizedUrl.isEmpty()) return AppActionResult.Error(context.getString(R.string.ui_url_is_empty))

        val normalizedUrl = if (!sanitizedUrl.startsWith(Constants.AppActions.HTTP_SCHEME) &&
            !sanitizedUrl.startsWith(Constants.AppActions.HTTPS_SCHEME)
        ) {
            "${Constants.AppActions.HTTPS_SCHEME}$sanitizedUrl"
        } else {
            sanitizedUrl
        }

        Timber.tag(TAG).d("Normalizing URL: '$url' -> '$normalizedUrl'")

        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalizedUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (intent.resolveActivity(context.packageManager) != null || isCommonBrowserPresent()) {
                context.startActivity(intent)
                AppActionResult.Success(context.getString(R.string.ui_opened_url_1_s, normalizedUrl))
            } else {
                Timber.tag(TAG).w("No activity found to handle URL: $normalizedUrl")
                AppActionResult.Error(context.getString(R.string.ui_no_browser_application_found_to_open_this_url))
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to open URL: $normalizedUrl")
            AppActionResult.Error(context.getString(R.string.ui_invalid_url_format_1_s, normalizedUrl))
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
        if (sanitizedQuery.isEmpty()) return AppActionResult.Error(context.getString(R.string.ui_search_query_is_empty))

        val gmmIntentUri =
            "${Constants.AppActions.MAPS_QUERY_URI}${Uri.encode(sanitizedQuery)}".toUri()
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
            setPackage(Constants.AppActions.GOOGLE_MAPS_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return if (mapIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(mapIntent)
            AppActionResult.Success(context.getString(R.string.ui_maps_opened_for_1_s, sanitizedQuery))
        } else {
            mapIntent.setPackage(null)
            if (mapIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(mapIntent)
                AppActionResult.Success(context.getString(R.string.ui_maps_opened_for_1_s, sanitizedQuery))
            } else {
                AppActionResult.Error(context.getString(R.string.ui_google_maps_or_a_compatible_maps_app_is_not_installed))
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
            AppActionResult.Success(context.getString(R.string.ui_email_draft_opened))
        } else {
            AppActionResult.Error(context.getString(R.string.ui_no_email_application_found))
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
            AppActionResult.Success(context.getString(R.string.ui_calendar_event_screen_opened))
        } else {
            AppActionResult.Error(context.getString(R.string.ui_no_calendar_application_found))
        }
    }

    private fun searchApps(request: SearchAppsRequest): AppActionResult {
        val intent = Intent(request.action).apply {
            request.mimeType?.let { type = it }
        }

        val packageManager = context.packageManager
        val activities = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)

        if (activities.isEmpty()) {
            return AppActionResult.Success(context.getString(R.string.ui_no_apps_found_for_action_1_s, request.action))
        }

        val appList = activities.joinToString("\n") { resolveInfo ->
            val label = resolveInfo.loadLabel(packageManager)
            val pkg = resolveInfo.activityInfo.packageName
            "- $label ($pkg)"
        }

        return AppActionResult.Success(context.getString(R.string.ui_found_the_following_apps_n_1_s, appList))
    }

    private fun listInstalledApps(): AppActionResult {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps = pm.queryIntentActivities(mainIntent, 0)
        if (apps.isEmpty()) {
            return AppActionResult.Error(context.getString(R.string.ui_no_apps_with_launcher_icons_found_check_app_permissions))
        }

        val appList = apps
            .map { it.loadLabel(pm).toString() }
            .distinct()
            .sorted()
            .joinToString(", ")

        return AppActionResult.Success(context.getString(R.string.ui_the_following_apps_are_installed_1_s, appList))
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
        } ?: return AppActionResult.Error(context.getString(R.string.ui_could_not_find_an_app_named_1_s, appName))

        val packageName = match.activityInfo.packageName
        val label = match.loadLabel(pm).toString()

        val capabilities = mutableListOf<String>()

        // Define more specific intents to check for accurate capability detection
        val specificIntents = listOf(
            Intent(Intent.ACTION_SEND).apply { type = "text/plain" } to context.getString(R.string.ui_sharing_text_or_files),
            Intent(
                Intent.ACTION_VIEW,
                "${Constants.AppActions.HTTPS_SCHEME}example.com".toUri()
            ) to context.getString(R.string.ui_opening_web_links_http_https),
            Intent(Intent.ACTION_VIEW, "geo:0,0".toUri()) to context.getString(R.string.ui_handling_map_location_requests),
            Intent(
                Intent.ACTION_SENDTO,
                (Constants.AppActions.MAILTO_SCHEME + "test@example.com").toUri()
            ) to context.getString(R.string.ui_sending_emails),
            Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
            } to context.getString(R.string.ui_creating_calendar_events),
            Intent(Intent.ACTION_DIAL, "tel:123".toUri()) to context.getString(R.string.ui_making_calls),
            Intent(Intent.ACTION_PICK).apply { type = "image/*" } to context.getString(R.string.ui_picking_images_files)
        )

        specificIntents.forEach { (intent, description) ->
            intent.setPackage(packageName)
            if (pm.queryIntentActivities(intent, 0).isNotEmpty()) {
                capabilities.add(description)
            }
        }

        val capabilitiesStr = if (capabilities.isEmpty()) {
            context.getString(R.string.ui_no_common_public_actions_detected_but_it_can_be_launched_normall)
        } else {
            capabilities.distinct().joinToString("\n- ", prefix = "- ")
        }

        return AppActionResult.Success(
            context.getString(R.string.ui_app_1_s_n, label) +
                    context.getString(R.string.ui_package_1_s_n, packageName) +
                    "Capabilities:\n$capabilitiesStr"
        )
    }

    private fun launchApp(packageName: String): AppActionResult {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        return if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            AppActionResult.Success(context.getString(R.string.ui_launched_app_1_s, packageName))
        } else {
            AppActionResult.Error(context.getString(R.string.ui_could_not_find_launch_intent_for_package_1_s, packageName))
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
            AppActionResult.Error(context.getString(R.string.ui_could_not_find_an_app_named_1_s_on_your_device, name))
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
                AppActionResult.Success(context.getString(R.string.ui_opened_deep_link_1_s, uriString))
            } else {
                AppActionResult.Error(context.getString(R.string.ui_no_app_found_that_can_handle_this_deep_link_1_s, uriString))
            }
        } catch (t: Throwable) {
            AppActionResult.Error(context.getString(R.string.ui_failed_to_open_deep_link_1_s, t.message))
        }
    }

    private fun controlDevice(request: DeviceControlRequest): AppActionResult {
        return when (request.control.lowercase()) {
            "volume" -> controlVolume(request)
            "media", "music" -> controlMedia(request)
            "brightness" -> openSystemSettings(
                action = Settings.ACTION_DISPLAY_SETTINGS,
                successMessage = context.getString(R.string.ui_opened_display_settings_so_you_can_adjust_screen_brightness)
            )
            "do_not_disturb", "dnd" -> openSystemSettings(
                action = Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS,
                successMessage = context.getString(R.string.ui_opened_do_not_disturb_access_settings)
            )
            else -> AppActionResult.Error(
                "Unsupported device control '${request.control}'. Supported controls: volume, music, brightness, do_not_disturb."
            )
        }
    }

    private fun controlMedia(request: DeviceControlRequest): AppActionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val keyEventCode = when (request.action.lowercase()) {
            "play", "resume" -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY
            "pause", "stop" -> android.view.KeyEvent.KEYCODE_MEDIA_PAUSE
            "play_pause", "toggle" -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next", "skip" -> android.view.KeyEvent.KEYCODE_MEDIA_NEXT
            "previous", "back" -> android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> return AppActionResult.Error("Unsupported media action '${request.action}'. Use play, pause, next, previous.")
        }

        // Dispatch media button down and up events
        audioManager.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyEventCode))
        audioManager.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyEventCode))

        return AppActionResult.Success(context.getString(R.string.ui_media_control_action_1_s_executed_successfully, request.action.uppercase()))
    }

    private fun controlVolume(request: DeviceControlRequest): AppActionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val stream = AudioManager.STREAM_MUSIC
        val flags = AudioManager.FLAG_SHOW_UI
        when (request.action.lowercase()) {
            "up", "increase" -> audioManager.adjustStreamVolume(
                stream,
                AudioManager.ADJUST_RAISE,
                flags
            )
            "down", "decrease" -> audioManager.adjustStreamVolume(
                stream,
                AudioManager.ADJUST_LOWER,
                flags
            )
            "mute" -> audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, flags)
            "unmute" -> audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, flags)
            "set" -> {
                val max = audioManager.getStreamMaxVolume(stream)
                val value = request.value?.coerceIn(0, 100)
                    ?: return AppActionResult.Error(context.getString(R.string.ui_missing_volume_value_from_0_to_100))
                val volume = ((value / 100.0) * max).toInt().coerceIn(0, max)
                audioManager.setStreamVolume(stream, volume, flags)
            }
            else -> return AppActionResult.Error(
                "Unsupported volume action '${request.action}'. Use up, down, mute, unmute, or set."
            )
        }
        return AppActionResult.Success(context.getString(R.string.ui_updated_media_volume))
    }

    private fun openSystemSettings(action: String, successMessage: String): AppActionResult {
        val intent = Intent(action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            AppActionResult.Success(successMessage)
        } else {
            AppActionResult.Error(context.getString(R.string.ui_no_system_settings_screen_found_for_this_control))
        }
    }

    private fun setAlarm(request: SetAlarmRequest): AppActionResult {
        val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(android.provider.AlarmClock.EXTRA_HOUR, request.hour)
            putExtra(android.provider.AlarmClock.EXTRA_MINUTES, request.minutes)
            putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, request.message ?: context.getString(R.string.ui_chevere_ai_alarm))
            putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            AppActionResult.Success(context.getString(R.string.ui_alarm_set_for_1_s_2_s_with_label_3_s, "%02d".format(request.hour), "%02d".format(request.minutes), request.message ?: context.getString(R.string.ui_chevere_ai_alarm)))
        } else {
            // In case AlarmClock intent is not supported directly, launch system clock app
            val fallbackIntent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (fallbackIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(fallbackIntent)
                AppActionResult.Success(context.getString(R.string.ui_opened_clock_app_to_set_alarm))
            } else {
                AppActionResult.Error(context.getString(R.string.ui_no_clock_or_alarm_application_found_on_device))
            }
        }
    }

    private fun setTimer(request: SetTimerRequest): AppActionResult {
        val intent = Intent(android.provider.AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(android.provider.AlarmClock.EXTRA_LENGTH, request.lengthSeconds)
            putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, request.message ?: context.getString(R.string.ui_chevere_ai_timer))
            putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            AppActionResult.Success(context.getString(R.string.ui_timer_set_for_1_s_seconds_with_label_2_s, request.lengthSeconds, request.message ?: context.getString(R.string.ui_chevere_ai_timer)))
        } else {
            AppActionResult.Error(context.getString(R.string.ui_no_timer_application_found_on_device))
        }
    }

    private fun showAlarms(): AppActionResult {
        val intent = Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            AppActionResult.Success(context.getString(R.string.ui_alarms_screen_opened))
        } else {
            AppActionResult.Error(context.getString(R.string.ui_no_alarm_application_found_on_device))
        }
    }
}
