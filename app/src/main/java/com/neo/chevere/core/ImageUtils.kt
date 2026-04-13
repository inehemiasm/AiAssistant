package com.neo.chevere.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

object ImageUtils {
    
    fun loadAndProcessImage(context: Context, uri: Uri, targetSize: Int): Bitmap {
        val orientation = context.contentResolver.openInputStream(uri)?.use { input ->
            val exif = ExifInterface(input)
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }

        options.apply {
            inJustDecodeBounds = false
            inSampleSize = calculateInSampleSize(options, targetSize, targetSize)
            inMutable = true
        }

        var bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        } ?: throw Exception("Failed to decode image")

        bitmap = rotateBitmap(bitmap, orientation)
        return centerCropAndScale(bitmap, targetSize)
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return try {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            bitmap.recycle()
            rotated
        } catch (e: OutOfMemoryError) {
            bitmap
        }
    }

    private fun centerCropAndScale(bitmap: Bitmap, size: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val minEdge = minOf(width, height)
        val dx = (width - minEdge) / 2
        val dy = (height - minEdge) / 2
        val cropped = Bitmap.createBitmap(bitmap, dx, dy, minEdge, minEdge)
        val scaled = Bitmap.createScaledBitmap(cropped, size, size, true)
        if (cropped != bitmap) cropped.recycle()
        if (bitmap != scaled) bitmap.recycle()
        return scaled.copy(Bitmap.Config.ARGB_8888, false)
    }
}
