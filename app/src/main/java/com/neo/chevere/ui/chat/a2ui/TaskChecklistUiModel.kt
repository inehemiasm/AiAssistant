package com.neo.chevere.ui.chat.a2ui

import com.neo.chevere.domain.AgentUiContent

/** Presentation metadata for a chat surface; contains no persistence or renderer-specific types. */
data class TaskChecklistUiModel(val surfaceId: String, val taskIds: List<Int>)

/** MVI maps the domain content request before supplying it to the A2UI renderer. */
internal fun AgentUiContent.TaskChecklist.toUiModel() = TaskChecklistUiModel(surfaceId, taskIds)
