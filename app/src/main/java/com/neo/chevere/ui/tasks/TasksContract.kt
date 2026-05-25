package com.neo.chevere.ui.tasks

import com.neo.chevere.core.UiEffect
import com.neo.chevere.core.UiIntent
import com.neo.chevere.core.UiState
import com.neo.chevere.data.datasource.local.TaskEntity

data class TasksState(
    val tasks: List<TaskEntity> = emptyList()
) : UiState

sealed class TasksIntent : UiIntent {
    data class AddTask(val title: String, val description: String) : TasksIntent()
    data class ToggleTaskStatus(val id: Int) : TasksIntent()
    data class DeleteTask(val id: Int) : TasksIntent()
}

sealed class TasksEffect : UiEffect {
    data class ShowToast(val message: String) : TasksEffect()
}
