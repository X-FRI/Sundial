package com.myapplication.shared.ui.settings

import com.myapplication.shared.domain.model.TodoList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.Instant

class ListSettingsSelectionTest {
    @Test
    fun keepsTheSelectedListWhenItStillExists() {
        val inbox = list(id = 1, name = "收件箱", position = 0)
        val work = list(id = 2, name = "工作", position = 1)

        val selected = resolveSelectedList(listOf(inbox, work), selectedListId = 2)

        assertEquals(work, selected)
    }

    @Test
    fun fallsBackToInboxWhenSelectionIsMissing() {
        val inbox = list(id = 1, name = "收件箱", position = 0)
        val work = list(id = 2, name = "工作", position = 1)

        val selected = resolveSelectedList(listOf(inbox, work), selectedListId = 99)

        assertEquals(inbox, selected)
    }

    private fun list(id: Long, name: String, position: Int): TodoList =
        TodoList(
            id = id,
            name = name,
            colorKey = "blue",
            position = position,
            createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        )
}
