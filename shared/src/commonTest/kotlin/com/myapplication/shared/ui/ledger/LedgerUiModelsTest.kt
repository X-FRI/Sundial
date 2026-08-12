package com.myapplication.shared.ui.ledger

import com.myapplication.shared.domain.model.TodoItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus

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

    @Test
    fun trashGroupsKeepsOnlyTrashedSortedByTrashedAtDesc() {
        val todos = listOf(
            item(id = 1, title = "old", due = "2026-08-12T09:00:00Z", completed = false, isTrashed = true, trashedAt = "2026-08-12T09:00:00Z"),
            item(id = 2, title = "active", due = "2026-08-12T10:00:00Z", completed = false),
            item(id = 3, title = "recent", due = "2026-08-12T11:00:00Z", completed = false, isTrashed = true, trashedAt = "2026-08-12T10:00:00Z"),
        )

        val groups = buildTrashGroups(todos)

        assertEquals(listOf(3L, 1L), groups.map { it.item.id })
        assert(groups.all { it.subtasks.isEmpty() })
    }

    @Test
    fun timelineSeparatesPastUpcomingAndUnscheduledWork() {
        val todos = listOf(
            item(id = 1, title = "missed", due = "2026-08-12T08:30:00Z", completed = false),
            item(id = 2, title = "next", due = "2026-08-12T13:00:00Z", completed = false),
            item(id = 3, title = "loose", due = null, completed = false),
            item(id = 4, title = "done", due = "2026-08-12T09:00:00Z", completed = true),
            item(id = 5, title = "tomorrow", due = "2026-08-13T09:00:00Z", completed = false),
        )

        val timeline = buildTodayTimelineState(
            todos = todos,
            now = Instant.parse("2026-08-12T12:00:00Z"),
            timeZone = tz,
        )

        assertEquals("06:00", timeline.startLabel)
        assertEquals("24:00", timeline.endLabel)
        assertEquals(listOf(1L), timeline.past.map { it.item.id })
        assertEquals(listOf(2L), timeline.upcoming.map { it.item.id })
        assertEquals(listOf(3L), timeline.unscheduled.map { it.item.id })
        assertEquals(1, timeline.completedTodayCount)
        assertEquals(1, timeline.futureCount)
    }

    @Test
    fun timelineCalculatesCurrentTimeProgressThroughDay() {
        val timeline = buildTodayTimelineState(
            todos = emptyList(),
            now = Instant.parse("2026-08-12T12:00:00Z"),
            timeZone = tz,
        )

        assertTrue(timeline.nowProgress in 0.33f..0.34f)
    }

    @Test
    fun workbenchTimelineShowsGlobalDistribution() {
        val todos = listOf(
            item(id = 1, title = "overdue", due = "2026-08-11T09:00:00Z", completed = false),
            item(id = 2, title = "today", due = "2026-08-12T09:00:00Z", completed = false),
            item(id = 3, title = "future", due = "2026-08-15T09:00:00Z", completed = false),
            item(id = 4, title = "loose", due = null, completed = false),
            item(id = 5, title = "inbox", due = null, completed = false, listId = 9),
            item(id = 6, title = "done", due = "2026-08-12T09:00:00Z", completed = true),
        )

        val state = buildContextTimelineState(
            todos = todos,
            scope = TimelineScope.Workbench,
            now = Instant.parse("2026-08-12T12:00:00Z"),
            timeZone = tz,
            inboxListId = 9,
        )

        assertEquals("工作台时间线", state.title)
        assertEquals(listOf("逾期" to 1, "今天" to 1, "未来 7 天" to 1, "无日期" to 2, "待整理" to 1), state.segments.map { it.label to it.count })
    }

    @Test
    fun scheduledTimelineBucketsFutureDates() {
        val now = Instant.parse("2026-08-12T12:00:00Z")
        val todayStart = Instant.parse("2026-08-12T00:00:00Z")
        val todos = listOf(
            item(id = 1, title = "today", due = "2026-08-12T09:00:00Z", completed = false),
            item(id = 2, title = "tomorrow", due = "2026-08-13T09:00:00Z", completed = false),
            item(id = 3, title = "week", due = "2026-08-16T09:00:00Z", completed = false),
            item(id = 4, title = "later", due = todayStart.plus(12, DateTimeUnit.DAY, tz).toString(), completed = false),
        )

        val state = buildContextTimelineState(todos, TimelineScope.Scheduled, now, tz, inboxListId = null)

        assertEquals(listOf("今天" to 1, "明天" to 1, "本周" to 1, "以后" to 1), state.segments.map { it.label to it.count })
    }

    @Test
    fun inboxTimelineTreatsInboxAsTriagePool() {
        val todos = listOf(
            item(id = 1, title = "dated", due = "2026-08-12T09:00:00Z", completed = false, listId = 9),
            item(id = 2, title = "loose", due = null, completed = false, listId = 9),
            item(id = 3, title = "overdue", due = "2026-08-11T09:00:00Z", completed = false, listId = 9),
            item(id = 4, title = "other", due = null, completed = false, listId = 1),
        )

        val state = buildContextTimelineState(
            todos = todos,
            scope = TimelineScope.List(listId = 9, isInbox = true),
            now = Instant.parse("2026-08-12T12:00:00Z"),
            timeZone = tz,
            inboxListId = 9,
        )

        assertEquals("收件箱 · 待整理", state.title)
        assertEquals(listOf("待整理" to 3, "已有日期" to 2, "无日期" to 1, "逾期" to 1), state.segments.map { it.label to it.count })
    }

    private fun item(
        id: Long,
        title: String,
        due: String?,
        completed: Boolean,
        parentId: Long? = null,
        isTrashed: Boolean = false,
        trashedAt: String? = null,
        listId: Long = 1,
    ): TodoItem = TodoItem(
        id = id,
        listId = listId,
        title = title,
        note = "",
        dueDate = due?.let { Instant.parse(it) },
        isCompleted = completed,
        flag = false,
        completedAt = if (completed) due?.let { Instant.parse(it) } else null,
        isTrashed = isTrashed,
        trashedAt = trashedAt?.let { Instant.parse(it) },
        parentId = parentId,
        sortPosition = id.toDouble(),
        createdAt = Instant.parse("2026-08-12T00:00:00Z"),
    )
}
