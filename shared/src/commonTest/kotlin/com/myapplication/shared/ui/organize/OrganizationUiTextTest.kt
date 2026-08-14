package com.myapplication.shared.ui.organize

import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.organize.OrganizationAction
import com.myapplication.shared.domain.organize.OrganizationReason
import com.myapplication.shared.domain.organize.OrganizationSuggestion
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class OrganizationUiTextTest {
    @Test
    fun visibleSuggestionsAreLimitedToFiveHighValueItems() {
        val suggestions =
            (1L..7L).map { id ->
                OrganizationSuggestion(
                    todo = todo(id),
                    reasons = setOf(OrganizationReason.Inbox, OrganizationReason.NoDate),
                    actions = listOf(OrganizationAction.ScheduleToday),
                )
            }

        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), suggestions.visibleOrganizationSuggestions().map { it.todo.id })
    }

    @Test
    fun visibleSuggestionsSkipSingleReasonItemsHandledByWorkbenchSections() {
        val suggestions =
            listOf(
                OrganizationSuggestion(todo(1), setOf(OrganizationReason.NoDate), listOf(OrganizationAction.ScheduleToday)),
                OrganizationSuggestion(todo(2), setOf(OrganizationReason.Overdue), listOf(OrganizationAction.ScheduleToday)),
                OrganizationSuggestion(
                    todo(3),
                    setOf(OrganizationReason.Inbox, OrganizationReason.NoDate),
                    listOf(OrganizationAction.MoveToList),
                ),
            )

        assertEquals(listOf(3L), suggestions.visibleOrganizationSuggestions().map { it.todo.id })
    }

    @Test
    fun visibleSuggestionsKeepLongTitleButHideInboxAfterDateIsArranged() {
        val suggestions =
            listOf(
                OrganizationSuggestion(todo(1), setOf(OrganizationReason.Inbox), listOf(OrganizationAction.MoveToList)),
                OrganizationSuggestion(todo(2), setOf(OrganizationReason.LongTitle), listOf(OrganizationAction.EditTitle)),
            )

        assertEquals(listOf(2L), suggestions.visibleOrganizationSuggestions().map { it.todo.id })
    }

    @Test
    fun reasonsAndActionsUseChineseLabelsInsteadOfEnumNames() {
        val reasonText =
            organizationReasonText(
                setOf(
                    OrganizationReason.Inbox,
                    OrganizationReason.NoDate,
                    OrganizationReason.LongTitle,
                ),
            )
        val actionLabels =
            listOf(
                OrganizationAction.ScheduleToday,
                OrganizationAction.ScheduleTomorrow,
                OrganizationAction.MoveToList,
                OrganizationAction.EditTitle,
                OrganizationAction.Trash,
            ).map { it.organizationActionLabel() }

        assertEquals("待归类 · 无日期 · 标题过长", reasonText)
        assertEquals(listOf("安排今天", "安排明天", "移动列表", "编辑标题", "删除"), actionLabels)
        assertFalse(reasonText.contains("Inbox"))
        assertFalse(actionLabels.any { label -> OrganizationAction.entries.any { label == it.name } })
    }

    private fun todo(id: Long): TodoItem =
        TodoItem(
            id = id,
            listId = 1,
            title = "Task $id",
            note = "",
            dueDate = null,
            isCompleted = false,
            flag = false,
            completedAt = null,
            isTrashed = false,
            trashedAt = null,
            parentId = null,
            sortPosition = id.toDouble(),
            createdAt = Instant.fromEpochMilliseconds(0),
        )
}
