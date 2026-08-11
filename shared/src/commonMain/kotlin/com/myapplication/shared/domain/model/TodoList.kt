package com.myapplication.shared.domain.model

import kotlinx.datetime.Instant

/**
 * 列表（列表页/分组）的领域模型。
 *
 * 设计要点：
 * - colorKey 为主题色键（如 "blue"），由 UI 层映射为具体颜色；
 * - position 决定列表页排序（0 起递增），本地新建时取当前最大 position + 1。
 */
data class TodoList(
    val id: Long,
    val name: String,
    val colorKey: String,
    val position: Int,
    val createdAt: Instant,
)
