package com.neo.chevere.domain

/** Supported agent UI requests. Renderer-specific types stay outside the domain layer. */
sealed interface AgentUiContent {
    /** A bounded list of task IDs. Room remains the source of task titles and completion state. */
    data class TaskChecklist(val surfaceId: String, val taskIds: List<Int>) : AgentUiContent
}
