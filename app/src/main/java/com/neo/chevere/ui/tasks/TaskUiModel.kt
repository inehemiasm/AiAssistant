package com.neo.chevere.ui.tasks

/** Presentation of a task. Does not carry Room metadata or domain status enums. */
data class TaskUiModel(
    val id: Int,
    val title: String,
    val description: String,
    val completion: TaskCompletionUiState = TaskCompletionUiState.Pending,
    val interaction: TaskInteractionUiState = TaskInteractionUiState.Enabled
) {
    val isCompleted: Boolean get() = completion is TaskCompletionUiState.Completed
    val isEnabled: Boolean get() = interaction is TaskInteractionUiState.Enabled
}

/** Visual completion state shared by Tasks and the A2UI task catalog. */
sealed interface TaskCompletionUiState {
    data object Pending : TaskCompletionUiState
    data object Completed : TaskCompletionUiState
}

/** Availability of a task's status control. */
sealed interface TaskInteractionUiState {
    data object Enabled : TaskInteractionUiState
    data object Updating : TaskInteractionUiState
}
