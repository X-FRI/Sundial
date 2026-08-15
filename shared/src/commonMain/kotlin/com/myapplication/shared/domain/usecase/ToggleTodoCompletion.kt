package com.myapplication.shared.domain.usecase

import arrow.core.Either
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.repository.TodoCommands

class ToggleTodoCompletionUseCase(
    private val commands: TodoCommands,
) {
    suspend operator fun invoke(item: TodoItem): Either<TodoError, Unit> =
        if (!item.isCompleted && item.recurrenceRule != null) {
            commands.completeRecurringTodo(item.id)
        } else {
            commands.setCompleted(item.id, !item.isCompleted)
        }
}
