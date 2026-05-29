package com.neo.chevere.data.datasource.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO for persistent chat history operations.
 *
 * Sessions are the top-level containers; messages are children keyed by
 * [ConversationMessageEntity.sessionId]. Deleting a session cascades to its messages
 * via the FK constraint defined on [ConversationMessageEntity].
 */
@Dao
interface ConversationHistoryDao {

    // ── Sessions ──────────────────────────────────────────────────────────────

    /** Emits the full session list ordered by most-recently-updated first. */
    @Query("SELECT * FROM conversation_sessions ORDER BY updatedAt DESC")
    fun getAllSessions(): Flow<List<ConversationSessionEntity>>

    /** Inserts a new session and returns its auto-generated id. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ConversationSessionEntity): Long

    /** Updates an existing session (title, updatedAt, messageCount, etc.). */
    @Update
    suspend fun updateSession(session: ConversationSessionEntity)

    /** Returns a session by id, or null if it does not exist. */
    @Query("SELECT * FROM conversation_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: Long): ConversationSessionEntity?

    /** Deletes a session row. The FK CASCADE removes all its messages automatically. */
    @Query("DELETE FROM conversation_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    // ── Messages ──────────────────────────────────────────────────────────────

    /** Returns all messages for a session in chronological order. */
    @Query("SELECT * FROM conversation_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesForSession(sessionId: Long): List<ConversationMessageEntity>

    /** Inserts a single message and returns its auto-generated id. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ConversationMessageEntity): Long
}
