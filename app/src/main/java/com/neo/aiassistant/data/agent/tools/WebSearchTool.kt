package com.neo.aiassistant.data.agent.tools

import android.util.Log
import com.neo.aiassistant.BuildConfig
import com.neo.aiassistant.data.agent.AgentTool
import com.neo.aiassistant.data.agent.ToolResult
import com.neo.aiassistant.data.datasource.local.SearchCacheDao
import com.neo.aiassistant.data.datasource.local.SearchCacheEntity
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A tool that allows the agent to search the web for real-time information using Serper.dev.
 * Implements a persistent Room-based cache with an LRU pruning strategy.
 */
@Singleton
class WebSearchTool @Inject constructor(
    private val httpClient: HttpClient,
    private val searchCacheDao: SearchCacheDao
) : AgentTool {
    override val name: String = "web_search"
    override val description: String = "Searches the web for real-time information, news, or general knowledge that might be after the model's cutoff date."
    override val inputSchema: String = "query: The search term or question to look up."

    private val cacheExpirationMs = 3600_000 * 24 // 24 hours for persistent cache
    private val maxCacheSize = 50 // Limit to 50 entries (LRU)

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val query = args["query"]?.trim() ?: return ToolResult.Error("Missing 'query' argument")
        
        // 1. Check Persistent Cache
        val now = System.currentTimeMillis()
        try {
            val cachedEntry = searchCacheDao.getCachedResult(query)
            if (cachedEntry != null) {
                if (now - cachedEntry.timestamp < cacheExpirationMs) {
                    Log.d("WebSearchTool", "Returning persistent cached results for: $query")
                    return ToolResult.Success(cachedEntry.results)
                } else {
                    Log.d("WebSearchTool", "Cache expired for: $query")
                }
            }
        } catch (e: Exception) {
            Log.e("WebSearchTool", "Error reading from cache", e)
        }

        // 2. Call Serper API
        return try {
            Log.d("WebSearchTool", "Calling Serper API for: $query")
            val response: SerperResponse = httpClient.post("https://google.serper.dev/search") {
                header("X-API-KEY", BuildConfig.SERPER_API_KEY)
                contentType(ContentType.Application.Json)
                setBody(SerperRequest(q = query))
            }.body()

            val formattedResults = formatSerperResults(response)
            
            // 3. Update Cache with LRU pruning
            try {
                searchCacheDao.insertAndPrune(
                    SearchCacheEntity(query, formattedResults, now),
                    maxCacheSize
                )
            } catch (e: Exception) {
                Log.e("WebSearchTool", "Error saving to cache", e)
            }
            
            ToolResult.Success(formattedResults)
        } catch (e: Exception) {
            Log.e("WebSearchTool", "Search failed", e)
            ToolResult.Error("Failed to search the web: ${e.message}")
        }
    }

    private fun formatSerperResults(response: SerperResponse): String {
        val snippets = response.organic?.take(3)?.mapIndexed { index, it ->
            "${index + 1}. [${it.title}] ${it.snippet}"
        } ?: emptyList()

        return if (snippets.isNotEmpty()) {
            "Top results found:\n${snippets.joinToString("\n")}"
        } else {
            "No significant results found for this query."
        }
    }

    @Serializable
    data class SerperRequest(val q: String)

    @Serializable
    data class SerperResponse(
        val organic: List<OrganicResult>? = null
    )

    @Serializable
    data class OrganicResult(
        val title: String,
        val snippet: String,
        val link: String
    )
}
