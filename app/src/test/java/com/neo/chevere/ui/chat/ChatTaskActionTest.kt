package com.neo.chevere.ui.chat

import android.app.Application
import com.neo.chevere.core.DispatcherProvider
import com.neo.chevere.data.PreferenceManager
import com.neo.chevere.data.agent.AgentState
import com.neo.chevere.ui.tasks.TaskCompletionUiState
import com.neo.chevere.data.telemetry.AppTelemetry
import com.neo.chevere.data.voice.VoiceInputManager
import com.neo.chevere.domain.*
import com.neo.chevere.ui.chat.a2ui.TaskChecklistData
import kotlinx.coroutines.async
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
    private val taskRepository = mock<TaskRepository>()
    private val store = androidx.lifecycle.ViewModelStore()
    private val tasks = MutableStateFlow(listOf(Task(7, "Buy milk", "Two cartons", TaskStatus.PENDING, 123)))
    private val repository = mock<ChatRepository>()
    private val reportRepository = mock<com.neo.chevere.data.report.AiContentReportRepository>()
    private val suggestionRepository = mock<ChatSuggestionRepository>()
    private val initialization = MutableStateFlow<InitializationStatus>(InitializationStatus.Uninitialized)
    private val agentState = MutableStateFlow<AgentState>(AgentState.Idle)
    private lateinit var viewModel: ChatViewModel

    @Before fun setup() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        whenever(repository.agentState).thenReturn(agentState)
        whenever(repository.activePartialResponse).thenReturn(emptyFlow())
        whenever(repository.getInitStatus()).thenReturn(initialization)
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
        whenever(taskRepository.observeTasks()).thenReturn(tasks)
        whenever(taskRepository.setTaskStatus(any(), any())).thenReturn(true)
        val dispatchers = object : DispatcherProvider {
            override val main = dispatcher
            override val io = dispatcher
            override val default = dispatcher
        }
        viewModel = ChatViewModel(RuntimeEnvironment.getApplication(), repository,
            mock<InitializeChatUseCase>(), mock<SendMessageUseCase>(), preferences,
            dispatchers, mock<AppTelemetry>(), voice, history, taskRepository, reportRepository, mock(), suggestionRepository)
        store.put("chat", viewModel)
        advanceUntilIdle()
        viewModel.onIntent(ChatIntent.LoadSession(5))
        advanceUntilIdle()
    }

    @Test fun suggestionsReflectConversationAndDisappearWhileDrafting() = runTest(dispatcher) {
        whenever(suggestionRepository.predict(any(), any())).thenReturn(listOf("Show completed tasks", "Add a task for tomorrow"))
        initialization.value = InitializationStatus.Ready
        advanceUntilIdle()
        assertEquals(listOf("Show completed tasks", "Add a task for tomorrow"), viewModel.currentState.suggestions)
        val snapshot = argumentCaptor<List<ChatMessage>>()
        verify(suggestionRepository).predict(snapshot.capture(), eq(false))
        assertEquals("Tasks", snapshot.firstValue.last().text)
        viewModel.onIntent(ChatIntent.UpdateInputText("Show completed tasks"))
        advanceUntilIdle()
        assertTrue(viewModel.currentState.suggestions.isEmpty())
        assertEquals("Show completed tasks", viewModel.currentState.inputText)
        verify(repository, never()).sendMessage(any(), any())
    }

    @Test fun failedReportDoesNotShowSuccessToast() = runTest(dispatcher) {
        whenever(reportRepository.submitReport(any(), anyOrNull(), any(), any())).thenReturn(Result.failure(IllegalStateException("offline")))
        val toast = async {
            viewModel.effect.filterIsInstance<ChatEffect.ShowToast>().first()
        }
        viewModel.onIntent(ChatIntent.OpenReportDialog(0))
        advanceUntilIdle()
        viewModel.onIntent(ChatIntent.SubmitReport("Other", ""))
        advanceUntilIdle()
        assertTrue(toast.await().message.contains("Could not confirm"))
    }

    @Test fun pendingConfirmationSurvivesTurnCompletion() = runTest(dispatcher) {
        clearInvocations(repository)
        agentState.value = AgentState.WaitingForConfirmation("mcp", "Connect to server?", emptyList())
        whenever(repository.confirmAction()).thenReturn(Result.success(AssistantResponse("Confirmation required")))
        viewModel.onIntent(ChatIntent.ConfirmAction)
        advanceUntilIdle()
        assertTrue(viewModel.currentState.isWaitingForConfirmation)
        verify(repository, never()).resetAgentState()
    }

    @After fun tearDown() {
        store.clear()
        dispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    @Test fun validAction_updatesOnlyTaskStatusAndObservesRoom() = runTest(dispatcher) {
        viewModel.onIntent(ChatIntent.SetTaskCompleted("surface-1", 7, true))
        advanceUntilIdle()
        verify(taskRepository).setTaskStatus(7, TaskStatus.COMPLETED)
        verify(taskRepository, never()).updateTask(any())
        tasks.value = listOf(tasks.value.single().copy(status = TaskStatus.COMPLETED))
        advanceUntilIdle()
        val state = viewModel.currentState.taskChecklistData as TaskChecklistData.Ready
        assertEquals(TaskCompletionUiState.Completed, state.tasks.single().completion)
        assertTrue(state.updatingIds.isEmpty())
    }

    @Test fun unknownSurfaceOrTask_cannotMutateRoom() = runTest(dispatcher) {
        viewModel.onIntent(ChatIntent.SetTaskCompleted("other-surface", 7, true))
        viewModel.onIntent(ChatIntent.SetTaskCompleted("surface-1", 99, true))
        advanceUntilIdle()
        verify(taskRepository, never()).setTaskStatus(any(), any())
        viewModel.onIntent(ChatIntent.NewConversation)
        advanceUntilIdle()
        viewModel.onIntent(ChatIntent.SetTaskCompleted("surface-1", 7, true))
        advanceUntilIdle()
        verify(taskRepository, never()).setTaskStatus(any(), any())
    }

    @Test fun failedWrite_keepsRoomStateAndReportsError() = runTest(dispatcher) {
        whenever(taskRepository.setTaskStatus(7, TaskStatus.COMPLETED)).thenThrow(IllegalStateException("Disk failure"))
        viewModel.onIntent(ChatIntent.SetTaskCompleted("surface-1", 7, true))
        advanceUntilIdle()
        val state = viewModel.currentState.taskChecklistData as TaskChecklistData.Ready
        assertEquals(TaskCompletionUiState.Pending, state.tasks.single().completion)
        assertTrue(state.updatingIds.isEmpty())
        assertTrue(viewModel.effect.filterIsInstance<ChatEffect.ShowToast>().first().message.isNotBlank())
    }
}
