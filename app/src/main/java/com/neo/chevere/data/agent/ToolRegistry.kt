package com.neo.chevere.data.agent

import com.neo.chevere.core.Constants
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

    private fun buildToolsSystemPrompt(): String {
        if (tools.isEmpty()) return ""
        
        return buildString {
            append("Agent protocol:\n")
            append("1. Treat the latest CURRENT USER REQUEST as the task to complete now.\n")
            append("2. Use conversation history only for context, not as a task list.\n")
            append("3. Answer directly when no external action is needed.\n")
            append("4. Call one tool only when the current request requires that tool's external capability.\n")
            append("5. After an observation, return a concise final answer for the user. Do not expose tool syntax in the final answer.\n")
            append("\n\nYou have access to the following tools:\n")
            tools.sortedBy { it.name }.forEach { tool ->
                append("- ${tool.name}: ${tool.description}. Input: ${tool.inputSchema}\n")
            }
            append("\nDo not use tools for private reasoning, grading, rewriting, explaining code, or summarizing the chat unless the latest user request explicitly asks for that specific tool-backed action.\n")
            append("If the user asks whether you can do something, answer the capability question instead of calling the tool.\n")
            append("For generate_image, improve the user's image request before the tool call. Expand vague prompts into a clear visual prompt with subject, environment, medium/style, lighting, camera/composition, and quality terms while preserving the user's intent.\n")
            append("\nTo call a tool, use the format: ${Constants.Agent.TOOL_CALL_PREFIX} tool_name, param1=value1, param2=value2]\n")
            append("For long or code-heavy arguments, quote the full value or use JSON-like arguments: ${Constants.Agent.TOOL_CALL_PREFIX} tool_name, {\"param\":\"value\"}]\n")
        }
    }
}
