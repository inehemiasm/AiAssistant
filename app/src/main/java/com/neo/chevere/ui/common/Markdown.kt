package com.neo.chevere.ui.common

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.neo.chevere.ui.designsystem.Typography
import kotlinx.coroutines.delay
import java.util.Locale

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
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    showCursor: Boolean = false,
    onPreviewHtmlFullScreen: ((String) -> Unit)? = null
) {
    var cursorVisible by remember { mutableStateOf(true) }
    if (showCursor) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(500)
                cursorVisible = !cursorVisible
            }
        }
    }

    val blocks = parseMarkdownBlocks(text)
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f)
    val codeBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val codeText = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        blocks.forEachIndexed { index, block ->
            val isLast = index == blocks.lastIndex
            if (block.isCode) {
                CodeBlock(
                    block = if (isLast && showCursor) {
                        block.copy(text = block.text + (if (cursorVisible) "▊" else ""))
                    } else block,
                    allBlocks = blocks,
                    background = codeBackground,
                    border = codeBorder,
                    contentColor = codeText,
                    onPreviewHtmlFullScreen = onPreviewHtmlFullScreen
                )
            } else {
                val blockText = if (isLast && showCursor) {
                    block.text + (if (cursorVisible) " ▊" else " \u200B")
                } else {
                    block.text
                }
                Text(
                    text = parseMarkdown(blockText),
                    style = textStyle.copy(color = textColor)
                )
            }
        }
    }
}

enum class CodeBlockTab { CODE, PREVIEW }

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HtmlSandboxPreview(
    html: String,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                }
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        },
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
    )
}

private fun compileHtmlWithAssets(htmlBlock: MarkdownBlock, allBlocks: List<MarkdownBlock>): String {
    var html = htmlBlock.text

    // Find all CSS code blocks in the message
    val cssContent = allBlocks
        .filter { it.isCode && it.language?.lowercase(Locale.US)?.trim() == "css" }
        .joinToString("\n") { it.text }

    // Find all JS/TS code blocks in the message
    val jsContent = allBlocks
        .filter { it.isCode && it.language?.lowercase(Locale.US)?.trim() in listOf("js", "javascript", "ts", "typescript") }
        .joinToString("\n") { it.text }

    // Inject CSS content into the HTML structure (before </head> or prepended)
    if (cssContent.isNotBlank()) {
        val styleTag = "\n<style>\n$cssContent\n</style>\n"
        html = if (html.contains("</head>")) {
            html.replace("</head>", "$styleTag</head>")
        } else {
            styleTag + html
        }
    }

    // Inject JS content into the HTML structure (before </body> or appended)
    if (jsContent.isNotBlank()) {
        val scriptTag = "\n<script>\n$jsContent\n</script>\n"
        html = if (html.contains("</body>")) {
            html.replace("</body>", "$scriptTag</body>")
        } else {
            html + scriptTag
        }
    }

    return html
}

@Composable
private fun CodeBlock(
    block: MarkdownBlock,
    allBlocks: List<MarkdownBlock>,
    background: Color,
    border: Color,
    contentColor: Color,
    scrollState: ScrollState = rememberScrollState(),
    onPreviewHtmlFullScreen: ((String) -> Unit)? = null
) {
    val isHtmlPreviewable = block.language?.lowercase(Locale.US)?.trim() in listOf("html", "xml", "svg")
    var activeTab by remember { mutableStateOf(CodeBlockTab.CODE) }

    Surface(
        color = background,
        contentColor = contentColor,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                block.language?.takeIf { it.isNotBlank() }?.let { language ->
                    Text(
                        text = language.uppercase(),
                        style = Typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = contentColor.copy(alpha = 0.62f)
                        )
                    )
                }

                if (isHtmlPreviewable) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val activeColor = MaterialTheme.colorScheme.primary
                        val inactiveColor = contentColor.copy(alpha = 0.5f)

                        TextButton(
                            onClick = { activeTab = CodeBlockTab.CODE },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(
                                text = "CODE",
                                style = Typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (activeTab == CodeBlockTab.CODE) activeColor else inactiveColor
                                )
                            )
                        }

                        TextButton(
                            onClick = { activeTab = CodeBlockTab.PREVIEW },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(
                                text = "PREVIEW",
                                style = Typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (activeTab == CodeBlockTab.PREVIEW) activeColor else inactiveColor
                                )
                            )
                        }

                        if (activeTab == CodeBlockTab.PREVIEW && onPreviewHtmlFullScreen != null) {
                            val compiledHtml = remember(block.text, allBlocks) {
                                compileHtmlWithAssets(block, allBlocks)
                            }
                            IconButton(
                                onClick = { onPreviewHtmlFullScreen(compiledHtml) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = "Preview fullscreen",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                color = border.copy(alpha = 0.5f),
                thickness = 0.5.dp
            )

            // Content Area
            Box(modifier = Modifier.padding(12.dp)) {
                if (isHtmlPreviewable && activeTab == CodeBlockTab.PREVIEW) {
                    val compiledHtml = remember(block.text, allBlocks) {
                        compileHtmlWithAssets(block, allBlocks)
                    }
                    HtmlSandboxPreview(html = compiledHtml)
                } else {
                    val highlightedText = remember(block.text, block.language) {
                        CodeHighlighter.highlight(block.text, block.language)
                    }
                    Text(
                        text = highlightedText,
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
