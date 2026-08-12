package com.myapplication.shared.ui.ledger

import com.myapplication.shared.domain.model.TodoItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

class LedgerUiModelsTest {
    private val tz = TimeZone.UTC

    @Test
    fun rhythmPicksNextUncompletedDueItemToday() {
        val todos = listOf(
            item(id = 1, title = "已完成", due = "2026-08-12T08:00:00Z", completed = true),
            item(id = 2, title = "下一件", due = "2026-08-12T09:00:00Z", completed = false),
            item(id = 3, title = "明天", due = "2026-08-13T09:00:00Z", completed = false),
        )

        val state = buildTodayRhythmState(
            todos = todos,
            now = Instant.parse("2026-08-12T08:42:00Z"),
            timeZone = tz,
        )

        assertEquals("08:42", state.nowLabel)
        assertEquals("09:00", state.nextDueLabel)
        assertEquals("下一件", state.nextTitle)
        assertEquals(1, state.completedTodayCount)
        assertEquals(1, state.pendingTodayCount)
    }

    @Test
    fun rhythmHasNoNextWhenAllTodayItemsAreCompleted() {
        val todos = listOf(
            item(id = 1, title = "done", due = "2026-08-12T08:00:00Z", completed = true),
        )

        val state = buildTodayRhythmState(
            todos = todos,
            now = Instant.parse("2026-08-12T12:00:00Z"),
            timeZone = tz,
        )

        assertNull(state.nextDueLabel)
        assertNull(state.nextTitle)
        assertEquals(1, state.completedTodayCount)
        assertEquals(0, state.pendingTodayCount)
    }

    @Test
    fun groupingSeparatesActiveAndCompletedParentTasks() {
        val todos = listOf(
            item(id = 1, title = "active", due = "2026-08-12T09:00:00Z", completed = false),
            item(id = 2, title = "done", due = "2026-08-12T10:00:00Z", completed = true),
            item(id = 3, title = "child", due = "2026-08-12T11:00:00Z", completed = false, parentId = 1),
        )

        val groups = buildTaskGroups(todos)

        assertEquals(listOf(1L), groups.active.map { it.item.id })
        assertEquals(listOf(3L), groups.active.first().subtasks.map { it.id })
        assertEquals(listOf(2L), groups.completed.map { it.item.id })
    }

    private fun item(
        id: Long,
        title: String,
        due: String,
        completed: Boolean,
        parentId: Long? = null,
    ): TodoItem = TodoItem(
        id = id,
        listId = 1,
        title = title,
        note = "",
        dueDate = Instant.parse(due),
        isCompleted = completed,
        flag = false,
        completedAt = if (completed) Instant.parse(due) else null,
        isTrashed = false,
        trashedAt = null,
        parentId = parentId,
        sortPosition = id.toDouble(),
        createdAt = Instant.parse("2026-08-12T00:00:00Z"),
    )
}
