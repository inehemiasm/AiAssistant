package com.neo.chevere.data.agent.ui

import com.neo.chevere.domain.AgentUiContent
import kotlinx.serialization.Serializable

/** Data-only storage/tool representation. Its JSON contract remains compatible with v1 history. */
@Serializable
internal data class AgentUiEnvelopeDto(
    val version: Int = 1,
    val text: String,
    val checklist: TaskChecklistDto
)

/** Serializable reference DTO; the corresponding domain model has no serialization annotations. */
@Serializable
internal data class TaskChecklistDto(val surfaceId: String, val taskIds: List<Int>)

internal fun AgentUiEnvelope.Payload.toDto() = AgentUiEnvelopeDto(
    version, text, TaskChecklistDto(checklist.surfaceId, checklist.taskIds)
)

internal fun AgentUiEnvelopeDto.toPayload() = AgentUiEnvelope.Payload(
    version, text, AgentUiContent.TaskChecklist(checklist.surfaceId, checklist.taskIds)
)
