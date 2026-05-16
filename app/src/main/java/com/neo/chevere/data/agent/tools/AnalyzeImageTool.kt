package com.neo.chevere.data.agent.tools

import com.neo.chevere.core.PiiUtils
import com.neo.chevere.data.agent.AgentTool
import com.neo.chevere.data.agent.ToolResult
import com.neo.chevere.data.inference.InferenceManager
import javax.inject.Inject

/**
 * A tool that prompts the model to focus specifically on image details.
 * In a multimodal context, the model already sees the image, but this tool
 * can be used to trigger specialized 'vision-only' reasoning steps.
 */
class AnalyzeImageTool @Inject constructor(
    private val inferenceManager: InferenceManager
) : AgentTool {
    override val name: String = "analyze_image"
    override val description: String = "Performs a deep analysis of the currently attached image."
    override val inputSchema: String =
        "focus: Specific aspect to look for (e.g., 'text', 'objects', 'mood')."

    override suspend fun execute(args: Map<String, String>): ToolResult {
        if (!inferenceManager.isVisionSupported()) {
            return ToolResult.Error("Vision is not supported on this model.")
        }

        val rawFocus = args["focus"] ?: "general"
        // Scrub input to prevent leakage of any text extracted by the model into logs
        val focus = PiiUtils.scrub(rawFocus)

        // Since the model is already multimodal and holds the conversation, 
        // this tool mainly serves as a "mental hint" or could trigger a different
        // specialized vision model/endpoint if one existed.
        return ToolResult.Success("Image analysis triggered with focus on: $focus. (On-device vision active)")
    }
}
