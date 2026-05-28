package com.neo.chevere.data.agent

import android.content.Context
import com.neo.chevere.core.Constants
import com.neo.chevere.data.PreferenceManager
import com.neo.chevere.data.agent.actions.AndroidAppActionExecutor
import com.neo.chevere.data.agent.tools.AlarmTimerTool
import com.neo.chevere.data.agent.tools.AnalyzeImageTool
import com.neo.chevere.data.agent.tools.CopyToClipboardTool
import com.neo.chevere.data.agent.tools.CreateCalendarEventTool
import com.neo.chevere.data.agent.tools.DeviceControlTool
import com.neo.chevere.data.agent.tools.DraftEmailTool
import com.neo.chevere.data.agent.tools.GetActiveModelTool
import com.neo.chevere.data.agent.tools.GetAppCapabilitiesTool
import com.neo.chevere.data.agent.tools.GetModelDetailsTool
import com.neo.chevere.data.agent.tools.ImageGenerationTool
import com.neo.chevere.data.agent.tools.LaunchAppTool
import com.neo.chevere.data.agent.tools.ListAppsTool
import com.neo.chevere.data.agent.tools.ListModelsTool
import com.neo.chevere.data.agent.tools.LocalDocumentRagTool
import com.neo.chevere.data.agent.tools.OpenAppTool
import com.neo.chevere.data.agent.tools.OpenDeepLinkTool
import com.neo.chevere.data.agent.tools.OpenMapsTool
import com.neo.chevere.data.agent.tools.OpenUrlTool
import com.neo.chevere.data.agent.tools.QueryCalendarTool
import com.neo.chevere.data.agent.tools.ReadLocalFileTool
import com.neo.chevere.data.agent.tools.RecommendModelTool
import com.neo.chevere.data.agent.tools.RuntimeStatusTool
import com.neo.chevere.data.agent.tools.SearchAppsTool
import com.neo.chevere.data.agent.tools.SearchContactsTool
import com.neo.chevere.data.agent.tools.SensorsTool
import com.neo.chevere.data.agent.tools.SelectModelTool
import com.neo.chevere.data.agent.tools.ShareTextTool
import com.neo.chevere.data.agent.tools.SummarizeTextTool
import com.neo.chevere.data.agent.tools.TaskRegistryTool
import com.neo.chevere.data.agent.tools.WeatherTool
import com.neo.chevere.data.agent.tools.WebSearchTool
import com.neo.chevere.data.datasource.local.DocumentChunkDao
import com.neo.chevere.data.datasource.local.SearchCacheDao
import com.neo.chevere.data.datasource.local.TaskDao
import com.neo.chevere.data.inference.ImageGenerationManager
import com.neo.chevere.data.inference.InferenceManager
import com.neo.chevere.domain.InstalledModelRegistry
import io.ktor.client.HttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

class ToolRegistryTest {

    private lateinit var mockContext: Context
    private lateinit var mockHttpClient: HttpClient
    private lateinit var mockPreferenceManager: PreferenceManager
    private lateinit var mockInstalledModelRegistry: InstalledModelRegistry
    private lateinit var mockImageGenerationManager: ImageGenerationManager
    private lateinit var mockAppActionExecutor: AndroidAppActionExecutor
    private lateinit var mockSearchCacheDao: SearchCacheDao
    private lateinit var mockTaskDao: TaskDao
    private lateinit var mockDocumentChunkDao: DocumentChunkDao
    private lateinit var mockInferenceManager: InferenceManager

    private lateinit var productionTools: Set<AgentTool>

    @Before
    fun setup() {
        mockContext = mock()
        mockHttpClient = mock()
        mockPreferenceManager = mock()
        mockInstalledModelRegistry = mock()
        mockImageGenerationManager = mock()
        mockAppActionExecutor = mock()
        mockSearchCacheDao = mock()
        mockTaskDao = mock()
        mockDocumentChunkDao = mock()
        mockInferenceManager = mock()

        // Recreate all tools defined in AgentModule (and any other files in the codebase)
        productionTools = setOf(
            SummarizeTextTool(),
            WebSearchTool(mockContext, mockHttpClient, mockSearchCacheDao),
            WeatherTool(mockHttpClient, mockPreferenceManager, mock(), mockContext),
            ListModelsTool(mockInstalledModelRegistry, mockPreferenceManager),
            GetActiveModelTool(mockInstalledModelRegistry, mockPreferenceManager),
            GetModelDetailsTool(mockInstalledModelRegistry, mockPreferenceManager),
            SelectModelTool(mockPreferenceManager, mockInstalledModelRegistry),
            RecommendModelTool(mockInstalledModelRegistry),
            ImageGenerationTool(mockImageGenerationManager, mockPreferenceManager),
            RuntimeStatusTool(mockPreferenceManager),
            CopyToClipboardTool(mockAppActionExecutor),
            ShareTextTool(mockAppActionExecutor),
            OpenUrlTool(mockAppActionExecutor),
            OpenMapsTool(mockAppActionExecutor),
            DraftEmailTool(mockAppActionExecutor),
            SearchContactsTool(mockContext),
            SensorsTool(mockContext),
            ReadLocalFileTool(mockContext),
            DeviceControlTool(mockAppActionExecutor),
            CreateCalendarEventTool(mockAppActionExecutor),
            SearchAppsTool(mockAppActionExecutor),
            ListAppsTool(mockAppActionExecutor),
            LaunchAppTool(mockAppActionExecutor),
            OpenAppTool(mockAppActionExecutor),
            OpenDeepLinkTool(mockAppActionExecutor),
            GetAppCapabilitiesTool(mockAppActionExecutor),
            TaskRegistryTool(mockTaskDao),
            LocalDocumentRagTool(mockContext, mockDocumentChunkDao),
            QueryCalendarTool(mockContext),
            AlarmTimerTool(mockAppActionExecutor),
            AnalyzeImageTool(mockInferenceManager)
        )
    }

    @Test
    fun getTool_returnsCorrectTool() {
        val registry = ToolRegistry(productionTools)
        val tool = registry.getTool("web_search")
        assertNotNull(tool)
        assertEquals("web_search", tool?.name)
    }

    @Test
    fun getTool_returnsNullIfNotFound() {
        val registry = ToolRegistry(productionTools)
        val tool = registry.getTool("non_existent_tool_12345")
        assertNull(tool)
    }

    @Test
    fun validateProductionTools_noDuplicateNames() {
        val names = productionTools.map { it.name }
        val duplicates = names.groupBy { it }.filter { it.value.size > 1 }.keys
        assertTrue(
            "Found duplicate tool names: $duplicates",
            duplicates.isEmpty()
        )
    }

    @Test
    fun validateProductionTools_namingConventions() {
        val nameRegex = Regex("^[a-zA-Z0-9_]+$")
        productionTools.forEach { tool ->
            assertTrue(
                "Tool name '${tool.name}' must contain only letters, numbers, and underscores",
                nameRegex.matches(tool.name)
            )
        }
    }

    @Test
    fun validateProductionTools_metadataIsNonBlank() {
        productionTools.forEach { tool ->
            assertTrue(
                "Tool '${tool.name}' description must not be blank",
                tool.description.isNotBlank()
            )
            assertTrue(
                "Tool '${tool.name}' input schema must not be blank",
                tool.inputSchema.isNotBlank()
            )
        }
    }

    @Test
    fun systemPrompt_containsAllToolDescriptionsAndSchemas() {
        val registry = ToolRegistry(productionTools)
        val systemPrompt = registry.getToolsSystemPrompt()

        productionTools.forEach { tool ->
            assertTrue(
                "System prompt must contain tool name '${tool.name}'",
                systemPrompt.contains(tool.name)
            )
            assertTrue(
                "System prompt must contain description of '${tool.name}'",
                systemPrompt.contains(tool.description)
            )
        }
    }
}
