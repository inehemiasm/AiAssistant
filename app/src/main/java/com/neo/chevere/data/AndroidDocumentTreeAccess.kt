package com.neo.chevere.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ensureActive
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/** Decoded provider data; SDK document rows remain inside the data layer. */
data class TreeTextFile(val reference: String, val name: String, val text: String)
/** Complete bounded tree read; unsupported and empty entries are counted separately. */
data class DocumentTreeSnapshot(val files: List<TreeTextFile>, val skipped: Int)

/** Scoped Storage Access Framework reader. Persists read access only; never modifies source files. */
class AndroidDocumentTreeAccess @Inject constructor(@ApplicationContext private val context: Context) {
    private val resolver get() = context.contentResolver

    fun persist(reference: String) {
        val uri = Uri.parse(reference)
        require(uri.scheme == "content" && DocumentsContract.isTreeUri(uri))
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun release(reference: String) {
        val uri = Uri.parse(reference)
        if (resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }) {
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun name(reference: String): String {
        val tree = Uri.parse(reference)
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        return resolver.query(root, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: error("Folder unavailable")
    }

    /** Fails without publishing a partial snapshot when enumeration, access, or size limits fail. */
    suspend fun read(reference: String): DocumentTreeSnapshot {
        val tree = Uri.parse(reference)
        require(tree.scheme == "content" && DocumentsContract.isTreeUri(tree))
        val pending = ArrayDeque<Triple<String, String, Int>>()
        pending.add(Triple(DocumentsContract.getTreeDocumentId(tree), "", 0))
        val visited = mutableSetOf<String>()
        val files = mutableListOf<TreeTextFile>()
        var entries = 0
        var skipped = 0
        var totalBytes = 0
        while (pending.isNotEmpty()) {
            coroutineContext.ensureActive()
            val (id, path, depth) = pending.removeFirst()
            check(depth <= 8) { "Folder limit" }
            if (!visited.add(id)) continue
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
            val cursor = resolver.query(children, arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ), null, null, null) ?: error("Folder unavailable")
            cursor.use {
                while (it.moveToNext()) {
                    coroutineContext.ensureActive()
                    check(++entries <= 1000) { "Folder limit" }
                    val childId = it.getString(0)
                    val name = it.getString(1) ?: error("Invalid document")
                    val relativeName = path + name
                    if (it.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                        pending.add(Triple(childId, "$relativeName/", depth + 1))
                    } else if (name.endsWith(".txt", true) || name.endsWith(".md", true)) {
                        check(files.size < 100) { "Folder limit" }
                        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, childId)
                        val bytes = resolver.openInputStream(uri)?.use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                coroutineContext.ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                check(output.size() + count <= 2 * 1024 * 1024) { "Folder limit" }
                                check(totalBytes + count <= 16 * 1024 * 1024) { "Folder limit" }
                                output.write(buffer, 0, count)
                                totalBytes += count
                            }
                            output.toByteArray()
                        } ?: error("Document unavailable")
                        val text = Charsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes)).toString()
                        if (text.isBlank() || '\u0000' in text) skipped++
                        else files.add(TreeTextFile(uri.toString(), relativeName, text))
                    } else skipped++
                }
                check(it.extras?.getBoolean(DocumentsContract.EXTRA_LOADING, false) != true) { "Provider loading" }
                check(it.extras?.getString(DocumentsContract.EXTRA_ERROR).isNullOrBlank()) { "Provider error" }
            }
        }
        return DocumentTreeSnapshot(files, skipped)
    }
}
