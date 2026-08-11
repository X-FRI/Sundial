package com.myapplication.shared.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.repository.TodoRepository
import kotlinx.datetime.Instant

data class AddTodoInput(
    val listId: Long?,
    val parentId: Long?,
    val title: String,
    val note: String,
    val dueDate: Instant?,
    val flag: Boolean,
)

class AddTodoUseCase(private val repository: TodoRepository) {

    suspend operator fun invoke(input: AddTodoInput): Either<TodoError, Unit> = either {
        ensure(input.title.isNotBlank()) { TodoError.EmptyTitle }
        val targetListId = when {
            input.parentId != null ->
                repository.findById(input.parentId).bind()?.listId
                    ?: raise(TodoError.ParentNotFound)
            input.listId != null -> input.listId
            else -> repository.ensureInbox().bind()
        }
        repository.insertTodo(
            listId = targetListId,
            title = input.title.trim(),
            note = input.note.trim(),
            dueDate = input.dueDate,
            parentId = input.parentId,
            flag = input.flag,
        ).bind()
    }
}
