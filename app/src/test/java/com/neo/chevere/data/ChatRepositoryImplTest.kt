package com.neo.chevere.data

import android.content.Context
import android.net.Uri
import com.neo.chevere.core.DispatcherProvider
import com.neo.chevere.data.agent.AgentOrchestrator
import com.neo.chevere.data.agent.AgentState
import com.neo.chevere.data.chat.ChatRequestRouter
import com.neo.chevere.data.context.ConversationContextManager
import com.neo.chevere.data.datasource.ModelCatalogDataSource
import com.neo.chevere.data.download.WorkManagerModelDownloadManager
import com.neo.chevere.data.inference.ImageGenerationManager
import com.neo.chevere.data.inference.InferenceManager
import com.neo.chevere.domain.InferenceResult
import com.neo.chevere.domain.InstallStatus
import com.neo.chevere.domain.InstalledModel
import com.neo.chevere.domain.InstalledModelRegistry
import com.neo.chevere.domain.ModelEntry
import com.neo.chevere.domain.ModelFormat
import com.neo.chevere.domain.ModelRuntime
import com.neo.chevere.domain.ModelSource
import com.neo.chevere.domain.ModelTaskType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ChatRepositoryImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private val agentPartialResponseFlow = MutableStateFlow("")
    private lateinit var context: Context
    private lateinit var inferenceManager: InferenceManager
    private lateinit var modelCatalog: ModelCatalogDataSource
    private lateinit var downloadManager: WorkManagerModelDownloadManager
    private lateinit var agentOrchestrator: AgentOrchestrator
    private lateinit var imageGenerationManager: ImageGenerationManager
    private lateinit var installedModelRegistry: InstalledModelRegistry
    private lateinit var conversationContextManager: ConversationContextManager
    private lateinit var chatRequestRouter: ChatRequestRouter
    private lateinit var repository: ChatRepositoryImpl

    @Before
    fun setup() {
        context = mock {
            on { filesDir } doReturn File("build/test-files")
        }
        inferenceManager = mock()
        modelCatalog = mock()
        downloadManager = mock()
        imageGenerationManager = mock()
        installedModelRegistry = mock()
        conversationContextManager = ConversationContextManager()
        chatRequestRouter = ChatRequestRouter()
        agentOrchestrator = mock {
            on { agentState } doReturn MutableStateFlow(AgentState.Idle)
            on { activePartialResponse } doReturn agentPartialResponseFlow
        }

        repository = ChatRepositoryImpl(
            context = context,
            inferenceManager = inferenceManager,
            modelCatalog = modelCatalog,
            downloadManager = downloadManager,
            agentOrchestrator = agentOrchestrator,
            imageGenerationManager = imageGenerationManager,
            installedModelRegistry = installedModelRegistry,
            conversationContextManager = conversationContextManager,
            chatRequestRouter = chatRequestRouter,
            dispatcherProvider = object : DispatcherProvider {
                override val io = testDispatcher
                override val main = testDispatcher
                override val default = testDispatcher
            }
        )
    }

    @Test
    fun sendMessage_whenVisionNotSupported_returnsFailure() = runTest(testDispatcher) {
        whenever(inferenceManager.isVisionSupported()).doReturn(false)
        val mockUri = mock<Uri>()

        val result = repository.sendMessage("test", mockUri)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun sendMessage_plainTextUsesDirectInference() = runTest(testDispatcher) {
        val prompt = "hello"
        whenever(inferenceManager.generateStream(any())).doReturn(flowOf(InferenceResult.Success("hi")))

        val result = repository.sendMessage(prompt, null)

        verify(inferenceManager).clearConversation()
        verify(inferenceManager).generateStream(argThat {
            imageUri == null &&
                    this.prompt.contains("You are Chevere AI") &&
                    this.prompt.endsWith(prompt)
        })
        assertEquals("hi", result.getOrNull())
    }

    @Test
    fun sendMessage_capabilityOverviewAnswersWithoutInferenceOrAgent() = runTest(testDispatcher) {
        val result = repository.sendMessage("What can you do?", null)

        verify(inferenceManager, never()).generate(any())
        verify(agentOrchestrator, never()).processUserRequest(any(), any(), any())
        assertTrue(result.getOrNull()?.contains("image generation") == true)
        assertTrue(result.getOrNull()?.contains("weather") == true)
    }

    @Test
    fun sendMessage_imageCapabilityQuestionDoesNotGenerateImage() = runTest(testDispatcher) {
        val result = repository.sendMessage("Can you generate images?", null)

        verify(inferenceManager, never()).generate(any())
        verify(agentOrchestrator, never()).processUserRequest(any(), any(), any())
        assertTrue(result.getOrNull()?.contains("Tell me what you want") == true)
    }

    @Test
    fun sendMessage_toolLikeTextDelegatesToOrchestrator() = runTest(testDispatcher) {
        val prompt = "what is the weather in Austin right now?"
        whenever(
            agentOrchestrator.processUserRequest(
                prompt,
                null,
                null
            )
        ).doReturn(Result.success("sunny"))

        val result = repository.sendMessage(prompt, null)

        verify(agentOrchestrator).processUserRequest(prompt, null, null)
        assertEquals("sunny", result.getOrNull())
    }

    @Test
    fun sendMessage_triggersBackgroundSummarizationWhenHistoryExceedsRecentWindow() = runTest(testDispatcher) {
        val mockModel = InstalledModel(
            id = "gemma",
            displayName = "Gemma",
            filePath = "gemma.bin",
            fileName = "gemma.bin",
            source = ModelSource.LOCAL,
            format = ModelFormat.LITERTLM,
            runtime = ModelRuntime.LITERT,
            taskType = ModelTaskType.CHAT,
            capabilities = emptySet(),
            installStatus = InstallStatus.INSTALLED
        )
        whenever(inferenceManager.currentModel).doReturn(mockModel)
        whenever(inferenceManager.generate(any())).doReturn(InferenceResult.Success("This is the summarized text."))
        whenever(inferenceManager.generateStream(any())).doReturn(flowOf(InferenceResult.Success("response text")))

        // Populate the conversation history past RECENT_TURN_COUNT (4 turns / 2 exchanges)
        conversationContextManager.recordExchange("q0", null, "a0")
        conversationContextManager.recordExchange("q1", null, "a1")
        conversationContextManager.recordExchange("q2", null, "a2")

        val result = repository.sendMessage("new prompt", null)

        // Initially delay has not passed
        assertTrue(conversationContextManager.persistentMemory == null)

        // Advance time to execute background job
        testScheduler.advanceTimeBy(3500)
        testScheduler.runCurrent()

        assertEquals("This is the summarized text.", conversationContextManager.persistentMemory)
    }

    @Test
    fun sendMessage_concreteImageRequestDelegatesToOrchestrator() = runTest(testDispatcher) {
        val prompt = "generate an image of a neon robot"
        whenever(
            agentOrchestrator.processUserRequest(
                prompt,
                null,
                null
            )
        ).doReturn(Result.success("created"))

        val result = repository.sendMessage(prompt, null)

        verify(agentOrchestrator).processUserRequest(prompt, null, null)
        assertEquals("created", result.getOrNull())
    }

    @Test
    fun sendMessage_withAttachmentAlwaysUsesVisionDirectInference() = runTest(testDispatcher) {
        val prompt = "generate an image based on this image"
        val mockUri = mock<Uri>()
        whenever(inferenceManager.isVisionSupported()).doReturn(true)
        whenever(inferenceManager.generateStream(any())).doReturn(flowOf(InferenceResult.Success("I can describe the attached image.")))

        val result = repository.sendMessage(prompt, mockUri)

        verify(agentOrchestrator, never()).processUserRequest(any(), any(), any())
        verify(inferenceManager).generateStream(argThat {
            imageUri == mockUri &&
                    this.prompt.contains("attached image") &&
                    this.prompt.contains("Do not generate")
        })
        assertEquals("I can describe the attached image.", result.getOrNull())
    }

    @Test
    fun fetchAvailableModels_delegatesToCatalog() = runTest(testDispatcher) {
        val models = listOf(ModelEntry("gemma", "url"))
        whenever(modelCatalog.fetchAvailableModels()).doReturn(Result.success(models))

        val result = repository.fetchAvailableModels()

        assertEquals(models, result.getOrNull())
    }

    @Test
    fun clearConversation_resetsAgentAndRuntime() = runTest(testDispatcher) {
        repository.clearConversation()

        verify(agentOrchestrator).reset()
        verify(inferenceManager).clearConversation()
    }

    @Test
    fun downloadModel_preregistersCatalogMetadataBeforeStartingWork() = runTest(testDispatcher) {
        val model = ModelEntry(
            name = "Landscape",
            url = "https://example.com/landscape.zip",
            provider = "Hugging Face",
            runtimeType = "ONNX Diffusion",
            fileName = "landscape.zip",
            license = "Community",
            sha256 = "abc123"
        )
        whenever(
            downloadManager.downloadModel(
                url = model.url,
                modelName = model.effectiveFileName,
                modelId = model.effectiveInstalledId,
                sha256 = model.sha256
            )
        ).doReturn(flowOf())

        repository.downloadModel(model).collect {}

        verify(installedModelRegistry).upsertInstalledModel(
            org.mockito.kotlin.argThat {
                id == "landscape" &&
                        source == ModelSource.HUGGING_FACE &&
                        format == ModelFormat.ONNX_DIFFUSION_BUNDLE &&
                        runtime == ModelRuntime.ONNX_DIFFUSION &&
                        taskType == ModelTaskType.IMAGE_GENERATION &&
                        checksum == "abc123" &&
                        license == "Community"
            }
        )
        verify(downloadManager).downloadModel(model.url, "landscape.zip", "landscape", "abc123")
    }

    @Test
    fun activePartialResponse_combinesDirectAndAgentFlows() = runTest(testDispatcher) {
        val directFlow = MutableStateFlow<InferenceResult>(InferenceResult.Success(""))
        whenever(inferenceManager.generateStream(any())).doReturn(directFlow)

        val emissions = mutableListOf<String>()
        val job = this.launch {
            repository.activePartialResponse.collect {
                emissions.add(it)
            }
        }

        // Initially both empty
        testScheduler.advanceUntilIdle()

        // Agent streams text
        agentPartialResponseFlow.value = "hello agent"
        testScheduler.advanceUntilIdle()

        // Direct streams text by starting direct chat turn
        val sendJob = this.launch {
            repository.sendMessage("hello", null)
        }
        testScheduler.advanceUntilIdle()

        directFlow.value = InferenceResult.Success("hello direct")
        testScheduler.advanceUntilIdle()

        assertTrue(emissions.contains("hello agent"))
        assertTrue(emissions.contains("hello direct"))

        // Reset flow values
        agentPartialResponseFlow.value = ""
        job.cancel()
        sendJob.cancel()
    }
}
