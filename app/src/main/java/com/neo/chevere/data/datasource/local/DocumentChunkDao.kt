package com.neo.chevere.data.datasource.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data Access Object for local document chunks indexed for RAG.
 */
@Dao
interface DocumentChunkDao {
    /** Replaces a folder snapshot without exposing partially refreshed search results. */
    @androidx.room.Transaction
    suspend fun replaceFolder(prefix: String, chunks: List<DocumentChunkEntity>) {
        deleteFolder(prefix)
        insertChunks(chunks)
    }

    @Query("DELETE FROM document_chunks WHERE substr(filePath, 1, length(:prefix)) = :prefix")
    suspend fun deleteFolder(prefix: String)

    @androidx.room.Transaction
    suspend fun replaceDocument(reference: String, chunks: List<DocumentChunkEntity>) {
        deleteChunksForFile(reference)
        insertChunks(chunks)
    }
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<DocumentChunkEntity>)

    @Query("SELECT * FROM document_chunks")
    suspend fun getAllChunks(): List<DocumentChunkEntity>

    @Query("DELETE FROM document_chunks WHERE filePath = :filePath")
    suspend fun deleteChunksForFile(filePath: String)

    @Query("DELETE FROM document_chunks")
    suspend fun clearAllChunks()
}
