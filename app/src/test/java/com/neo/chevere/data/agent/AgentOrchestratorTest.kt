package com.neo.chevere.data.agent

import com.neo.chevere.core.Constants
import com.neo.chevere.data.inference.InferenceManager
import com.neo.chevere.domain.InferenceRequest
import com.neo.chevere.domain.InferenceResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class AgentOrchestratorTest {

    private lateinit var mockInferenceManager: InferenceManager
    private lateinit var mockToolRegistry: ToolRegistry
    private val parser = ToolCallParser()
    private lateinit var orchestrator: AgentOrchestrator

    private lateinit var mockWeatherTool: AgentTool
    private lateinit var mockConfirmationTool: AgentTool

    @Before
    fun setup() {
        mockInferenceManager = mock()
        mockToolRegistry = mock()
        orchestrator = AgentOrchestrator(mockInferenceManager, mockToolRegistry, parser)

        mockWeatherTool = mock {
            on { name } doReturn "get_weather"
            on { description } doReturn "Gets weather for a location"
            on { inputSchema } doReturn "location: string"
        }

        mockConfirmationTool = mock {
            on { name } doReturn "delete_file"
            on { description } doReturn "Deletes a file"
            on { inputSchema } doReturn "path: string"
        }

        whenever(mockToolRegistry.getToolsSystemPrompt()).doReturn("Mock System Prompt")
        whenever(mockToolRegistry.getTool("get_weather")).doReturn(mockWeatherTool)
        whenever(mockToolRegistry.getTool("delete_file")).doReturn(mockConfirmationTool)
    }

    @Test
    fun processUserRequest_successFlow() = runTest {
        // Mock Tool Execution
        whenever(mockWeatherTool.execute(any())).doAnswer { invocation ->
            val args = invocation.arguments[0] as Map<*, *>
            assertEquals("Austin", args["location"])
            ToolResult.Success("75 degrees, Sunny")
        }

        // Mock Inference Manager responses depending on the input prompt
        whenever(mockInferenceManager.generateStream(any())).doAnswer { invocation ->
            val request = invocation.arguments[0] as InferenceRequest
            val prompt = request.prompt
            if (prompt.contains("OBSERVATION from get_weather")) {
                // Second turn: Model returns final answer
                flowOf(InferenceResult.Success("The weather in Austin is sunny and 75 degrees."))
            } else {
                // First turn: Model returns tool call
                flowOf(InferenceResult.Success("[TOOL_CALL: get_weather, location=\"Austin\"]"))
            }
        }

        val result = orchestrator.processUserRequest("How is the weather in Austin?")

        assertTrue(result.isSuccess)
        assertEquals("The weather in Austin is sunny and 75 degrees.", result.getOrNull())
    }

    @Test
    fun processUserRequest_errorPropagation() = runTest {
        // Mock Tool Execution failing
        whenever(mockWeatherTool.execute(any())).doReturn(ToolResult.Error("API Limit Reached"))

        // Mock Inference Manager responses
        whenever(mockInferenceManager.generateStream(any())).doAnswer { invocation ->
            val request = invocation.arguments[0] as InferenceRequest
            val prompt = request.prompt
            if (prompt.contains("TOOL_ERROR from get_weather")) {
                flowOf(InferenceResult.Success("I could not fetch the weather because the API limit was reached."))
            } else {
                flowOf(InferenceResult.Success("[TOOL_CALL: get_weather, location=\"Austin\"]"))
            }
        }

        val result = orchestrator.processUserRequest("How is the weather in Austin?")

        assertTrue(result.isSuccess)
        assertEquals(
            "I could not fetch the weather because the API limit was reached.",
            result.getOrNull()
        )
    }

    @Test
    fun processUserRequest_maxToolCallLimitPreventsInfiniteLoop() = runTest {
        // Mock Inference Manager always returning a tool call to simulate an infinite loop
        whenever(mockInferenceManager.generateStream(any())).doReturn(
            flowOf(InferenceResult.Success("[TOOL_CALL: get_weather, location=\"Austin\"]"))
        )
        whenever(mockWeatherTool.execute(any())).doReturn(
            ToolResult.Success("75 degrees")
        )

        val result = orchestrator.processUserRequest("How is the weather?")

        assertTrue(result.isSuccess)
        // Fallback summary is used when loop ends
        assertEquals("75 degrees", result.getOrNull())
    }

    @Test
    fun processUserRequest_confirmationFlow_cancel() = runTest {
        // Mock Tool Execution requiring confirmation
        var confirmCalled = false
        val needsConfirmationResult = ToolResult.NeedsConfirmation(
            message = "Are you sure you want to delete file.txt?"
        ) {
            confirmCalled = true
            ToolResult.Success("File deleted")
        }
        whenever(mockConfirmationTool.execute(any())).doReturn(needsConfirmationResult)

        // Mock Inference Manager returns tool call
        whenever(mockInferenceManager.generateStream(any())).doAnswer {
            flowOf(InferenceResult.Success("[TOOL_CALL: delete_file, path=\"file.txt\"]"))
        }

        val initialResult = orchestrator.processUserRequest("Delete my file")
        assertTrue(initialResult.isSuccess)
        assertEquals(
            "I need your confirmation to proceed with delete_file: Are you sure you want to delete file.txt?",
            initialResult.getOrNull()
        )
        assertTrue(orchestrator.agentState.value is AgentState.WaitingForConfirmation)

        // User cancels confirmation
        whenever(mockInferenceManager.generateStream(any())).doAnswer { invocation ->
            val request = invocation.arguments[0] as InferenceRequest
            if (request.prompt.contains("User canceled the action")) {
                flowOf(InferenceResult.Success("Deletion canceled by user request."))
            } else {
                flowOf(InferenceResult.Failure("Should not reach here"))
            }
        }

        val cancelResult = orchestrator.cancelAction()
        assertTrue(cancelResult.isSuccess)
        assertEquals("Deletion canceled by user request.", cancelResult.getOrNull())
        assertTrue(!confirmCalled)
    }

    @Test
    fun processUserRequest_confirmationFlow_confirm() = runTest {
        // Mock Tool Execution requiring confirmation
        var confirmCalled = false
        val needsConfirmationResult = ToolResult.NeedsConfirmation(
            message = "Are you sure you want to delete file.txt?"
        ) {
            confirmCalled = true
            ToolResult.Success("File deleted")
        }
        whenever(mockConfirmationTool.execute(any())).doReturn(needsConfirmationResult)

        // Mock Inference Manager returns tool call
        whenever(mockInferenceManager.generateStream(any())).doAnswer {
            flowOf(InferenceResult.Success("[TOOL_CALL: delete_file, path=\"file.txt\"]"))
        }

        val initialResult = orchestrator.processUserRequest("Delete my file")
        assertTrue(initialResult.isSuccess)
        assertEquals(
            "I need your confirmation to proceed with delete_file: Are you sure you want to delete file.txt?",
            initialResult.getOrNull()
        )
        assertTrue(orchestrator.agentState.value is AgentState.WaitingForConfirmation)

        // User confirms the action
        whenever(mockInferenceManager.generateStream(any())).doAnswer { invocation ->
            val request = invocation.arguments[0] as InferenceRequest
            if (request.prompt.contains("Action successful: File deleted")) {
                flowOf(InferenceResult.Success("I have successfully deleted file.txt."))
            } else {
                flowOf(InferenceResult.Failure("Should not reach here"))
            }
        }

        val confirmResult = orchestrator.confirmAction()
        assertTrue(confirmResult.isSuccess)
        assertEquals("I have successfully deleted file.txt.", confirmResult.getOrNull())
        assertTrue(confirmCalled)
    }
}
