package com.neo.chevere.data.agent

import com.neo.chevere.core.Constants
import com.neo.chevere.data.chat.RoutingCategory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry for all available agent tools.
 *
 * This class maintains a set of [AgentTool] implementations and provides
 * methods to retrieve them and generate documentation for the LLM.
 *
 * @property tools The set of registered tools, injected via Dagger/Hilt.
 */
@Singleton
class ToolRegistry @Inject constructor(
    private val tools: Set<@JvmSuppressWildcards AgentTool>
) {
    private val cachedToolsSystemPrompt: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildToolsSystemPrompt()
    }

    /**
     * Retrieves a tool by its unique name.
     *
     * @param name The name of the tool to find.
     * @return The [AgentTool] if found, or `null` otherwise.
     */
    fun getTool(name: String): AgentTool? = tools.find { it.name == name }

    /**
     * Returns a list of all registered tools.
     */
    fun getAllTools(): List<AgentTool> = tools.toList()

    /**
     * Generates a system prompt section describing all available tools.
     *
     * This prompt is used to inform the AI model about what tools it can use,
     * what they do, and what parameters they expect.
     *
     * @return A formatted string containing tool descriptions and call syntax.
     */
    fun getToolsSystemPrompt(): String = cachedToolsSystemPrompt

    fun getToolsSystemPrompt(routingCategory: RoutingCategory?): String {
        val toolNames = routingCategory?.toolNamesForCategory() ?: return cachedToolsSystemPrompt
        return buildToolsSystemPrompt(toolNames)
    }

    private fun buildToolsSystemPrompt(toolNames: Set<String>? = null): String {
        if (tools.isEmpty()) return ""
        val includedTools = tools
            .filter { toolNames == null || it.name in toolNames }
            .sortedBy { it.name }
        if (includedTools.isEmpty()) return ""

        return buildString {
            append("Agent protocol:\n")
            append("1. Treat the latest CURRENT USER REQUEST as the task to complete now.\n")
            append("2. Use conversation history only for context, not as a task list.\n")
            append("3. Answer directly when no external action is needed.\n")
            append("4. Call one tool only when the current request requires that tool's external capability.\n")
            append("5. Make sensible assumptions to fulfill requests immediately unless permission or safety checks are required.\n")
            append("6. After an observation, return a concise final answer for the user. Do not expose tool syntax in the final answer.\n")
            append("\n\nTools:\n")
            val includedToolNames = includedTools.map { it.name }.toSet()
            includedTools.forEach { tool ->
                append("- ${tool.name}: ${tool.description.toPromptLine(Constants.Agent.TOOL_DESCRIPTION_CHAR_LIMIT, includedToolNames)} Input: ${tool.inputSchema.toPromptLine(Constants.Agent.TOOL_SCHEMA_CHAR_LIMIT)}\n")
            }
            append("Do not use tools for private reasoning, grading, rewriting, explaining code, or summarizing the chat unless the latest user request explicitly asks for that specific tool-backed action.\n")
            append("If the user asks whether you can do something, answer the capability question instead of calling the tool.\n")
            if (includedTools.any { it.name == "read_sensors" }) {
                append("For sensor data questions, call read_sensors. For visual sensor screens, call perform_app_action with the sensor-radar URI.\n")
            }
            if (includedTools.any { it.name == Constants.Agent.IMAGE_GENERATION_TOOL_NAME }) {
                append("For generate_image, improve the user's image request before the tool call. Expand vague prompts into a clear visual prompt while preserving intent. Do not call generate_image for websites, UIs, widgets, or code.\n")
            }
            if (includedTools.any { it.name == "invoice_registry" }) {
                append("For invoice scanning, parsing, or importing requests, you MUST extract the vendor, total_amount, currency, invoice_number, date, items, and payment status from the image or text, and then call invoice_registry with action=import to save it to the database. Detect payment status: if you see a PAID stamp, watermark, or zero balance due, pass status=paid. If you see overdue or past due, pass status=overdue. Otherwise use status=pending. Tell the user you are importing the invoice details.\n")
            }
            append("\nTo call a tool, use the format: ${Constants.Agent.TOOL_CALL_PREFIX} tool_name, param1=value1, param2=value2]\n")
            append("For long or code-heavy arguments, quote the full value or use JSON-like arguments: ${Constants.Agent.TOOL_CALL_PREFIX} tool_name, {\"param\":\"value\"}]\n")
        }
    }

    private fun String.toPromptLine(limit: Int, allowedToolNames: Set<String>? = null): String {
        val excludedToolNames = allowedToolNames
            ?.let { allowed -> tools.map { it.name }.filterNot { it in allowed } }
            .orEmpty()
        val compact = lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .removeExcludedToolReferences(excludedToolNames)
        return if (compact.length <= limit) {
            compact
        } else {
            "${compact.take(limit).trimEnd()}..."
        }
    }

    private fun String.removeExcludedToolReferences(excludedToolNames: List<String>): String {
        if (excludedToolNames.isEmpty()) return this
        return split(Regex("(?<=[.!?])\\s+"))
            .filterNot { sentence -> excludedToolNames.any { it in sentence } }
            .joinToString(" ")
            .ifBlank { this }
    }

    private fun RoutingCategory.toolNamesForCategory(): Set<String> = when (this) {
        RoutingCategory.IMAGE_GENERATION -> setOf(Constants.Agent.IMAGE_GENERATION_TOOL_NAME)
        RoutingCategory.LIVE_INFORMATION -> setOf("get_weather", "web_search")
        RoutingCategory.DEVICE_ACTION -> setOf(
            "control_device",
            "copy_to_clipboard",
            "create_calendar_event",
            "draft_email",
            "get_app_capabilities",
            "launch_app",
            "launch_app_home_screen",
            "list_apps",
            "manage_alarms_timers",
            "open_maps",
            "open_url",
            "perform_app_action",
            "query_calendar",
            "search_apps",
            "search_contacts",
            "share_text"
        )
        RoutingCategory.MODEL_MANAGEMENT -> setOf(
            "getActiveModel",
            "getModelDetails",
            "getRuntimeStatus",
            "listInstalledModels",
            "recommendInstalledModelForTask",
            "selectInstalledModel"
        )
        RoutingCategory.TASK_REGISTRY -> setOf("extract_tasks", "task_registry")
        RoutingCategory.INVOICE_MANAGEMENT -> setOf("invoice_registry")
        RoutingCategory.SENSORS -> setOf("perform_app_action", "read_sensors")
        RoutingCategory.DIRECT_CHAT -> emptySet()
    }
}
