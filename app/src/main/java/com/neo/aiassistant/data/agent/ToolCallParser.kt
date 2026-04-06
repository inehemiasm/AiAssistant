package com.neo.aiassistant.data.agent

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
            paramsString.split(",").forEach { param ->
                val parts = param.split("=").map { it.trim() }
                if (parts.size == 2) {
                    arguments[parts[0]] = parts[1]
                }
            }
        }
        
        return ToolCall(toolName, arguments)
    }
    
    fun stripToolCall(text: String): String {
        return text.replace("""\[TOOL_CALL:.*?\]""".toRegex(), "").trim()
    }
}
