package com.neo.chevere.domain

import kotlinx.coroutines.flow.Flow

/**
 * Contract for reading and writing persistent chat session history.
 *
 * Implementations back this with Room via [ConversationHistoryDao]. All write
 * operations are safe to call from any coroutine context — the implementation
 * dispatches to IO internally.
 */
interface ChatHistoryRepository {

    /**
     * Emits the full list of sessions ordered by [ConversationSession.updatedAt]
     * descending (newest first). Updates in real-time as sessions are added or modified.
     */
    fun getAllSessions(): Flow<List<ConversationSession>>

    /**
     * Returns all [ChatMessage]s for a given session, in chronological order.
     * Returns an empty list if the session does not exist.
     */
    suspend fun getMessages(sessionId: Long): List<ChatMessage>

    /**
     * Creates a new session and returns its generated ID.
     *
     * @param firstUserMessage Used to auto-generate the session title.
     * @param modelName        Active LLM model name at creation time.
     */
    suspend fun createSession(firstUserMessage: String, modelName: String): Long

    /**
     * Persists a completed [ChatMessage] to an existing session and bumps
     * [ConversationSession.updatedAt] and [ConversationSession.messageCount].
     */
    suspend fun appendMessage(sessionId: Long, message: ChatMessage)

    /**
     * Renames a session. No-op if [sessionId] does not exist.
     */
    suspend fun renameSession(sessionId: Long, newTitle: String)

    /**
     * Permanently removes a session and all its messages (CASCADE delete).
     */
    suspend fun deleteSession(sessionId: Long)
}
