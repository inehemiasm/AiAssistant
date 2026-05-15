package com.neo.chevere.data.agent

import com.neo.chevere.core.Constants
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses structured tool calls from LLM text responses.
 * Format: [TOOL_CALL: tool_name, param1=value1, param2=value2]
 * Also accepts JSON-like payloads: [TOOL_CALL: tool_name, {"param":"value"}]
 */
@Singleton
class ToolCallParser @Inject constructor() {

    fun parse(text: String): ToolCall? {
        val span = findToolCallSpan(text) ?: return null
        return parseToolCallBody(span.body)
    }

    private fun splitParams(paramsString: String): List<String> {
        val params = mutableListOf<String>()
        val current = StringBuilder()
        var quoteChar: Char? = null
        var escaped = false
        var nestingDepth = 0
        var inCodeFence = false

        var index = 0
        while (index < paramsString.length) {
            if (paramsString.startsWith("```", index)) {
                inCodeFence = !inCodeFence
                current.append("```")
                index += 3
                continue
            }

            val char = paramsString[index]
            when {
                inCodeFence -> current.append(char)
                quoteChar != null -> {
                    current.append(char)
                    if (escaped) {
                        escaped = false
                    } else if (char == '\\') {
                        escaped = true
                    } else if (char == quoteChar) {
                        quoteChar = null
                    }
                }

                char == '"' || char == '\'' -> {
                    quoteChar = char
                    current.append(char)
                }

                char == '{' || char == '[' || char == '(' -> {
                    nestingDepth++
                    current.append(char)
                }

                char == '}' || char == ']' || char == ')' -> {
                    nestingDepth = (nestingDepth - 1).coerceAtLeast(0)
                    current.append(char)
                }

                char == ',' && nestingDepth == 0 -> {
                    params.add(current.toString())
                    current.clear()
                }

                else -> current.append(char)
            }
            index++
        }

        if (current.isNotBlank()) {
            params.add(current.toString())
        }

        return params
    }

    fun stripToolCall(text: String): String {
        val span = findToolCallSpan(text) ?: return text.trim()
        return (text.substring(0, span.start) + text.substring(span.endExclusive)).trim()
    }

    private fun parseToolCallBody(body: String): ToolCall? {
        val trimmedBody = body.trim()
        if (trimmedBody.isEmpty()) return null

        val toolNameEnd = trimmedBody.indexOfFirst { it == ',' || it.isWhitespace() }
            .let { if (it == -1) trimmedBody.length else it }
        val toolName = trimmedBody.substring(0, toolNameEnd).trim()
        if (toolName.isEmpty()) return null

        val paramsString = trimmedBody.substring(toolNameEnd).trim().removePrefix(",").trim()
        return ToolCall(toolName, parseArguments(paramsString))
    }

    private fun parseArguments(paramsString: String): Map<String, String> {
        if (paramsString.isBlank()) return emptyMap()

        val normalizedParams = paramsString.trim()
        val jsonParams = parseFlatJsonObject(normalizedParams)
        if (jsonParams != null) return jsonParams

        return splitParams(normalizedParams)
            .mapNotNull { param ->
                val parts = param.split("=", limit = 2).map { it.trim() }
                if (parts.size != 2) return@mapNotNull null
                parts[0] to stripSurroundingQuotes(parts[1])
            }
            .toMap()
    }

    private fun parseFlatJsonObject(json: String): Map<String, String>? {
        if (!json.startsWith("{") || !json.endsWith("}")) return null

        val inner = json.substring(1, json.length - 1)
        return splitParams(inner)
            .mapNotNull { param ->
                val parts = param.split(":", limit = 2).map { it.trim() }
                if (parts.size != 2) return@mapNotNull null
                stripSurroundingQuotes(parts[0]) to stripSurroundingQuotes(parts[1])
            }
            .toMap()
    }

    private fun stripSurroundingQuotes(rawValue: String): String {
        var value = rawValue.trim()
        while ((value.startsWith("\"") && value.endsWith("\"")) ||
            (value.startsWith("'") && value.endsWith("'"))
        ) {
            if (value.length < 2) break
            value = value.substring(1, value.length - 1).trim()
        }
        return value.replace("\\\"", "\"").replace("\\'", "'")
    }

    private fun findToolCallSpan(text: String): ToolCallSpan? {
        val start = text.indexOf(Constants.Agent.TOOL_CALL_PREFIX)
        if (start == -1) return null

        val bodyStart = start + Constants.Agent.TOOL_CALL_PREFIX.length
        val end = findClosingBracket(text, bodyStart) ?: return null
        return ToolCallSpan(
            start = start,
            endExclusive = end + 1,
            body = text.substring(bodyStart, end)
        )
    }

    private fun findClosingBracket(text: String, startIndex: Int): Int? {
        var quoteChar: Char? = null
        var escaped = false
        var nestingDepth = 0
        var inCodeFence = false

        var index = startIndex
        while (index < text.length) {
            if (text.startsWith("```", index)) {
                inCodeFence = !inCodeFence
                index += 3
                continue
            }

            val char = text[index]
            when {
                inCodeFence -> Unit
                quoteChar != null -> {
                    if (escaped) {
                        escaped = false
                    } else if (char == '\\') {
                        escaped = true
                    } else if (char == quoteChar) {
                        quoteChar = null
                    }
                }

                char == '"' || char == '\'' -> quoteChar = char
                char == '[' || char == '{' || char == '(' -> nestingDepth++
                char == ']' -> {
                    if (nestingDepth == 0) return index
                    nestingDepth--
                }

                char == '}' || char == ')' -> nestingDepth = (nestingDepth - 1).coerceAtLeast(0)
            }
            index++
        }

        return null
    }

    private data class ToolCallSpan(
        val start: Int,
        val endExclusive: Int,
        val body: String
    )
}
