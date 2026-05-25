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
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<DocumentChunkEntity>)

    @Query("SELECT * FROM document_chunks")
    suspend fun getAllChunks(): List<DocumentChunkEntity>

    @Query("DELETE FROM document_chunks WHERE filePath = :filePath")
    suspend fun deleteChunksForFile(filePath: String)

    @Query("DELETE FROM document_chunks")
    suspend fun clearAllChunks()
}
