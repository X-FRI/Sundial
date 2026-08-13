package com.myapplication.shared.domain.analytics

import com.myapplication.shared.domain.model.TodoItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class ListAnalyticsModelTest {
    private val today = LocalDate(2026, 8, 13)
    private val tz = TimeZone.UTC

    @Test
    fun buildsCompletionSeriesForOnlyTheRequestedList() {
        val model = buildListAnalyticsModel(
            listId = 2,
            todos = listOf(
                todo(id = 1, listId = 2, completedAt = "2026-08-12T10:00:00Z"),
                todo(id = 2, listId = 2, completedAt = "2026-08-13T10:00:00Z"),
                todo(id = 3, listId = 3, completedAt = "2026-08-13T10:00:00Z"),
            ),
            today = today,
            range = AnalyticsRange.Week,
            timeZone = tz,
        )

        assertEquals(2, model.listId)
        assertEquals(2, model.completedTotal)
        assertEquals("完成趋势", model.completion.title)
        assertEquals(listOf(0, 0, 0, 0, 0, 1, 1), model.completion.points.map { it.value })
        assertEquals("8/12", model.completion.points[5].label)
        assertEquals("今天", model.completion.points[6].label)
    }

    @Test
    fun ignoresTrashedIncompleteAndNullCompletedAtTodos() {
        val model = buildListAnalyticsModel(
            listId = 2,
            todos = listOf(
                todo(id = 1, listId = 2, completedAt = "2026-08-13T10:00:00Z", trashed = true),
                todo(id = 2, listId = 2, completedAt = "2026-08-13T11:00:00Z", completed = false),
                todo(id = 3, listId = 2, completedAt = null, completed = true),
                todo(id = 4, listId = 2, completedAt = "2026-08-13T12:00:00Z"),
            ),
            today = today,
            range = AnalyticsRange.Week,
            timeZone = tz,
        )

        assertEquals(1, model.completedTotal)
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 1), model.completion.points.map { it.value })
    }

    @Test
    fun monthRangeProducesThirtyPoints() {
        val model = buildListAnalyticsModel(
            listId = 2,
            todos = emptyList(),
            today = today,
            range = AnalyticsRange.Month,
            timeZone = tz,
        )

        assertEquals(30, model.completion.points.size)
        assertEquals(30, model.energy.points.size)
    }

    @Test
    fun energySeriesScoresBaseNoteAndFlagPerDay() {
        val model = buildListAnalyticsModel(
            listId = 2,
            todos = listOf(
                todo(id = 1, listId = 2, completedAt = "2026-08-13T10:00:00Z"),
                todo(id = 2, listId = 2, completedAt = "2026-08-13T11:00:00Z", note = "deep"),
                todo(id = 3, listId = 2, completedAt = "2026-08-13T12:00:00Z", flag = true),
                todo(id = 4, listId = 2, completedAt = "2026-08-12T10:00:00Z", note = "deep", flag = true),
            ),
            today = today,
            range = AnalyticsRange.Week,
            timeZone = tz,
        )

        assertEquals("精力输出", model.energy.title)
        assertEquals(listOf(0, 0, 0, 0, 0, 3, 5), model.energy.points.map { it.value })
    }

    private fun todo(
        id: Long,
        listId: Long,
        completedAt: String?,
        note: String = "",
        flag: Boolean = false,
        trashed: Boolean = false,
        completed: Boolean = true,
    ): TodoItem =
        TodoItem(
            id = id,
            listId = listId,
            title = "Task $id",
            note = note,
            dueDate = null,
            isCompleted = completed,
            flag = flag,
            completedAt = completedAt?.let { Instant.parse(it) },
            isTrashed = trashed,
            trashedAt = if (trashed) Instant.parse("2026-08-13T12:00:00Z") else null,
            parentId = null,
            sortPosition = id.toDouble(),
            createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        )
}
