package com.neo.chevere.data.agent.tools

import com.neo.chevere.data.agent.ui.AgentUiEnvelope
import com.neo.chevere.data.agent.AgentTool
import com.neo.chevere.data.agent.ToolResult
import com.neo.chevere.domain.TaskRepository
import com.neo.chevere.domain.TaskStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class TaskRegistryTool @Inject constructor(
    private val taskRepository: TaskRepository
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
                if (title.isBlank()) return@withContext ToolResult.Error("Task title cannot be empty")
                val newId = taskRepository.createTask(title, desc)
                ToolResult.Success("Task created successfully with ID: $newId and title: '$title'.")
            }

            "list" -> {
                val tasks = taskRepository.getTasks()
                val visibleTasks = tasks.take(AgentUiEnvelope.MAX_TASKS)
                val summary = if (tasks.isEmpty()) "No tasks found in the list." else {
                    val heading = if (tasks.size > visibleTasks.size) {
                        "Showing the latest ${visibleTasks.size} of ${tasks.size} tasks. Open Tasks for the full list."
                    } else "Current tasks:"
                    heading + "\n" + visibleTasks.joinToString("\n") { task ->
                        "[ID: ${task.id}] [${task.status}] ${task.title}${if (task.description.isNotBlank()) " - ${task.description}" else ""}"
                    }
                }
                ToolResult.Success(AgentUiEnvelope.checklist(summary, visibleTasks.map { it.id }))
            }

            "update" -> {
                val idStr = args["id"]?.trim() ?: return@withContext ToolResult.Error("Missing task 'id' argument for update action")
                val id = idStr.toIntOrNull() ?: return@withContext ToolResult.Error("Invalid 'id' format (must be numeric)")
                val task = taskRepository.getTask(id) ?: return@withContext ToolResult.Error("Task with ID $id not found")

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
                ToolResult.Success("Task $id updated successfully to: [${updatedTask.status}] ${updatedTask.title}.")
            }

            "delete" -> {
                val idStr = args["id"]?.trim() ?: return@withContext ToolResult.Error("Missing task 'id' argument for delete action")
                val id = idStr.toIntOrNull() ?: return@withContext ToolResult.Error("Invalid 'id' format (must be numeric)")
                val task = taskRepository.getTask(id) ?: return@withContext ToolResult.Error("Task with ID $id not found")

                taskRepository.deleteTask(id)
                ToolResult.Success("Task $id ('${task.title}') deleted successfully.")
            }

            else -> ToolResult.Error("Unsupported action '$action'. Must be one of 'create', 'list', 'update', 'delete'.")
        }
    }
}
