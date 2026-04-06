package com.neo.aiassistant.data.agent

/**
 * Represents the current state of the Agent's reasoning loop.
 */
sealed interface AgentState {
    object Idle : AgentState
    object Planning : AgentState
    data class ExecutingTool(val toolName: String) : AgentState
    data class WaitingForConfirmation(val toolName: String, val message: String) : AgentState
    object Completed : AgentState
    data class Error(val message: String) : AgentState
}

/**
 * Represents a parsed tool call from the LLM.
 * Usually extracted from a structured format like JSON or a specific tag-based syntax.
 */
data class ToolCall(
    val toolName: String,
    val arguments: Map<String, String>
)

/**
 * A step in the agent's execution history.
 */
data class AgentStep(
    val toolCall: ToolCall,
    val result: ToolResult
)
