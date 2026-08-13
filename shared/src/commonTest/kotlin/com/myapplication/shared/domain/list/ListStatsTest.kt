package com.myapplication.shared.domain.list

import com.myapplication.shared.domain.model.TodoItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class ListStatsTest {
    private val tz = TimeZone.UTC
    private val today = LocalDate(2026, 8, 13)

    @Test
    fun countsListPressure() {
        val todos = listOf(
            todo(id = 1, listId = 2, dueMillis = Instant.parse("2026-08-12T09:00:00Z").toEpochMilliseconds()),
            todo(id = 2, listId = 2, dueMillis = Instant.parse("2026-08-13T09:00:00Z").toEpochMilliseconds()),
            todo(id = 3, listId = 2, dueMillis = null),
            todo(id = 4, listId = 2, dueMillis = null, completed = true, completedMillis = Instant.parse("2026-08-13T10:00:00Z").toEpochMilliseconds()),
            todo(id = 5, listId = 3, dueMillis = null),
        )

        val stats = buildListStats(listId = 2, todos = todos, today = today, timeZone = tz)

        assertEquals(3, stats.activeCount)
        assertEquals(1, stats.completedCount)
        assertEquals(1, stats.overdueCount)
        assertEquals(1, stats.todayCount)
        assertEquals(1, stats.noDateCount)
        assertEquals(0, stats.trashedCount)
    }

    @Test
    fun trashedTodosOnlyCountAsTrashed() {
        val todos = listOf(
            todo(id = 1, listId = 2, dueMillis = Instant.parse("2026-08-12T09:00:00Z").toEpochMilliseconds()),
            todo(id = 2, listId = 2, dueMillis = Instant.parse("2026-08-13T09:00:00Z").toEpochMilliseconds(), completed = true),
            todo(id = 3, listId = 2, dueMillis = Instant.parse("2026-08-12T09:00:00Z").toEpochMilliseconds(), trashed = true),
            todo(id = 4, listId = 2, dueMillis = null, completed = true, trashed = true),
        )

        val stats = buildListStats(listId = 2, todos = todos, today = today, timeZone = tz)

        assertEquals(1, stats.activeCount)
        assertEquals(1, stats.completedCount)
        assertEquals(1, stats.overdueCount)
        assertEquals(0, stats.todayCount)
        assertEquals(0, stats.noDateCount)
        assertEquals(2, stats.trashedCount)
    }

    @Test
    fun dueDatesUseProvidedTimezoneForLocalDayBuckets() {
        val shanghai = TimeZone.of("Asia/Shanghai")
        val todos = listOf(
            todo(id = 1, listId = 2, dueMillis = Instant.parse("2026-08-12T16:30:00Z").toEpochMilliseconds()),
        )

        val stats = buildListStats(listId = 2, todos = todos, today = today, timeZone = shanghai)

        assertEquals(0, stats.overdueCount)
        assertEquals(1, stats.todayCount)
    }

    private fun todo(
        id: Long,
        listId: Long,
        dueMillis: Long?,
        completed: Boolean = false,
        completedMillis: Long? = null,
        trashed: Boolean = false,
        trashedMillis: Long? = null,
    ): TodoItem =
        TodoItem(
            id = id,
            listId = listId,
            title = "Task $id",
            note = "",
            dueDate = dueMillis?.let { Instant.fromEpochMilliseconds(it) },
            isCompleted = completed,
            flag = false,
            completedAt = completedMillis?.let { Instant.fromEpochMilliseconds(it) },
            isTrashed = trashed,
            trashedAt = trashedMillis?.let { Instant.fromEpochMilliseconds(it) },
            parentId = null,
            sortPosition = 0.0,
            createdAt = Instant.fromEpochMilliseconds(0),
        )
}
