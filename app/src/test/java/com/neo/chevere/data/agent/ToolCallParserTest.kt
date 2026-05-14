package com.neo.chevere.data.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToolCallParserTest {

    private val parser = ToolCallParser()

    @Test
    fun parse_keepsCommasInsideQuotedValues() {
        val call = parser.parse(
            """[TOOL_CALL: generate_image, prompt="A wolf, under the moon, cinematic", seed=123]"""
        )

        assertEquals("generate_image", call?.toolName)
        assertEquals("A wolf, under the moon, cinematic", call?.arguments?.get("prompt"))
        assertEquals("123", call?.arguments?.get("seed"))
    }

    @Test
    fun parse_keepsEqualsInsideValues() {
        val call = parser.parse("""[TOOL_CALL: open_url, url="https://example.com?a=1&b=2"]""")

        assertEquals("https://example.com?a=1&b=2", call?.arguments?.get("url"))
    }

    @Test
    fun parse_keepsBracketsAndCommasInsideQuotedGeneratedCode() {
        val call = parser.parse(
            """[TOOL_CALL: summarize_text, text="```kotlin
fun main() {
    println(listOf("[one]", "two, three"))
}
```"]"""
        )

        assertEquals("summarize_text", call?.toolName)
        assertEquals(
            """```kotlin
fun main() {
    println(listOf("[one]", "two, three"))
}
```""",
            call?.arguments?.get("text")
        )
    }

    @Test
    fun parse_acceptsFlatJsonPayload() {
        val call = parser.parse(
            """[TOOL_CALL: open_url, {"url":"https://example.com?a=1&b=2","label":"docs"}]"""
        )

        assertEquals("open_url", call?.toolName)
        assertEquals("https://example.com?a=1&b=2", call?.arguments?.get("url"))
        assertEquals("docs", call?.arguments?.get("label"))
    }

    @Test
    fun stripToolCall_removesOnlyStructuredCallWithBracketedArguments() {
        val output = parser.stripToolCall(
            """Thought first. [TOOL_CALL: summarize_text, text="value with [brackets]"] trailing"""
        )

        assertEquals("Thought first.  trailing", output)
    }

    @Test
    fun parse_returnsNullWhenNoToolCallExists() {
        assertNull(parser.parse("hello world"))
    }
}
