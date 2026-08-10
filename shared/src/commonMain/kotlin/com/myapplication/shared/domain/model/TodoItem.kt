package com.myapplication.shared.domain.model

import kotlinx.datetime.Instant

data class TodoItem(
    val id: Long,
    val listId: Long,
    val title: String,
    val note: String,
    val dueDate: Instant?,
    val isCompleted: Boolean,
    val completedAt: Instant?,
    val isTrashed: Boolean,
    val trashedAt: Instant?,
    val parentId: Long?,
    val sortPosition: Double,
    val createdAt: Instant,
)
