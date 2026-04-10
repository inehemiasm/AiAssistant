package com.neo.aiassistant.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

@Composable
fun parseMarkdown(text: String): AnnotatedString {
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f)
    val primaryColor = MaterialTheme.colorScheme.primary
    return parseMarkdownLogic(text, codeBackground, primaryColor)
}

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
        val highlightRegex = Regex("(Nebula Core)") // Example of keyword highlighting from design

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
                match.value == "Nebula Core" -> {
                    addStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold), start, end)
                }
            }
            
            currentPos = match.range.last + 1
        }
    }
}
