package com.neo.chevere.ui.tasks

import com.neo.chevere.domain.Task
import com.neo.chevere.domain.TaskStatus

/** ViewModels map domain tasks into presentation state before emitting it to Compose. */
internal fun Task.toUiModel(interaction: TaskInteractionUiState = TaskInteractionUiState.Enabled) = TaskUiModel(
    id = id, title = title, description = description,
    completion = when (status) {
        TaskStatus.PENDING -> TaskCompletionUiState.Pending
        TaskStatus.COMPLETED -> TaskCompletionUiState.Completed
    },
    interaction = interaction
)

/** Converts validated renderer properties into a native presentation model, without domain entities. */
internal fun taskUiModelFromProtocol(
    id: Int, title: String, description: String, completed: Boolean, enabled: Boolean
) = TaskUiModel(
    id = id, title = title, description = description,
    completion = if (completed) TaskCompletionUiState.Completed else TaskCompletionUiState.Pending,
    interaction = if (enabled) TaskInteractionUiState.Enabled else TaskInteractionUiState.Updating
)
