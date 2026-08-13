package com.myapplication.shared.domain.list

import com.myapplication.shared.domain.model.TodoItem
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class ListStats(
    val listId: Long,
    val activeCount: Int,
    val completedCount: Int,
    val overdueCount: Int,
    val todayCount: Int,
    val noDateCount: Int,
    val trashedCount: Int,
)

fun buildListStats(
    listId: Long,
    todos: List<TodoItem>,
    today: LocalDate,
    timeZone: TimeZone,
): ListStats {
    val inList = todos.filter { it.listId == listId }
    val active = inList.filter { !it.isTrashed && !it.isCompleted }
    val completed = inList.filter { !it.isTrashed && it.isCompleted }
    return ListStats(
        listId = listId,
        activeCount = active.size,
        completedCount = completed.size,
        overdueCount = active.count { it.localDueDate(timeZone)?.let { due -> due < today } == true },
        todayCount = active.count { it.localDueDate(timeZone) == today },
        noDateCount = active.count { it.dueDate == null },
        trashedCount = inList.count { it.isTrashed },
    )
}

private fun TodoItem.localDueDate(timeZone: TimeZone): LocalDate? =
    dueDate?.toLocalDateTime(timeZone)?.date
