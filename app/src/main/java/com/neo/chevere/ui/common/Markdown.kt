package com.neo.chevere.ui.common

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.style.TextAlign
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
    val isCode: Boolean = false,
    /** True when the block is a LaTeX block-math expression (delimited by `$$...$$`). */
    val isMath: Boolean = false
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
            when {
                block.isMath -> {
                    MathBlock(
                        expression = if (isLast && showCursor) {
                            block.text + (if (cursorVisible) "▊" else "")
                        } else block.text
                    )
                }
                block.isCode -> {
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
                }
                else -> {
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
}

/**
 * Renders a block-math LaTeX expression as a styled display block.
 *
 * The expression is prettified (LaTeX commands → Unicode) and shown centered
 * on a tinted surface so it stands out from prose.
 *
 * @param expression The raw LaTeX expression (without surrounding `$$`).
 */
@Composable
private fun MathBlock(expression: String, modifier: Modifier = Modifier) {
    val mathColor = MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
    val borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)

    Surface(
        color = bgColor,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = prettifyMath(expression.trim()),
            style = Typography.bodyMedium.copy(
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = mathColor,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
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
        // Check for block math $$ before code fences $$
        val mathFenceStart = text.indexOf("$$", currentIndex)
        val codeFenceStart = text.indexOf("```", currentIndex)

        // Pick whichever delimiter comes first
        val nextIsMath = mathFenceStart != -1 &&
            (codeFenceStart == -1 || mathFenceStart <= codeFenceStart)
        val nextIsCode = codeFenceStart != -1 &&
            (mathFenceStart == -1 || codeFenceStart < mathFenceStart)

        when {
            nextIsMath -> {
                // Text before the $$
                addTextBlock(blocks, text.substring(currentIndex, mathFenceStart))

                val contentStart = mathFenceStart + 2
                val mathEnd = text.indexOf("$$", contentStart)
                if (mathEnd == -1) {
                    // Unclosed $$: treat as plain text
                    addTextBlock(blocks, text.substring(mathFenceStart))
                    currentIndex = text.length
                } else {
                    val mathContent = text.substring(contentStart, mathEnd).trim()
                    if (mathContent.isNotEmpty()) {
                        blocks += MarkdownBlock(text = mathContent, isMath = true)
                    }
                    currentIndex = mathEnd + 2
                }
            }

            nextIsCode -> {
                addTextBlock(blocks, text.substring(currentIndex, codeFenceStart))

                val infoStart = codeFenceStart + 3
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
                    currentIndex = text.length
                } else {
                    blocks += MarkdownBlock(
                        text = text.substring(codeStart, fenceEnd),
                        language = language,
                        isCode = true
                    )
                    currentIndex = fenceEnd + 3
                }
            }

            else -> {
                // No more delimiters
                addTextBlock(blocks, text.substring(currentIndex))
                currentIndex = text.length
            }
        }
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
 * Converts a LaTeX math expression into a human-readable Unicode string.
 *
 * Handles common constructs such as fractions, integrals, sums, products,
 * square roots, Greek letters, and superscript / subscript notation.
 * Unrecognised commands are left with the backslash stripped.
 *
 * @param expr The raw LaTeX expression (without surrounding `$` delimiters).
 * @return A prettified, Unicode-enriched string suitable for display.
 */
fun prettifyMath(expr: String): String {
    var s = expr

    // --- Multi-character commands (order matters: longest first) ---
    s = s.replace("\\frac{", "")
    s = s.replace("}{", "/")
    s = s.replace("\\sqrt{", "√(")
    s = s.replace("\\int_{" , "∫_{")   // keep subscript token for later
    s = s.replace("\\int^{" , "∫^{")
    s = s.replace("\\int"   , "∫")
    s = s.replace("\\sum_{" , "Σ_{")   // keep subscript token for later
    s = s.replace("\\sum"   , "Σ")
    s = s.replace("\\prod_{" , "Π_{")  // keep subscript token for later
    s = s.replace("\\prod"  , "Π")
    s = s.replace("\\lim"   , "lim")
    s = s.replace("\\infty" , "∞")
    s = s.replace("\\partial", "∂")
    s = s.replace("\\nabla" , "∇")
    s = s.replace("\\Delta" , "Δ")
    s = s.replace("\\delta" , "δ")
    s = s.replace("\\Sigma" , "Σ")
    s = s.replace("\\sigma" , "σ")
    s = s.replace("\\Alpha" , "Α")
    s = s.replace("\\alpha" , "α")
    s = s.replace("\\Beta"  , "Β")
    s = s.replace("\\beta"  , "β")
    s = s.replace("\\Gamma" , "Γ")
    s = s.replace("\\gamma" , "γ")
    s = s.replace("\\Lambda", "Λ")
    s = s.replace("\\lambda", "λ")
    s = s.replace("\\Mu"    , "Μ")
    s = s.replace("\\mu"    , "μ")
    s = s.replace("\\Pi"    , "Π")
    s = s.replace("\\pi"    , "π")
    s = s.replace("\\Phi"   , "Φ")
    s = s.replace("\\phi"   , "φ")
    s = s.replace("\\Psi"   , "Ψ")
    s = s.replace("\\psi"   , "ψ")
    s = s.replace("\\Omega" , "Ω")
    s = s.replace("\\omega" , "ω")
    s = s.replace("\\Theta" , "Θ")
    s = s.replace("\\theta" , "θ")
    s = s.replace("\\Epsilon","Ε")
    s = s.replace("\\epsilon","ε")
    s = s.replace("\\Xi"    , "Ξ")
    s = s.replace("\\xi"    , "ξ")
    s = s.replace("\\Eta"   , "Η")
    s = s.replace("\\eta"   , "η")
    s = s.replace("\\Zeta"  , "Ζ")
    s = s.replace("\\zeta"  , "ζ")
    s = s.replace("\\tau"   , "τ")
    s = s.replace("\\rho"   , "ρ")
    s = s.replace("\\nu"    , "ν")
    s = s.replace("\\iota"  , "ι")
    s = s.replace("\\kappa" , "κ")
    s = s.replace("\\chi"   , "χ")
    s = s.replace("\\cdot"  , "·")
    s = s.replace("\\times" , "×")
    s = s.replace("\\div"   , "÷")
    s = s.replace("\\pm"    , "±")
    s = s.replace("\\mp"    , "∓")
    s = s.replace("\\leq"   , "≤")
    s = s.replace("\\geq"   , "≥")
    s = s.replace("\\neq"   , "≠")
    s = s.replace("\\approx", "≈")
    s = s.replace("\\equiv" , "≡")
    s = s.replace("\\in"    , "∈")
    s = s.replace("\\notin" , "∉")
    s = s.replace("\\subset", "⊂")
    s = s.replace("\\cup"   , "∪")
    s = s.replace("\\cap"   , "∩")
    s = s.replace("\\forall", "∀")
    s = s.replace("\\exists", "∃")
    s = s.replace("\\to"    , "→")
    s = s.replace("\\rightarrow", "→")
    s = s.replace("\\leftarrow" , "←")
    s = s.replace("\\Rightarrow", "⇒")
    s = s.replace("\\Leftarrow" , "⇐")
    s = s.replace("\\Leftrightarrow", "⟺")
    s = s.replace("\\leftrightarrow", "↔")
    s = s.replace("\\log"   , "log")
    s = s.replace("\\ln"    , "ln")
    s = s.replace("\\sin"   , "sin")
    s = s.replace("\\cos"   , "cos")
    s = s.replace("\\tan"   , "tan")
    s = s.replace("\\cot"   , "cot")
    s = s.replace("\\sec"   , "sec")
    s = s.replace("\\csc"   , "csc")
    s = s.replace("\\arcsin", "arcsin")
    s = s.replace("\\arccos", "arccos")
    s = s.replace("\\arctan", "arctan")
    s = s.replace("\\exp"   , "exp")
    s = s.replace("\\max"   , "max")
    s = s.replace("\\min"   , "min")
    s = s.replace("\\gcd"   , "gcd")
    s = s.replace("\\lcm"   , "lcm")
    s = s.replace("\\mathbb{R}", "ℝ")
    s = s.replace("\\mathbb{Z}", "ℤ")
    s = s.replace("\\mathbb{N}", "ℕ")
    s = s.replace("\\mathbb{Q}", "ℚ")
    s = s.replace("\\mathbb{C}", "ℂ")
    s = s.replace("\\left(", "(").replace("\\right)", ")")
    s = s.replace("\\left[", "[").replace("\\right]", "]")
    s = s.replace("\\left{", "{").replace("\\right}", "}")
    s = s.replace("\\left|", "|").replace("\\right|", "|")
    s = s.replace("\\|" , "‖")
    // Strip remaining unknown commands (backslash + word chars)
    s = s.replace(Regex("\\\\[a-zA-Z]+"), "")

    // Superscript: ^{...} or ^X  →  convert to Unicode superscripts when possible
    s = s.replace(Regex("\\^\\{([^}]*)\\}")) { m ->
        toSuperscript(m.groupValues[1])
    }
    s = s.replace(Regex("\\^(.)")) { m ->
        toSuperscript(m.groupValues[1])
    }

    // Subscript: _{...} or _X  →  convert to Unicode subscripts when possible
    s = s.replace(Regex("_\\{([^}]*)\\}")) { m ->
        toSubscript(m.groupValues[1])
    }
    s = s.replace(Regex("_(.)" )) { m ->
        toSubscript(m.groupValues[1])
    }

    // Clean up remaining bare braces and extra whitespace
    s = s.replace("{", "").replace("}", "")
    s = s.replace(Regex("\\s+"), " ").trim()
    return s
}

/** Maps each character of [text] to its Unicode superscript equivalent where available. */
private fun toSuperscript(text: String): String {
    val map = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'n' to 'ⁿ', 'i' to 'ⁱ', 'x' to 'ˣ', 'k' to 'ᵏ', 'm' to 'ᵐ'
    )
    return if (text.length == 1) {
        map[text[0]]?.toString() ?: "^$text"
    } else {
        "^($text)"
    }
}

/** Maps each character of [text] to its Unicode subscript equivalent where available. */
private fun toSubscript(text: String): String {
    val map = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
        'a' to 'ₐ', 'e' to 'ₑ', 'o' to 'ₒ', 'x' to 'ₓ', 'n' to 'ₙ',
        'i' to 'ᵢ', 'k' to 'ₖ', 'm' to 'ₘ'
    )
    return if (text.length == 1) {
        map[text[0]]?.toString() ?: "_$text"
    } else {
        "_($text)"
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
        val boldRegex      = Regex("""\*\*(.*?)\*\*""")
        val italicRegex    = Regex("""\*(?!\*)(.*?)\*""")
        val codeRegex      = Regex("""`(.*?)`""")
        val highlightRegex = Regex("(Chevere AI|Chevere)")
        // Inline math: $...$ (single dollar, not preceded/followed by another $)
        val inlineMathRegex = Regex("""(?<!\$)\$(?!\$)(.*?)(?<!\$)\$(?!\$)""")

        var currentPos = 0

        while (currentPos < cleanText.length) {
            val bMatch  = boldRegex.find(cleanText, currentPos)
            val iMatch  = italicRegex.find(cleanText, currentPos)
            val cMatch  = codeRegex.find(cleanText, currentPos)
            val hMatch  = highlightRegex.find(cleanText, currentPos)
            val mMatch  = inlineMathRegex.find(cleanText, currentPos)

            val matches = listOfNotNull(bMatch, iMatch, cMatch, hMatch, mMatch)
                .sortedWith(compareBy({ it.range.first }, { -it.value.length }))

            if (matches.isEmpty()) {
                append(cleanText.substring(currentPos))
                break
            }

            val match = matches.first()

            if (match.range.first > currentPos) {
                append(cleanText.substring(currentPos, match.range.first))
            }

            val start   = length
            val content = if (match.groupValues.size > 1) match.groupValues[1] else ""

            when {
                match == mMatch -> {
                    // Inline math: prettify and style distinctly
                    val pretty = prettifyMath(content)
                    append(pretty)
                    val end = length
                    addStyle(
                        SpanStyle(
                            color = primaryColor,
                            fontStyle = FontStyle.Italic,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Medium,
                            background = codeBackground.copy(alpha = 0.18f)
                        ),
                        start, end
                    )
                }

                match.value.startsWith("**") -> {
                    append(content)
                    val end = length
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                }

                match.value.startsWith("`") -> {
                    append(content)
                    val end = length
                    addStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBackground,
                            color = primaryColor
                        ),
                        start, end
                    )
                }

                match.value.startsWith("*") -> {
                    append(content)
                    val end = length
                    addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                }

                match.value == "Chevere" || match.value == "Chevere AI" -> {
                    append(content)
                    val end = length
                    addStyle(
                        SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold),
                        start, end
                    )
                }

                else -> {
                    append(content)
                }
            }

            currentPos = match.range.last + 1
        }
    }
}
