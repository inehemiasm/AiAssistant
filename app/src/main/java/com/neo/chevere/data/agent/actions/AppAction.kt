package com.neo.chevere.data.agent.actions

/**
 * Represents a structured action that the assistant can perform.
 */
interface AppAction<T : AppActionRequest> {
    val id: String
    val description: String
    suspend fun execute(request: T): AppActionResult
}

/**
 * Marker interface for app action requests.
 */
interface AppActionRequest

/**
 * Represents the outcome of an [AppAction].
 */
sealed interface AppActionResult {
    /** Action completed successfully. */
    data class Success(val output: String) : AppActionResult

    /** Action failed with an error. */
    data class Error(val message: String) : AppActionResult

    /** Action requires explicit user confirmation. */
    data class ConfirmationRequired(
        val message: String,
        val data: AppActionRequest
    ) : AppActionResult
}
