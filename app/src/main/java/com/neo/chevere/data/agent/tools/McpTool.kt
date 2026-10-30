package com.neo.chevere.data.agent.tools

import com.neo.chevere.data.agent.AgentTool
import com.neo.chevere.data.agent.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Minimal remote MCP client: negotiates a session, discovers tools, and confirms remote calls. */
class McpTool @Inject constructor() : AgentTool {
    override val name = "mcp"
    override val description = "Connect to a user-specified HTTPS MCP server. List tools first, then call a discovered tool."
    override val inputSchema = "action: list or call; endpoint: HTTPS MCP URL; tool: name for call; arguments: JSON object for call"
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .callTimeout(30, TimeUnit.SECONDS).build()

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val endpoint = args["endpoint"] ?: return ToolResult.Error("Provide an HTTPS MCP endpoint.")
        val url = try { endpoint.toHttpUrl() } catch (_: Exception) { return ToolResult.Error("Invalid MCP URL.") }
        if (!url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty()) return ToolResult.Error("Use HTTPS without credentials in the URL.")
        val action = args["action"] ?: "list"
        if (action !in setOf("list", "call")) return ToolResult.Error("Use list or call.")
        return ToolResult.NeedsConfirmation(
            if (action == "call") "Call MCP tool ${args["tool"]} on ${url.host} with arguments ${args["arguments"] ?: "{}"}?"
            else "Connect to MCP server $url and discover its tools?"
        ) { exchange(endpoint, action, args) }
    }

    private suspend fun exchange(endpoint: String, action: String, args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        try {
            var session: String? = null
            var version: String? = null
            var nextId = 0
            fun rpc(method: String, params: JSONObject = JSONObject(), notification: Boolean = false): JSONObject {
                val id = ++nextId
                val payload = JSONObject().put("jsonrpc", "2.0").put("method", method).put("params", params)
                if (!notification) payload.put("id", id)
                val request = Request.Builder().url(endpoint)
                    .header("Accept", "application/json, text/event-stream")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                session?.let { request.header("Mcp-Session-Id", it) }
                version?.let { request.header("MCP-Protocol-Version", it) }
                client.newCall(request.build()).execute().use { response ->
                    check(response.isSuccessful) { "MCP HTTP ${response.code}. Check the endpoint and server access requirements." }
                    response.header("Mcp-Session-Id")?.let { session = it }
                    if (notification) return JSONObject()
                    val body = response.body ?: error("Empty MCP response.")
                    val message = if (response.header("Content-Type").orEmpty().startsWith("text/event-stream")) {
                        val source = body.source()
                        var data = StringBuilder()
                        var size = 0
                        var found: JSONObject? = null
                        while (!source.exhausted()) {
                            val line = source.readUtf8LineStrict(1024 * 1024)
                            size += line.length
                            check(size <= 1024 * 1024) { "MCP response is too large." }
                            if (line.isEmpty()) {
                                if (data.isNotEmpty()) {
                                    val event = JSONObject(data.toString())
                                    if (event.optInt("id", -1) == id && (event.has("result") || event.has("error"))) { found = event; break }
                                }
                                data = StringBuilder()
                            } else if (line.startsWith("data:")) {
                                if (data.isNotEmpty()) data.append('\n')
                                data.append(line.removePrefix("data:").removePrefix(" "))
                            }
                        }
                        found ?: error("MCP stream ended before the response arrived.")
                    } else {
                        val source = body.source()
                        check(!source.request(1024 * 1024 + 1L)) { "MCP response is too large." }
                        JSONObject(source.readUtf8())
                    }
                    check(message.optString("jsonrpc") == "2.0" && message.optInt("id", -1) == id) { "Invalid MCP response." }
                    check(!message.has("error")) { "MCP error: ${message.optJSONObject("error")?.optString("message")}" }
                    return message.getJSONObject("result")
                }
            }
            val initialized = rpc("initialize", JSONObject()
                .put("protocolVersion", "2025-11-25").put("capabilities", JSONObject())
                .put("clientInfo", JSONObject().put("name", "Chevere").put("version", "1.0")))
            version = initialized.getString("protocolVersion")
            check(version in setOf("2025-11-25", "2025-06-18", "2025-03-26")) { "Unsupported MCP protocol version." }
            rpc("notifications/initialized", notification = true)
            check(initialized.optJSONObject("capabilities")?.has("tools") == true) { "Server does not support tools." }
            val tools = mutableListOf<JSONObject>()
            var cursor: String? = null
            var pages = 0
            do {
                check(++pages <= 20) { "Too many MCP tool pages." }
                val params = JSONObject()
                cursor?.let { params.put("cursor", it) }
                val page = rpc("tools/list", params)
                val entries = page.getJSONArray("tools")
                for (i in 0 until entries.length()) tools.add(entries.getJSONObject(i))
                check(tools.size <= 200) { "Server exposes too many tools." }
                val previous = cursor
                cursor = page.optString("nextCursor").takeIf { it.isNotBlank() }
                check(cursor == null || cursor != previous) { "Invalid MCP pagination." }
            } while (cursor != null)
            if (action == "list") ToolResult.Success(tools.joinToString("\n").take(24000))
            else {
                val tool = args["tool"] ?: error("Specify a tool name.")
                check(tools.any { it.getString("name") == tool }) { "Tool is not advertised by this server." }
                val result = rpc("tools/call", JSONObject().put("name", tool)
                    .put("arguments", JSONObject(args["arguments"] ?: "{}")))
                if (result.optBoolean("isError")) ToolResult.Error(result.toString().take(12000))
                else ToolResult.Success(result.toString().take(12000))
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { ToolResult.Error(e.message ?: "MCP connection failed.") }
    }
}
