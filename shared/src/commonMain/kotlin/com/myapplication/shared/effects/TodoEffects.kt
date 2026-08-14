package com.myapplication.shared.effects

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import com.myapplication.shared.domain.error.TodoError
import kotlinx.coroutines.CancellationException

typealias TodoEffect<A> = suspend Raise<TodoError>.() -> A

suspend fun <A> runTodoEffect(effect: TodoEffect<A>): Either<TodoError, A> = either { effect() }

suspend fun <A> Raise<TodoError>.catchPersistence(
    fallbackMessage: String,
    block: suspend () -> A,
): A =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        raise(TodoError.Persistence(e.message ?: fallbackMessage))
    }
