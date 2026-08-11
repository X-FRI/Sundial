package com.myapplication.shared.domain.sync

import arrow.core.Either
import com.myapplication.shared.test.FakeTodoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FakeSyncClient : SyncClient {
    val pushed = mutableListOf<List<SyncRow>>()
    var failPush = false
    val remote = MutableStateFlow<List<SyncRow>>(emptyList())

    override suspend fun push(rows: List<SyncRow>): Either<SyncError, Unit> {
        if (failPush) return Either.Left(SyncError.Transport("network down"))
        pushed += rows
        return Either.Right(Unit)
    }

    override fun observeRemoteChanges(): Flow<SyncRow> =
        flow { remote.value.forEach { emit(it) } }

    override suspend fun close() = Unit
}

class SyncCoordinatorTest {

    private fun row(
        seq: Long = 1,
        table: String = "todo",
        rowId: Long = 1,
        action: SyncAction = SyncAction.UPSERT,
        payload: String? = null,
        updatedAt: Long = 100,
        updatedBy: String = "device-b",
    ) = SyncRow(seq, table, rowId, action, payload, updatedAt, updatedBy)

    @Test
    fun drainPushesRowsAndClearsOutbox() = runTest {
        val repo = FakeTodoRepository()
        repo.outboxState.value = listOf(row(seq = 1, rowId = 10), row(seq = 2, rowId = 11))
        val client = FakeSyncClient()
        val coordinator = SyncCoordinator(repo, client, deviceId = "device-a")
        val result = coordinator.drainOutbox()
        assertTrue(result.isRight())
        assertEquals(2, result.getOrNull())
        assertEquals(listOf(10L, 11L), client.pushed.single().map { it.rowId })
        assertTrue(repo.outboxState.value.isEmpty())
    }

    @Test
    fun drainKeepsOutboxWhenPushFails() = runTest {
        val repo = FakeTodoRepository()
        repo.outboxState.value = listOf(row(seq = 1))
        val client = FakeSyncClient().apply { failPush = true }
        val coordinator = SyncCoordinator(repo, client, "device-a")
        val result = coordinator.drainOutbox()
        assertEquals(SyncError.Transport("network down"), result.leftOrNull())
        assertEquals(1, repo.outboxState.value.size)
    }

    @Test
    fun applyRemoteSkipsOwnDevice() = runTest {
        val repo = FakeTodoRepository()
        val coordinator = SyncCoordinator(repo, FakeSyncClient(), "device-a")
        val result = coordinator.applyRemote(row(updatedBy = "device-a"))
        assertTrue(result.isRight())
        assertTrue(repo.appliedUpserts.isEmpty())
    }

    @Test
    fun applyRemoteUpsertTodoDecodesPayload() = runTest {
        val repo = FakeTodoRepository()
        val coordinator = SyncCoordinator(repo, FakeSyncClient(), "device-a")
        val payload = Json.encodeToString(
            TodoRowDto(1, 1, "远程", "", null, false, null, false, null, null, 0.0, false, 0, 200, "device-b"),
        )
        val result = coordinator.applyRemote(row(payload = payload, updatedAt = 200))
        assertTrue(result.isRight())
        assertEquals("远程", repo.appliedUpserts.single().title)
    }

    @Test
    fun applyRemoteMalformedPayloadReturnsTransportError() = runTest {
        val repo = FakeTodoRepository()
        val coordinator = SyncCoordinator(repo, FakeSyncClient(), "device-a")
        val result = coordinator.applyRemote(row(payload = "not-json"))
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull() is SyncError.Transport)
        assertTrue(repo.appliedUpserts.isEmpty())
    }

    @Test
    fun applyRemoteDeleteDelegates() = runTest {
        val repo = FakeTodoRepository()
        val coordinator = SyncCoordinator(repo, FakeSyncClient(), "device-a")
        val result = coordinator.applyRemote(row(action = SyncAction.DELETE, rowId = 7, updatedAt = 300))
        assertTrue(result.isRight())
        assertEquals("todo" to 7L, repo.appliedDeletes.single())
    }
}
