package com.neo.chevere.data.agent.tools

import com.neo.chevere.data.agent.ToolResult
import com.neo.chevere.data.agent.ui.AgentUiEnvelope
import com.neo.chevere.domain.TaskRepository
import com.neo.chevere.domain.Task
import com.neo.chevere.domain.TaskStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

class TaskRegistryUiTest {
    @Test fun list_requestsChecklistWithRealTaskIds() = runTest {
        val dao = mock<TaskRepository>()
        whenever(dao.getTasks()).thenReturn(listOf(Task(7, "Call Alice", "Tomorrow", TaskStatus.PENDING, 123)))
        val result = TaskRegistryTool(dao).execute(mapOf("action" to "list")) as ToolResult.Success
        val payload = requireNotNull(AgentUiEnvelope.decode(result.data))
        assertEquals(listOf(7), payload.checklist.taskIds)
        assertTrue(payload.text.contains("Call Alice"))
    }

    @Test fun emptyList_stillHasRenderableSurface() = runTest {
        val dao = mock<TaskRepository>()
        whenever(dao.getTasks()).thenReturn(emptyList())
        val result = TaskRegistryTool(dao).execute(mapOf("action" to "list")) as ToolResult.Success
        assertTrue(requireNotNull(AgentUiEnvelope.decode(result.data)).checklist.taskIds.isEmpty())
    }
}
