package com.myapplication.shared.domain.recurrence

import com.myapplication.shared.test.FakeTodoRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

class CompleteRecurringTodoUseCaseTest {
    @Test
    fun completingRecurringTodoCreatesNextOccurrence() = runTest {
        val repo = FakeTodoRepository()
        repo.addList("收件箱", "blue")
        repo.insertTodo(1, "喝水", "", Instant.parse("2026-08-13T09:00:00Z"), null, false)
        val todo = repo.todos.first().copy(recurrenceRule = RecurrenceRule.Daily())
        repo.replaceTodo(todo)

        val useCase = CompleteRecurringTodoUseCase(repo, TimeZone.UTC)
        val result = useCase(todo)

        assertTrue(result.isRight())
        assertEquals(true, repo.todos.first { it.id == todo.id }.isCompleted)
        assertEquals(2, repo.todos.size)
        assertEquals("喝水", repo.todos.last().title)
        assertEquals(Instant.parse("2026-08-14T09:00:00Z"), repo.todos.last().dueDate)
        assertEquals(RecurrenceRule.Daily(), repo.todos.last().recurrenceRule)
    }

    @Test
    fun completingRecurringTodoIsNoopWhenAlreadyCompleted() = runTest {
        val repo = FakeTodoRepository()
        repo.addList("收件箱", "blue")
        repo.insertTodo(1, "喝水", "", Instant.parse("2026-08-13T09:00:00Z"), null, false)
        val todo = repo.todos.first().copy(isCompleted = true, recurrenceRule = RecurrenceRule.Daily())
        repo.replaceTodo(todo)

        val useCase = CompleteRecurringTodoUseCase(repo, TimeZone.UTC)
        val result = useCase(todo)

        assertTrue(result.isRight())
        assertEquals(1, repo.todos.size)
    }

    @Test
    fun failedNextOccurrenceInsertDoesNotCompleteOriginal() = runTest {
        val repo = FakeTodoRepository()
        repo.addList("收件箱", "blue")
        repo.insertTodo(1, "喝水", "", Instant.parse("2026-08-13T09:00:00Z"), null, false)
        val todo = repo.todos.first().copy(recurrenceRule = RecurrenceRule.Daily())
        repo.replaceTodo(todo)
        repo.failNextInsert = true

        val useCase = CompleteRecurringTodoUseCase(repo, TimeZone.UTC)
        val result = useCase(todo)

        assertTrue(result.isLeft())
        assertFalse(repo.todos.first { it.id == todo.id }.isCompleted)
        assertEquals(1, repo.todos.size)
    }
}
