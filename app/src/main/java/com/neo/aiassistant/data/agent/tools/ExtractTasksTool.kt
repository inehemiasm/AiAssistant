package com.neo.aiassistant.data.agent.tools

import com.neo.aiassistant.data.agent.AgentTool
import com.neo.aiassistant.data.agent.ToolResult
import javax.inject.Inject

/**
 * A tool that identifies potential tasks or to-do items from text.
 */
class ExtractTasksTool @Inject constructor() : AgentTool {
    override val name: String = "extract_tasks"
    override val description: String = "Extracts a list of actionable tasks or to-do items from the provided text."
    override val inputSchema: String = "text: The content to analyze for tasks."

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val text = args["text"] ?: return ToolResult.Error("Missing 'text' argument")
        
        // Simple regex-based task extraction for demonstration
        val lines = text.split("\n")
        val tasks = lines.filter { line ->
            line.contains("todo", ignoreCase = true) || 
            line.contains("need to", ignoreCase = true) ||
            line.contains("should", ignoreCase = true) ||
            line.trim().startsWith("-") ||
            line.trim().startsWith("*")
        }.map { it.trim().removePrefix("-").removePrefix("*").trim() }
        
        return if (tasks.isNotEmpty()) {
            ToolResult.Success("Tasks found:\n" + tasks.joinToString("\n") { "- $it" })
        } else {
            ToolResult.Success("No clear tasks identified in the text.")
        }
    }
}
