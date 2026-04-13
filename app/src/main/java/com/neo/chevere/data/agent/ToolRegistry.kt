package com.neo.chevere.data.agent

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
    fun getToolsSystemPrompt(): String {
        if (tools.isEmpty()) return ""
        
        return buildString {
            append("\n\nYou have access to the following tools:\n")
            tools.forEach { tool ->
                append("- ${tool.name}: ${tool.description}. Input: ${tool.inputSchema}\n")
            }
            append("\nTo call a tool, use the format: [TOOL_CALL: tool_name, param1=value1, param2=value2]\n")
        }
    }
}
