package com.neo.aiassistant.data.inference

import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Message
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles extraction and mapping of LiteRT-LM response messages into domain strings.
 */
@Singleton
class LlmResponseMapper @Inject constructor() {

    /**
     * Extracts text content from a LiteRT-LM Message response.
     */
    fun mapToString(message: Message): String {
        return message.contents.contents.joinToString("") { content ->
            when (content) {
                is Content.Text -> content.text
                else -> {
                    // Fallback for cases where the specific library version might wrap text differently
                    // or if future content types (like ToolCalls) are added.
                    val str = content.toString()
                    if (str.contains("text=")) {
                        str.substringAfter("text=").substringBeforeLast(")")
                    } else {
                        ""
                    }
                }
            }
        }.trim()
    }
}
