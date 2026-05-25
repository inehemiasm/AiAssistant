package com.neo.chevere.ui.tasks

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.neo.chevere.core.BaseViewModel
import com.neo.chevere.data.datasource.local.TaskDao
import com.neo.chevere.data.datasource.local.TaskEntity
import com.neo.chevere.data.datasource.local.TaskStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TasksViewModel @Inject constructor(
    application: Application,
    private val taskDao: TaskDao
) : BaseViewModel<TasksState, TasksIntent, TasksEffect>(application, TasksState()) {

    init {
        viewModelScope.launch {
            taskDao.getAllTasksFlow().collectLatest { taskList ->
                setState { copy(tasks = taskList) }
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
                val task = TaskEntity(title = intent.title, description = intent.description)
                taskDao.insertTask(task)
            }

            is TasksIntent.ToggleTaskStatus -> {
                val task = taskDao.getTaskById(intent.id)
                if (task != null) {
                    val newStatus = if (task.status == TaskStatus.PENDING) {
                        TaskStatus.COMPLETED
                    } else {
                        TaskStatus.PENDING
                    }
                    taskDao.updateTask(task.copy(status = newStatus))
                }
            }

            is TasksIntent.DeleteTask -> {
                taskDao.deleteTask(intent.id)
            }
        }
    }
}
