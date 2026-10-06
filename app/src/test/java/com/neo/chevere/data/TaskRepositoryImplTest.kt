package com.neo.chevere.data

import com.neo.chevere.core.DispatcherProvider
import com.neo.chevere.data.datasource.local.TaskDao
import com.neo.chevere.data.datasource.local.TaskEntity
import com.neo.chevere.data.datasource.local.TaskStatus as StoredStatus
import com.neo.chevere.domain.Task
import com.neo.chevere.domain.TaskStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class TaskRepositoryImplTest {
    private fun repository(dao: TaskDao, dispatcher: TestDispatcher) = TaskRepositoryImpl(
        dao, object : DispatcherProvider {
            override val io = dispatcher
            override val main = dispatcher
            override val default = dispatcher
        }
    )

    @Test fun roomUpdates_emitMappedDomainTasksWithIdentityAndMetadata() = runTest {
        val dao = mock<TaskDao>()
        val pending = TaskEntity(7, "Buy milk", "Two cartons", StoredStatus.PENDING, 123)
        val entities = MutableStateFlow(listOf(pending))
        whenever(dao.getAllTasksFlow()).thenReturn(entities)
        val repo = repository(dao, StandardTestDispatcher(testScheduler))
        val emissions = async { repo.observeTasks().take(2).toList() }
        runCurrent()
        entities.value = listOf(pending.copy(status = StoredStatus.COMPLETED))
        val results = emissions.await()
        assertEquals(Task(7, "Buy milk", "Two cartons", TaskStatus.PENDING, 123), results[0].single())
        assertEquals(TaskStatus.COMPLETED, results[1].single().status)
    }

    @Test fun readsAndWrites_mapBothDirectionsWithoutLosingFields() = runTest {
        val dao = mock<TaskDao>()
        val entity = TaskEntity(9, "Call Alice", "Tomorrow", StoredStatus.COMPLETED, 456)
        whenever(dao.getAllTasks()).thenReturn(listOf(entity))
        whenever(dao.getTaskById(9)).thenReturn(entity)
        val repo = repository(dao, StandardTestDispatcher(testScheduler))
        val task = repo.getTask(9)!!
        assertEquals(task, repo.getTasks().single())
        repo.updateTask(task.copy(title = "Call Bob", status = TaskStatus.PENDING))
        verify(dao).updateTask(entity.copy(title = "Call Bob", status = StoredStatus.PENDING))
        assertNull(repo.getTask(999))
    }

    @Test fun completion_updatesOnlyStatusAndReturnsWhetherTaskExists() = runTest {
        val dao = mock<TaskDao>()
        whenever(dao.setTaskStatus(7, StoredStatus.COMPLETED)).thenReturn(1)
        whenever(dao.setTaskStatus(999, StoredStatus.PENDING)).thenReturn(0)
        val repo = repository(dao, StandardTestDispatcher(testScheduler))
        assertTrue(repo.setTaskStatus(7, TaskStatus.COMPLETED))
        assertFalse(repo.setTaskStatus(999, TaskStatus.PENDING))
        verify(dao, never()).updateTask(any())
    }

    @Test fun createAndDelete_keepPersistenceDetailsInsideRepository() = runTest {
        val dao = mock<TaskDao>()
        whenever(dao.insertTask(any())).thenReturn(42)
        val repo = repository(dao, StandardTestDispatcher(testScheduler))
        assertEquals(42L, repo.createTask("  Buy milk  ", "  Two cartons  "))
        val saved = argumentCaptor<TaskEntity>()
        verify(dao).insertTask(saved.capture())
        assertEquals("Buy milk", saved.firstValue.title)
        assertEquals("Two cartons", saved.firstValue.description)
        assertEquals(StoredStatus.PENDING, saved.firstValue.status)
        assertTrue(saved.firstValue.createdAt > 0)
        repo.deleteTask(42)
        verify(dao).deleteTask(42)
    }
}
