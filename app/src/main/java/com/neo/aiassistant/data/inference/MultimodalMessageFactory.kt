package com.neo.aiassistant.data.inference

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Message
import com.neo.aiassistant.core.ImageUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory for creating multimodal LiteRT-LM messages.
 * Centralizes image processing and message assembly.
 */
@Singleton
class MultimodalMessageFactory @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TARGET_IMAGE_SIZE = 448
        private const val JPEG_QUALITY = 90
    }

    /**
     * Creates a user message from text and an optional image URI.
     */
    fun createMessage(prompt: String, imageUri: Uri?): Message {
        return if (imageUri != null) {
            val imageBytes = processImage(imageUri)
            Message.user(
                Contents.of(
                    Content.ImageBytes(imageBytes),
                    Content.Text(prompt)
                )
            )
        } else {
            Message.user(prompt)
        }
    }

    /**
     * Creates a simple text message.
     */
    fun createTextMessage(text: String): Message {
        return Message.user(text)
    }

    /**
     * Creates a warmup message for vision backends.
     */
    fun createWarmupMessage(): Message {
        val dummyBitmap = Bitmap.createBitmap(TARGET_IMAGE_SIZE, TARGET_IMAGE_SIZE, Bitmap.Config.ARGB_8888)
        val bos = ByteArrayOutputStream()
        dummyBitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)
        return Message.user(
            Contents.of(
                Content.ImageBytes(bos.toByteArray()),
                Content.Text("warmup")
            )
        )
    }

    private fun processImage(uri: Uri): ByteArray {
        val bitmap = ImageUtils.loadAndProcessImage(context, uri, TARGET_IMAGE_SIZE)
        val bos = ByteArrayOutputStream()
        // Using PNG as it's generally safer for multimodal input across LiteRT versions
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)
        return bos.toByteArray()
    }
}
