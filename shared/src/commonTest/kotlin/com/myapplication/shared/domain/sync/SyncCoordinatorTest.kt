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

/**
 * 内存版 SyncClient 假实现：
 * - [pushed] 记录每次 push 的行，[failPush] 注入传输失败；
 * - [remote] 是远端行列表的状态流，observeRemoteChanges 一次性重放
 *   （模拟"拉取当前远端快照"而非实时订阅）。
 */
class FakeSyncClient : SyncClient {
    val pushed = mutableListOf<List<SyncRow>>()
    var failPush = false
    val remote = MutableStateFlow<List<SyncRow>>(emptyList())
    var pullResult: List<SyncRow> = emptyList()
    var failPull = false

    override suspend fun push(rows: List<SyncRow>): Either<SyncError, Unit> {
        if (failPush) return Either.Left(SyncError.Transport("network down"))
        pushed += rows
        return Either.Right(Unit)
    }

    override suspend fun pull(): Either<SyncError, List<SyncRow>> =
        if (failPull) Either.Left(SyncError.Transport("network down")) else Either.Right(pullResult)

    override fun observeRemoteChanges(): Flow<SyncRow> =
        flow { remote.value.forEach { emit(it) } }

    override suspend fun close() = Unit
}

/**
 * SyncCoordinator 的契约测试（配合 FakeTodoRepository / FakeSyncClient）。
 *
 * 覆盖的四类契约：
 * 1. 出站：drainOutbox 把 outbox 全部 push 并清空；push 失败则保留 outbox；
 * 2. 入站（自设备回环）：updatedBy == 本设备时跳过，避免把自己发出去的行
 *    再应用回来（否则 updatedAt 会被本地覆盖，破坏 LWW）；
 * 3. 入站（远端写入）：todo 的 payload 反序列化为 TodoRowDto 后交给
 *    repository.applyRemoteUpsert；非法 payload 归为 Transport 错误；
 * 4. 入站（远端删除）：按 (table, rowId) 委派给 applyRemoteDelete。
 */
class SyncCoordinatorTest {

    // 构造 SyncRow 的默认参数工厂：大多数断言只关心其中一两个字段
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
        // 预置两条待发上行
        repo.outboxState.value = listOf(row(seq = 1, rowId = 10), row(seq = 2, rowId = 11))
        val client = FakeSyncClient()
        val coordinator = SyncCoordinator(repo, client, deviceId = "device-a")
        val result = coordinator.drainOutbox()
        assertTrue(result.isRight())
        assertEquals(2, result.getOrNull())
        // 一次 push 带走全部行，且按 rowId 原样透传
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
        // 传输失败 → 返回 Transport 错误且 outbox 不动（保证可重试不丢数据）
        assertEquals(SyncError.Transport("network down"), result.leftOrNull())
        assertEquals(1, repo.outboxState.value.size)
    }

    @Test
    fun applyRemoteSkipsOwnDevice() = runTest {
        val repo = FakeTodoRepository()
        val coordinator = SyncCoordinator(repo, FakeSyncClient(), "device-a")
        val result = coordinator.applyRemote(row(updatedBy = "device-a"))
        assertTrue(result.isRight())
        // 自设备行被丢弃，未进入应用管线
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
        // payload 正确解码后原样交给 repository
        assertEquals("远程", repo.appliedUpserts.single().title)
    }

    @Test
    fun applyRemoteMalformedPayloadReturnsTransportError() = runTest {
        val repo = FakeTodoRepository()
        val coordinator = SyncCoordinator(repo, FakeSyncClient(), "device-a")
        val result = coordinator.applyRemote(row(payload = "not-json"))
        // 坏 payload 视为传输层问题（数据不可信），不落到本地
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

    @Test
    fun pullAppliesRemoteRows() = runTest {
        val repo = FakeTodoRepository()
        val client = FakeSyncClient().apply {
            pullResult = listOf(
                row(rowId = 1, payload = todoPayload("a")),
                row(rowId = 2, payload = todoPayload("b")),
            )
        }
        val coordinator = SyncCoordinator(repo, client, "device-a")
        val result = coordinator.pullFromRemote()
        // 两行远端 todo 全部应用，返回应用行数 2
        assertTrue(result.isRight())
        assertEquals(2, result.getOrNull())
        assertEquals(listOf("a", "b"), repo.appliedUpserts.map { it.title })
    }

    @Test
    fun pullSkipsOwnDeviceRows() = runTest {
        val repo = FakeTodoRepository()
        val client = FakeSyncClient().apply {
            pullResult = listOf(
                row(rowId = 1, updatedBy = "device-a", payload = todoPayload("own")),
                row(rowId = 2, updatedBy = "device-b", payload = todoPayload("remote")),
            )
        }
        val coordinator = SyncCoordinator(repo, client, "device-a")
        val result = coordinator.pullFromRemote()
        // 自设备行被回声过滤，只应用远端行
        assertTrue(result.isRight())
        assertEquals(1, result.getOrNull())
        assertEquals(listOf("remote"), repo.appliedUpserts.map { it.title })
    }

    @Test
    fun pullSkipsMalformedRowButAppliesValidRows() = runTest {
        val repo = FakeTodoRepository()
        val client = FakeSyncClient().apply {
            pullResult = listOf(
                row(rowId = 1, payload = "not-json"),
                row(rowId = 2, payload = todoPayload("ok")),
            )
        }
        val coordinator = SyncCoordinator(repo, client, "device-a")
        val result = coordinator.pullFromRemote()
        // 单行应用失败只影响该行，整体返回成功且有效行照常应用
        assertTrue(result.isRight())
        assertEquals(1, result.getOrNull())
        assertEquals(listOf("ok"), repo.appliedUpserts.map { it.title })
    }

    @Test
    fun pullPropagatesTransportFailure() = runTest {
        val repo = FakeTodoRepository()
        val client = FakeSyncClient().apply { failPull = true }
        val coordinator = SyncCoordinator(repo, client, "device-a")
        val result = coordinator.pullFromRemote()
        // 传输层失败经 bind 直接向上传播，不落入应用管线
        assertEquals(SyncError.Transport("network down"), result.leftOrNull())
        assertTrue(repo.appliedUpserts.isEmpty())
    }

    private fun todoPayload(title: String) = Json.encodeToString(
        TodoRowDto(0, 1, title, "", null, false, null, false, null, null, 0.0, false, 0, 100, "device-b"),
    )
}
