package com.myapplication.shared.ui.ledger

import com.myapplication.shared.domain.model.TodoItem
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class TodayRhythmState(
    val nowLabel: String,
    val nextDueLabel: String?,
    val nextTitle: String?,
    val pendingTodayCount: Int,
    val completedTodayCount: Int,
)

data class TaskRowModel(
    val item: TodoItem,
    val subtasks: List<TodoItem>,
)

data class TaskGroups(
    val active: List<TaskRowModel>,
    val completed: List<TaskRowModel>,
)

fun buildTodayRhythmState(
    todos: List<TodoItem>,
    now: Instant,
    timeZone: TimeZone,
): TodayRhythmState {
    val today = now.toLocalDateTime(timeZone).date
    val todayItems = todos.filter { todo ->
        todo.dueDate?.toLocalDateTime(timeZone)?.date == today
    }
    val pending = todayItems.filter { !it.isCompleted }
        .sortedWith(compareBy<TodoItem> { it.dueDate }.thenBy { it.sortPosition }.thenBy { it.id })
    val completed = todayItems.count { it.isCompleted }
    val next = pending.firstOrNull { item -> item.dueDate?.let { it >= now } ?: false } ?: pending.firstOrNull()
    return TodayRhythmState(
        nowLabel = formatLedgerTime(now, timeZone),
        nextDueLabel = next?.dueDate?.let { formatLedgerTime(it, timeZone) },
        nextTitle = next?.title,
        pendingTodayCount = pending.size,
        completedTodayCount = completed,
    )
}

fun buildTaskGroups(todos: List<TodoItem>): TaskGroups {
    val visible = todos.filter { !it.isTrashed }
    val subtasks = visible.filter { it.parentId != null }
        .groupBy { it.parentId!! }
    val parents = visible.filter { it.parentId == null }
        .sortedWith(compareBy<TodoItem> { it.isCompleted }.thenBy { it.dueDate }.thenBy { it.sortPosition }.thenBy { it.id })
    val active = parents.filter { !it.isCompleted }.map { parent ->
        TaskRowModel(parent, subtasks[parent.id].orEmpty().sortedBy { it.sortPosition })
    }
    val completed = parents.filter { it.isCompleted }.map { parent ->
        TaskRowModel(parent, subtasks[parent.id].orEmpty().sortedBy { it.sortPosition })
    }
    return TaskGroups(active = active, completed = completed)
}

fun formatLedgerTime(instant: Instant, timeZone: TimeZone): String {
    val time = instant.toLocalDateTime(timeZone).time
    return "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
}
