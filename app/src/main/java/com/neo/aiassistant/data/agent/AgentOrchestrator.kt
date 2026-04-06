package com.neo.aiassistant.data.agent

import android.net.Uri
import android.util.Log
import com.neo.aiassistant.data.inference.LlmRuntimeManager
import com.neo.aiassistant.data.inference.LlmResponseMapper
import com.neo.aiassistant.data.inference.MultimodalMessageFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the Agent loop: Reason -> Act -> Observe.
 * Inspired by the "Agent" pattern in Google AI Edge Gallery.
 */
@Singleton
class AgentOrchestrator @Inject constructor(
    private val runtimeManager: LlmRuntimeManager,
    private val toolRegistry: ToolRegistry,
    private val parser: ToolCallParser,
    private val responseMapper: LlmResponseMapper,
    private val messageFactory: MultimodalMessageFactory
) {
    private val _agentState = MutableStateFlow<AgentState>(AgentState.Idle)
    val agentState: StateFlow<AgentState> = _agentState.asStateFlow()

    private val MAX_STEPS = 3

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
            
            val message = messageFactory.createMessage(currentPrompt, currentImageUri)
            val result = runtimeManager.sendMessage(message)
            
            if (result.isFailure) {
                _agentState.value = AgentState.Error("LLM Failure: ${result.exceptionOrNull()?.message}")
                return Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
            }

            val responseMessage = result.getOrThrow()
            val rawText = responseMapper.mapToString(responseMessage)
            
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

    fun reset() {
        _agentState.value = AgentState.Idle
    }
}
