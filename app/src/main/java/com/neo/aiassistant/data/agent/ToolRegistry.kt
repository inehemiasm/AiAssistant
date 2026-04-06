package com.neo.aiassistant.data.agent

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry for all available agent tools.
 */
@Singleton
class ToolRegistry @Inject constructor(
    private val tools: Set<@JvmSuppressWildcards AgentTool>
) {
    fun getTool(name: String): AgentTool? = tools.find { it.name == name }
    
    fun getAllTools(): List<AgentTool> = tools.toList()
    
    /**
     * Generates a system prompt section describing available tools.
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
