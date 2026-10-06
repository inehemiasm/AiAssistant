package com.neo.chevere.ui.chat

import android.app.Application
import com.neo.chevere.core.DispatcherProvider
import com.neo.chevere.data.PreferenceManager
import com.neo.chevere.data.agent.AgentState
import com.neo.chevere.data.datasource.local.TaskDao
import com.neo.chevere.data.datasource.local.TaskEntity
import com.neo.chevere.data.datasource.local.TaskStatus
import com.neo.chevere.data.telemetry.AppTelemetry
import com.neo.chevere.data.voice.VoiceInputManager
import com.neo.chevere.domain.*
import com.neo.chevere.ui.chat.a2ui.TaskChecklistData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
class ChatTaskActionTest {
    private val dispatcher = StandardTestDispatcher()
    private val dao = mock<TaskDao>()
    private val store = androidx.lifecycle.ViewModelStore()
    private val tasks = MutableStateFlow(listOf(TaskEntity(7, "Buy milk", "Two cartons")))
    private lateinit var viewModel: ChatViewModel

    @Before fun setup() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val repository = mock<ChatRepository>()
        whenever(repository.agentState).thenReturn(MutableStateFlow(AgentState.Idle))
        whenever(repository.activePartialResponse).thenReturn(emptyFlow())
        whenever(repository.getInitStatus()).thenReturn(flowOf(InitializationStatus.Uninitialized))
        whenever(repository.getLocalModels()).thenReturn(emptyList())
        val preferences = mock<PreferenceManager>()
        whenever(preferences.selectedModelPreference).thenReturn(flowOf(null))
        val voice = mock<VoiceInputManager>()
        whenever(voice.results).thenReturn(emptyFlow())
        val history = mock<ChatHistoryRepository>()
        whenever(history.getAllSessions()).thenReturn(emptyFlow())
        whenever(history.getMessages(5)).thenReturn(listOf(ChatMessage(
            text = "Tasks", isUser = false,
            agentUiContent = AgentUiContent.TaskChecklist("surface-1", listOf(7)))))
        whenever(dao.getAllTasksFlow()).thenReturn(tasks)
        whenever(dao.setTaskStatus(any(), any())).thenReturn(1)
        val dispatchers = object : DispatcherProvider {
            override val main = dispatcher
            override val io = dispatcher
            override val default = dispatcher
        }
        viewModel = ChatViewModel(RuntimeEnvironment.getApplication(), repository,
            mock<InitializeChatUseCase>(), mock<SendMessageUseCase>(), preferences,
            dispatchers, mock<AppTelemetry>(), voice, history, dao)
        store.put("chat", viewModel)
        advanceUntilIdle()
        viewModel.onIntent(ChatIntent.LoadSession(5))
        advanceUntilIdle()
    }

    @After fun tearDown() {
        store.clear()
        dispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    @Test fun validAction_updatesOnlyTaskStatusAndObservesRoom() = runTest(dispatcher) {
        viewModel.onIntent(ChatIntent.SetTaskCompleted("surface-1", 7, true))
        advanceUntilIdle()
        verify(dao).setTaskStatus(7, TaskStatus.COMPLETED)
        verify(dao, never()).updateTask(any())
        tasks.value = listOf(tasks.value.single().copy(status = TaskStatus.COMPLETED))
        advanceUntilIdle()
        val state = viewModel.currentState.taskChecklistData as TaskChecklistData.Ready
        assertEquals(TaskStatus.COMPLETED, state.tasks.single().status)
        assertTrue(state.updatingIds.isEmpty())
    }

    @Test fun unknownSurfaceOrTask_cannotMutateRoom() = runTest(dispatcher) {
        viewModel.onIntent(ChatIntent.SetTaskCompleted("other-surface", 7, true))
        viewModel.onIntent(ChatIntent.SetTaskCompleted("surface-1", 99, true))
        advanceUntilIdle()
        verify(dao, never()).setTaskStatus(any(), any())
        viewModel.onIntent(ChatIntent.NewConversation)
        advanceUntilIdle()
        viewModel.onIntent(ChatIntent.SetTaskCompleted("surface-1", 7, true))
        advanceUntilIdle()
        verify(dao, never()).setTaskStatus(any(), any())
    }

    @Test fun failedWrite_keepsRoomStateAndReportsError() = runTest(dispatcher) {
        whenever(dao.setTaskStatus(7, TaskStatus.COMPLETED)).thenThrow(IllegalStateException("Disk failure"))
        viewModel.onIntent(ChatIntent.SetTaskCompleted("surface-1", 7, true))
        advanceUntilIdle()
        val state = viewModel.currentState.taskChecklistData as TaskChecklistData.Ready
        assertEquals(TaskStatus.PENDING, state.tasks.single().status)
        assertTrue(state.updatingIds.isEmpty())
        assertTrue(viewModel.effect.filterIsInstance<ChatEffect.ShowToast>().first().message.isNotBlank())
    }
}
