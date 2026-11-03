package com.neo.chevere

import android.util.Log
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.neo.chevere.data.inference.minicpm.MiniCpmBundle
import com.neo.chevere.data.inference.minicpm.MiniCpmNative
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

/** Opt-in device evidence: skipped until the complete in-app model download exists. */
@RunWith(AndroidJUnit4::class)
class MiniCpmNativeSmokeTest {
    @Test(timeout = 240000) fun boundedTextVisionAndCancellationReuse() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.filesDir, MiniCpmBundle.ID)
        assumeTrue("Download the pinned MiniCPM bundle first", MiniCpmBundle.isComplete(root, MiniCpmBundle.manifest(context)))
        Log.i("MiniCpmSmoke", "Opening model")
        val handle = MiniCpmNative.open(root.absolutePath.toByteArray(Charsets.UTF_8))
        Log.i("MiniCpmSmoke", "Model opened")
        assertTrue(handle != 0L)
        try {
            Log.i("MiniCpmSmoke", "Starting request")
            MiniCpmNative.arm(handle)
            assertEquals(-2, MiniCpmNative.begin(handle, "word ".repeat(5000).toByteArray(), null))
            Log.i("MiniCpmSmoke", "Starting request")
            MiniCpmNative.arm(handle)
            assertTrue(MiniCpmNative.begin(handle, "What is 2 + 2? Answer only with the number.".toByteArray(), null) > 0)
            MiniCpmNative.cancel(handle)
            assertNull(MiniCpmNative.next(handle))
            Log.i("MiniCpmSmoke", "Starting request")
            MiniCpmNative.arm(handle)
            assertTrue(MiniCpmNative.begin(handle, "What is 2 + 2? Answer only with the number.".toByteArray(), null) > 0)
            val answer = readReply(handle)
            Log.i("MiniCpmSmoke", "Text completed: " + answer)
            assertTrue(answer.contains("4"))
            val bitmap = Bitmap.createBitmap(160, 120, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.RED)
            val image = ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it); it.toByteArray() }
            bitmap.recycle()
            Log.i("MiniCpmSmoke", "Starting request")
            MiniCpmNative.arm(handle)
            assertTrue(MiniCpmNative.begin(handle, "What is the main color of this image? Answer in English with one word.".toByteArray(), image) > 0)
            val vision = readReply(handle)
            Log.i("MiniCpmSmoke", "Vision completed: " + vision)
            assertTrue(vision.contains("red", ignoreCase = true))
        } finally { MiniCpmNative.close(handle) }
    }
    private fun readReply(handle: Long): String {
        val output = ByteArrayOutputStream()
        repeat(8) { val piece = MiniCpmNative.next(handle) ?: return output.toString("UTF-8"); output.write(piece) }
        return output.toString("UTF-8")
    }
}
