package com.myapplication.shared.domain.widget

import com.myapplication.shared.domain.model.TodoItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

class TodayWidgetSnapshotTest {
    private val tz = TimeZone.UTC

    @Test
    fun snapshotPicksTodayCountsAndNextTask() {
        val now = Instant.parse("2026-08-12T08:30:00Z")
        val todos = listOf(
            item(1, "逾期", "2026-08-11T09:00:00Z", completed = false),
            item(2, "下一件", "2026-08-12T09:00:00Z", completed = false),
            item(3, "晚点", "2026-08-12T18:00:00Z", completed = false),
            item(4, "完成", "2026-08-12T07:00:00Z", completed = true),
            item(5, "明天", "2026-08-13T09:00:00Z", completed = false),
            item(6, "待整理", null, completed = false, listId = 9),
        )

        val snapshot = buildTodayWidgetSnapshot(
            todos = todos,
            now = now,
            timeZone = tz,
            inboxListId = 9,
            maxTasks = 3,
        )

        assertEquals("2026-08-12", snapshot.dateLabel)
        assertEquals(2, snapshot.pendingTodayCount)
        assertEquals(1, snapshot.completedTodayCount)
        assertEquals("下一件", snapshot.nextTaskTitle)
        assertEquals("09:00", snapshot.nextTaskDueLabel)
        assertEquals(listOf("下一件", "晚点"), snapshot.topTodayTasks.map { it.title })
        assertEquals(1, snapshot.overdueCount)
        assertEquals(1, snapshot.inboxCount)
        assertEquals(now, snapshot.lastUpdatedAt)
    }

    private fun item(
        id: Long,
        title: String,
        due: String?,
        completed: Boolean,
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
        isTrashed = false,
        trashedAt = null,
        parentId = null,
        sortPosition = id.toDouble(),
        createdAt = Instant.parse("2026-08-12T00:00:00Z"),
    )
}
