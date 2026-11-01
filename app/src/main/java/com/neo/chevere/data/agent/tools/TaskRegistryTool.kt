package com.neo.chevere.data.agent.tools

import com.neo.chevere.data.agent.ui.AgentUiEnvelope
import android.content.Context
import com.neo.chevere.R
import dagger.hilt.android.qualifiers.ApplicationContext
import com.neo.chevere.data.agent.AgentTool
import com.neo.chevere.data.agent.ToolResult
import com.neo.chevere.domain.TaskRepository
import com.neo.chevere.domain.TaskStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class TaskRegistryTool @Inject constructor(
    private val taskRepository: TaskRepository,
    @ApplicationContext private val context: Context
) : AgentTool {
    override val name: String = "task_registry"
    override val description: String =
        "Manages the user's local tasks or to-do list (create, list, update/complete status, or delete tasks). Listing tasks displays an interactive checklist in chat."
    override val inputSchema: String =
        "action: One of 'create', 'list', 'update', 'delete'. title: Task title (required for 'create'). description: Optional task description. status: One of 'pending', 'completed' (for 'update'). id: The numeric task ID (required for 'update' or 'delete')."

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val action = args["action"]?.trim()?.lowercase() ?: return@withContext ToolResult.Error("Missing 'action' argument")

        when (action) {
            "create" -> {
                val title = args["title"]?.trim() ?: return@withContext ToolResult.Error("Missing 'title' argument for create action")
                val desc = args["description"]?.trim().orEmpty()
                if (title.isBlank()) return@withContext ToolResult.Error(context.getString(R.string.ui_task_title_cannot_be_empty))
                val newId = taskRepository.createTask(title, desc)
                ToolResult.Success(context.getString(R.string.ui_task_created, newId, title))
            }

            "list" -> {
                val tasks = taskRepository.getTasks()
                val visibleTasks = tasks.take(AgentUiEnvelope.MAX_TASKS)
                val summary = if (tasks.isEmpty()) context.getString(R.string.ui_no_tasks_in_list) else {
                    val heading = if (tasks.size > visibleTasks.size) {
                        context.getString(R.string.ui_task_list_window, visibleTasks.size, tasks.size)
                    } else context.getString(R.string.ui_current_tasks)
                    heading + "\n" + visibleTasks.joinToString("\n") { task ->
                        "[ID: ${task.id}] [${statusLabel(task.status)}] ${task.title}${if (task.description.isNotBlank()) " - ${task.description}" else ""}"
                    }
                }
                ToolResult.Success(AgentUiEnvelope.checklist(summary, visibleTasks.map { it.id }))
            }

            "update" -> {
                val idStr = args["id"]?.trim() ?: return@withContext ToolResult.Error("Missing task 'id' argument for update action")
                val id = idStr.toIntOrNull() ?: return@withContext ToolResult.Error("Invalid 'id' format (must be numeric)")
                val task = taskRepository.getTask(id) ?: return@withContext ToolResult.Error(context.getString(R.string.ui_task_not_found, id))

                val newTitle = args["title"]?.trim() ?: task.title
                val newDesc = args["description"]?.trim() ?: task.description
                val statusStr = args["status"]?.trim()?.uppercase()
                val newStatus = if (statusStr != null) {
                    try {
                        TaskStatus.valueOf(statusStr)
                    } catch (e: Exception) {
                        return@withContext ToolResult.Error("Invalid 'status' argument (must be 'pending' or 'completed')")
                    }
                } else {
                    task.status
                }

                val updatedTask = task.copy(title = newTitle, description = newDesc, status = newStatus)
                taskRepository.updateTask(updatedTask)
                ToolResult.Success(context.getString(R.string.ui_task_updated, id, statusLabel(updatedTask.status), updatedTask.title))
            }

            "delete" -> {
                val idStr = args["id"]?.trim() ?: return@withContext ToolResult.Error("Missing task 'id' argument for delete action")
                val id = idStr.toIntOrNull() ?: return@withContext ToolResult.Error("Invalid 'id' format (must be numeric)")
                val task = taskRepository.getTask(id) ?: return@withContext ToolResult.Error(context.getString(R.string.ui_task_not_found, id))

                taskRepository.deleteTask(id)
                ToolResult.Success(context.getString(R.string.ui_task_deleted, id, task.title))
            }

            else -> ToolResult.Error("Unsupported action '$action'. Must be one of 'create', 'list', 'update', 'delete'.")
        }
    }

    private fun statusLabel(status: TaskStatus): String = context.getString(
        if (status == TaskStatus.COMPLETED) R.string.task_completed else R.string.task_pending
    )
}
