package com.neo.aiassistant.data.agent

/**
 * Interface for an AI Agent Tool.
 * Inspired by Google AI Edge Gallery's "Skill" pattern, but simplified for on-device LiteRT.
 */
interface AgentTool {
    val name: String
    val description: String
    val inputSchema: String // Simplified description of expected JSON/parameters
    
    /**
     * Executes the tool with the given arguments.
     * @param args A map of arguments extracted from the LLM's tool call.
     */
    suspend fun execute(args: Map<String, String>): ToolResult
}

sealed interface ToolResult {
    data class Success(val data: String) : ToolResult
    data class Error(val message: String) : ToolResult
    data class NeedsConfirmation(val message: String, val onConfirm: suspend () -> ToolResult) : ToolResult
}
