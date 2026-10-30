package com.neo.chevere.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.neo.chevere.data.datasource.local.DocumentChunkDao
import com.neo.chevere.data.datasource.local.DocumentChunkEntity
import com.neo.chevere.domain.DocumentImportResult
import com.neo.chevere.domain.DocumentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Reads bounded UTF-8 text from a user-granted content URI and atomically replaces its index. */
class LocalDocumentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DocumentChunkDao
) : DocumentRepository {
    override suspend fun importDocument(reference: String): DocumentImportResult = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(reference)
            require(uri.scheme == "content") { "Select a document using the document picker." }
            val resolver = context.contentResolver
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: "document.txt"
            require(name.endsWith(".txt", true) || name.endsWith(".md", true)) { "Choose a .txt or .md document." }
            val bytes = resolver.openInputStream(uri)?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (output.size() <= 2 * 1024 * 1024) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                output.toByteArray() }
                ?: error("Unable to open document.")
            require(bytes.size <= 2 * 1024 * 1024) { "Choose a document smaller than 2 MB." }
            val text = bytes.toString(Charsets.UTF_8)
            require(text.isNotBlank() && '\u0000' !in text) { "Document contains no readable text." }
            val chunks = text.windowed(800, 600, partialWindows = true).mapIndexed { index, chunk ->
                DocumentChunkEntity(filePath = reference, fileName = name, chunkIndex = index, text = chunk)
            }
            dao.replaceDocument(reference, chunks)
            DocumentImportResult.Imported(name, chunks.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DocumentImportResult.Failed(e.message ?: "Document import failed.")
        }
    }
}
