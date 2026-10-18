package com.neo.chevere.data.datasource.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM local_tasks ORDER BY createdAt DESC")
    fun getAllTasksFlow(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM local_tasks ORDER BY createdAt DESC")
    suspend fun getAllTasks(): List<TaskEntity>

    @Query("SELECT * FROM local_tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: Int): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    /** Updates status atomically without overwriting a concurrently edited title or description. */
    @Query("UPDATE local_tasks SET status = :status WHERE id = :id")
    suspend fun setTaskStatus(id: Int, status: TaskStatus): Int

    @Query("DELETE FROM local_tasks WHERE id = :id")
    suspend fun deleteTask(id: Int)
}
