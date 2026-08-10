package com.myapplication.shared.domain.model

import kotlinx.datetime.Instant

data class TodoList(
    val id: Long,
    val name: String,
    val colorKey: String,
    val position: Int,
    val createdAt: Instant,
)
