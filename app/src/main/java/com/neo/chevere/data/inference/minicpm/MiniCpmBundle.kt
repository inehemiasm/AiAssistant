package com.neo.chevere.data.inference.minicpm

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Immutable, app-owned download manifest. No remote path can select an arbitrary native model. */
internal object MiniCpmBundle {
    const val ID = "MiniCPM-V-4.7-35B-A3B-LES"
    const val MODEL = "MiniCPM-V-4_7-35B-A3B-mix_q3gud_im-les.gguf"
    const val PROJECTOR = "mmproj-model-f16.gguf"
    @Serializable data class Entry(val path: String, val url: String, val sizeBytes: Long, val sha256: String)
    @Serializable data class Manifest(val id: String, val revision: String, val modelFile: String,
        val projectorFile: String, val files: List<Entry>)
    fun manifest(context: Context): Manifest = context.assets.open("model_catalog/minicpm_v47_bundle.json")
        .bufferedReader().use { Json.decodeFromString<Manifest>(it.readText()) }.also {
            require(it.id == ID && it.files.size == 43 && it.files.map { e -> e.path }.distinct().size == 43)
            it.files.forEach { e ->
                require(e.path.split('/').none { part -> part.isBlank() || part == ".." || part == "." })
                require(!e.path.contains('\\') && !e.path.contains(':'))
                require(e.url.startsWith("https://huggingface.co/tc-mb/MiniCPM-V-Apps-gguf/resolve/" + it.revision + "/"))
                require(e.sizeBytes > 0 && e.sha256.matches(Regex("[0-9a-f]{64}")))
            }
        }
    /** Fast startup health check; cryptographic validation happens before atomic installation. */
    fun isComplete(root: File, manifest: Manifest): Boolean = root.isDirectory && manifest.files.all {
        val file = File(root, it.path)
        file.isFile && file.length() == it.sizeBytes
    }
}
