package com.neo.chevere.data.datasource.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persists a single message belonging to a [ConversationSessionEntity].
 *
 * @property sessionId Foreign key to [ConversationSessionEntity.id].
 * @property text The message body. For assistant image messages, this holds
 *                the caption text; the image path is stored in [imageUri].
 * @property isUser `true` for user messages, `false` for assistant messages.
 * @property imageUri Optional file/content URI of an attached or generated image.
 * @property modelName Name of the model that generated this message, if applicable.
 * @property inferenceTimeMs Generation latency in ms (assistant messages only).
 * @property inputTokenCount Estimated input tokens (assistant messages only).
 * @property outputTokenCount Estimated output tokens (assistant messages only).
 * @property isExplicitImage Whether this image was generated via an age-gated prompt.
 * @property timestamp Epoch-ms timestamp of the message; used for ordering.
 */
@Entity(
    tableName = "conversation_messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class ConversationMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val text: String,
    val isUser: Boolean,
    val imageUri: String? = null,
    val modelName: String? = null,
    val inferenceTimeMs: Long? = null,
    val inputTokenCount: Int? = null,
    val outputTokenCount: Int? = null,
    val isExplicitImage: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
