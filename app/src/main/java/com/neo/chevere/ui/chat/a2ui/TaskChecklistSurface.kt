package com.neo.chevere.ui.chat.a2ui

import androidx.a2ui.compose.runtime.A2uiComponentState
import androidx.a2ui.compose.runtime.observeA2uiComponentState
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.a2ui.compose.ui.A2uiMessageProcessor
import androidx.a2ui.model.processor.A2uiActionInterceptor
import androidx.a2ui.model.protocol.A2uiComponentPayload
import androidx.a2ui.model.protocol.A2uiCreateSurfaceMessage
import androidx.a2ui.model.protocol.A2uiEventAction
import androidx.a2ui.model.protocol.A2uiUpdateComponentsMessage
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.neo.chevere.R
import com.neo.chevere.domain.AgentUiContent
import kotlinx.coroutines.launch

/** Hosts AndroidX processing while the chat card is composed, including after history restore. */
@Composable
fun TaskChecklistSurface(
    content: AgentUiContent.TaskChecklist,
    data: TaskChecklistData,
    onSetCompleted: (surfaceId: String, taskId: Int, completed: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val callback by rememberUpdatedState(onSetCompleted)
    val processor = remember(content.surfaceId) {
        A2uiMessageProcessor(
            catalogs = listOf(ChevereA2uiCatalog),
            interceptors = listOf(A2uiActionInterceptor { action ->
                if (action is A2uiEventAction && action.eventName == SET_TASK_COMPLETED &&
                    action.surfaceId == content.surfaceId && action.componentId == "root") {
                    val taskId = (action.context["taskId"] as? String)?.toIntOrNull()
                    val completed = action.context["completed"] as? Boolean
                    if (taskId != null && taskId in content.taskIds && completed != null) {
                        callback(action.surfaceId, taskId, completed)
                    }
                }
                // This catalog supports task actions only; never execute arbitrary functions.
                null
            })
        )
    }
    LaunchedEffect(processor) {
        launch { processor.collectMessages() }
        launch { processor.outboundEvents.collect { } }
        processor.processMessage(A2uiCreateSurfaceMessage(content.surfaceId, CHEVERE_CATALOG_ID))
    }
    LaunchedEffect(processor, data, content.taskIds) {
        if (data is TaskChecklistData.Ready) {
            val tasksById = data.tasks.associateBy { it.id }
            val rows = content.taskIds.mapNotNull { tasksById[it] }.map { task ->
                mapOf(
                    "id" to task.id.toString(), "title" to task.title,
                    "description" to task.description,
                    "completed" to (task.status == com.neo.chevere.data.datasource.local.TaskStatus.COMPLETED),
                    "enabled" to (task.id !in data.updatingIds)
                )
            }
            processor.processMessage(A2uiUpdateComponentsMessage(content.surfaceId, listOf(
                A2uiComponentPayload("root", "TaskChecklist", mapOf("tasks" to rows))
            )))
        }
    }
    val surfaces by processor.activeSurfaces.collectAsState()
    when (data) {
        TaskChecklistData.Loading -> Text(stringResource(R.string.a2ui_loading), modifier)
        TaskChecklistData.Unavailable -> Text(stringResource(R.string.a2ui_unavailable), modifier)
        is TaskChecklistData.Ready -> {
            val surface = surfaces.firstOrNull { it.id == content.surfaceId }
            if (surface == null) Text(stringResource(R.string.a2ui_loading), modifier)
            else when (val state = observeA2uiComponentState(surface)) {
                A2uiComponentState.Loading -> Text(stringResource(R.string.a2ui_loading), modifier)
                is A2uiComponentState.Error -> Text(stringResource(R.string.a2ui_unavailable), modifier)
                is A2uiComponentState.Success -> A2uiComponent(state.component, modifier)
            }
        }
    }
}
