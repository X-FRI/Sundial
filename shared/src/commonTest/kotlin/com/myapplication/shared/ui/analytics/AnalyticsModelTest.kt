package com.myapplication.shared.ui.analytics

import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.model.TodoList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class AnalyticsModelTest {
    private val today = LocalDate(2026, 8, 12)
    private val tz = TimeZone.UTC

    @Test
    fun modelSummarizesCompletionEnergyAndPressure() {
        val todos = listOf(
            item(id = 1, title = "today done", due = "2026-08-12T09:00:00Z", completedAt = "2026-08-12T10:00:00Z", note = "deep", flag = true),
            item(id = 2, title = "yesterday done", due = "2026-08-11T09:00:00Z", completedAt = "2026-08-11T10:00:00Z"),
            item(id = 3, title = "child done", due = null, completedAt = "2026-08-12T11:00:00Z", parentId = 1),
            item(id = 4, title = "overdue", due = "2026-08-10T09:00:00Z"),
            item(id = 5, title = "today pending", due = "2026-08-12T13:00:00Z"),
            item(id = 6, title = "future", due = "2026-08-18T09:00:00Z"),
            item(id = 7, title = "loose", due = null),
            item(id = 8, title = "trashed", due = "2026-08-12T14:00:00Z", trashed = true),
        )

        val model = buildAnalyticsModel(
            todos = todos,
            lists = listOf(TodoList(1, "收件箱", "blue", 0, Instant.parse("2026-08-01T00:00:00Z"))),
            today = today,
            timeZone = tz,
        )

        assertEquals(1, model.completedToday)
        assertEquals(2, model.streakDays)
        assertEquals(5, model.weekEnergy)
        assertEquals(33, model.completionRate)
        assertEquals(listOf(1, 1, 1, 1), model.pressure.map { it.count })
        assertEquals(1, model.deepWorkCount)
        assertEquals(1, model.quickWinCount)
        assertEquals(1, model.flaggedCompletedCount)
        assertEquals("今天已经推进 1 件，连续 2 天有完成记录。节奏正在形成。", model.encouragement)
    }

    @Test
    fun emptyModelEncouragesStartingSmall() {
        val model = buildAnalyticsModel(
            todos = emptyList(),
            lists = emptyList(),
            today = today,
            timeZone = tz,
        )

        assertEquals(0, model.completedToday)
        assertEquals(0, model.streakDays)
        assertEquals(0, model.weekEnergy)
        assertEquals(0, model.completionRate)
        assertEquals(listOf(0, 0, 0, 0), model.pressure.map { it.count })
        assertEquals("先完成一件足够小的待办，让今天的曲线开始出现。", model.encouragement)
    }

    private fun item(
        id: Long,
        title: String,
        due: String?,
        completedAt: String? = null,
        note: String = "",
        flag: Boolean = false,
        parentId: Long? = null,
        trashed: Boolean = false,
    ): TodoItem =
        TodoItem(
            id = id,
            listId = 1,
            title = title,
            note = note,
            dueDate = due?.let { Instant.parse(it) },
            isCompleted = completedAt != null,
            flag = flag,
            completedAt = completedAt?.let { Instant.parse(it) },
            isTrashed = trashed,
            trashedAt = if (trashed) Instant.parse("2026-08-12T12:00:00Z") else null,
            parentId = parentId,
            sortPosition = id.toDouble(),
            createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        )
}
