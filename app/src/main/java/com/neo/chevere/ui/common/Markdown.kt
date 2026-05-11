package com.neo.chevere.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * Parses a simple markdown string and converts it to an [AnnotatedString] for display in Compose.
 *
 * Supports bold (**text**), italic (*text*), and inline code (`text`).
 * Also provides special styling for the "Chevere" keyword.
 *
 * @param text The markdown text to parse.
 * @return An [AnnotatedString] with the appropriate styles applied.
 */
@Composable
fun parseMarkdown(text: String): AnnotatedString {
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f)
    val primaryColor = MaterialTheme.colorScheme.primary
    return parseMarkdownLogic(text, codeBackground, primaryColor)
}

/**
 * Internal logic for markdown parsing, decoupled from Compose themes for easier testing.
 *
 * @param text The text to parse.
 * @param codeBackground The background color to use for code spans.
 * @param primaryColor The primary color to use for highlights and code text.
 * @return An [AnnotatedString] with the appropriate styles applied.
 */
fun parseMarkdownLogic(
    text: String,
    codeBackground: Color,
    primaryColor: Color
): AnnotatedString {
    // Remove surrounding brackets if they exist
    var cleanText = text.trim()
    if (cleanText.startsWith("[") && cleanText.endsWith("]")) {
        cleanText = cleanText.substring(1, cleanText.length - 1).trim()
    }

    // Replace multiline bullet points
    cleanText = cleanText.replace(Regex("^\\s*[*+]\\s+", RegexOption.MULTILINE), " • ")
    cleanText = cleanText.replace(Regex("^\\s*-\\s+", RegexOption.MULTILINE), " • ")

    return buildAnnotatedString {
        val boldRegex = Regex("""\*\*(.*?)\*\*""")
        val italicRegex = Regex("""\*(?!\*)(.*?)\*""")
        val codeRegex = Regex("""`(.*?)`""")
        val highlightRegex = Regex("(Chevere)") // Example of keyword highlighting from design

        var currentPos = 0
        
        while (currentPos < cleanText.length) {
            val bMatch = boldRegex.find(cleanText, currentPos)
            val iMatch = italicRegex.find(cleanText, currentPos)
            val cMatch = codeRegex.find(cleanText, currentPos)
            val hMatch = highlightRegex.find(cleanText, currentPos)

            val matches = listOfNotNull(bMatch, iMatch, cMatch, hMatch)
                .sortedWith(compareBy({ it.range.first }, { -it.value.length }))

            if (matches.isEmpty()) {
                append(cleanText.substring(currentPos))
                break
            }

            val match = matches.first()
            
            if (match.range.first > currentPos) {
                append(cleanText.substring(currentPos, match.range.first))
            }

            val start = length
            val content = if (match.groupValues.size > 1) match.groupValues[1] else ""
            append(content)
            val end = length

            when {
                match.value.startsWith("**") -> {
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                }
                match.value.startsWith("`") -> {
                    addStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBackground,
                            color = primaryColor
                        ), start, end
                    )
                }
                match.value.startsWith("*") -> {
                    addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                }
                match.value == "Chevere" -> {
                    addStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold), start, end)
                }
            }
            
            currentPos = match.range.last + 1
        }
    }
}
