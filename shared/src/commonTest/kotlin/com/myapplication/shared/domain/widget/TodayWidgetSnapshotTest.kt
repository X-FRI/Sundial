package com.myapplication.shared.domain.widget

import com.myapplication.shared.domain.model.TodoItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

class TodayWidgetSnapshotTest {
    private val zone = TimeZone.UTC
    private val now = Instant.parse("2026-08-13T08:00:00Z")

    @Test
    fun snapshotSeparatesTodayOverdueAndInboxWork() {
        val snapshot = buildTodayWidgetSnapshot(
            todos = listOf(
                todo(1, "今天 9 点", due = "2026-08-13T09:00:00Z", listId = 10),
                todo(2, "逾期", due = "2026-08-12T10:00:00Z", listId = 10),
                todo(3, "待整理", due = null, listId = 99),
                todo(4, "已完成", due = "2026-08-13T07:00:00Z", completedAt = "2026-08-13T07:30:00Z", listId = 10),
            ),
            now = now,
            timeZone = zone,
            inboxListId = 99,
            maxTasks = 5,
        )

        assertEquals(1, snapshot.pendingTodayCount)
        assertEquals(1, snapshot.overdueCount)
        assertEquals(1, snapshot.inboxCount)
        assertEquals(1, snapshot.completedTodayCount)
        assertEquals(listOf("今天 9 点"), snapshot.topTodayTasks.map { it.title })
        assertEquals(listOf("逾期"), snapshot.topOverdueTasks.map { it.title })
        assertTrue(snapshot.topTodayTasks.first().isFlagged)
    }

    @Test
    fun widgetSizePolicyControlsVisibleTaskCounts() {
        assertEquals(1, WidgetSnapshotSize.Small.maxTodayTasks)
        assertEquals(0, WidgetSnapshotSize.Small.maxOverdueTasks)
        assertEquals(3, WidgetSnapshotSize.Medium.maxTodayTasks)
        assertEquals(1, WidgetSnapshotSize.Medium.maxOverdueTasks)
        assertEquals(6, WidgetSnapshotSize.Large.maxTodayTasks)
        assertEquals(3, WidgetSnapshotSize.Large.maxOverdueTasks)
    }

    private fun todo(
        id: Long,
        title: String,
        due: String?,
        listId: Long,
        completedAt: String? = null,
    ): TodoItem = TodoItem(
        id = id,
        listId = listId,
        title = title,
        note = "",
        dueDate = due?.let(Instant::parse),
        isCompleted = completedAt != null,
        flag = id == 1L,
        completedAt = completedAt?.let(Instant::parse),
        isTrashed = false,
        trashedAt = null,
        parentId = null,
        sortPosition = id.toDouble(),
        createdAt = Instant.parse("2026-08-01T00:00:00Z"),
    )
}
