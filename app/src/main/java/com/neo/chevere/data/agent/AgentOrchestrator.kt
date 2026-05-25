package com.neo.chevere.data.agent

import android.net.Uri
import com.neo.chevere.core.Constants
import com.neo.chevere.core.PiiUtils
import com.neo.chevere.data.inference.InferenceManager
import com.neo.chevere.domain.ContactsPermissionException
import com.neo.chevere.domain.CalendarPermissionException
import com.neo.chevere.domain.InferenceRequest
import com.neo.chevere.domain.InferenceResult
import com.neo.chevere.domain.LocationPermissionException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import timber.log.Timber
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

    private val _activePartialResponse = MutableStateFlow("")
    val activePartialResponse: StateFlow<String> = _activePartialResponse.asStateFlow()

    private val loopMutex = Mutex()
    private var pendingConfirmation: (suspend () -> ToolResult)? = null
    private var lastPrompt: String = ""
    private var lastImageUri: Uri? = null
    private var stepCount = 0
    private var lastToolSummary: String? = null
    private val currentSteps = mutableListOf<AgentStep>()

    suspend fun processUserRequest(
        prompt: String,
        imageUri: Uri? = null,
        conversationContext: String? = null
    ): Result<String> = loopMutex.withLock {
        _activePartialResponse.value = ""
        currentSteps.clear()
        _agentState.value = AgentState.Planning(currentSteps.toList())
        // Scrub log output to avoid PII in logcat
        Timber.tag(TAG).i(">>> Starting agent loop for user prompt: \"${PiiUtils.scrub(prompt)}\"")

        val systemPrompt = toolRegistry.getToolsSystemPrompt()
        val contextualUserPrompt = buildString {
            if (!conversationContext.isNullOrBlank()) {
                append(conversationContext)
                append("\n\n")
            }
            append(Constants.ContextWindow.CURRENT_REQUEST_HEADER)
            if (imageUri != null) {
                append(" [current message includes an image]")
            }
            append(":\n")
            append(Constants.ContextWindow.CURRENT_REQUEST_INSTRUCTION)
            append("\n")
            append(prompt)
        }
        val initialPrompt = if (systemPrompt.isNotEmpty()) {
            "${Constants.Agent.SYSTEM_PROMPT_PREFIX}$systemPrompt${Constants.Agent.USER_PROMPT_PREFIX}$contextualUserPrompt"
        } else {
            contextualUserPrompt
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
                Timber.tag(TAG).d("Loop iteration ${stepCount + 1}")

                val request = InferenceRequest(lastPrompt, lastImageUri)
                var lastResult: InferenceResult = InferenceResult.Failure("No output generated")
                var isStreamingEnabled = true

                _activePartialResponse.value = ""

                inferenceManager.generateStream(request).collect { result ->
                    lastResult = result
                    if (result is InferenceResult.Success) {
                        val text = result.text
                        val trimmed = text.trimStart()
                        if (trimmed.contains(Constants.Agent.TOOL_CALL_PREFIX)) {
                            isStreamingEnabled = false
                            _activePartialResponse.value = ""
                        } else if (isStreamingEnabled) {
                            val prefix = Constants.Agent.TOOL_CALL_PREFIX
                            val isPrefix = if (trimmed.length < prefix.length) {
                                prefix.startsWith(trimmed)
                            } else {
                                trimmed.startsWith(prefix)
                            }
                            if (!isPrefix) {
                                _activePartialResponse.value = text
                            }
                        }
                    }
                }

                val inferenceResult = lastResult

                val turnResult = when (inferenceResult) {
                    is InferenceResult.Success -> {
                        val text = inferenceResult.text
                        // Log scrubbed text
                        Timber.tag(TAG).d("Model raw output: \"${PiiUtils.scrub(text)}\"")
                        val toolCall = parser.parse(text)
                        if (toolCall != null) {
                            AssistantTurnResult.ToolRequest(toolCall)
                        } else {
                            AssistantTurnResult.Text(parser.stripToolCall(text))
                        }
                    }

                    is InferenceResult.ImageSuccess -> {
                        AssistantTurnResult.Error("Image inference result is not supported in the agent loop.")
                    }

                    is InferenceResult.Failure -> {
                        AssistantTurnResult.Error(
                            inferenceResult.message,
                            inferenceResult.throwable
                        )
                    }
                }

                // Record intermediate reasoning steps
                when (turnResult) {
                    is AssistantTurnResult.ToolRequest -> {
                        val toolCall = turnResult.toolCall
                        val rawText = (inferenceResult as InferenceResult.Success).text
                        val thought = parser.stripToolCall(rawText)
                        val step = AgentStep(
                            thought = thought.takeIf { it.isNotBlank() },
                            toolCall = toolCall,
                            result = null
                        )
                        currentSteps.add(step)
                        _agentState.value = AgentState.Planning(currentSteps.toList())
                    }
                    else -> {}
                }

                when (turnResult) {
                    is AssistantTurnResult.Text -> {
                        val originalText = turnResult.content
                        val processedText = finalizeResponse(originalText)

                        if (stepCount > 0 && isVeryShort(processedText) && lastToolSummary != null) {
                            Timber.tag(TAG).d(
                                "Model returned short text after tool call. Forcing summary."
                            )
                            lastPrompt =
                                "OBSERVATION: Action completed. Result: $lastToolSummary\n\nPlease provide a friendly confirmation to the user about what was done."
                            stepCount++
                            continue
                        }

                        // Log scrubbed final text
                        Timber.tag(TAG).i("<<< Loop finished. Final text: \"${PiiUtils.scrub(processedText)}\"")
                        _activePartialResponse.value = ""
                        _agentState.value = AgentState.Completed(currentSteps.toList())
                        return Result.success(processedText)
                    }

                    is AssistantTurnResult.ToolRequest -> {
                        stepCount++
                        val toolCall = turnResult.toolCall
                        Timber.tag(TAG).i("Tool request: ${toolCall.toolName} with ${PiiUtils.scrub(toolCall.arguments.toString())}")

                        val tool = toolRegistry.getTool(toolCall.toolName)
                        if (tool == null) {
                            Timber.tag(TAG).w("Tool '${toolCall.toolName}' not found.")
                            lastPrompt =
                                "${Constants.Agent.TOOL_ERROR_PREFIX}Tool '${toolCall.toolName}' not found. Please proceed without it or inform the user."
                            lastImageUri = null
                            
                            val lastIndex = currentSteps.indexOfLast { it.toolCall == toolCall }
                            if (lastIndex != -1) {
                                currentSteps[lastIndex] = currentSteps[lastIndex].copy(
                                    result = ToolResult.Error("Tool '${toolCall.toolName}' not found.")
                                )
                            }
                            continue
                        }

                        _agentState.value = AgentState.ExecutingTool(tool.name, currentSteps.toList())

                        val toolResult = try {
                            withTimeout(tool.executionTimeoutMs()) {
                                tool.execute(toolCall.arguments)
                            }
                        } catch (e: TimeoutCancellationException) {
                            Timber.tag(TAG).e("Tool ${tool.name} timed out.")
                            ToolResult.Error("Tool execution timed out.")
                        } catch (e: Exception) {
                            Timber.tag(TAG).e(e, "Tool ${tool.name} failed")
                            ToolResult.Error("Tool execution failed: ${e.message}")
                        }

                        val lastIndex = currentSteps.indexOfLast { it.toolCall == toolCall }
                        if (lastIndex != -1) {
                            currentSteps[lastIndex] = currentSteps[lastIndex].copy(result = toolResult)
                        }

                        val stopLoopResult = handleToolResult(tool, toolResult)
                        if (stopLoopResult != null) return stopLoopResult

                        lastImageUri = null
                    }

                    is AssistantTurnResult.Error -> {
                        Timber.tag(TAG).e("Inference error: ${turnResult.message}")
                        _agentState.value = AgentState.Error(turnResult.message, currentSteps.toList())
                        return Result.failure(turnResult.throwable ?: Exception(turnResult.message))
                    }
                }
            }

            Timber.tag(TAG).w("Reached max tool calls (${Constants.Agent.MAX_TOOL_CALLS_PER_TURN}).")
            val finalFallback = lastToolSummary ?: "I've completed the requested actions."
            _agentState.value = AgentState.Completed(currentSteps.toList())
            return Result.success(finalFallback)

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Unexpected error in agent loop")
            _agentState.value = AgentState.Error("Unexpected error: ${e.message}", currentSteps.toList())
            return Result.failure(e)
        }
    }

    private fun isVeryShort(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.length < 5 || trimmed.split(" ").size < 3
    }

    private fun AgentTool.executionTimeoutMs(): Long {
        return if (name == Constants.Agent.IMAGE_GENERATION_TOOL_NAME) {
            Constants.Agent.IMAGE_GENERATION_TOOL_TIMEOUT_MS
        } else {
            Constants.Agent.TOOL_EXECUTION_TIMEOUT_MS
        }
    }

    private fun finalizeResponse(text: String): String {
        val trimmed = text.trim()
        val isGarbage =
            trimmed.isEmpty() || trimmed == "-" || trimmed == "|" || trimmed == "." || trimmed.length < 2

        return if (isGarbage && lastToolSummary != null) {
            Timber.tag(TAG).w(
                "Model returned garbage text after tool call. Using fallback summary: $lastToolSummary"
            )
            lastToolSummary!!
        } else trimmed.ifEmpty {
            "I'm sorry, I encountered an issue generating a response."
        }
    }

    private suspend fun handleToolResult(tool: AgentTool, toolResult: ToolResult): Result<String>? {
        return when (toolResult) {
            is ToolResult.Success -> {
                Timber.tag(TAG).d("Tool ${tool.name} SUCCESS: ${PiiUtils.scrub(toolResult.data)}")
                lastToolSummary = toolResult.data
                if (toolResult.data.startsWith(Constants.Agent.IMAGE_GENERATION_RESULT_PREFIX)) {
                    _agentState.value = AgentState.Completed(currentSteps.toList())
                    return Result.success(toolResult.data)
                }
                lastPrompt =
                    "${Constants.Agent.OBSERVATION_PREFIX}${tool.name}: ${toolResult.data}\n\nAction completed successfully. Please provide a brief final confirmation to the user."
                null
            }

            is ToolResult.Error -> {
                Timber.tag(TAG).e("Tool ${tool.name} ERROR: ${toolResult.message}")
                if (toolResult.message == "LOCATION_PERMISSION_REQUIRED") {
                    _agentState.value = AgentState.Idle
                    return Result.failure(LocationPermissionException())
                }
                if (toolResult.message == "CONTACTS_PERMISSION_REQUIRED") {
                    _agentState.value = AgentState.Idle
                    return Result.failure(ContactsPermissionException())
                }
                if (toolResult.message == "CALENDAR_PERMISSION_REQUIRED") {
                    _agentState.value = AgentState.Idle
                    return Result.failure(CalendarPermissionException())
                }
                lastToolSummary = "Error: ${toolResult.message}"
                lastPrompt =
                    "${Constants.Agent.TOOL_ERROR_FROM_PREFIX}${tool.name}: ${toolResult.message}\n\nPlease explain the error to the user or try an alternative."
                null
            }

            is ToolResult.NeedsConfirmation -> {
                Timber.tag(TAG).i("Tool ${tool.name} needs confirmation: ${toolResult.message}")
                pendingConfirmation = toolResult.onConfirm
                _agentState.value = AgentState.WaitingForConfirmation(tool.name, toolResult.message, currentSteps.toList())
                Result.success("I need your confirmation to proceed with ${tool.name}: ${toolResult.message}")
            }
        }
    }

    suspend fun confirmAction(): Result<String> = loopMutex.withLock {
        val onConfirm = pendingConfirmation
            ?: return Result.failure(IllegalStateException("No pending confirmation"))
        pendingConfirmation = null

        _agentState.value = AgentState.ExecutingTool("confirming...", currentSteps.toList())

        val toolResult = try {
            onConfirm()
        } catch (e: Exception) {
            ToolResult.Error("Action confirmation failed: ${e.message}")
        }

        val lastIndex = currentSteps.indexOfLast { it.result is ToolResult.NeedsConfirmation }
        if (lastIndex != -1) {
            currentSteps[lastIndex] = currentSteps[lastIndex].copy(result = toolResult)
        }

        when (toolResult) {
            is ToolResult.Success -> {
                lastToolSummary = toolResult.data
                lastPrompt =
                    "OBSERVATION: User confirmed. Action successful: ${toolResult.data}\nContinue and provide a final response."
            }

            is ToolResult.Error -> {
                lastToolSummary = "Error: ${toolResult.message}"
                lastPrompt =
                    "OBSERVATION: User confirmed, but action failed: ${toolResult.message}\nPlease inform the user."
            }

            is ToolResult.NeedsConfirmation -> {
                pendingConfirmation = toolResult.onConfirm
                _agentState.value = AgentState.WaitingForConfirmation("nested", toolResult.message, currentSteps.toList())
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
        currentSteps.clear()
    }
}
