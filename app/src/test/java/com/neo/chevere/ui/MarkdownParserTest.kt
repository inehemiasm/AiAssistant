package com.neo.chevere.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.neo.chevere.ui.common.parseMarkdownLogic
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MarkdownParserTest {

    private val codeBackground = Color.Gray
    private val primaryColor = Color.Blue

    @Test
    fun `test plain text`() {
        val input = "Hello world"
        val result = parseMarkdownLogic(input, codeBackground, primaryColor)
        assertEquals("Hello world", result.text)
        assertEquals(0, result.spanStyles.size)
    }

    @Test
    fun `test bold text`() {
        val input = "Hello **bold** world"
        val result = parseMarkdownLogic(input, codeBackground, primaryColor)
        assertEquals("Hello bold world", result.text)
        val span = result.spanStyles.find { it.item.fontWeight == FontWeight.Bold }
        assert(span != null)
        assertEquals(6, span!!.start)
        assertEquals(10, span.end)
    }

    @Test
    fun `test italic text`() {
        val input = "Hello *italic* world"
        val result = parseMarkdownLogic(input, codeBackground, primaryColor)
        assertEquals("Hello italic world", result.text)
        val span = result.spanStyles.find { it.item.fontStyle == FontStyle.Italic }
        assert(span != null)
        assertEquals(6, span!!.start)
        assertEquals(12, span.end)
    }

    @Test
    fun `test inline code`() {
        val input = "Hello `code` world"
        val result = parseMarkdownLogic(input, codeBackground, primaryColor)
        assertEquals("Hello code world", result.text)
        val span = result.spanStyles.find { it.item.fontFamily == FontFamily.Monospace }
        assert(span != null)
        assertEquals(6, span!!.start)
        assertEquals(10, span.end)
    }

    @Test
    fun `test mixed markdown`() {
        val input = "Text with **bold**, *italic* and `code`."
        val result = parseMarkdownLogic(input, codeBackground, primaryColor)
        assertEquals("Text with bold, italic and code.", result.text)
        assert(result.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assert(result.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
        assert(result.spanStyles.any { it.item.fontFamily == FontFamily.Monospace })
    }

    @Test
    fun `test unmatched asterisks`() {
        val input = "This is *unmatched"
        val result = parseMarkdownLogic(input, codeBackground, primaryColor)
        assertEquals("This is *unmatched", result.text)
        // One could argue if it should be 0 or if something was mis-parsed. 
        // With current regex `\*(?!\*)(.*?)\*`, it requires a closing asterisk.
        assertEquals(0, result.spanStyles.size)
    }
    
    @Test
    fun `test escaped or malformed markdown does not crash`() {
        val input = "Malformed **bold *italic` code"
        // Should not crash
        parseMarkdownLogic(input, codeBackground, primaryColor)
    }

    @Test
    fun `test multiple spans of same type`() {
        val input = "**bold1** and **bold2**"
        val result = parseMarkdownLogic(input, codeBackground, primaryColor)
        assertEquals("bold1 and bold2", result.text)
        val boldSpans = result.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertEquals(2, boldSpans.size)
    }

    @Test
    fun `test bullet point replacement`() {
        val input = "* item 1\n* item 2"
        val result = parseMarkdownLogic(input, codeBackground, primaryColor)
        assert(result.text.contains("• item 1"))
        assert(result.text.contains("• item 2"))
    }
}
