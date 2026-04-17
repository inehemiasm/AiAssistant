package com.neo.chevere.data.agent.tools

import com.neo.chevere.data.agent.AgentTool
import com.neo.chevere.data.agent.ToolResult
import com.neo.chevere.data.agent.actions.AndroidAppActionExecutor
import com.neo.chevere.data.agent.actions.AppActionResult
import com.neo.chevere.data.agent.actions.CopyToClipboardRequest
import com.neo.chevere.data.agent.actions.CreateCalendarEventRequest
import com.neo.chevere.data.agent.actions.DraftEmailRequest
import com.neo.chevere.data.agent.actions.GetAppCapabilitiesRequest
import com.neo.chevere.data.agent.actions.LaunchAppByNameRequest
import com.neo.chevere.data.agent.actions.LaunchAppRequest
import com.neo.chevere.data.agent.actions.ListAppsRequest
import com.neo.chevere.data.agent.actions.OpenDeepLinkRequest
import com.neo.chevere.data.agent.actions.OpenMapsRequest
import com.neo.chevere.data.agent.actions.OpenUrlRequest
import com.neo.chevere.data.agent.actions.SearchAppsRequest
import com.neo.chevere.data.agent.actions.ShareTextRequest
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

class SearchAppsTool @Inject constructor(
    private val actionExecutor: AndroidAppActionExecutor
) : BaseAppActionTool(actionExecutor) {
    override val name: String = "search_apps"
    override val description: String = "Searches for installed apps that can handle a specific action or content type."
    override val inputSchema: String = "action: The Android Intent action (e.g. android.intent.action.SEND). mimeType: Optional MIME type (e.g. text/plain)."

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val action = args["action"] ?: return ToolResult.Error("Missing 'action' argument")
        val request = SearchAppsRequest(action, args["mimeType"])
        return handleActionResult(actionExecutor.execute(request))
    }
}

class ListAppsTool @Inject constructor(
    private val actionExecutor: AndroidAppActionExecutor
) : BaseAppActionTool(actionExecutor) {
    override val name: String = "list_apps"
    override val description: String = "Lists all installed applications on the device."
    override val inputSchema: String = ""

    override suspend fun execute(args: Map<String, String>): ToolResult {
        return handleActionResult(actionExecutor.execute(ListAppsRequest))
    }
}

class LaunchAppTool @Inject constructor(
    private val actionExecutor: AndroidAppActionExecutor
) : BaseAppActionTool(actionExecutor) {
    override val name: String = "launch_app"
    override val description: String = "Launches an installed app by its unique package name."
    override val inputSchema: String = "packageName: The Android package name (e.g. com.google.android.gm)."

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val packageName = args["packageName"] ?: return ToolResult.Error("Missing 'packageName' argument")
        return handleActionResult(actionExecutor.execute(LaunchAppRequest(packageName)))
    }
}

class OpenAppTool @Inject constructor(
    private val actionExecutor: AndroidAppActionExecutor
) : BaseAppActionTool(actionExecutor) {
    override val name: String = "launch_app_home_screen"
    override val description: String = "Use ONLY to open the basic home screen of an app. NEVER use this for specific tasks like 'create invoice' or 'view order'. If the user mentions any specific action, you MUST use perform_app_action instead."
    override val inputSchema: String = "name: The common name of the app (e.g., 'Settings')."

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val name = args["name"] ?: return ToolResult.Error("Missing 'name' argument")
        return handleActionResult(actionExecutor.execute(LaunchAppByNameRequest(name)))
    }
}

class OpenDeepLinkTool @Inject constructor(
    private val actionExecutor: AndroidAppActionExecutor
) : BaseAppActionTool(actionExecutor) {
    override val name: String = "perform_app_action"
    override val description: String = "MANDATORY for specific actions inside an app. For Squarespace: use uri='squarespace://invoices/create' to create an invoice, uri='squarespace://pay-links/create' for pay links, or uri='squarespace://orders' to view orders."
    override val inputSchema: String = "uri: The action URI (required). packageName: Optional package name (e.g. 'com.squarespace.android')."

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val uri = args["uri"] ?: return ToolResult.Error("Missing 'uri' argument")
        val packageName = args["packageName"]
        return handleActionResult(actionExecutor.execute(OpenDeepLinkRequest(uri, packageName)))
    }
}

class GetAppCapabilitiesTool @Inject constructor(
    private val actionExecutor: AndroidAppActionExecutor
) : BaseAppActionTool(actionExecutor) {
    override val name: String = "get_app_capabilities"
    override val description: String = "Returns the specific actions and intents an app can handle (e.g., sharing, viewing links, mapping)."
    override val inputSchema: String = "name: The common name of the app to analyze."

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val name = args["name"] ?: return ToolResult.Error("Missing 'name' argument")
        return handleActionResult(actionExecutor.execute(GetAppCapabilitiesRequest(name)))
    }
}
