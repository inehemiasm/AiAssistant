package com.neo.chevere.ui.tasks

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.neo.chevere.core.BaseViewModel
import com.neo.chevere.domain.TaskRepository
import com.neo.chevere.domain.TaskStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Maps domain tasks to UI state and dispatches user actions through the task repository. */
@HiltViewModel
class TasksViewModel @Inject constructor(
    application: Application,
    private val taskRepository: TaskRepository
) : BaseViewModel<TasksState, TasksIntent, TasksEffect>(application, TasksState()) {
    init {
        viewModelScope.launch {
            taskRepository.observeTasks().collectLatest { tasks ->
                setState { copy(tasks = tasks.map { it.toUiModel() }) }
            }
        }
    }

    override suspend fun handleIntent(intent: TasksIntent) {
        when (intent) {
            is TasksIntent.AddTask -> {
                if (intent.title.isBlank()) {
                    sendEffect { TasksEffect.ShowToast("Task title cannot be empty") }
                    return
                }
                taskRepository.createTask(intent.title, intent.description)
            }
            is TasksIntent.ToggleTaskStatus -> {
                val task = taskRepository.getTask(intent.id) ?: return
                val newStatus = when (task.status) {
                    TaskStatus.PENDING -> TaskStatus.COMPLETED
                    TaskStatus.COMPLETED -> TaskStatus.PENDING
                }
                taskRepository.setTaskStatus(task.id, newStatus)
            }
            is TasksIntent.DeleteTask -> taskRepository.deleteTask(intent.id)
        }
    }
}
