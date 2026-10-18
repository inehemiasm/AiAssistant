package com.neo.chevere.data

import com.neo.chevere.core.DispatcherProvider
import com.neo.chevere.data.datasource.local.TaskDao
import com.neo.chevere.data.datasource.local.TaskEntity
import com.neo.chevere.data.datasource.local.TaskStatus as StoredTaskStatus
import com.neo.chevere.domain.Task
import com.neo.chevere.domain.TaskRepository
import com.neo.chevere.domain.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Maps Room task entities to domain tasks and owns database execution context. */
@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val dao: TaskDao,
    private val dispatchers: DispatcherProvider
) : TaskRepository {
    override fun observeTasks(): Flow<List<Task>> = dao.getAllTasksFlow()
        .map { entities -> entities.map { it.toDomain() } }
        .flowOn(dispatchers.io)

    override suspend fun getTasks(): List<Task> = withContext(dispatchers.io) {
        dao.getAllTasks().map { it.toDomain() }
    }

    override suspend fun getTask(id: Int): Task? = withContext(dispatchers.io) {
        dao.getTaskById(id)?.toDomain()
    }

    override suspend fun createTask(title: String, description: String): Long = withContext(dispatchers.io) {
        require(title.isNotBlank()) { "Task title cannot be empty" }
        dao.insertTask(TaskEntity(title = title.trim(), description = description.trim()))
    }

    override suspend fun updateTask(task: Task): Unit = withContext(dispatchers.io) {
        dao.updateTask(task.toEntity())
    }

    override suspend fun setTaskStatus(id: Int, status: TaskStatus): Boolean = withContext(dispatchers.io) {
        dao.setTaskStatus(id, status.toStoredStatus()) > 0
    }

    override suspend fun deleteTask(id: Int): Unit = withContext(dispatchers.io) {
        dao.deleteTask(id)
    }
}

private fun TaskEntity.toDomain() = Task(
    id = id, title = title, description = description,
    status = when (status) {
        StoredTaskStatus.PENDING -> TaskStatus.PENDING
        StoredTaskStatus.COMPLETED -> TaskStatus.COMPLETED
    },
    createdAt = createdAt
)

private fun Task.toEntity() = TaskEntity(
    id = id, title = title, description = description,
    status = status.toStoredStatus(), createdAt = createdAt
)

private fun TaskStatus.toStoredStatus() = when (this) {
    TaskStatus.PENDING -> StoredTaskStatus.PENDING
    TaskStatus.COMPLETED -> StoredTaskStatus.COMPLETED
}
