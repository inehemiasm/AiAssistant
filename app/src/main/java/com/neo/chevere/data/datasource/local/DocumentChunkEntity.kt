package com.neo.chevere.data.datasource.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a segmented text chunk of a local document indexed for RAG.
 */
@Entity(tableName = "document_chunks")
data class DocumentChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val filePath: String,
    val fileName: String,
    val chunkIndex: Int,
    val text: String
)
