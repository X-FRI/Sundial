package com.myapplication.shared.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.repository.TodoRepository

class AddSubTaskUseCase(private val repository: TodoRepository) {

    suspend operator fun invoke(parentId: Long, title: String): Either<TodoError, Unit> = either {
        ensure(title.isNotBlank()) { TodoError.EmptyTitle }
        val parent = repository.findById(parentId).bind()
            ?: raise(TodoError.ParentNotFound)
        repository.insertTodo(parent.listId, title.trim(), "", null, parentId, false).bind()
    }
}
