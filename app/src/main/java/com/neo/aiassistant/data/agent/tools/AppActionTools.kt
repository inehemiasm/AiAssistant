package com.neo.aiassistant.data.agent.tools

import com.neo.aiassistant.data.agent.AgentTool
import com.neo.aiassistant.data.agent.ToolResult
import com.neo.aiassistant.data.agent.actions.*
import javax.inject.Inject

/**
 * Base tool for app actions to reduce boilerplate.
 */
abstract class BaseAppActionTool(
    private val actionExecutor: AndroidAppActionExecutor
) : AgentTool {
    
    protected fun handleActionResult(result: AppActionResult): ToolResult {
        return when (result) {
            is AppActionResult.Success -> ToolResult.Success(result.output)
            is AppActionResult.Error -> ToolResult.Error(result.message)
            is AppActionResult.ConfirmationRequired -> ToolResult.NeedsConfirmation(result.message) {
                handleActionResult(actionExecutor.execute(result.data))
            }
        }
    }
}

class CopyToClipboardTool @Inject constructor(
    private val actionExecutor: AndroidAppActionExecutor
) : BaseAppActionTool(actionExecutor) {
    override val name: String = "copy_to_clipboard"
    override val description: String = "Copies the given text to the system clipboard."
    override val inputSchema: String = "text: The text to copy."

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val text = args["text"] ?: return ToolResult.Error("Missing 'text' argument")
        return handleActionResult(actionExecutor.execute(CopyToClipboardRequest(text)))
    }
}

class ShareTextTool @Inject constructor(
    private val actionExecutor: AndroidAppActionExecutor
) : BaseAppActionTool(actionExecutor) {
    override val name: String = "share_text"
    override val description: String = "Opens the Android share sheet to share the given text."
    override val inputSchema: String = "text: The text to share."

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val text = args["text"] ?: return ToolResult.Error("Missing 'text' argument")
        return handleActionResult(actionExecutor.execute(ShareTextRequest(text)))
    }
}

class OpenUrlTool @Inject constructor(
    private val actionExecutor: AndroidAppActionExecutor
) : BaseAppActionTool(actionExecutor) {
    override val name: String = "open_url"
    override val description: String = "Opens a URL in the default web browser."
    override val inputSchema: String = "url: The full URL to open (must include http/https)."

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val url = args["url"] ?: return ToolResult.Error("Missing 'url' argument")
        return handleActionResult(actionExecutor.execute(OpenUrlRequest(url)))
    }
}

class OpenMapsTool @Inject constructor(
    private val actionExecutor: AndroidAppActionExecutor
) : BaseAppActionTool(actionExecutor) {
    override val name: String = "open_maps"
    override val description: String = "Opens Google Maps for a specific location or search query."
    override val inputSchema: String = "query: The address or place to search for."

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val query = args["query"] ?: return ToolResult.Error("Missing 'query' argument")
        return handleActionResult(actionExecutor.execute(OpenMapsRequest(query)))
    }
}

class DraftEmailTool @Inject constructor(
    private val actionExecutor: AndroidAppActionExecutor
) : BaseAppActionTool(actionExecutor) {
    override val name: String = "draft_email"
    override val description: String = "Opens an email app with a pre-filled draft."
    override val inputSchema: String = "to: Optional recipient email. subject: Optional subject line. body: Optional email body text."

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val request = DraftEmailRequest(
            to = args["to"],
            subject = args["subject"],
            body = args["body"]
        )
        // Email is usually something users want to review, but Intent itself is a safe action 
        // as it just opens the app.
        return handleActionResult(actionExecutor.execute(request))
    }
}

class CreateCalendarEventTool @Inject constructor(
    private val actionExecutor: AndroidAppActionExecutor
) : BaseAppActionTool(actionExecutor) {
    override val name: String = "create_calendar_event"
    override val description: String = "Opens the calendar app to create a new event."
    override val inputSchema: String = "title: The title of the event. startTime: Optional start time in milliseconds (Long). location: Optional location."

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val title = args["title"] ?: return ToolResult.Error("Missing 'title' argument")
        val request = CreateCalendarEventRequest(
            title = title,
            startTime = args["startTime"]?.toLongOrNull(),
            location = args["location"]
        )
        return handleActionResult(actionExecutor.execute(request))
    }
}
