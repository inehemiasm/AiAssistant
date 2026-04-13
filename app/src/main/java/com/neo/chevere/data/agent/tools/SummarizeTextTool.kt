package com.neo.chevere.data.agent.tools

import com.neo.chevere.data.agent.AgentTool
import com.neo.chevere.data.agent.ToolResult
import javax.inject.Inject

/**
 * A tool that "simulates" text summarization or provides a specialized logic for it.
 * In a real app, this might use a different model or a specific algorithm.
 */
class SummarizeTextTool @Inject constructor() : AgentTool {
    override val name: String = "summarize_text"
    override val description: String = "Summarizes a long piece of text into a concise paragraph."
    override val inputSchema: String = "text: The long text to summarize."

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val text = args["text"] ?: return ToolResult.Error("Missing 'text' argument")
        
        // For now, we'll just simulate a summary. In a real agent, 
        // this might be a call to a smaller/faster model.
        val words = text.split(" ")
        val summary = if (words.size > 20) {
            words.take(15).joinToString(" ") + "..."
        } else {
            text
        }
        
        return ToolResult.Success("Summary: $summary")
    }
}
