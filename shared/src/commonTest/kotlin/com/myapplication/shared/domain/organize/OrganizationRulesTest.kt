package com.myapplication.shared.domain.organize

import com.myapplication.shared.domain.model.TodoItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class OrganizationRulesTest {
    private val today = LocalDate(2026, 8, 13)
    private val tz = TimeZone.UTC

    @Test
    fun inboxTaskWithoutDateGetsInboxAndNoDateReasons() {
        val suggestions = buildOrganizationSuggestions(
            todos = listOf(todo(id = 1, listId = 10, dueMillis = null)),
            inboxListId = 10,
            today = today,
            timeZone = tz,
        )

        val reasons = suggestions.single().reasons
        assertTrue(OrganizationReason.Inbox in reasons)
        assertTrue(OrganizationReason.NoDate in reasons)
    }

    @Test
    fun overdueTaskGetsOverdueReasonUsingProvidedTimezone() {
        val suggestions = buildOrganizationSuggestions(
            todos = listOf(
                todo(
                    id = 1,
                    listId = 20,
                    dueMillis = Instant.parse("2026-08-12T16:30:00Z").toEpochMilliseconds(),
                ),
            ),
            inboxListId = 10,
            today = today,
            timeZone = TimeZone.of("America/New_York"),
        )

        assertEquals(setOf(OrganizationReason.Overdue), suggestions.single().reasons)
    }

    @Test
    fun longTitleAndMissingNextStepReasonsUseTitleAndBlankNote() {
        val suggestions = buildOrganizationSuggestions(
            todos = listOf(
                todo(
                    id = 1,
                    listId = 20,
                    title = "This title is intentionally much longer than thirty six characters",
                    note = "   ",
                    dueMillis = Instant.parse("2026-08-13T09:00:00Z").toEpochMilliseconds(),
                ),
            ),
            inboxListId = 10,
            today = today,
            timeZone = tz,
        )

        assertEquals(
            setOf(OrganizationReason.LongTitle, OrganizationReason.MissingNextStep),
            suggestions.single().reasons,
        )
    }

    @Test
    fun skipsTrashedCompletedSubtasksAndTodosWithoutReasons() {
        val suggestions = buildOrganizationSuggestions(
            todos = listOf(
                todo(id = 1, listId = 10, dueMillis = null, trashed = true),
                todo(id = 2, listId = 10, dueMillis = null, completed = true),
                todo(id = 3, listId = 10, dueMillis = null, parentId = 99),
                todo(id = 4, listId = 20, dueMillis = Instant.parse("2026-08-13T09:00:00Z").toEpochMilliseconds()),
                todo(id = 5, listId = 10, dueMillis = null),
            ),
            inboxListId = 10,
            today = today,
            timeZone = tz,
        )

        assertEquals(listOf(5L), suggestions.map { it.todo.id })
    }

    @Test
    fun actionsAreStableDistinctAndLimitedToThree() {
        val suggestions = buildOrganizationSuggestions(
            todos = listOf(
                todo(
                    id = 1,
                    listId = 10,
                    title = "This title is intentionally much longer than thirty six characters",
                    note = "",
                    dueMillis = null,
                ),
                todo(
                    id = 2,
                    listId = 20,
                    title = "This title is long enough",
                    note = "",
                    dueMillis = Instant.parse("2026-08-12T09:00:00Z").toEpochMilliseconds(),
                ),
            ),
            inboxListId = 10,
            today = today,
            timeZone = tz,
        )

        assertEquals(
            listOf(
                OrganizationAction.ScheduleToday,
                OrganizationAction.ScheduleTomorrow,
                OrganizationAction.MoveToList,
            ),
            suggestions.first { it.todo.id == 1L }.actions,
        )
        assertEquals(
            listOf(
                OrganizationAction.ScheduleToday,
                OrganizationAction.ScheduleTomorrow,
                OrganizationAction.CreateSubtask,
            ),
            suggestions.first { it.todo.id == 2L }.actions,
        )
        assertTrue(suggestions.all { it.actions.size <= 3 })
        assertTrue(suggestions.all { it.actions.distinct() == it.actions })
    }

    private fun todo(
        id: Long,
        listId: Long,
        title: String = "Task $id",
        note: String = "",
        dueMillis: Long?,
        completed: Boolean = false,
        trashed: Boolean = false,
        parentId: Long? = null,
    ): TodoItem =
        TodoItem(
            id = id,
            listId = listId,
            title = title,
            note = note,
            dueDate = dueMillis?.let { Instant.fromEpochMilliseconds(it) },
            isCompleted = completed,
            flag = false,
            completedAt = if (completed) Instant.fromEpochMilliseconds(0) else null,
            isTrashed = trashed,
            trashedAt = if (trashed) Instant.fromEpochMilliseconds(0) else null,
            parentId = parentId,
            sortPosition = id.toDouble(),
            createdAt = Instant.fromEpochMilliseconds(0),
        )
}
