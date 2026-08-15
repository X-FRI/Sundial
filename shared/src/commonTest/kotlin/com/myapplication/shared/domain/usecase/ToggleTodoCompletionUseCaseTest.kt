package com.myapplication.shared.domain.usecase

import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.recurrence.RecurrenceRule
import com.myapplication.shared.test.FakeTodoRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class ToggleTodoCompletionUseCaseTest {
    @Test
    fun incompleteRecurringTodoCreatesNextOccurrence() =
        runTest {
            val repo = FakeTodoRepository()
            repo.ensureInbox()
            val useCase = ToggleTodoCompletionUseCase(repo)
            repo.insertTodo(1, "站会", "", Instant.parse("2026-08-13T09:00:00Z"), null, false, RecurrenceRule.Daily())
            val item = repo.todos.first()

            useCase(item)

            assertEquals(true, repo.todos.first { it.id == item.id }.isCompleted)
            assertEquals(2, repo.todos.size)
            assertEquals(Instant.parse("2026-08-14T09:00:00Z"), repo.todos.last().dueDate)
            assertEquals(RecurrenceRule.Daily(), repo.todos.last().recurrenceRule)
        }

    @Test
    fun completedRecurringTodoUsesNormalToggle() =
        runTest {
            val repo = FakeTodoRepository()
            val useCase = ToggleTodoCompletionUseCase(repo)
            val item =
                TodoItem(
                    7,
                    1,
                    "站会",
                    "",
                    Instant.parse("2026-08-13T09:00:00Z"),
                    true,
                    false,
                    null,
                    false,
                    null,
                    null,
                    0.0,
                    Instant.fromEpochMilliseconds(0),
                    RecurrenceRule.Daily(),
                )

            useCase(item)

            assertEquals(7L, repo.toggledId)
            assertEquals(false, repo.toggledValue)
            assertEquals(0, repo.todos.size)
        }
}
