package com.myapplication.shared.ui.analytics

import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.model.TodoList
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

internal data class AnalyticsModel(
    val encouragement: String,
    val completedToday: Int,
    val streakDays: Int,
    val weekEnergy: Int,
    val completionRate: Int,
    val days: List<DayPoint>,
    val pressure: List<PressureBucket>,
    val deepWorkCount: Int,
    val quickWinCount: Int,
    val flaggedCompletedCount: Int,
    val outputSummary: String,
)

internal data class DayPoint(
    val date: LocalDate,
    val label: String,
    val completedCount: Int,
    val energy: Int,
)

internal data class PressureBucket(
    val label: String,
    val count: Int,
    val tone: AnalyticsTone,
)

internal enum class AnalyticsTone {
    Danger,
    Brand,
    Info,
    Neutral,
}

internal fun buildAnalyticsModel(
    todos: List<TodoItem>,
    lists: List<TodoList>,
    today: LocalDate,
    timeZone: TimeZone,
): AnalyticsModel {
    val visible = todos.filter { !it.isTrashed }
    val parents = visible.filter { it.parentId == null }
    val subtasksByParent = visible.filter { it.parentId != null }.groupBy { it.parentId }
    val completed = parents.filter { it.isCompleted && it.completedAt != null }
    val active = parents.filter { !it.isCompleted }
    val days = (6 downTo 0).map { offset -> today.plus(-offset, DateTimeUnit.DAY) }
    val completedByDate = completed.groupBy { it.completedAt!!.toLocalDateTime(timeZone).date }
    val dayPoints =
        days.map { date ->
            val done = completedByDate[date].orEmpty()
            DayPoint(
                date = date,
                label = dayLabel(date, today),
                completedCount = done.size,
                energy = done.sumOf { energyScore(it, subtasksByParent[it.id].orEmpty()) },
            )
        }
    val weekEnergy = dayPoints.sumOf { it.energy }
    val completedToday = completedByDate[today].orEmpty().size
    val completionRate = if (parents.isEmpty()) 0 else ((completed.size * 100f) / parents.size).toInt()
    val streak = completionStreak(today, completedByDate)
    val overdue = active.count { it.localDueDate(timeZone)?.let { due -> due < today } == true }
    val dueToday = active.count { it.localDueDate(timeZone) == today }
    val futureSeven =
        active.count { item ->
            item.localDueDate(timeZone)?.let { due ->
                due > today && due <= today.plus(7, DateTimeUnit.DAY)
            } == true
        }
    val noDate = active.count { it.dueDate == null }
    val completedScores = completed.map { energyScore(it, subtasksByParent[it.id].orEmpty()) }
    val deepWork = completedScores.count { it >= 3 }
    val quickWins = completedScores.count { it <= 1 }
    val flaggedCompleted = completed.count { it.flag }
    val listCount = lists.size
    return AnalyticsModel(
        encouragement = encouragement(completedToday, streak, overdue, listCount),
        completedToday = completedToday,
        streakDays = streak,
        weekEnergy = weekEnergy,
        completionRate = completionRate,
        days = dayPoints,
        pressure =
            listOf(
                PressureBucket("逾期", overdue, AnalyticsTone.Danger),
                PressureBucket("今天", dueToday, AnalyticsTone.Brand),
                PressureBucket("未来 7 天", futureSeven, AnalyticsTone.Info),
                PressureBucket("无日期", noDate, AnalyticsTone.Neutral),
            ),
        deepWorkCount = deepWork,
        quickWinCount = quickWins,
        flaggedCompletedCount = flaggedCompleted,
        outputSummary = if (weekEnergy == 0) "先完成一件小事，图表就会开始发光。" else "本周已经输出 $weekEnergy 点，继续保持节奏。",
    )
}

private fun TodoItem.localDueDate(timeZone: TimeZone): LocalDate? = dueDate?.toLocalDateTime(timeZone)?.date

private fun energyScore(
    todo: TodoItem,
    subtasks: List<TodoItem>,
): Int {
    var score = 1
    if (todo.note.isNotBlank()) score += 1
    if (todo.flag) score += 1
    score += subtasks.count { it.isCompleted }.coerceAtMost(3)
    return score
}

private fun completionStreak(
    today: LocalDate,
    completedByDate: Map<LocalDate, List<TodoItem>>,
): Int {
    var streak = 0
    var day = today
    while (completedByDate[day].orEmpty().isNotEmpty()) {
        streak += 1
        day = day.plus(-1, DateTimeUnit.DAY)
    }
    return streak
}

private fun dayLabel(
    date: LocalDate,
    today: LocalDate,
): String = if (date == today) "今天" else "${date.month.ordinal + 1}/${date.day}"

private fun encouragement(
    completedToday: Int,
    streak: Int,
    overdue: Int,
    listCount: Int,
): String =
    when {
        completedToday > 0 && streak > 1 -> "今天已经推进 $completedToday 件，连续 $streak 天有完成记录。节奏正在形成。"
        completedToday > 0 -> "今天已经完成 $completedToday 件，先把势能留住。"
        overdue > 0 -> "有 $overdue 件逾期待办，先处理最小的一件就能降低压力。"
        listCount > 1 -> "任务已经分布在 $listCount 个列表里，今天可以选一个方向集中推进。"
        else -> "先完成一件足够小的待办，让今天的曲线开始出现。"
    }
