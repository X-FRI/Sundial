package com.myapplication.shared.domain.model

import kotlinx.datetime.Instant

/**
 * 待办条目的领域模型，供 UI 层消费。
 *
 * 设计要点：
 * - 时间字段在领域层用 [Instant]（数据库与同步 DTO 中为 epoch 毫秒 Long，由
 *   TodoRepositoryImpl 负责转换，避免平台时区问题）；
 * - 软删除：isTrashed + trashedAt 标记，仅 deleteForever 才真正删行；
 * - parentId 非空时该条为子任务，挂在本列表内的父任务之下（见 sq 的 selectSubTasks）。
 */
data class TodoItem(
    val id: Long,
    val listId: Long,
    val title: String,
    val note: String,
    val dueDate: Instant?,
    val isCompleted: Boolean,
    val flag: Boolean,
    val completedAt: Instant?,
    val isTrashed: Boolean,
    val trashedAt: Instant?,
    val parentId: Long?,
    val sortPosition: Double,
    val createdAt: Instant,
)
