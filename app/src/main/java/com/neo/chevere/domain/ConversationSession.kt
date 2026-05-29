package com.neo.chevere.domain

/**
 * Domain representation of a saved conversation session shown in the history list.
 *
 * @property id Unique Row ID from the Room database.
 * @property title Short label (first user message, truncated). Editable by the user.
 * @property messageCount Number of messages stored in this session.
 * @property createdAt Epoch-ms of the first message.
 * @property updatedAt Epoch-ms of the most recent message — drives newest-first sort.
 * @property modelName The active LLM model name at the time the session was created.
 */
data class ConversationSession(
    val id: Long,
    val title: String,
    val messageCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val modelName: String
)
