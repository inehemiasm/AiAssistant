package com.neo.chevere.data.inference

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
    fun mapToString(message: Message, trim: Boolean = true): String {
        val contents = message.contents.contents
        if (contents.isEmpty()) return ""

        val rawText = contents.joinToString("") { content ->
            when (content) {
                is Content.Text -> content.text
                else -> extractTextFromContent(content)
            }
        }
        return if (trim) rawText.trim() else rawText
    }

    internal fun extractTextFromContent(content: Any): String {
        try {
            val stringField = content.javaClass.declaredFields.find { it.type == String::class.java }
            return if (stringField != null) {
                stringField.isAccessible = true
                stringField.get(content) as? String ?: ""
            } else {
                val str = content.toString()
                val prefix = "text="
                val startIndex = str.indexOf(prefix)
                if (startIndex != -1) {
                    val valStart = startIndex + prefix.length
                    val valEnd = str.lastIndexOf(')')
                    if (valEnd > valStart) {
                        str.substring(valStart, valEnd)
                    } else {
                        str.substring(valStart)
                    }
                } else {
                    ""
                }
            }
        } catch (e: Exception) {
            // Safe fallback
            val str = content.toString()
            return if (str.contains("text=")) {
                str.substringAfter("text=").substringBeforeLast(")")
            } else {
                ""
            }
        }
    }
}
