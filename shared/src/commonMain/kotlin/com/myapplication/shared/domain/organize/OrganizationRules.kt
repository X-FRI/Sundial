package com.myapplication.shared.domain.organize

import com.myapplication.shared.domain.model.TodoItem
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun buildOrganizationSuggestions(
    todos: List<TodoItem>,
    inboxListId: Long?,
    today: LocalDate,
    timeZone: TimeZone,
): List<OrganizationSuggestion> =
    todos
        .filter { !it.isTrashed && !it.isCompleted && it.parentId == null }
        .mapNotNull { todo ->
            val reasons =
                buildSet {
                    if (inboxListId != null && todo.listId == inboxListId) add(OrganizationReason.Inbox)
                    if (todo.dueDate == null) add(OrganizationReason.NoDate)
                    if (todo.localDueDate(timeZone)?.let { it < today } == true) add(OrganizationReason.Overdue)
                    if (todo.title.length > 100) add(OrganizationReason.LongTitle)
                }
            if (reasons.isEmpty()) {
                null
            } else {
                OrganizationSuggestion(
                    todo = todo,
                    reasons = reasons,
                    actions = actionsFor(reasons),
                )
            }
        }

private fun actionsFor(reasons: Set<OrganizationReason>): List<OrganizationAction> =
    buildList {
        if (OrganizationReason.Overdue in reasons || OrganizationReason.NoDate in reasons) {
            add(OrganizationAction.ScheduleToday)
            add(OrganizationAction.ScheduleTomorrow)
        }
        if (OrganizationReason.Inbox in reasons) add(OrganizationAction.MoveToList)
        if (OrganizationReason.LongTitle in reasons) add(OrganizationAction.EditTitle)
        add(OrganizationAction.Trash)
    }.distinct().take(3)

private fun TodoItem.localDueDate(timeZone: TimeZone): LocalDate? = dueDate?.toLocalDateTime(timeZone)?.date
