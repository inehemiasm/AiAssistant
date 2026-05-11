package com.neo.chevere.data.inference

import android.net.Uri
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Role
import android.content.Context
import com.neo.chevere.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MultimodalMessageFactoryTest {

    private lateinit var imageProcessor: ImageProcessor
    private lateinit var context: Context
    private lateinit var factory: MultimodalMessageFactory

    @Before
    fun setup() {
        context = mock {
            on { getString(R.string.error_warmup) } doReturn "warmup"
        }
        imageProcessor = mock()
        factory = MultimodalMessageFactory(context, imageProcessor)
    }

    @Test
    fun createTextMessage_returnsUserMessageWithCorrectText() {
        val text = "Hello AI"
        val message = factory.createTextMessage(text)
        
        assertEquals(Role.USER, message.role)
        assertEquals(1, message.contents.contents.size)
        val textContent = message.contents.contents[0] as Content.Text
        assertEquals(text, textContent.text)
    }

    @Test
    fun createMessage_withNullImage_returnsTextMessage() {
        val prompt = "Just text"
        val message = factory.createMessage(prompt, null)
        
        assertEquals(Role.USER, message.role)
        assertEquals(1, message.contents.contents.size)
        assertTrue(message.contents.contents[0] is Content.Text)
        assertEquals(prompt, (message.contents.contents[0] as Content.Text).text)
    }

    @Test
    fun createMessage_withImage_callsProcessorAndReturnsMultimodalMessage() {
        val prompt = "What is this?"
        val mockUri = mock<Uri>()
        val mockBytes = byteArrayOf(1, 2, 3)
        
        whenever(imageProcessor.processImage(any(), any())).doReturn(mockBytes)
        
        val message = factory.createMessage(prompt, mockUri)
        
        verify(imageProcessor).processImage(mockUri, 448)
        assertEquals(Role.USER, message.role)
        assertEquals(2, message.contents.contents.size)
        assertTrue(message.contents.contents[0] is Content.ImageBytes)
        assertTrue(message.contents.contents[1] is Content.Text)
        
        val imageContent = message.contents.contents[0] as Content.ImageBytes
        assertEquals(mockBytes.toList(), imageContent.bytes.toList())
        assertEquals(prompt, (message.contents.contents[1] as Content.Text).text)
    }

    @Test
    fun createWarmupMessage_callsDummyImageAndReturnsWarmupMessage() {
        val mockBytes = byteArrayOf(0)
        whenever(imageProcessor.createDummyImage(any())).doReturn(mockBytes)
        
        val message = factory.createWarmupMessage()
        
        verify(imageProcessor).createDummyImage(448)
        assertEquals(Role.USER, message.role)
        assertEquals(2, message.contents.contents.size)
        assertTrue(message.contents.contents[0] is Content.ImageBytes)
        assertEquals("warmup", (message.contents.contents[1] as Content.Text).text)
    }
}
