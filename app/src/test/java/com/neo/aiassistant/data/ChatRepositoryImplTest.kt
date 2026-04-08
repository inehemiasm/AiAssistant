package com.neo.aiassistant.data

import android.content.Context
import android.net.Uri
import com.neo.aiassistant.data.agent.AgentOrchestrator
import com.neo.aiassistant.data.agent.AgentState
import com.neo.aiassistant.data.datasource.ModelCatalogDataSource
import com.neo.aiassistant.data.download.WorkManagerModelDownloadManager
import com.neo.aiassistant.data.inference.LlmResponseMapper
import com.neo.aiassistant.data.inference.LlmRuntimeManager
import com.neo.aiassistant.data.inference.MultimodalMessageFactory
import com.neo.aiassistant.domain.ModelEntry
import kotlinx.coroutines.flow.MutableStateFlow
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

class ChatRepositoryImplTest {

    private lateinit var context: Context
    private lateinit var runtimeManager: LlmRuntimeManager
    private lateinit var messageFactory: MultimodalMessageFactory
    private lateinit var responseMapper: LlmResponseMapper
    private lateinit var modelCatalog: ModelCatalogDataSource
    private lateinit var downloadManager: WorkManagerModelDownloadManager
    private lateinit var agentOrchestrator: AgentOrchestrator
    private lateinit var repository: ChatRepositoryImpl

    @Before
    fun setup() {
        context = mock()
        runtimeManager = mock()
        messageFactory = mock()
        responseMapper = mock()
        modelCatalog = mock()
        downloadManager = mock()
        agentOrchestrator = mock {
            on { agentState } doReturn MutableStateFlow(AgentState.Idle)
        }
        
        repository = ChatRepositoryImpl(
            context,
            runtimeManager,
            messageFactory,
            responseMapper,
            modelCatalog,
            downloadManager,
            agentOrchestrator
        )
    }

    @Test
    fun initializeModel_resetsAgentAndCallsRuntime() = runTest {
        val path = "path/to/model"
        whenever(runtimeManager.initialize(any())).doReturn(Result.success(Unit))
        
        repository.initializeModel(path)
        
        verify(agentOrchestrator).reset()
        verify(runtimeManager).initialize(path)
    }

    @Test
    fun sendMessage_whenVisionNotSupported_returnsFailure() = runTest {
        whenever(runtimeManager.isVisionSupported()).doReturn(false)
        val mockUri = mock<Uri>()
        
        val result = repository.sendMessage("test", mockUri)
        
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun sendMessage_delegatesToOrchestrator() = runTest {
        val prompt = "hello"
        whenever(agentOrchestrator.processUserRequest(prompt, null)).doReturn(Result.success("hi"))
        
        val result = repository.sendMessage(prompt, null)
        
        verify(agentOrchestrator).processUserRequest(prompt, null)
        assertEquals("hi", result.getOrNull())
    }

    @Test
    fun fetchAvailableModels_delegatesToCatalog() = runTest {
        val models = listOf(ModelEntry("gemma", "url"))
        whenever(modelCatalog.fetchAvailableModels()).doReturn(Result.success(models))
        
        val result = repository.fetchAvailableModels()
        
        assertEquals(models, result.getOrNull())
    }

    @Test
    fun clearConversation_resetsAgentAndRuntime() = runTest {
        repository.clearConversation()
        
        verify(agentOrchestrator).reset()
        verify(runtimeManager).clearConversation()
    }
}
