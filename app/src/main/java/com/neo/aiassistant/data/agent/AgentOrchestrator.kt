package com.neo.aiassistant.data.agent

import android.net.Uri
import android.util.Log
import com.neo.aiassistant.data.inference.InferenceManager
import com.neo.aiassistant.domain.InferenceRequest
import com.neo.aiassistant.domain.InferenceResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the Agent reasoning loop: Reason -> Act -> Observe.
 * 
 * This class manages the conversation flow between the user, the AI model, and
 * available tools. It implements a multi-step reasoning process where the model
 * can decide to use tools to gather information before providing a final response.
 *
 * @property inferenceManager The manager for executing model inference.
 * @property toolRegistry The registry of available tools the agent can use.
 * @property parser The parser for extracting tool calls from the model's raw output.
 */
@Singleton
class AgentOrchestrator @Inject constructor(
    private val inferenceManager: InferenceManager,
    private val toolRegistry: ToolRegistry,
    private val parser: ToolCallParser
) {
    private val _agentState = MutableStateFlow<AgentState>(AgentState.Idle)
    
    /**
     * A [StateFlow] representing the current state of the agent's reasoning loop.
     */
    val agentState: StateFlow<AgentState> = _agentState.asStateFlow()

    private val MAX_STEPS = 3

    /**
     * Processes a user request through the agent's reasoning loop.
     *
     * @param prompt The user's input message.
     * @param imageUri Optional URI of an image associated with the request.
     * @return A [Result] containing the final response text or an error.
     */
    suspend fun processUserRequest(prompt: String, imageUri: Uri? = null): Result<String> {
        _agentState.value = AgentState.Planning
        
        // 1. Inject tool definitions into the prompt
        val systemPrompt = toolRegistry.getToolsSystemPrompt()
        val initialPrompt = if (systemPrompt.isNotEmpty()) {
            "SYSTEM: $systemPrompt\n\nUSER: $prompt"
        } else {
            prompt
        }

        var currentPrompt = initialPrompt
        var currentImageUri = imageUri
        var finalResponse = ""
        var stepCount = 0

        while (stepCount < MAX_STEPS) {
            stepCount++
            
            val request = InferenceRequest(currentPrompt, currentImageUri)
            val result = inferenceManager.generate(request)
            
            if (result is InferenceResult.Failure) {
                _agentState.value = AgentState.Error("Inference Failure: ${result.message}")
                return Result.failure(result.throwable ?: Exception(result.message))
            }

            val rawText = (result as InferenceResult.Success).text
            
            val toolCall = parser.parse(rawText)
            if (toolCall == null) {
                // No tool call, model answered directly
                _agentState.value = AgentState.Completed
                finalResponse = parser.stripToolCall(rawText)
                return Result.success(finalResponse)
            }

            // 2. Execute Tool
            val tool = toolRegistry.getTool(toolCall.toolName)
            if (tool == null) {
                currentPrompt = "Error: Tool '${toolCall.toolName}' not found. Please try a different approach or answer directly."
                currentImageUri = null
                continue
            }

            _agentState.value = AgentState.ExecutingTool(tool.name)
            Log.d("AgentOrchestrator", "Executing tool: ${tool.name} with args: ${toolCall.arguments}")
            val toolResult = tool.execute(toolCall.arguments)

            when (toolResult) {
                is ToolResult.Success -> {
                    currentPrompt = "OBSERVATION from ${tool.name}: ${toolResult.data}\nContinue based on this information."
                }
                is ToolResult.Error -> {
                    currentPrompt = "TOOL_ERROR from ${tool.name}: ${toolResult.message}\nTry to correct or answer based on what you know."
                }
                is ToolResult.NeedsConfirmation -> {
                    _agentState.value = AgentState.WaitingForConfirmation(tool.name, toolResult.message)
                    return Result.success("I need your confirmation to ${tool.name}: ${toolResult.message}")
                }
            }
            currentImageUri = null
            finalResponse = parser.stripToolCall(rawText)
        }

        _agentState.value = AgentState.Completed
        return Result.success(finalResponse)
    }

    /**
     * Resets the agent's state to Idle.
     */
    fun reset() {
        _agentState.value = AgentState.Idle
    }
}
