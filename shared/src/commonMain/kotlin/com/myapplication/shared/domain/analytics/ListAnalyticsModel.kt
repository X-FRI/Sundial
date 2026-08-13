package com.myapplication.shared.domain.analytics

import com.myapplication.shared.domain.model.TodoItem
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

data class ListAnalyticsModel(
    val listId: Long,
    val completedTotal: Int,
    val completion: ChartSeries,
    val energy: ChartSeries,
)

fun buildListAnalyticsModel(
    listId: Long,
    todos: List<TodoItem>,
    today: LocalDate,
    range: AnalyticsRange,
    timeZone: TimeZone,
): ListAnalyticsModel {
    val days = ((range.dayCount - 1) downTo 0).map { offset ->
        today.plus(-offset, DateTimeUnit.DAY)
    }
    val completed = todos.filter { todo ->
        todo.listId == listId &&
            !todo.isTrashed &&
            todo.isCompleted &&
            todo.completedAt != null
    }
    val completedByDate = completed.groupBy { todo ->
        todo.completedAt!!.toLocalDateTime(timeZone).date
    }

    return ListAnalyticsModel(
        listId = listId,
        completedTotal = completed.size,
        completion = ChartSeries(
            title = "完成趋势",
            points = days.map { date ->
                ChartPoint(date.shortLabel(today), completedByDate[date].orEmpty().size)
            },
        ),
        energy = ChartSeries(
            title = "精力输出",
            points = days.map { date ->
                ChartPoint(date.shortLabel(today), completedByDate[date].orEmpty().sumOf { it.energyScore() })
            },
        ),
    )
}

private fun TodoItem.energyScore(): Int {
    var score = 1
    if (note.isNotBlank()) score += 1
    if (flag) score += 1
    return score
}

private fun LocalDate.shortLabel(today: LocalDate): String =
    if (this == today) "今天" else "${month.ordinal + 1}/$day"
