package com.neo.chevere.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neo.chevere.ui.designsystem.Typography

data class MarkdownBlock(
    val text: String,
    val language: String? = null,
    val isCode: Boolean = false
)

/**
 * Parses a simple markdown string and converts it to an [AnnotatedString] for display in Compose.
 *
 * Supports bold (**text**), italic (*text*), and inline code (`text`).
 * Also provides special styling for the "Chevere AI" brand keyword.
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

@Composable
fun MarkdownContent(
    text: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = Typography.bodyMedium,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val blocks = parseMarkdownBlocks(text)
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f)
    val codeBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val codeText = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        blocks.forEach { block ->
            if (block.isCode) {
                CodeBlock(
                    block = block,
                    background = codeBackground,
                    border = codeBorder,
                    contentColor = codeText
                )
            } else {
                Text(
                    text = parseMarkdown(block.text),
                    style = textStyle.copy(color = textColor)
                )
            }
        }
    }
}

@Composable
private fun CodeBlock(
    block: MarkdownBlock,
    background: Color,
    border: Color,
    contentColor: Color,
    scrollState: ScrollState = rememberScrollState()
) {
    Surface(
        color = background,
        contentColor = contentColor,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            block.language?.takeIf { it.isNotBlank() }?.let { language ->
                Text(
                    text = language.uppercase(),
                    style = Typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor.copy(alpha = 0.62f)
                    ),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Text(
                text = block.text.trimEnd(),
                style = Typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = contentColor
                ),
                modifier = Modifier.horizontalScroll(scrollState)
            )
        }
    }
}

fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    var currentIndex = 0

    while (currentIndex < text.length) {
        val fenceStart = text.indexOf("```", currentIndex)
        if (fenceStart == -1) {
            addTextBlock(blocks, text.substring(currentIndex))
            break
        }

        addTextBlock(blocks, text.substring(currentIndex, fenceStart))

        val infoStart = fenceStart + 3
        val lineEnd = text.indexOf('\n', infoStart)
        val codeStart: Int
        val language: String?
        if (lineEnd == -1) {
            codeStart = infoStart
            language = null
        } else {
            language = text.substring(infoStart, lineEnd).trim().takeIf { it.isNotBlank() }
            codeStart = lineEnd + 1
        }

        val fenceEnd = text.indexOf("```", codeStart)
        if (fenceEnd == -1) {
            blocks += MarkdownBlock(
                text = text.substring(codeStart),
                language = language,
                isCode = true
            )
            break
        }

        blocks += MarkdownBlock(
            text = text.substring(codeStart, fenceEnd),
            language = language,
            isCode = true
        )
        currentIndex = fenceEnd + 3
    }

    return blocks.ifEmpty { listOf(MarkdownBlock(text)) }
}

private fun addTextBlock(blocks: MutableList<MarkdownBlock>, text: String) {
    val cleaned = text.trim()
    if (cleaned.isNotEmpty()) {
        blocks += MarkdownBlock(cleaned)
    }
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
        val highlightRegex = Regex("(Chevere AI|Chevere)")

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

                match.value == "Chevere" || match.value == "Chevere AI" -> {
                    addStyle(
                        SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold),
                        start,
                        end
                    )
                }
            }

            currentPos = match.range.last + 1
        }
    }
}
