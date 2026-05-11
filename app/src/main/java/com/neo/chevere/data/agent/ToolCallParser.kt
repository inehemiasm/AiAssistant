package com.neo.chevere.data.agent

import com.neo.chevere.core.Constants
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses structured tool calls from LLM text responses.
 * Format: [TOOL_CALL: tool_name, param1=value1, param2=value2]
 */
@Singleton
class ToolCallParser @Inject constructor() {

    fun parse(text: String): ToolCall? {
        val regex = Constants.Agent.TOOL_CALL_PATTERN.toRegex()
        val match = regex.find(text) ?: return null
        
        val toolName = match.groupValues[1]
        val paramsString = match.groupValues.getOrNull(2) ?: ""
        
        val arguments = mutableMapOf<String, String>()
        if (paramsString.isNotBlank()) {
            splitParams(paramsString).forEach { param ->
                // Split by first '=' only to support values that contain '=' (like URLs)
                val parts = param.split("=", limit = 2).map { it.trim() }
                if (parts.size == 2) {
                    val key = parts[0]
                    var value = parts[1]
                    
                    // Strip quotes if present (e.g., url="https://google.com" or url='https://google.com')
                    // We remove all surrounding quotes to handle cases like ""google.com"" or " google.com "
                    value = value.trim()
                    while ((value.startsWith("\"") && value.endsWith("\"")) || 
                           (value.startsWith("'") && value.endsWith("'"))) {
                        if (value.length < 2) break
                        value = value.substring(1, value.length - 1).trim()
                    }
                    
                    arguments[key] = value
                }
            }
        }
        
        return ToolCall(toolName, arguments)
    }

    private fun splitParams(paramsString: String): List<String> {
        val params = mutableListOf<String>()
        val current = StringBuilder()
        var quoteChar: Char? = null

        paramsString.forEach { char ->
            when {
                quoteChar != null -> {
                    current.append(char)
                    if (char == quoteChar) quoteChar = null
                }
                char == '"' || char == '\'' -> {
                    quoteChar = char
                    current.append(char)
                }
                char == ',' -> {
                    params.add(current.toString())
                    current.clear()
                }
                else -> current.append(char)
            }
        }

        if (current.isNotBlank()) {
            params.add(current.toString())
        }

        return params
    }
    
    fun stripToolCall(text: String): String {
        return text.replace(Constants.Agent.TOOL_CALL_STRIP_PATTERN.toRegex(), "").trim()
    }
}
