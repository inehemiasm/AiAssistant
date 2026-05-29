package com.neo.chevere.data.datasource.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persists a single conversation session header.
 *
 * @property title Auto-generated from the first user message (≤ 60 chars).
 *                 Can be updated by the user via rename.
 * @property createdAt Epoch-ms timestamp of the first message.
 * @property updatedAt Epoch-ms timestamp of the most recent message — used
 *                     to sort the history list newest-first.
 * @property modelName The active LLM filename at session creation.
 * @property messageCount Cached count of messages; updated after each append.
 */
@Entity(tableName = "conversation_sessions")
data class ConversationSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val modelName: String = "",
    val messageCount: Int = 0
)
