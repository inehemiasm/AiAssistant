package com.neo.chevere.domain

/** Imports user-selected text documents and voice transcripts into the local searchable library. */
interface DocumentRepository {
    suspend fun importDocument(reference: String): DocumentImportResult

    /**
     * Indexes a plain-text or Markdown note (such as a recorded voice memo) directly into
     * the local document chunk store using the standard 800-char / 200-char overlap window.
     */
    suspend fun saveTextDocument(name: String, content: String): DocumentImportResult =
        DocumentImportResult.Failed("Unsupported")
}

/** Validated outcome of a document import. */
sealed interface DocumentImportResult {
    data class Imported(val name: String, val chunks: Int) : DocumentImportResult
    data class Failed(val reason: String) : DocumentImportResult
}
