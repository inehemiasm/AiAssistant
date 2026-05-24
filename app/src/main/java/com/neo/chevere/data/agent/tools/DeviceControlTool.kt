package com.neo.chevere.data.agent.tools

import com.neo.chevere.data.agent.AgentTool
import com.neo.chevere.data.agent.ToolResult
import com.neo.chevere.data.agent.actions.AndroidAppActionExecutor
import com.neo.chevere.data.agent.actions.DeviceControlRequest
import javax.inject.Inject

/**
 * Performs small device control actions after explicit user confirmation.
 */
class DeviceControlTool @Inject constructor(
    private val actionExecutor: AndroidAppActionExecutor
) : BaseAppActionTool(actionExecutor) {
    override val name: String = "control_device"
    override val description: String =
        "Adjusts safe device controls after user confirmation. Supports media volume directly, and opens system settings for brightness or Do Not Disturb."
    override val inputSchema: String =
        "control: volume|brightness|do_not_disturb. action: up|down|mute|unmute|set|open. value: Optional 0-100 for volume set."

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val control = args["control"]?.trim().orEmpty()
        val action = args["action"]?.trim().orEmpty().ifBlank { "open" }
        if (control.isBlank()) return ToolResult.Error("Missing 'control' argument")

        val request = DeviceControlRequest(
            control = control,
            action = action,
            value = args["value"]?.toIntOrNull()
        )
        return ToolResult.NeedsConfirmation(buildConfirmationMessage(request)) {
            handleActionResult(actionExecutor.execute(request))
        }
    }

    private fun buildConfirmationMessage(request: DeviceControlRequest): String {
        return when (request.control.lowercase()) {
            "volume" -> if (request.action.equals("set", ignoreCase = true)) {
                "Set media volume to ${request.value ?: "the requested level"}%?"
            } else {
                "Adjust media volume: ${request.action}?"
            }
            "brightness" -> "Open Display settings so you can adjust brightness?"
            "do_not_disturb", "dnd" -> "Open Do Not Disturb settings?"
            else -> "Run device control '${request.control}'?"
        }
    }
}
