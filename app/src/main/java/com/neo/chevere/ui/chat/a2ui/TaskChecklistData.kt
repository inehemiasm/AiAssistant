package com.neo.chevere.ui.chat.a2ui

import com.neo.chevere.ui.tasks.TaskUiModel
import com.neo.chevere.ui.tasks.TaskInteractionUiState

/** Presentation-only task data; loading and errors cannot enable stale snapshot actions. */
sealed interface TaskChecklistData {
    data object Loading : TaskChecklistData
    data class Ready(val tasks: List<TaskUiModel>) : TaskChecklistData {
        val updatingIds: Set<Int> get() = tasks.filter { !it.isEnabled }.map { it.id }.toSet()

        /** Centralizes control availability so layouts only read presentation state. */
        fun withUpdatingIds(ids: Set<Int>) = copy(tasks = tasks.map { task ->
            task.copy(interaction = if (task.id in ids) TaskInteractionUiState.Updating else TaskInteractionUiState.Enabled)
        })
    }
    data object Unavailable : TaskChecklistData
}
