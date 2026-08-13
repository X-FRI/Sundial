package com.myapplication.shared.domain.widget

import com.myapplication.shared.domain.model.TodoItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TodayWidgetSnapshotTest {
    private val zone = TimeZone.UTC
    private val now = Instant.parse("2026-08-13T08:00:00Z")
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
    }

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

    @Test
    fun emptySnapshotUsesNowAndZeroCounts() {
        val snapshot = TodayWidgetSnapshot.empty(now)

        assertEquals("今天", snapshot.dateLabel)
        assertEquals(0, snapshot.pendingTodayCount)
        assertEquals(0, snapshot.completedTodayCount)
        assertEquals(null, snapshot.nextTaskTitle)
        assertEquals(null, snapshot.nextTaskDueLabel)
        assertEquals(emptyList(), snapshot.topTodayTasks)
        assertEquals(emptyList(), snapshot.topOverdueTasks)
        assertEquals(0, snapshot.overdueCount)
        assertEquals(0, snapshot.inboxCount)
        assertEquals(now, snapshot.lastUpdatedAt)
    }

    @Test
    fun snapshotSerializationRoundTrips() {
        val snapshot = buildTodayWidgetSnapshot(
            todos = listOf(
                todo(1, "今天 9 点", due = "2026-08-13T09:00:00Z", listId = 10),
                todo(2, "逾期", due = "2026-08-12T10:00:00Z", listId = 10),
            ),
            now = now,
            timeZone = zone,
            inboxListId = 99,
            maxTasks = 5,
        )

        val decoded = json.decodeFromString<TodayWidgetSnapshot>(json.encodeToString(snapshot))

        assertEquals(snapshot, decoded)
    }

    @Test
    fun legacySnapshotWithoutTopOverdueTasksDecodesWithEmptyList() {
        val decoded = json.decodeFromString<TodayWidgetSnapshot>(
            """
            {
              "dateLabel": "2026-08-13",
              "pendingTodayCount": 1,
              "completedTodayCount": 0,
              "nextTaskTitle": "今天 9 点",
              "nextTaskDueLabel": "09:00",
              "topTodayTasks": [
                {
                  "id": 1,
                  "title": "今天 9 点",
                  "dueLabel": "09:00",
                  "isFlagged": true
                }
              ],
              "overdueCount": 1,
              "inboxCount": 0,
              "lastUpdatedAt": "2026-08-13T08:00:00Z"
            }
            """.trimIndent(),
        )

        assertEquals(emptyList(), decoded.topOverdueTasks)
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
