package com.neo.chevere.data

import com.neo.chevere.data.datasource.local.ConversationHistoryDao
import com.neo.chevere.data.datasource.local.ConversationMessageEntity
import com.neo.chevere.data.datasource.local.ConversationSessionEntity
import com.neo.chevere.domain.ChatHistoryRepository
import com.neo.chevere.domain.ChatMessage
import com.neo.chevere.domain.ConversationSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_TITLE_LENGTH = 60

/**
 * Room-backed implementation of [ChatHistoryRepository].
 *
 * Entity ↔ domain mapping is encapsulated here so neither the DAO layer nor
 * the ViewModel layer needs to know about the other's types.
 */
@Singleton
class ChatHistoryRepositoryImpl @Inject constructor(
    private val dao: ConversationHistoryDao
) : ChatHistoryRepository {

    override fun getAllSessions(): Flow<List<ConversationSession>> =
        dao.getAllSessions().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getMessages(sessionId: Long): List<ChatMessage> =
        dao.getMessagesForSession(sessionId).map { it.toDomain() }

    override suspend fun createSession(firstUserMessage: String, modelName: String): Long {
        val title = firstUserMessage.trim().take(MAX_TITLE_LENGTH).ifBlank { "New conversation" }
        val session = ConversationSessionEntity(
            title = title,
            modelName = modelName,
            messageCount = 0
        )
        return dao.insertSession(session)
    }

    override suspend fun appendMessage(sessionId: Long, message: ChatMessage) {
        // Persist the message
        dao.insertMessage(message.toEntity(sessionId))

        // Update session header: bump updatedAt and messageCount
        val session = dao.getSessionById(sessionId) ?: return
        dao.updateSession(
            session.copy(
                updatedAt = System.currentTimeMillis(),
                messageCount = session.messageCount + 1
            )
        )
    }

    override suspend fun renameSession(sessionId: Long, newTitle: String) {
        val session = dao.getSessionById(sessionId) ?: return
        dao.updateSession(session.copy(title = newTitle.trim().take(MAX_TITLE_LENGTH)))
    }

    override suspend fun deleteSession(sessionId: Long) {
        dao.deleteSession(sessionId)
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private fun ConversationSessionEntity.toDomain() = ConversationSession(
        id = id,
        title = title,
        messageCount = messageCount,
        createdAt = createdAt,
        updatedAt = updatedAt,
        modelName = modelName
    )

    private fun ConversationMessageEntity.toDomain() = ChatMessage(
        text = text,
        isUser = isUser,
        imageUri = imageUri,
        modelName = modelName,
        inferenceTimeMs = inferenceTimeMs,
        inputTokenCount = inputTokenCount,
        outputTokenCount = outputTokenCount,
        isExplicitImage = isExplicitImage,
        // Restored messages are never masked — user already saw them
        isImageMasked = false
    )

    private fun ChatMessage.toEntity(sessionId: Long) = ConversationMessageEntity(
        sessionId = sessionId,
        text = text,
        isUser = isUser,
        imageUri = imageUri,
        modelName = modelName,
        inferenceTimeMs = inferenceTimeMs,
        inputTokenCount = inputTokenCount,
        outputTokenCount = outputTokenCount,
        isExplicitImage = isExplicitImage
    )
}
