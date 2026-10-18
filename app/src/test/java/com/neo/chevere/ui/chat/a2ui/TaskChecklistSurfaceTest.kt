package com.neo.chevere.ui.chat.a2ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.neo.chevere.ui.tasks.TaskUiModel
import com.neo.chevere.ui.tasks.TaskCompletionUiState
import com.neo.chevere.domain.ChatMessage
import com.neo.chevere.ui.chat.components.FuturisticChatBubble
import com.neo.chevere.domain.AgentUiContent
import com.neo.chevere.ui.designsystem.HighTechAiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TaskChecklistSurfaceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun nativeRow_dispatchesActionAndReactsToRoomData() {
        val task = TaskUiModel(7, "Buy milk", "Two cartons")
        val data = mutableStateOf<TaskChecklistData>(TaskChecklistData.Ready(listOf(task)))
        val events = java.util.concurrent.CopyOnWriteArrayList<Triple<String, Int, Boolean>>()
        compose.setContent {
            HighTechAiTheme {
                Box(Modifier.width(360.dp)) {
                    FuturisticChatBubble(
                        message = ChatMessage(text = "Current tasks:\n[ID: 7] [PENDING] Buy milk", isUser = false,
                            agentUiContent = AgentUiContent.TaskChecklist("surface-1", listOf(7))),
                        taskChecklistData = data.value,
                        taskChecklist = TaskChecklistUiModel("surface-1", listOf(7)),
                        onSetTaskCompleted = { surface, id, completed -> events += Triple(surface, id, completed) }
                    )
                }
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("Buy milk")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onRoot().captureRoboImage("build/a2ui-checklist.png")
        compose.onNodeWithContentDescription("Task pending").performClick()
        compose.waitUntil(10_000) { events.isNotEmpty() }
        assertEquals(Triple("surface-1", 7, true), events.single())
        compose.runOnIdle {
            data.value = TaskChecklistData.Ready(listOf(task.copy(completion = TaskCompletionUiState.Completed)))
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Task completed")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.runOnIdle { data.value = TaskChecklistData.Ready(emptyList()) }
        compose.waitUntil(10_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("No tasks in this checklist.")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Buy milk").assertDoesNotExist()
    }

    @Test fun loadingAndUnavailable_doNotRenderStaleTasks() {
        val data = mutableStateOf<TaskChecklistData>(TaskChecklistData.Loading)
        compose.setContent {
            HighTechAiTheme {
                TaskChecklistSurface(TaskChecklistUiModel("surface-2", listOf(7)), data.value,
                    onSetCompleted = { _, _, _ -> error("No actions allowed") })
            }
        }
        compose.onNodeWithText("Loading checklist…").assertExists()
        compose.runOnIdle { data.value = TaskChecklistData.Unavailable }
        compose.onNodeWithText("Checklist unavailable. Please ask to list tasks again.").assertExists()
    }
}
