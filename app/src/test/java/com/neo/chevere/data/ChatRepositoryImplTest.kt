package com.neo.chevere.data

import android.content.Context
import android.net.Uri
import com.neo.chevere.core.DispatcherProvider
import com.neo.chevere.data.agent.AgentOrchestrator
import com.neo.chevere.data.agent.AgentState
import com.neo.chevere.data.datasource.ModelCatalogDataSource
import com.neo.chevere.data.download.WorkManagerModelDownloadManager
import com.neo.chevere.data.inference.ImageGenerationManager
import com.neo.chevere.data.inference.InferenceManager
import com.neo.chevere.domain.ModelEntry
import com.neo.chevere.domain.ModelFormat
import com.neo.chevere.domain.ModelRuntime
import com.neo.chevere.domain.ModelSource
import com.neo.chevere.domain.ModelTaskType
import com.neo.chevere.domain.InstalledModelRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ChatRepositoryImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var inferenceManager: InferenceManager
    private lateinit var modelCatalog: ModelCatalogDataSource
    private lateinit var downloadManager: WorkManagerModelDownloadManager
    private lateinit var agentOrchestrator: AgentOrchestrator
    private lateinit var imageGenerationManager: ImageGenerationManager
    private lateinit var installedModelRegistry: InstalledModelRegistry
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
        agentOrchestrator = mock {
            on { agentState } doReturn MutableStateFlow(AgentState.Idle)
        }

        repository = ChatRepositoryImpl(
            context = context,
            inferenceManager = inferenceManager,
            modelCatalog = modelCatalog,
            downloadManager = downloadManager,
            agentOrchestrator = agentOrchestrator,
            imageGenerationManager = imageGenerationManager,
            installedModelRegistry = installedModelRegistry,
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
    fun sendMessage_delegatesToOrchestrator() = runTest(testDispatcher) {
        val prompt = "hello"
        whenever(agentOrchestrator.processUserRequest(prompt, null)).doReturn(Result.success("hi"))

        val result = repository.sendMessage(prompt, null)

        verify(agentOrchestrator).processUserRequest(prompt, null)
        assertEquals("hi", result.getOrNull())
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
}
