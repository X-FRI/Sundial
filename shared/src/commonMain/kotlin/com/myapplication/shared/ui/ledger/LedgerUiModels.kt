package com.myapplication.shared.ui.ledger

import com.myapplication.shared.domain.model.TodoItem
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

sealed interface TimelineScope {
    data object Workbench : TimelineScope

    data object Today : TimelineScope

    data object Scheduled : TimelineScope

    data object Completed : TimelineScope

    data object Trash : TimelineScope

    data class List(
        val listId: Long,
        val isInbox: Boolean,
    ) : TimelineScope
}

enum class TimelineTone { Danger, Brand, Warning, Success, Neutral, Info }

data class TimelineSegment(
    val label: String,
    val count: Int,
    val tone: TimelineTone,
)

data class ContextTimelineState(
    val title: String,
    val subtitle: String,
    val emptyText: String,
    val segments: List<TimelineSegment>,
)

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

fun buildContextTimelineState(
    todos: List<TodoItem>,
    scope: TimelineScope,
    now: Instant,
    timeZone: TimeZone,
    inboxListId: Long?,
): ContextTimelineState {
    val today = now.toLocalDateTime(timeZone).date
    val activeParents = todos.filter { !it.isTrashed && !it.isCompleted && it.parentId == null }
    val completedParents = todos.filter { !it.isTrashed && it.isCompleted && it.parentId == null }
    val trashedParents = todos.filter { it.isTrashed && it.parentId == null }
    return when (scope) {
        TimelineScope.Workbench ->
            ContextTimelineState(
                title = "工作台时间线",
                subtitle = "所有待办的压力分布",
                emptyText = "没有待办，工作台很清爽。",
                segments =
                    listOf(
                        segment("逾期", activeParents.count { it.isOverdue(today, timeZone) }, TimelineTone.Danger),
                        segment("今天", activeParents.count { it.isDueOn(today, timeZone) }, TimelineTone.Brand),
                        segment("未来 7 天", activeParents.count { it.isDueBetween(today, 1, 7, timeZone) }, TimelineTone.Info),
                        segment("无日期", activeParents.count { it.dueDate == null }, TimelineTone.Neutral),
                        segment("待整理", activeParents.count { inboxListId != null && it.listId == inboxListId }, TimelineTone.Warning),
                    ),
            )
        TimelineScope.Today -> {
            val rhythm = buildTodayRhythmState(todos, now, timeZone)
            ContextTimelineState(
                title = "今天时间线",
                subtitle = "当天执行节奏",
                emptyText = "今天没有待办。",
                segments =
                    listOf(
                        segment("下一件", if (rhythm.nextTitle == null) 0 else 1, TimelineTone.Brand),
                        segment("今日待办", rhythm.pendingTodayCount, TimelineTone.Warning),
                        segment("已完成", rhythm.completedTodayCount, TimelineTone.Success),
                        segment("以后", activeParents.count { it.isDueAfter(today.plus(1, DateTimeUnit.DAY), timeZone) }, TimelineTone.Neutral),
                    ),
            )
        }
        TimelineScope.Scheduled ->
            ContextTimelineState(
                title = "计划时间线",
                subtitle = "未来安排分布",
                emptyText = "没有计划任务。",
                segments =
                    listOf(
                        segment("今天", activeParents.count { it.isDueOn(today, timeZone) }, TimelineTone.Brand),
                        segment("明天", activeParents.count { it.isDueOn(today.plus(1, DateTimeUnit.DAY), timeZone) }, TimelineTone.Info),
                        segment("本周", activeParents.count { it.isDueBetween(today, 2, 7, timeZone) }, TimelineTone.Warning),
                        segment("以后", activeParents.count { it.isDueAfter(today.plus(7, DateTimeUnit.DAY), timeZone) }, TimelineTone.Neutral),
                    ),
            )
        is TimelineScope.List -> {
            val listItems = activeParents.filter { it.listId == scope.listId }
            if (scope.isInbox) {
                ContextTimelineState(
                    title = "收件箱 · 待整理",
                    subtitle = "快速捕获后需要归类或安排",
                    emptyText = "无待整理任务。",
                    segments =
                        listOf(
                            segment("待整理", listItems.size, TimelineTone.Warning),
                            segment("已有日期", listItems.count { it.dueDate != null }, TimelineTone.Info),
                            segment("无日期", listItems.count { it.dueDate == null }, TimelineTone.Neutral),
                            segment("逾期", listItems.count { it.isOverdue(today, timeZone) }, TimelineTone.Danger),
                        ),
                )
            } else {
                ContextTimelineState(
                    title = "列表时间线",
                    subtitle = "当前列表的任务节奏",
                    emptyText = "这个列表没有待办。",
                    segments =
                        listOf(
                            segment("逾期", listItems.count { it.isOverdue(today, timeZone) }, TimelineTone.Danger),
                            segment("今天", listItems.count { it.isDueOn(today, timeZone) }, TimelineTone.Brand),
                            segment("本周", listItems.count { it.isDueBetween(today, 1, 7, timeZone) }, TimelineTone.Warning),
                            segment("以后", listItems.count { it.isDueAfter(today.plus(7, DateTimeUnit.DAY), timeZone) }, TimelineTone.Neutral),
                            segment("无日期", listItems.count { it.dueDate == null }, TimelineTone.Neutral),
                        ),
                )
            }
        }
        TimelineScope.Completed ->
            ContextTimelineState(
                title = "完成回顾",
                subtitle = "最近完成的任务",
                emptyText = "还没有完成记录。",
                segments =
                    listOf(
                        segment("今天完成", completedParents.count { it.completedAt?.toLocalDateTime(timeZone)?.date == today }, TimelineTone.Success),
                        segment(
                            "本周完成",
                            completedParents.count { item ->
                                item.completedAt
                                    ?.toLocalDateTime(timeZone)
                                    ?.date
                                    ?.let { completedDate -> completedDate.daysUntil(today) in 0..7 } == true
                            },
                            TimelineTone.Info,
                        ),
                        segment("最近完成", completedParents.size, TimelineTone.Neutral),
                    ),
            )
        TimelineScope.Trash ->
            ContextTimelineState(
                title = "垃圾箱摘要",
                subtitle = "可恢复和待清理的任务",
                emptyText = "垃圾箱为空。",
                segments =
                    listOf(
                        segment("最近删除", trashedParents.count { it.trashedAt?.toLocalDateTime(timeZone)?.date == today }, TimelineTone.Warning),
                        segment("可恢复", trashedParents.size, TimelineTone.Info),
                        segment("无日期", trashedParents.count { it.dueDate == null }, TimelineTone.Neutral),
                    ),
            )
    }
}

fun buildTodayRhythmState(
    todos: List<TodoItem>,
    now: Instant,
    timeZone: TimeZone,
): TodayRhythmState {
    val today = now.toLocalDateTime(timeZone).date
    val todayItems =
        todos.filter { todo ->
            todo.dueDate?.toLocalDateTime(timeZone)?.date == today
        }
    val pending =
        todayItems
            .filter { !it.isCompleted }
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
    val subtasks =
        visible
            .filter { it.parentId != null }
            .groupBy { it.parentId!! }
    val parents =
        visible
            .filter { it.parentId == null }
            .sortedWith(compareBy<TodoItem> { it.isCompleted }.thenBy { it.dueDate }.thenBy { it.sortPosition }.thenBy { it.id })
    val active =
        parents.filter { !it.isCompleted }.map { parent ->
            TaskRowModel(parent, subtasks[parent.id].orEmpty().sortedBy { it.sortPosition })
        }
    val completed =
        parents.filter { it.isCompleted }.map { parent ->
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
    val visibleParents =
        todos
            .filter { !it.isTrashed && it.parentId == null }
            .sortedWith(compareBy<TodoItem> { it.dueDate }.thenBy { it.sortPosition }.thenBy { it.id })
    val completedToday =
        visibleParents.count { todo ->
            todo.isCompleted && todo.completedAt?.toLocalDateTime(timeZone)?.date == today
        }
    val pending = visibleParents.filter { !it.isCompleted }
    val todayDue =
        pending.filter { todo ->
            todo.dueDate?.toLocalDateTime(timeZone)?.date == today
        }
    val pastItems = todayDue.filter { dueItem -> dueItem.dueDate?.let { it < now } == true }
    val upcomingItems = todayDue.filter { dueItem -> dueItem.dueDate?.let { it >= now } == true }
    val nextId = upcomingItems.firstOrNull()?.id
    val unscheduled =
        pending
            .filter { it.dueDate == null }
            .map { TaskRowModel(it, emptyList()) }
    val futureCount =
        pending.count { todo ->
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
    todos
        .filter { it.isTrashed }
        .sortedWith(compareByDescending<TodoItem> { it.trashedAt ?: it.createdAt })
        .map { TaskRowModel(it, emptyList()) }

fun formatLedgerTime(
    instant: Instant,
    timeZone: TimeZone,
): String {
    val time = instant.toLocalDateTime(timeZone).time
    return "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
}

private fun TodoItem.toTimelineTask(
    timeZone: TimeZone,
    isNext: Boolean,
): TimelineTask {
    val due = requireNotNull(dueDate)
    val time = due.toLocalDateTime(timeZone).time
    return TimelineTask(
        item = this,
        timeLabel = formatLedgerTime(due, timeZone),
        progress = timeProgress(time.hour, time.minute),
        isNext = isNext,
    )
}

private fun timeProgress(
    hour: Int,
    minute: Int,
): Float {
    val position = hour + minute / 60f
    val span = TimelineEndHour - TimelineStartHour
    return ((position - TimelineStartHour) / span).coerceIn(0f, 1f)
}

private fun segment(
    label: String,
    count: Int,
    tone: TimelineTone,
): TimelineSegment = TimelineSegment(label = label, count = count, tone = tone)

private fun TodoItem.localDueDate(timeZone: TimeZone): LocalDate? = dueDate?.toLocalDateTime(timeZone)?.date

private fun TodoItem.isOverdue(
    today: LocalDate,
    timeZone: TimeZone,
): Boolean = localDueDate(timeZone)?.let { it < today } == true

private fun TodoItem.isDueOn(
    date: LocalDate,
    timeZone: TimeZone,
): Boolean = localDueDate(timeZone) == date

private fun TodoItem.isDueBetween(
    today: LocalDate,
    startOffset: Int,
    endOffset: Int,
    timeZone: TimeZone,
): Boolean {
    val due = localDueDate(timeZone) ?: return false
    val from = today.plus(startOffset, DateTimeUnit.DAY)
    val to = today.plus(endOffset, DateTimeUnit.DAY)
    return due >= from && due <= to
}

private fun TodoItem.isDueAfter(
    date: LocalDate,
    timeZone: TimeZone,
): Boolean = localDueDate(timeZone)?.let { it > date } == true
