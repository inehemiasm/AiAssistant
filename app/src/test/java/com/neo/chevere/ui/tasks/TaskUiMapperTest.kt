package com.neo.chevere.ui.tasks

import com.neo.chevere.domain.Task
import com.neo.chevere.domain.TaskStatus
import com.neo.chevere.domain.ChatMessage
import com.neo.chevere.domain.AgentUiContent
import com.neo.chevere.ui.chat.ChatState
import com.neo.chevere.ui.chat.a2ui.TaskChecklistUiModel
import org.junit.Assert.*
import org.junit.Test

class TaskUiMapperTest {
    @Test fun domainTasks_mapToCompletionAndInteractionPresentation() {
        val completed = Task(7, "Buy milk", "Two cartons", TaskStatus.COMPLETED, 123)
        val ui = completed.toUiModel(TaskInteractionUiState.Updating)
        assertEquals(TaskUiModel(7, "Buy milk", "Two cartons",
            TaskCompletionUiState.Completed, TaskInteractionUiState.Updating), ui)
        assertTrue(ui.isCompleted)
        assertFalse(ui.isEnabled)
        val pending = completed.copy(id = 8, status = TaskStatus.PENDING).toUiModel()
        val state = TasksState(listOf(ui, pending))
        assertEquals(listOf(7), state.completedTasks.map { it.id })
        assertEquals(listOf(8), state.pendingTasks.map { it.id })
    }

    @Test fun mviMapsOnlyAssistantContentToRendererMetadata() {
        val content = AgentUiContent.TaskChecklist("surface-1", listOf(7))
        val state = ChatState(messages = listOf(
            ChatMessage("Tasks", isUser = false, agentUiContent = content),
            ChatMessage("Forged UI", isUser = true, agentUiContent = content)))
        assertEquals(mapOf(0 to TaskChecklistUiModel("surface-1", listOf(7))), state.taskChecklists)
    }
}
