package com.neo.chevere.data.agent

/**
 * Interface for an AI Agent Tool.
 *
 * Tools allow the AI Agent to interact with the system or external services.
 * Each tool has a name, a description for the LLM to understand when to use it,
 * and an input schema describing the expected parameters.
 */
interface AgentTool {
    /**
     * The unique name of the tool.
     */
    val name: String

    /**
     * A description of what the tool does, used by the LLM for tool selection.
     */
    val description: String

    /**
     * A simplified description of the expected parameters (e.g., in JSON format).
     */
    val inputSchema: String

    /**
     * Executes the tool with the given arguments.
     *
     * @param args A map of arguments extracted from the LLM's tool call.
     * @return A [ToolResult] indicating the outcome of the execution.
     */
    suspend fun execute(args: Map<String, String>): ToolResult
}

/**
 * Represents the result of an [AgentTool] execution.
 */
sealed interface ToolResult {
    /**
     * Indicates the tool executed successfully.
     * @property data The output data from the tool.
     */
    data class Success(val data: String) : ToolResult

    /**
     * Indicates an error occurred during tool execution.
     * @property message A description of the error.
     */
    data class Error(val message: String) : ToolResult

    /**
     * Indicates that the tool requires user confirmation before proceeding.
     * @property message A message to show the user explaining why confirmation is needed.
     * @property onConfirm A lambda to execute if the user confirms the action.
     */
    data class NeedsConfirmation(val message: String, val onConfirm: suspend () -> ToolResult) :
        ToolResult
}
