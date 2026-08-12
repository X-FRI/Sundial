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

data class TimelineTask(
    val item: TodoItem,
    val timeLabel: String,
    val progress: Float,
    val isNext: Boolean,
)

data class TodayTimelineState(
    val startLabel: String,
    val endLabel: String,
    val nowLabel: String,
    val nowProgress: Float,
    val past: List<TimelineTask>,
    val upcoming: List<TimelineTask>,
    val unscheduled: List<TaskRowModel>,
    val completedTodayCount: Int,
    val futureCount: Int,
)

private const val TimelineStartHour = 6
private const val TimelineEndHour = 24

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

fun buildTodayTimelineState(
    todos: List<TodoItem>,
    now: Instant,
    timeZone: TimeZone,
): TodayTimelineState {
    val today = now.toLocalDateTime(timeZone).date
    val nowTime = now.toLocalDateTime(timeZone).time
    val visibleParents = todos
        .filter { !it.isTrashed && it.parentId == null }
        .sortedWith(compareBy<TodoItem> { it.dueDate }.thenBy { it.sortPosition }.thenBy { it.id })
    val completedToday = visibleParents.count { todo ->
        todo.isCompleted && todo.completedAt?.toLocalDateTime(timeZone)?.date == today
    }
    val pending = visibleParents.filter { !it.isCompleted }
    val todayDue = pending.filter { todo ->
        todo.dueDate?.toLocalDateTime(timeZone)?.date == today
    }
    val pastItems = todayDue.filter { dueItem -> dueItem.dueDate?.let { it < now } == true }
    val upcomingItems = todayDue.filter { dueItem -> dueItem.dueDate?.let { it >= now } == true }
    val nextId = upcomingItems.firstOrNull()?.id
    val unscheduled = pending
        .filter { it.dueDate == null }
        .map { TaskRowModel(it, emptyList()) }
    val futureCount = pending.count { todo ->
        val dueDate = todo.dueDate?.toLocalDateTime(timeZone)?.date
        dueDate != null && dueDate > today
    }
    return TodayTimelineState(
        startLabel = "${TimelineStartHour.toString().padStart(2, '0')}:00",
        endLabel = "24:00",
        nowLabel = formatLedgerTime(now, timeZone),
        nowProgress = timeProgress(nowTime.hour, nowTime.minute),
        past = pastItems.map { it.toTimelineTask(timeZone, isNext = false) },
        upcoming = upcomingItems.map { it.toTimelineTask(timeZone, isNext = it.id == nextId) },
        unscheduled = unscheduled,
        completedTodayCount = completedToday,
        futureCount = futureCount,
    )
}

fun buildTrashGroups(todos: List<TodoItem>): List<TaskRowModel> =
    todos.filter { it.isTrashed }
        .sortedWith(compareByDescending<TodoItem> { it.trashedAt ?: it.createdAt })
        .map { TaskRowModel(it, emptyList()) }

fun formatLedgerTime(instant: Instant, timeZone: TimeZone): String {
    val time = instant.toLocalDateTime(timeZone).time
    return "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
}

private fun TodoItem.toTimelineTask(timeZone: TimeZone, isNext: Boolean): TimelineTask {
    val due = requireNotNull(dueDate)
    val time = due.toLocalDateTime(timeZone).time
    return TimelineTask(
        item = this,
        timeLabel = formatLedgerTime(due, timeZone),
        progress = timeProgress(time.hour, time.minute),
        isNext = isNext,
    )
}

private fun timeProgress(hour: Int, minute: Int): Float {
    val position = hour + minute / 60f
    val span = TimelineEndHour - TimelineStartHour
    return ((position - TimelineStartHour) / span).coerceIn(0f, 1f)
}
