package com.neo.chevere.data.agent

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses structured tool calls from LLM text responses.
 * Format: [TOOL_CALL: tool_name, param1=value1, param2=value2]
 */
@Singleton
class ToolCallParser @Inject constructor() {

    fun parse(text: String): ToolCall? {
        val regex = """\[TOOL_CALL:\s*(\w+)\s*(?:,\s*([^\]]+))?\]""".toRegex()
        val match = regex.find(text) ?: return null
        
        val toolName = match.groupValues[1]
        val paramsString = match.groupValues.getOrNull(2) ?: ""
        
        val arguments = mutableMapOf<String, String>()
        if (paramsString.isNotBlank()) {
            // Split by comma, but be careful of commas inside quoted values if we ever support them.
            // For now, simple split is okay for basic parameters.
            paramsString.split(",").forEach { param ->
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
    
    fun stripToolCall(text: String): String {
        return text.replace("""\[TOOL_CALL:.*?\]""".toRegex(), "").trim()
    }
}
