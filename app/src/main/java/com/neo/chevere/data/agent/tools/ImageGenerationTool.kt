package com.neo.chevere.data.agent.tools

import android.net.Uri
import com.neo.chevere.BuildConfig
import com.neo.chevere.core.Constants
import com.neo.chevere.data.agent.AgentTool
import com.neo.chevere.data.agent.ToolResult
import com.neo.chevere.data.inference.ImageGenerationManager
import com.neo.chevere.domain.ExplicitImagePromptPolicy
import com.neo.chevere.domain.ImageGenerationRequest
import com.neo.chevere.domain.ImageGenerationResult
import javax.inject.Inject
import javax.inject.Singleton

const val IMAGE_GENERATION_RESULT_PREFIX = Constants.Agent.IMAGE_GENERATION_RESULT_PREFIX

/**
 * Agent tool that generates an image from a text prompt.
 */
@Singleton
class ImageGenerationTool @Inject constructor(
    private val imageGenerationManager: ImageGenerationManager
) : AgentTool {
    private val explicitImagePromptPolicy = ExplicitImagePromptPolicy()

    override val name: String = Constants.Agent.IMAGE_GENERATION_TOOL_NAME
    override val description: String = "Generates an image from a text prompt using an installed image generation model. Before calling this tool, rewrite short user requests into a concise visual prompt with subject, setting, style, lighting, composition, and quality details."
    override val inputSchema: String = "prompt: Improved visual prompt, not the raw user request. Optional: negativePrompt, width, height, steps, guidanceScale, seed, conditionImageUri."

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val prompt = args["prompt"]?.trim().orEmpty()
        if (prompt.isBlank()) {
            return ToolResult.Error("Missing image prompt.")
        }

        if (!BuildConfig.DEBUG && explicitImagePromptPolicy.requiresAgeVerification(prompt)) {
            return ToolResult.Error(Constants.ContentPolicy.EXPLICIT_RELEASE_BLOCK_MESSAGE)
        }

        if (!imageGenerationManager.isImageGenerationAvailable()) {
            return ToolResult.Error("No compatible image generation model is installed. Download an ONNX Diffusion model from the Marketplace first.")
        }

        val request = ImageGenerationRequest(
            prompt = prompt,
            negativePrompt = args["negativePrompt"]?.trim()?.takeIf { it.isNotBlank() },
            width = args["width"]?.toIntOrNull() ?: 512,
            height = args["height"]?.toIntOrNull() ?: 512,
            steps = args["steps"]?.toIntOrNull() ?: 20,
            guidanceScale = args["guidanceScale"]?.toFloatOrNull() ?: 7.5f,
            seed = args["seed"]?.toLongOrNull(),
            conditionImageUri = args["conditionImageUri"]?.let(Uri::parse)
        )

        return when (val result = imageGenerationManager.generate(request)) {
            is ImageGenerationResult.Success -> ToolResult.Success(
                listOf(
                    IMAGE_GENERATION_RESULT_PREFIX,
                    "uri=${result.imageUri}",
                    "prompt=${result.prompt.replace(Constants.Agent.IMAGE_GENERATION_RESULT_SEPARATOR, " ")}",
                    "width=${result.width}",
                    "height=${result.height}",
                    "seed=${result.seed ?: ""}"
                ).joinToString(Constants.Agent.IMAGE_GENERATION_RESULT_SEPARATOR)
            )
            is ImageGenerationResult.Failure -> ToolResult.Error(result.message)
        }
    }
}
