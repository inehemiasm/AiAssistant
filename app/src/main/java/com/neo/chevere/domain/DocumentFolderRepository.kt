package com.neo.chevere.domain

/** A user-selected document source; indexed text is a local snapshot, not a live filesystem view. */
data class DocumentFolder(val reference: String, val name: String)

/** Manages persisted folder access and bounded, atomic text-index snapshots. */
interface DocumentFolderRepository {
    suspend fun getFolders(): List<DocumentFolder>
    suspend fun indexFolder(reference: String): FolderIndexResult
    suspend fun disconnectFolder(reference: String): FolderDisconnectResult
}

/** Folder indexing outcomes contain no platform document-provider types. */
sealed interface FolderIndexResult {
    data class Indexed(val files: Int, val skipped: Int) : FolderIndexResult
    data class Failed(val reason: String) : FolderIndexResult
}

/** Disconnect removes only this source's indexed text and releases its access grant. */
sealed interface FolderDisconnectResult {
    data object Disconnected : FolderDisconnectResult
    data class Failed(val reason: String) : FolderDisconnectResult
}
