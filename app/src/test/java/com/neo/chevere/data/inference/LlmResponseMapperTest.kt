package com.neo.chevere.data.inference

import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Message
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LlmResponseMapperTest {

    private lateinit var mapper: LlmResponseMapper

    @Before
    fun setup() {
        mapper = LlmResponseMapper()
    }

    @Test
    fun mapToString_withPlainText_returnsTrimmedText() {
        val message = Message.model("  Hello World  ")
        val result = mapper.mapToString(message)
        assertEquals("Hello World", result)
    }

    @Test
    fun mapToString_withMultipleTextParts_joinsThem() {
        val contents = Contents.of(
            Content.Text("Hello "),
            Content.Text("World")
        )
        val message = Message.model(contents)
        val result = mapper.mapToString(message)
        assertEquals("Hello World", result)
    }

    @Test
    fun mapToString_withEmptyContent_returnsEmptyString() {
        val message = Message.model("")
        val result = mapper.mapToString(message)
        assertEquals("", result)
    }

    @Test
    fun mapToString_withMixedContentIncludingImages_extractsTextOnly() {
        val contents = Contents.of(
            Content.ImageBytes(byteArrayOf(0)),
            Content.Text("Found an image")
        )
        val message = Message.model(contents)
        val result = mapper.mapToString(message)
        // Verify it extracts the text
        assert(result.contains("Found an image"))
    }
}
