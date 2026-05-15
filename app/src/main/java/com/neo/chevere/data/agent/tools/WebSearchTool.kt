package com.neo.chevere.data.agent.tools

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.neo.chevere.BuildConfig
import com.neo.chevere.core.Constants
import com.neo.chevere.data.agent.AgentTool
import com.neo.chevere.data.agent.ToolResult
import com.neo.chevere.data.datasource.local.SearchCacheDao
import com.neo.chevere.data.datasource.local.SearchCacheEntity
import dagger.hilt.android.qualifiers.ApplicationContext
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
 * Includes internet connectivity checks to prevent unnecessary network requests.
 */
@Singleton
class WebSearchTool @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val httpClient: HttpClient,
    private val searchCacheDao: SearchCacheDao
) : AgentTool {
    override val name: String = "web_search"
    override val description: String =
        "Searches the web for real-time information, news, or general knowledge that might be after the model's cutoff date."
    override val inputSchema: String = "query: The search term or question to look up."

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val query = args["query"]?.trim() ?: return ToolResult.Error("Missing 'query' argument")

        // 1. Check Persistent Cache (Can work offline)
        val now = System.currentTimeMillis()
        try {
            val cachedEntry = searchCacheDao.getCachedResult(query)
            if (cachedEntry != null) {
                if (now - cachedEntry.timestamp < Constants.WebSearch.CACHE_EXPIRATION_MS) {
                    Log.d("WebSearchTool", "Returning persistent cached results for: $query")
                    return ToolResult.Success(cachedEntry.results)
                } else {
                    Log.d("WebSearchTool", "Cache expired for: $query")
                }
            }
        } catch (e: Exception) {
            Log.e("WebSearchTool", "Error reading from cache", e)
        }

        // 2. Check Internet Connectivity
        if (!isNetworkAvailable()) {
            return ToolResult.Error("No internet connection. Web search is currently unavailable, and no valid cached result was found.")
        }

        // 3. Call Serper API
        return try {
            Log.d("WebSearchTool", "Calling Serper API for: $query")
            val response: SerperResponse = httpClient.post(Constants.WebSearch.SERPER_API_URL) {
                header("X-API-KEY", BuildConfig.SERPER_API_KEY)
                contentType(ContentType.Application.Json)
                setBody(SerperRequest(q = query))
            }.body()

            val formattedResults = formatSerperResults(response)

            // 4. Update Cache with LRU pruning
            try {
                searchCacheDao.insertAndPrune(
                    SearchCacheEntity(query, formattedResults, now),
                    Constants.WebSearch.MAX_CACHE_SIZE
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

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false

        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> true
            else -> false
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
