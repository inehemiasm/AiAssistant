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
    fun parse_returnsNullWhenNoToolCallExists() {
        assertNull(parser.parse("hello world"))
    }
}
