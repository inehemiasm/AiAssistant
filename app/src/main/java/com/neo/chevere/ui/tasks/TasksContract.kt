package com.neo.chevere.ui.tasks

import com.neo.chevere.core.UiEffect
import com.neo.chevere.core.UiIntent
import com.neo.chevere.core.UiState

/** Presentation-only task state; grouping belongs to MVI rather than Compose layout code. */
data class TasksState(
    val tasks: List<TaskUiModel> = emptyList()
) : UiState {
    val pendingTasks: List<TaskUiModel> get() = tasks.filter { !it.isCompleted }
    val completedTasks: List<TaskUiModel> get() = tasks.filter { it.isCompleted }
}

sealed class TasksIntent : UiIntent {
    data class AddTask(val title: String, val description: String) : TasksIntent()
    data class ToggleTaskStatus(val id: Int) : TasksIntent()
    data class DeleteTask(val id: Int) : TasksIntent()
}

sealed class TasksEffect : UiEffect {
    data class ShowToast(val message: String) : TasksEffect()
}
