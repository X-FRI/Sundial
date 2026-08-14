package com.myapplication.shared.effects

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.sync.SyncError
import kotlinx.coroutines.CancellationException

typealias SyncEffect<A> = suspend Raise<SyncError>.() -> A

suspend fun <A> runSyncEffect(effect: SyncEffect<A>): Either<SyncError, A> = either { effect() }

suspend fun <A> Raise<SyncError>.catchTransport(
    fallbackMessage: String,
    block: suspend () -> A,
): A =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        raise(SyncError.Transport(e.message ?: fallbackMessage))
    }

fun TodoError.toSyncError(): SyncError = SyncError.Transport((this as? TodoError.Persistence)?.message ?: "本地读取失败")

fun <A> Raise<SyncError>.bindLocal(effect: Either<TodoError, A>): A = effect.mapLeft { it.toSyncError() }.bind()
