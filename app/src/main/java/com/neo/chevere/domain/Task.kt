package com.neo.chevere.domain

/** A local task independent of Room persistence and presentation concerns. */
data class Task(
    val id: Int,
    val title: String,
    val description: String,
    val status: TaskStatus,
    val createdAt: Long
)

/** Business completion status; persistence and UI have their own representations. */
enum class TaskStatus { PENDING, COMPLETED }
