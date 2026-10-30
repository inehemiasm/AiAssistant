package com.neo.chevere.domain

/** Imports user-selected text documents into the local searchable library. */
interface DocumentRepository {
    suspend fun importDocument(reference: String): DocumentImportResult
}

/** Validated outcome of a document import. */
sealed interface DocumentImportResult {
    data class Imported(val name: String, val chunks: Int) : DocumentImportResult
    data class Failed(val reason: String) : DocumentImportResult
}
