package com.neo.aiassistant.data.agent.actions

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
