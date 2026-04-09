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
        val contents = message.contents.contents
        if (contents.isEmpty()) return ""

        return contents.joinToString("") { content ->
            when (content) {
                is Content.Text -> content.text
                else -> {
                    // Handle cases where the library might use internal subclasses 
                    // or if future content types are added.
                    try {
                        val textField = content.javaClass.getDeclaredField("text")
                        textField.isAccessible = true
                        textField.get(content) as? String ?: ""
                    } catch (e: Exception) {
                        // Fallback to string parsing as a last resort
                        val str = content.toString()
                        if (str.contains("text=")) {
                            str.substringAfter("text=").substringBeforeLast(")")
                        } else {
                            ""
                        }
                    }
                }
            }
        }.trim()
    }
}
