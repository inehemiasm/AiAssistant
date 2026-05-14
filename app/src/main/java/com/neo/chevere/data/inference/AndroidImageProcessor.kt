package com.neo.chevere.data.inference

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.neo.chevere.core.ImageUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidImageProcessor @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ImageProcessor {

    override fun processImage(uri: Uri, targetSize: Int): ByteArray {
        val bitmap = ImageUtils.loadAndProcessImage(context, uri, targetSize)
        val bos = ByteArrayOutputStream()
        // Using PNG as it's generally safer for multimodal input across LiteRT versions
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)
        val bytes = bos.toByteArray()
        bitmap.recycle()
        return bytes
    }

    override fun createDummyImage(size: Int): ByteArray {
        val dummyBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val bos = ByteArrayOutputStream()
        dummyBitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)
        val bytes = bos.toByteArray()
        dummyBitmap.recycle()
        return bytes
    }
}
