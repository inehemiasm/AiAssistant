package com.neo.chevere.data.agent

import com.neo.chevere.data.agent.tools.LocalDocumentRagTool
import com.neo.chevere.data.agent.tools.McpTool
import com.neo.chevere.data.datasource.local.DocumentChunkDao
import com.neo.chevere.data.datasource.local.DocumentChunkEntity
import com.neo.chevere.data.chat.RoutingCategory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class DocumentRagTest {
    @Test fun unicodeDocumentSearchAndEmptyIndexAreUsable() = runTest {
        val dao = mock<DocumentChunkDao>()
        val tool = LocalDocumentRagTool(RuntimeEnvironment.getApplication(), dao)
        whenever(dao.getAllChunks()).thenReturn(emptyList())
        assertTrue((tool.execute(mapOf("action" to "search", "query" to "warranty")) as ToolResult.Success).data.contains("No documents"))
        whenever(dao.getAllChunks()).thenReturn(listOf(DocumentChunkEntity(filePath="content://test/1", fileName="policy.md", chunkIndex=0, text="La garantía cubre reparación durante dos años.")))
        val result = tool.execute(mapOf("action" to "search", "query" to "garantía reparación")) as ToolResult.Success
        assertTrue(result.data.contains("policy.md"))
        val summary = tool.execute(mapOf("action" to "search", "query" to "Summarize policy.md")) as ToolResult.Success
        assertTrue(summary.data.contains("La garantía"))
        val prompt = ToolRegistry(setOf(tool)).getToolsSystemPrompt(RoutingCategory.DOCUMENTS)
        assertTrue(prompt.contains("document_rag"))
        assertTrue(prompt.contains("untrusted data"))
    }

    @Test fun mcpRequiresSecureEndpointAndConfirmation() = runTest {
        val tool = McpTool()
        assertTrue(tool.execute(mapOf("endpoint" to "http://example.com/mcp")) is ToolResult.Error)
        assertTrue(tool.execute(mapOf("endpoint" to "https://example.com/mcp", "action" to "list")) is ToolResult.NeedsConfirmation)
    }
}
