package com.neo.chevere.data.inference.minicpm

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MiniCpmBundleTest {
    @Test fun pinnedBundleContainsEveryExpertAndChecksum() {
        val manifest = Json.decodeFromString<MiniCpmBundle.Manifest>(File("src/main/assets/model_catalog/minicpm_v47_bundle.json").readText())
        assertEquals(MiniCpmBundle.ID, manifest.id)
        assertEquals(43, manifest.files.size)
        assertEquals(43, manifest.files.map { it.path }.distinct().size)
        for (layer in 0..39) assertTrue(manifest.files.any { it.path == "experts/layer_%03d.bin".format(layer) })
        assertTrue(manifest.files.any { it.path == "experts/manifest.json" })
        assertTrue(manifest.files.any { it.path == MiniCpmBundle.MODEL })
        assertTrue(manifest.files.any { it.path == MiniCpmBundle.PROJECTOR })
        assertTrue(manifest.files.all { it.sha256.matches(Regex("[0-9a-f]{64}")) && it.url.contains("/resolve/" + manifest.revision + "/") })
        assertTrue(manifest.files.sumOf { it.sizeBytes } in 16_000_000_000L..17_000_000_000L)
    }
    @Test fun partialBundleIsNeverHealthy() {
        val dir = kotlin.io.path.createTempDirectory().toFile()
        try {
            val entry = MiniCpmBundle.Entry("model.gguf", "unused", 4, "unused")
            val manifest = MiniCpmBundle.Manifest("test", "test", "test", "test", listOf(entry))
            assertFalse(MiniCpmBundle.isComplete(dir, manifest))
            File(dir, entry.path).writeBytes(byteArrayOf(1,2))
            assertFalse(MiniCpmBundle.isComplete(dir, manifest))
            File(dir, entry.path).writeBytes(byteArrayOf(1,2,3,4))
            assertTrue(MiniCpmBundle.isComplete(dir, manifest))
        } finally { dir.deleteRecursively() }
    }
    @Test fun thoughtTextAndPartialMarkersStayHidden() {
        assertEquals("", miniCpmVisibleText("<think>private"))
        assertEquals("Answer", miniCpmVisibleText("<think>private</think>Answer"))
        assertEquals("Answer", miniCpmVisibleText("[Start thinking]private[End thinking]Answer"))
        assertEquals("Answer", miniCpmVisibleText("Answer<thin"))
        assertEquals("Answer", miniCpmVisibleText("Answer<|im_end|>"))
    }
}
