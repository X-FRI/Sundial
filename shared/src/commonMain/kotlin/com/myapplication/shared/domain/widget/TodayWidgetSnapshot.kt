package com.myapplication.shared.domain.widget

import com.myapplication.shared.domain.model.TodoItem
import kotlinx.datetime.Instant
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class WidgetTask(
    val id: Long,
    val title: String,
    val dueLabel: String?,
    val isFlagged: Boolean,
)

@Serializable
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
    val topOverdueTasks: List<WidgetTask> = emptyList(),
) {
    companion object {
        fun empty(now: Instant): TodayWidgetSnapshot =
            TodayWidgetSnapshot(
                dateLabel = "今天",
                pendingTodayCount = 0,
                completedTodayCount = 0,
                nextTaskTitle = null,
                nextTaskDueLabel = null,
                topTodayTasks = emptyList(),
                overdueCount = 0,
                inboxCount = 0,
                lastUpdatedAt = now,
                topOverdueTasks = emptyList(),
            )
    }
}

enum class WidgetSnapshotSize(val maxTodayTasks: Int, val maxOverdueTasks: Int) {
    Small(maxTodayTasks = 1, maxOverdueTasks = 0),
    Medium(maxTodayTasks = 3, maxOverdueTasks = 1),
    Large(maxTodayTasks = 6, maxOverdueTasks = 3),
}

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
    val overdue = activeParents
        .filter { it.dueDate?.toLocalDateTime(timeZone)?.date?.let { due -> due < today } == true }
        .sortedWith(compareBy<TodoItem> { it.dueDate }.thenBy { it.sortPosition }.thenBy { it.id })
    val next = todayPending.firstOrNull { it.dueDate?.let { due -> due >= now } == true } ?: todayPending.firstOrNull()

    return TodayWidgetSnapshot(
        dateLabel = "${today.year}-${today.month.number.toString().padStart(2, '0')}-${today.day.toString().padStart(2, '0')}",
        pendingTodayCount = todayPending.size,
        completedTodayCount = completedParents.count { it.completedAt?.toLocalDateTime(timeZone)?.date == today },
        nextTaskTitle = next?.title,
        nextTaskDueLabel = next?.dueDate?.let { formatWidgetTime(it, timeZone) },
        topTodayTasks = todayPending.take(maxTasks).map { item -> item.toWidgetTask(timeZone) },
        overdueCount = overdue.size,
        inboxCount = activeParents.count { inboxListId != null && it.listId == inboxListId },
        lastUpdatedAt = now,
        topOverdueTasks = overdue.take(maxTasks).map { item -> item.toWidgetTask(timeZone) },
    )
}

fun TodayWidgetSnapshot.isCurrentFor(now: Instant, timeZone: TimeZone): Boolean =
    lastUpdatedAt.toLocalDateTime(timeZone).date == now.toLocalDateTime(timeZone).date

private fun TodoItem.toWidgetTask(timeZone: TimeZone): WidgetTask =
    WidgetTask(
        id = id,
        title = title,
        dueLabel = dueDate?.let { formatWidgetTime(it, timeZone) },
        isFlagged = flag,
    )

private fun formatWidgetTime(instant: Instant, timeZone: TimeZone): String {
    val time = instant.toLocalDateTime(timeZone).time
    return "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
}

private val Month.number: Int
    get() = ordinal + 1
