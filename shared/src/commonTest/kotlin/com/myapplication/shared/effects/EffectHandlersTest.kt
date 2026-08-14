package com.myapplication.shared.effects

import arrow.core.Either
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.sync.SyncError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EffectHandlersTest {
    @Test
    fun todoEffectMapsUnexpectedExceptionToPersistenceError() =
        runTest {
            val result =
                runTodoEffect {
                    catchPersistence("fallback") { error("boom") }
                }

            assertEquals(TodoError.Persistence("boom"), result.leftOrNull())
        }

    @Test
    fun todoEffectRethrowsCancellation() =
        runTest {
            assertFailsWith<CancellationException> {
                runTodoEffect {
                    catchPersistence("fallback") { throw CancellationException("cancel") }
                }
            }
        }

    @Test
    fun syncEffectMapsUnexpectedExceptionToTransportError() =
        runTest {
            val result =
                runSyncEffect {
                    catchTransport("fallback") { error("network boom") }
                }

            assertEquals(SyncError.Transport("network boom"), result.leftOrNull())
        }

    @Test
    fun syncEffectMapsLocalTodoErrorToTransportError() =
        runTest {
            val result =
                runSyncEffect {
                    bindLocal(Either.Left(TodoError.Persistence("db down")))
                }

            assertEquals(SyncError.Transport("db down"), result.leftOrNull())
        }

    @Test
    fun syncEffectRethrowsCancellation() =
        runTest {
            assertFailsWith<CancellationException> {
                runSyncEffect {
                    catchTransport("fallback") { throw CancellationException("cancel") }
                }
            }
        }

    @Test
    fun syncEffectCanRaiseTypedErrorDirectly() =
        runTest {
            val result =
                runSyncEffect<String> {
                    raise(SyncError.Transport("typed"))
                }

            assertTrue(result.isLeft())
            assertEquals(SyncError.Transport("typed"), result.leftOrNull())
        }
}
