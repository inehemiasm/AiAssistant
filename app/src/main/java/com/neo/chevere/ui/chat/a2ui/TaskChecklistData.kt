package com.neo.chevere.ui.chat.a2ui

import com.neo.chevere.data.datasource.local.TaskEntity

/** Room-backed data available to chat surfaces; prevents stale actions during loading. */
sealed interface TaskChecklistData {
    data object Loading : TaskChecklistData
    data class Ready(val tasks: List<TaskEntity>, val updatingIds: Set<Int> = emptySet()) : TaskChecklistData
    data object Unavailable : TaskChecklistData
}
