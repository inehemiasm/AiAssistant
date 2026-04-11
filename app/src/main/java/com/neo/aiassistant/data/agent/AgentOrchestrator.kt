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

        var currentPrompt = initialPrompt
        var currentImageUri = imageUri
        var stepCount = 0

        try {
            while (stepCount < MAX_TOOL_CALLS_PER_TURN) {
                Log.d(TAG, "Agent Loop Iteration ${stepCount + 1}")
                
                val request = InferenceRequest(currentPrompt, currentImageUri)
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
                            currentPrompt = "Error: Tool '${toolCall.toolName}' not found. Answer directly if possible."
                            currentImageUri = null
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

                        when (toolResult) {
                            is ToolResult.Success -> {
                                Log.d(TAG, "Tool ${tool.name} success: ${toolResult.data}")
                                currentPrompt = "OBSERVATION from ${tool.name}: ${toolResult.data}\nContinue."
                            }
                            is ToolResult.Error -> {
                                Log.e(TAG, "Tool ${tool.name} error: ${toolResult.message}")
                                currentPrompt = "TOOL_ERROR from ${tool.name}: ${toolResult.message}\nPlease try to recover or answer directly."
                            }
                            is ToolResult.NeedsConfirmation -> {
                                Log.i(TAG, "Tool ${tool.name} needs confirmation.")
                                _agentState.value = AgentState.WaitingForConfirmation(tool.name, toolResult.message)
                                // We stop the loop and return a special message or wait for UI. 
                                // For now, we'll return the confirmation request as the result.
                                return Result.success("I need your confirmation to ${tool.name}: ${toolResult.message}")
                            }
                        }
                        currentImageUri = null
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

    fun reset() {
        _agentState.value = AgentState.Idle
    }
}
