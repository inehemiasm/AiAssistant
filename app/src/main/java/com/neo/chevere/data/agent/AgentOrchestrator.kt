package com.neo.chevere.data.agent

import android.net.Uri
import android.util.Log
import com.neo.chevere.core.Constants
import com.neo.chevere.data.inference.InferenceManager
import com.neo.chevere.domain.InferenceRequest
import com.neo.chevere.domain.InferenceResult
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AgentOrchestrator"

/**
 * Orchestrates the Agent reasoning loop: Reason -> Act -> Observe.
 */
@Singleton
class AgentOrchestrator @Inject constructor(
    private val inferenceManager: InferenceManager,
    private val toolRegistry: ToolRegistry,
    private val parser: ToolCallParser
) {
    private val _agentState = MutableStateFlow<AgentState>(AgentState.Idle)
    val agentState: StateFlow<AgentState> = _agentState.asStateFlow()

    private val loopMutex = Mutex()
    private var pendingConfirmation: (suspend () -> ToolResult)? = null
    private var lastPrompt: String = ""
    private var lastImageUri: Uri? = null
    private var stepCount = 0
    private var lastToolSummary: String? = null

    suspend fun processUserRequest(prompt: String, imageUri: Uri? = null): Result<String> = loopMutex.withLock {
        _agentState.value = AgentState.Planning
        Log.i(TAG, ">>> Starting agent loop for user prompt: \"$prompt\"")
        
        val systemPrompt = toolRegistry.getToolsSystemPrompt()
        val initialPrompt = if (systemPrompt.isNotEmpty()) {
            "${Constants.Agent.SYSTEM_PROMPT_PREFIX}$systemPrompt${Constants.Agent.USER_PROMPT_PREFIX}$prompt"
        } else {
            prompt
        }

        lastPrompt = initialPrompt
        lastImageUri = imageUri
        stepCount = 0
        pendingConfirmation = null
        lastToolSummary = null

        return runLoopInternal()
    }

    private suspend fun runLoopInternal(): Result<String> {
        try {
            while (stepCount < Constants.Agent.MAX_TOOL_CALLS_PER_TURN) {
                Log.d(TAG, "Loop iteration ${stepCount + 1}")
                
                val request = InferenceRequest(lastPrompt, lastImageUri)
                val inferenceResult = inferenceManager.generate(request)
                
                val turnResult = when (inferenceResult) {
                    is InferenceResult.Success -> {
                        val text = inferenceResult.text
                        Log.d(TAG, "Model raw output: \"$text\"")
                        val toolCall = parser.parse(text)
                        if (toolCall != null) {
                            AssistantTurnResult.ToolRequest(toolCall)
                        } else {
                            AssistantTurnResult.Text(parser.stripToolCall(text))
                        }
                    }
                    is InferenceResult.Failure -> {
                        AssistantTurnResult.Error(inferenceResult.message, inferenceResult.throwable)
                    }
                }

                when (turnResult) {
                    is AssistantTurnResult.Text -> {
                        val originalText = turnResult.content
                        val processedText = finalizeResponse(originalText)
                        
                        if (stepCount > 0 && isVeryShort(processedText) && lastToolSummary != null) {
                            Log.d(TAG, "Model returned short text after tool call. Forcing summary.")
                            lastPrompt = "OBSERVATION: Action completed. Result: $lastToolSummary\n\nPlease provide a friendly confirmation to the user about what was done."
                            stepCount++
                            continue
                        }

                        Log.i(TAG, "<<< Loop finished. Final text: \"$processedText\"")
                        _agentState.value = AgentState.Completed
                        return Result.success(processedText)
                    }
                    is AssistantTurnResult.ToolRequest -> {
                        stepCount++
                        val toolCall = turnResult.toolCall
                        Log.i(TAG, "Tool request: ${toolCall.toolName} with ${toolCall.arguments}")
                        
                        val tool = toolRegistry.getTool(toolCall.toolName)
                        if (tool == null) {
                            Log.w(TAG, "Tool '${toolCall.toolName}' not found.")
                            lastPrompt = "${Constants.Agent.TOOL_ERROR_PREFIX}Tool '${toolCall.toolName}' not found. Please proceed without it or inform the user."
                            lastImageUri = null
                            continue
                        }

                        _agentState.value = AgentState.ExecutingTool(tool.name)
                        
                        val toolResult = try {
                            withTimeout(Constants.Agent.TOOL_EXECUTION_TIMEOUT_MS) {
                                tool.execute(toolCall.arguments)
                            }
                        } catch (e: TimeoutCancellationException) {
                            Log.e(TAG, "Tool ${tool.name} timed out.")
                            ToolResult.Error("Tool execution timed out.")
                        } catch (e: Exception) {
                            Log.e(TAG, "Tool ${tool.name} failed", e)
                            ToolResult.Error("Tool execution failed: ${e.message}")
                        }

                        val stopLoopResult = handleToolResult(tool, toolResult)
                        if (stopLoopResult != null) return stopLoopResult
                        
                        lastImageUri = null
                    }
                    is AssistantTurnResult.Error -> {
                        Log.e(TAG, "Inference error: ${turnResult.message}")
                        _agentState.value = AgentState.Error(turnResult.message)
                        return Result.failure(turnResult.throwable ?: Exception(turnResult.message))
                    }
                }
            }

            Log.w(TAG, "Reached max tool calls (${Constants.Agent.MAX_TOOL_CALLS_PER_TURN}).")
            val finalFallback = lastToolSummary ?: "I've completed the requested actions."
            _agentState.value = AgentState.Completed
            return Result.success(finalFallback)

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in agent loop", e)
            _agentState.value = AgentState.Error("Unexpected error: ${e.message}")
            return Result.failure(e)
        }
    }

    private fun isVeryShort(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.length < 5 || trimmed.split(" ").size < 3
    }

    private fun finalizeResponse(text: String): String {
        val trimmed = text.trim()
        val isGarbage = trimmed.isEmpty() || trimmed == "-" || trimmed == "|" || trimmed == "." || trimmed.length < 2
        
        return if (isGarbage && lastToolSummary != null) {
            Log.w(TAG, "Model returned garbage text after tool call. Using fallback summary: $lastToolSummary")
            lastToolSummary!!
        } else if (trimmed.isEmpty()) {
            "I'm sorry, I encountered an issue generating a response."
        } else {
            trimmed
        }
    }

    private suspend fun handleToolResult(tool: AgentTool, toolResult: ToolResult): Result<String>? {
        return when (toolResult) {
            is ToolResult.Success -> {
                Log.d(TAG, "Tool ${tool.name} SUCCESS: ${toolResult.data}")
                lastToolSummary = toolResult.data
                lastPrompt = "${Constants.Agent.OBSERVATION_PREFIX}${tool.name}: ${toolResult.data}\n\nAction completed successfully. Please provide a brief final confirmation to the user."
                null
            }
            is ToolResult.Error -> {
                Log.e(TAG, "Tool ${tool.name} ERROR: ${toolResult.message}")
                lastToolSummary = "Error: ${toolResult.message}"
                lastPrompt = "TOOL_ERROR from ${tool.name}: ${toolResult.message}\n\nPlease explain the error to the user or try an alternative."
                null
            }
            is ToolResult.NeedsConfirmation -> {
                Log.i(TAG, "Tool ${tool.name} needs confirmation: ${toolResult.message}")
                pendingConfirmation = toolResult.onConfirm
                _agentState.value = AgentState.WaitingForConfirmation(tool.name, toolResult.message)
                Result.success("I need your confirmation to proceed with ${tool.name}: ${toolResult.message}")
            }
        }
    }

    suspend fun confirmAction(): Result<String> = loopMutex.withLock {
        val onConfirm = pendingConfirmation ?: return Result.failure(IllegalStateException("No pending confirmation"))
        pendingConfirmation = null
        
        _agentState.value = AgentState.ExecutingTool("confirming...")
        
        val toolResult = try {
            onConfirm()
        } catch (e: Exception) {
            ToolResult.Error("Action confirmation failed: ${e.message}")
        }
        
        when (toolResult) {
            is ToolResult.Success -> {
                lastToolSummary = toolResult.data
                lastPrompt = "OBSERVATION: User confirmed. Action successful: ${toolResult.data}\nContinue and provide a final response."
            }
            is ToolResult.Error -> {
                lastToolSummary = "Error: ${toolResult.message}"
                lastPrompt = "OBSERVATION: User confirmed, but action failed: ${toolResult.message}\nPlease inform the user."
            }
            is ToolResult.NeedsConfirmation -> {
                pendingConfirmation = toolResult.onConfirm
                _agentState.value = AgentState.WaitingForConfirmation("nested", toolResult.message)
                return Result.success("Additional confirmation needed: ${toolResult.message}")
            }
        }
        
        return runLoopInternal()
    }

    suspend fun cancelAction(): Result<String> = loopMutex.withLock {
        pendingConfirmation = null
        lastPrompt = "OBSERVATION: User canceled the action. Please acknowledge this cancellation."
        return runLoopInternal()
    }

    fun reset() {
        _agentState.value = AgentState.Idle
        pendingConfirmation = null
        stepCount = 0
        lastToolSummary = null
    }
}
