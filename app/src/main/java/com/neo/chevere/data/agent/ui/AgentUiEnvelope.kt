package com.neo.chevere.data.agent.ui

import com.neo.chevere.domain.AgentUiContent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/** Versioned storage/tool envelope; text remains suitable for sharing and conversation memory. */
object AgentUiEnvelope {
    const val PREFIX = "CHEVERE_A2UI_RESULT:"
    const val MAX_TASKS = 50
    private const val MAX_ENVELOPE_LENGTH = 65_536
    private val json = Json { ignoreUnknownKeys = true }

    /** Persisted readable summary and a renderer-independent checklist reference. */
    data class Payload(
        val version: Int = 1,
        val text: String,
        val checklist: AgentUiContent.TaskChecklist
    )

    /** Builds a bounded checklist result without asking the local model to author protocol JSON. */
    fun checklist(text: String, taskIds: List<Int>): String = encode(
        Payload(text = text.take(8_000), checklist = AgentUiContent.TaskChecklist(
            surfaceId = UUID.randomUUID().toString(),
            taskIds = taskIds.distinct().take(MAX_TASKS)
        ))
    )

    /** Encodes metadata for the existing string tool result and Room message text column. */
    fun encode(payload: Payload): String = PREFIX + json.encodeToString(payload.toDto())

    /** Returns null for unsupported, malformed or oversized envelopes without exposing raw JSON. */
    fun decode(value: String): Payload? {
        if (!value.startsWith(PREFIX) || value.length > MAX_ENVELOPE_LENGTH) return null
        return runCatching { json.decodeFromString<AgentUiEnvelopeDto>(value.removePrefix(PREFIX)) }
            .getOrNull()?.takeIf {
                it.version == 1 && it.checklist.surfaceId.matches(Regex("[a-zA-Z0-9-]{1,64}")) &&
                    it.checklist.taskIds.size <= MAX_TASKS &&
                    it.checklist.taskIds.all { id -> id > 0 } &&
                    it.checklist.taskIds.distinct().size == it.checklist.taskIds.size
            }?.toPayload()
    }

    /** Keeps protocol metadata out of sharing, speech, and compressed conversation memory. */
    fun displayText(value: String): String = decode(value)?.text
        ?: if (value.startsWith(PREFIX)) "Unable to display the task checklist. Please ask to list tasks again." else value
}
