package com.neo.chevere.data.agent.actions

data class CopyToClipboardRequest(val text: String) : AppActionRequest
data class ShareTextRequest(val text: String) : AppActionRequest
data class OpenUrlRequest(val url: String) : AppActionRequest
data class OpenMapsRequest(val query: String) : AppActionRequest
data class DraftEmailRequest(
    val to: String? = null,
    val subject: String? = null,
    val body: String? = null
) : AppActionRequest
data class CreateCalendarEventRequest(
    val title: String,
    val startTime: Long? = null,
    val location: String? = null
) : AppActionRequest
object PickImageRequest : AppActionRequest

/**
 * Request to search for apps that can handle a specific type of action or content.
 * @property action The intent action to search for (e.g., "android.intent.action.SEND").
 * @property mimeType Optional MIME type to filter by.
 */
data class SearchAppsRequest(
    val action: String,
    val mimeType: String? = null
) : AppActionRequest

/**
 * Request to launch a specific app by its package name.
 */
data class LaunchAppRequest(val packageName: String) : AppActionRequest

/**
 * Request to find and launch an app by its common name.
 */
data class LaunchAppByNameRequest(val name: String) : AppActionRequest

/**
 * Request to list all installed apps with launcher icons.
 */
object ListAppsRequest : AppActionRequest

/**
 * Request to get detailed capabilities (intents handled) by a specific app.
 */
data class GetAppCapabilitiesRequest(val appName: String) : AppActionRequest

/**
 * Request to open a deep link URI.
 * @property uri The deep link URI to open.
 * @property packageName Optional package name to force a specific app.
 */
data class OpenDeepLinkRequest(
    val uri: String,
    val packageName: String? = null
) : AppActionRequest
