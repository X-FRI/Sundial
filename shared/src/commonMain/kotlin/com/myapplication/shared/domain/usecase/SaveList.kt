package com.myapplication.shared.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.repository.ListCommands

class SaveListUseCase(
    private val lists: ListCommands,
) {
    suspend fun add(
        name: String,
        colorKey: String,
    ): Either<TodoError, Unit> =
        either {
            val trimmedName = name.trim()
            ensure(trimmedName.isNotEmpty()) { TodoError.EmptyTitle }
            lists.addList(trimmedName, colorKey.trim()).bind()
        }

    suspend fun update(
        listId: Long,
        name: String,
        colorKey: String,
    ): Either<TodoError, Unit> =
        either {
            val trimmedName = name.trim()
            ensure(trimmedName.isNotEmpty()) { TodoError.EmptyTitle }
            lists.updateList(listId, trimmedName, colorKey.trim()).bind()
        }
}
