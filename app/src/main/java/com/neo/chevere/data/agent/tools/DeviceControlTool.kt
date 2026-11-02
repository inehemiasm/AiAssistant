package com.neo.chevere.data.agent.tools

import android.content.Context
import com.neo.chevere.R
import dagger.hilt.android.qualifiers.ApplicationContext

import com.neo.chevere.data.agent.AgentTool
import com.neo.chevere.data.agent.ToolResult
import com.neo.chevere.data.agent.actions.AndroidAppActionExecutor
import com.neo.chevere.data.agent.actions.DeviceControlRequest
import javax.inject.Inject

/**
 * Performs small device control actions after explicit user confirmation.
 */
class DeviceControlTool @Inject constructor(
    private val actionExecutor: AndroidAppActionExecutor,
    @ApplicationContext private val context: Context
) : BaseAppActionTool(actionExecutor) {
    override val name: String = "control_device"
    override val description: String =
        "Adjusts safe device controls after user confirmation. Supports media volume directly, media playback controls (play, pause, next, previous), and opens system settings for brightness or Do Not Disturb."
    override val inputSchema: String =
        "control: volume|music|brightness|do_not_disturb. action: up|down|mute|unmute|set|open|play|pause|next|previous. value: Optional 0-100 for volume set."

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
                context.getString(R.string.ui_set_media_volume_to_1_s, request.value ?: context.getString(R.string.ui_the_requested_level))
            } else {
                context.getString(R.string.ui_adjust_media_volume_1_s, request.action)
            }
            "music", "media" -> when (request.action.lowercase()) {
                "play", "resume" -> context.getString(R.string.ui_resume_music_playback)
                "pause", "stop" -> context.getString(R.string.ui_pause_music_playback)
                "next", "skip" -> context.getString(R.string.ui_skip_to_the_next_song)
                "previous", "back" -> context.getString(R.string.ui_go_back_to_the_previous_song)
                else -> context.getString(R.string.ui_control_music_playback_1_s, request.action)
            }
            "brightness" -> context.getString(R.string.ui_open_display_settings_so_you_can_adjust_brightness)
            "do_not_disturb", "dnd" -> context.getString(R.string.ui_open_do_not_disturb_settings)
            else -> context.getString(R.string.ui_run_device_control_1_s, request.control)
        }
    }
}
