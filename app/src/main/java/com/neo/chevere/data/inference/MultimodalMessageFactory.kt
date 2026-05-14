package com.neo.chevere.data.inference

import android.content.Context
import android.net.Uri
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Message
import com.neo.chevere.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory for creating multimodal LiteRT-LM messages.
 * Centralizes image processing and message assembly.
 */
@Singleton
class MultimodalMessageFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val imageProcessor: ImageProcessor
) {
    companion object {
        private const val TARGET_IMAGE_SIZE = 448
    }

    /**
     * Creates a user message from text and an optional image URI.
     */
    fun createMessage(prompt: String, imageUri: Uri?): Message {
        return if (imageUri != null) {
            val imageBytes = imageProcessor.processImage(imageUri, TARGET_IMAGE_SIZE)
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
        val dummyImage = imageProcessor.createDummyImage(TARGET_IMAGE_SIZE)
        return Message.user(
            Contents.of(
                Content.ImageBytes(dummyImage),
                Content.Text(context.getString(R.string.error_warmup))
            )
        )
    }
}
