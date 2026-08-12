package com.myapplication.shared.domain.widget

import com.myapplication.shared.domain.model.TodoItem
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class WidgetTask(
    val id: Long,
    val title: String,
    val dueLabel: String?,
    val isFlagged: Boolean,
)

data class TodayWidgetSnapshot(
    val dateLabel: String,
    val pendingTodayCount: Int,
    val completedTodayCount: Int,
    val nextTaskTitle: String?,
    val nextTaskDueLabel: String?,
    val topTodayTasks: List<WidgetTask>,
    val overdueCount: Int,
    val inboxCount: Int,
    val lastUpdatedAt: Instant,
)

fun buildTodayWidgetSnapshot(
    todos: List<TodoItem>,
    now: Instant,
    timeZone: TimeZone,
    inboxListId: Long?,
    maxTasks: Int = 5,
): TodayWidgetSnapshot {
    val today = now.toLocalDateTime(timeZone).date
    val activeParents = todos.filter { !it.isTrashed && !it.isCompleted && it.parentId == null }
    val completedParents = todos.filter { !it.isTrashed && it.isCompleted && it.parentId == null }
    val todayPending = activeParents
        .filter { it.dueDate?.toLocalDateTime(timeZone)?.date == today }
        .sortedWith(compareBy<TodoItem> { it.dueDate }.thenBy { it.sortPosition }.thenBy { it.id })
    val next = todayPending.firstOrNull { it.dueDate?.let { due -> due >= now } == true } ?: todayPending.firstOrNull()

    return TodayWidgetSnapshot(
        dateLabel = "${today.year}-${today.monthNumber.toString().padStart(2, '0')}-${today.dayOfMonth.toString().padStart(2, '0')}",
        pendingTodayCount = todayPending.size,
        completedTodayCount = completedParents.count { it.completedAt?.toLocalDateTime(timeZone)?.date == today },
        nextTaskTitle = next?.title,
        nextTaskDueLabel = next?.dueDate?.let { formatWidgetTime(it, timeZone) },
        topTodayTasks = todayPending.take(maxTasks).map { item ->
            WidgetTask(
                id = item.id,
                title = item.title,
                dueLabel = item.dueDate?.let { formatWidgetTime(it, timeZone) },
                isFlagged = item.flag,
            )
        },
        overdueCount = activeParents.count { item ->
            item.dueDate?.toLocalDateTime(timeZone)?.date?.let { it < today } == true
        },
        inboxCount = activeParents.count { inboxListId != null && it.listId == inboxListId },
        lastUpdatedAt = now,
    )
}

private fun formatWidgetTime(instant: Instant, timeZone: TimeZone): String {
    val time = instant.toLocalDateTime(timeZone).time
    return "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
}
