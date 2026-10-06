package com.neo.chevere.ui.tasks

import android.app.Application
import com.neo.chevere.domain.Task
import com.neo.chevere.domain.TaskRepository
import com.neo.chevere.domain.TaskStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class TasksViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repo = mock<TaskRepository>()
    private val task = Task(7, "Buy milk", "Two cartons", TaskStatus.PENDING, 123)
    private val tasks = MutableStateFlow(listOf(task))
    private val store = androidx.lifecycle.ViewModelStore()
    private lateinit var viewModel: TasksViewModel

    @Before fun setup() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        whenever(repo.observeTasks()).thenReturn(tasks)
        whenever(repo.getTask(7)).thenReturn(task)
        viewModel = TasksViewModel(RuntimeEnvironment.getApplication(), repo)
        store.put("tasks", viewModel)
        advanceUntilIdle()
    }

    @After fun tearDown() {
        store.clear()
        dispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    @Test fun repositoryChanges_areMappedBeforeReachingTheScreen() = runTest(dispatcher) {
        assertEquals(listOf(7), viewModel.currentState.pendingTasks.map { it.id })
        tasks.value = listOf(task.copy(status = TaskStatus.COMPLETED))
        advanceUntilIdle()
        assertTrue(viewModel.currentState.pendingTasks.isEmpty())
        assertEquals(TaskCompletionUiState.Completed, viewModel.currentState.completedTasks.single().completion)
    }

    @Test fun statusAndDeleteActions_goThroughDomainRepository() = runTest(dispatcher) {
        viewModel.onIntent(TasksIntent.ToggleTaskStatus(7))
        advanceUntilIdle()
        verify(repo).setTaskStatus(7, TaskStatus.COMPLETED)
        verify(repo, never()).updateTask(any())
        viewModel.onIntent(TasksIntent.DeleteTask(7))
        advanceUntilIdle()
        verify(repo).deleteTask(7)
    }

    @Test fun blankTitle_isRejectedBeforeCallingRepository() = runTest(dispatcher) {
        viewModel.onIntent(TasksIntent.AddTask("  ", ""))
        advanceUntilIdle()
        verify(repo, never()).createTask(any(), any())
        viewModel.onIntent(TasksIntent.AddTask("Call Alice", "Tomorrow"))
        advanceUntilIdle()
        verify(repo).createTask("Call Alice", "Tomorrow")
    }
}
