package com.myapplication.shared.domain.recurrence

import arrow.core.Either
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.repository.TodoRepository
import kotlinx.datetime.TimeZone

class CompleteRecurringTodoUseCase(
    private val repository: TodoRepository,
    @Suppress("unused") private val timeZone: TimeZone,
) {
    suspend operator fun invoke(todo: TodoItem): Either<TodoError, Unit> = repository.completeRecurringTodo(todo.id)
}
