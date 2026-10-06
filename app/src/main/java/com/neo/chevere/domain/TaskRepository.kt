package com.neo.chevere.domain

import kotlinx.coroutines.flow.Flow

/** Task persistence boundary. Callers never handle Room entities or database dispatchers. */
interface TaskRepository {
    fun observeTasks(): Flow<List<Task>>
    suspend fun getTasks(): List<Task>
    suspend fun getTask(id: Int): Task?
    suspend fun createTask(title: String, description: String): Long
    suspend fun updateTask(task: Task)
    /** Updates completion only, preserving concurrent title and description edits. */
    suspend fun setTaskStatus(id: Int, status: TaskStatus): Boolean
    suspend fun deleteTask(id: Int)
}
