package com.neo.aiassistant.data.inference

import android.net.Uri

/**
 * Interface for processing images for multimodal models.
 */
interface ImageProcessor {
    fun processImage(uri: Uri, targetSize: Int): ByteArray
    fun createDummyImage(size: Int): ByteArray
}
