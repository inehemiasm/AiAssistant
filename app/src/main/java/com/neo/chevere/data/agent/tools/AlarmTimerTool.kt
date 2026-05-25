package com.neo.chevere.data.agent.tools

import com.neo.chevere.data.agent.ToolResult
import com.neo.chevere.data.agent.actions.AndroidAppActionExecutor
import com.neo.chevere.data.agent.actions.SetAlarmRequest
import com.neo.chevere.data.agent.actions.SetTimerRequest
import com.neo.chevere.data.agent.actions.ShowAlarmsRequest
import javax.inject.Inject

/**
 * Manages device alarms and timers after user confirmation.
 */
class AlarmTimerTool @Inject constructor(
    private val actionExecutor: AndroidAppActionExecutor
) : BaseAppActionTool(actionExecutor) {

    override val name: String = "manage_alarms_timers"
    override val description: String =
        "Configures or displays system clock alarms and timers. Actions: 'set_alarm', 'set_timer', 'show_alarms'. Setting alarms or timers requires user confirmation."
    override val inputSchema: String =
        "action: 'set_alarm'|'set_timer'|'show_alarms'. hour: 0-23 (for alarm). minutes: 0-59 (for alarm). length: duration in seconds (for timer). message: optional label."

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val action = args["action"]?.trim()?.lowercase() ?: return ToolResult.Error("Missing 'action' argument")

        return when (action) {
            "set_alarm" -> {
                val hour = args["hour"]?.toIntOrNull() ?: return ToolResult.Error("Missing or invalid 'hour' parameter (0-23)")
                val minutes = args["minutes"]?.toIntOrNull() ?: return ToolResult.Error("Missing or invalid 'minutes' parameter (0-59)")
                val message = args["message"]?.trim()
                
                val request = SetAlarmRequest(hour, minutes, message)
                ToolResult.NeedsConfirmation("Set alarm for ${"%02d".format(hour)}:${"%02d".format(minutes)}${if (!message.isNullOrBlank()) " labeled '$message'" else ""}?") {
                    handleActionResult(actionExecutor.execute(request))
                }
            }
            "set_timer" -> {
                val lengthSeconds = args["length"]?.toIntOrNull() ?: return ToolResult.Error("Missing or invalid 'length' (seconds) parameter")
                val message = args["message"]?.trim()
                
                val request = SetTimerRequest(lengthSeconds, message)
                val durationText = formatDuration(lengthSeconds)
                ToolResult.NeedsConfirmation("Start a timer for $durationText${if (!message.isNullOrBlank()) " labeled '$message'" else ""}?") {
                    handleActionResult(actionExecutor.execute(request))
                }
            }
            "show_alarms" -> {
                handleActionResult(actionExecutor.execute(ShowAlarmsRequest))
            }
            else -> ToolResult.Error("Unsupported action '$action'. Use 'set_alarm', 'set_timer', or 'show_alarms'.")
        }
    }

    private fun formatDuration(seconds: Int): String {
        return when {
            seconds < 60 -> "$seconds seconds"
            seconds % 60 == 0 -> "${seconds / 60} minute(s)"
            else -> "${seconds / 60} minute(s) and ${seconds % 60} second(s)"
        }
    }
}
