package com.myapplication.shared.domain.organize

import com.myapplication.shared.domain.model.TodoItem

data class OrganizationSuggestion(
    val todo: TodoItem,
    val reasons: Set<OrganizationReason>,
    val actions: List<OrganizationAction>,
)

enum class OrganizationAction {
    ScheduleToday,
    ScheduleTomorrow,
    MoveToList,
    CreateSubtask,
    Trash,
}
