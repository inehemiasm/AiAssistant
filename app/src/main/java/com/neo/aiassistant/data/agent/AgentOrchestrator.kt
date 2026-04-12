package com.neo.aiassistant.data.agent

import android.net.Uri
import android.util.Log
import com.neo.aiassistant.data.inference.InferenceManager
import com.neo.aiassistant.domain.InferenceRequest
import com.neo.aiassistant.domain.InferenceResult
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AgentOrchestrator"

/**
 * Orchestrates the Agent reasoning loop: Reason -> Act -> Observe.
 * 
 * This class handles the "Agent Loop" logic, managing multiple turns between 
 * the model and tools until a final answer is reached or limits are hit.
 */
@Singleton
class AgentOrchestrator @Inject constructor(
    private val inferenceManager: InferenceManager,
    private val toolRegistry: ToolRegistry,
    private val parser: ToolCallParser
) {
    private val _agentState = MutableStateFlow<AgentState>(AgentState.Idle)
    val agentState: StateFlow<AgentState> = _agentState.asStateFlow()

    private var pendingConfirmation: (suspend () -> ToolResult)? = null
    private var lastPrompt: String = ""
    private var lastImageUri: Uri? = null
    private var stepCount = 0

    private val MAX_TOOL_CALLS_PER_TURN = 5
    private val TOOL_EXECUTION_TIMEOUT_MS = 30_000L // 30 seconds

    /**
     * Processes a user request through the agent loop.
     */
    suspend fun processUserRequest(prompt: String, imageUri: Uri? = null): Result<String> {
        _agentState.value = AgentState.Planning
        Log.i(TAG, "Starting agent loop for prompt: $prompt")
        
        val systemPrompt = toolRegistry.getToolsSystemPrompt()
        val initialPrompt = if (systemPrompt.isNotEmpty()) {
            "SYSTEM: $systemPrompt\n\nUSER: $prompt"
        } else {
            prompt
        }

        lastPrompt = initialPrompt
        lastImageUri = imageUri
        stepCount = 0
        pendingConfirmation = null

        return runLoop()
    }

    private suspend fun runLoop(): Result<String> {
        try {
            while (stepCount < MAX_TOOL_CALLS_PER_TURN) {
                Log.d(TAG, "Agent Loop Iteration ${stepCount + 1}")
                
                val request = InferenceRequest(lastPrompt, lastImageUri)
                val inferenceResult = inferenceManager.generate(request)
                
                val turnResult = when (inferenceResult) {
                    is InferenceResult.Success -> {
                        val toolCall = parser.parse(inferenceResult.text)
                        if (toolCall != null) {
                            AssistantTurnResult.ToolRequest(toolCall)
                        } else {
                            AssistantTurnResult.Text(parser.stripToolCall(inferenceResult.text))
                        }
                    }
                    is InferenceResult.Failure -> {
                        AssistantTurnResult.Error(inferenceResult.message, inferenceResult.throwable)
                    }
                }

                when (turnResult) {
                    is AssistantTurnResult.Text -> {
                        Log.i(TAG, "Model returned final text response.")
                        _agentState.value = AgentState.Completed
                        return Result.success(turnResult.content)
                    }
                    is AssistantTurnResult.ToolRequest -> {
                        stepCount++
                        val toolCall = turnResult.toolCall
                        Log.i(TAG, "Model requested tool: ${toolCall.toolName} with args: ${toolCall.arguments}")
                        
                        val tool = toolRegistry.getTool(toolCall.toolName)
                        if (tool == null) {
                            Log.w(TAG, "Tool ${toolCall.toolName} not found.")
                            lastPrompt = "Error: Tool '${toolCall.toolName}' not found. Answer directly if possible."
                            lastImageUri = null
                            continue
                        }

                        _agentState.value = AgentState.ExecutingTool(tool.name)
                        
                        val toolResult = try {
                            withTimeout(TOOL_EXECUTION_TIMEOUT_MS) {
                                tool.execute(toolCall.arguments)
                            }
                        } catch (e: TimeoutCancellationException) {
                            Log.e(TAG, "Tool ${tool.name} timed out.")
                            ToolResult.Error("Tool execution timed out.")
                        } catch (e: Exception) {
                            Log.e(TAG, "Tool ${tool.name} failed with exception", e)
                            ToolResult.Error("Tool execution failed: ${e.message}")
                        }

                        val processedResult = handleToolResult(tool, toolResult)
                        if (processedResult != null) return processedResult
                        
                        lastImageUri = null
                    }
                    is AssistantTurnResult.Error -> {
                        Log.e(TAG, "Inference error: ${turnResult.message}")
                        _agentState.value = AgentState.Error(turnResult.message)
                        return Result.failure(turnResult.throwable ?: Exception(turnResult.message))
                    }
                }
            }

            Log.w(TAG, "Reached max tool calls ($MAX_TOOL_CALLS_PER_TURN). Stopping loop.")
            _agentState.value = AgentState.Error("Max reasoning steps reached.")
            return Result.failure(Exception("Max reasoning steps reached without final answer."))

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in agent loop", e)
            _agentState.value = AgentState.Error("Unexpected error: ${e.message}")
            return Result.failure(e)
        }
    }

    private suspend fun handleToolResult(tool: AgentTool, toolResult: ToolResult): Result<String>? {
        return when (toolResult) {
            is ToolResult.Success -> {
                Log.d(TAG, "Tool ${tool.name} success: ${toolResult.data}")
                lastPrompt = "OBSERVATION from ${tool.name}: ${toolResult.data}\nContinue."
                null
            }
            is ToolResult.Error -> {
                Log.e(TAG, "Tool ${tool.name} error: ${toolResult.message}")
                lastPrompt = "TOOL_ERROR from ${tool.name}: ${toolResult.message}\nPlease try to recover or answer directly."
                null
            }
            is ToolResult.NeedsConfirmation -> {
                Log.i(TAG, "Tool ${tool.name} needs confirmation.")
                pendingConfirmation = toolResult.onConfirm
                _agentState.value = AgentState.WaitingForConfirmation(tool.name, toolResult.message)
                Result.success("I need your confirmation to ${tool.name}: ${toolResult.message}")
            }
        }
    }

    /**
     * Resumes the agent loop after a user confirmation.
     */
    suspend fun confirmAction(): Result<String> {
        val onConfirm = pendingConfirmation ?: return Result.failure(IllegalStateException("No pending confirmation"))
        pendingConfirmation = null
        
        _agentState.value = AgentState.ExecutingTool("confirming...")
        
        val toolResult = try {
            onConfirm()
        } catch (e: Exception) {
            ToolResult.Error("Action confirmation failed: ${e.message}")
        }
        
        // We need a reference to the tool name if we want to log it, 
        // but for now we'll just use a generic name or keep it simple.
        // To be better, ToolResult.NeedsConfirmation could include the tool name.
        
        // After confirmation result, we continue the loop
        when (toolResult) {
            is ToolResult.Success -> {
                lastPrompt = "OBSERVATION: Action confirmed and successful: ${toolResult.data}\nContinue."
            }
            is ToolResult.Error -> {
                lastPrompt = "OBSERVATION: Action confirmed but failed: ${toolResult.message}\nPlease try to recover."
            }
            is ToolResult.NeedsConfirmation -> {
                // Nested confirmations? Unlikely but supported
                pendingConfirmation = toolResult.onConfirm
                _agentState.value = AgentState.WaitingForConfirmation("unknown", toolResult.message)
                return Result.success("I need another confirmation: ${toolResult.message}")
            }
        }
        
        return runLoop()
    }

    /**
     * Cancels the pending action and resumes the agent loop with a cancellation observation.
     */
    suspend fun cancelAction(): Result<String> {
        pendingConfirmation = null
        lastPrompt = "OBSERVATION: User canceled the action. Please acknowledge and ask if there's anything else you can do."
        return runLoop()
    }

    fun reset() {
        _agentState.value = AgentState.Idle
        pendingConfirmation = null
        stepCount = 0
    }
}
