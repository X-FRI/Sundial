package com.myapplication.shared.data.sync

import arrow.core.right
import com.myapplication.shared.domain.sync.SyncAction
import com.myapplication.shared.domain.sync.SyncConfig
import com.myapplication.shared.domain.sync.SyncMode
import com.myapplication.shared.domain.sync.SyncRow
import com.myapplication.shared.test.FakeSyncClient
import com.myapplication.shared.test.FakeTodoRepository
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SyncEngine 的状态机契约测试（配合 FakeTodoRepository / FakeSyncClient）。
 *
 * 测试在 runTest 虚拟时间下运行：引擎的两个后台循环挂在 backgroundScope
 * 上（测试结束自动取消），用 runCurrent/advanceTimeBy 精确推进调度，
 * 断言 syncing/connected/lastError 等对外状态，以及退避重试节奏。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncEngineTest {

    private fun supabaseConfig(deviceId: String = "device-a") =
        SyncConfig(
            SyncMode.Supabase,
            supabaseUrl = "https://example.com",
            supabaseKey = "test-key",
            deviceId = deviceId,
        )

    private fun syncRow(
        seq: Long = 1,
        rowId: Long = 1,
    ) = SyncRow(seq, "todo", rowId, SyncAction.UPSERT, "", 0L, "device-b")

    /** 固定时钟：lastSyncAt 断言直接对拍时钟值。 */
    private class FakeClock(private val currentMs: Long) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(currentMs)
    }

    @Test
    fun syncNowSuccessResetsSyncingAndUpdatesStatus() = runTest {
        val repo = FakeTodoRepository()
        val client = FakeSyncClient().apply { pushDelayMs = 1_000 }
        val clock = FakeClock(1_000_000)
        val engine = SyncEngine(backgroundScope, repo, clock) { client.right() }
        engine.configure(supabaseConfig())
        runCurrent() // 首次自动对齐完成（outbox 为空）
        repo.outboxState.value = listOf(syncRow(seq = 1, rowId = 10))
        engine.syncNow()
        runCurrent()
        // 进行中：push 挂在虚拟时间的 1000ms 延迟上，syncing=true
        assertTrue(engine.status.value.syncing)
        advanceTimeBy(1_000)
        runCurrent()
        val status = engine.status.value
        assertFalse(status.syncing)
        assertTrue(status.connected)
        assertNull(status.lastError)
        assertEquals(0, status.pendingCount)
        assertEquals(1_000_000L, status.lastSyncAt)
        assertEquals(1, client.pushed.size)
    }

    @Test
    fun syncNowFailureClearsSyncingAndRecordsError() = runTest {
        val repo = FakeTodoRepository()
        val client = FakeSyncClient().apply { failPull = true }
        val engine = SyncEngine(backgroundScope, repo, FakeClock(1_000_000)) { client.right() }
        engine.configure(supabaseConfig())
        runCurrent() // 首次自动对齐：pull 失败
        engine.syncNow()
        runCurrent()
        val status = engine.status.value
        assertFalse(status.syncing)
        assertFalse(status.connected)
        assertEquals("network down", status.lastError)
    }

    @Test
    fun syncNowUnexpectedExceptionIsFailureNotSuccess() = runTest {
        val repo = FakeTodoRepository()
        val client = FakeSyncClient().apply { crashPull = true }
        val engine = SyncEngine(backgroundScope, repo, FakeClock(1_000_000)) { client.right() }
        engine.configure(supabaseConfig())
        runCurrent() // 首次自动对齐：pull 抛意外异常（null 结果）-> 必须记为失败
        val status = engine.status.value
        assertFalse(status.syncing)
        assertFalse(status.connected)
        assertEquals("同步失败: 未知错误", status.lastError)
    }

    @Test
    fun configureToLocalWhileSyncingResetsSyncing() = runTest {
        val repo = FakeTodoRepository()
        val client = FakeSyncClient().apply { pushDelayMs = 1_000 }
        val engine = SyncEngine(backgroundScope, repo, FakeClock(1_000_000)) { client.right() }
        engine.configure(supabaseConfig())
        repo.outboxState.value = listOf(syncRow(seq = 1, rowId = 10))
        runCurrent()
        // 自动对齐挂起在 push 延迟上，syncing=true（旧实现切 Local 后会卡死在此值）
        assertTrue(engine.status.value.syncing)
        engine.configure(SyncConfig(SyncMode.Local))
        runCurrent()
        val status = engine.status.value
        assertEquals(SyncMode.Local, status.mode)
        assertFalse(status.syncing)
        assertFalse(status.connected)
        // Local 下 syncNow 是 no-op，不会再置 syncing
        engine.syncNow()
        runCurrent()
        assertFalse(engine.status.value.syncing)
    }

    @Test
    fun pushLoopFailureBacksOffAndKeepsRetrying() = runTest {
        val repo = FakeTodoRepository()
        val client = FakeSyncClient().apply { failPush = true }
        val engine = SyncEngine(backgroundScope, repo, FakeClock(1_000_000)) { client.right() }
        repo.outboxState.value = listOf(syncRow(seq = 1, rowId = 10))
        engine.configure(supabaseConfig())
        runCurrent()
        // 首次失败：自动对齐 + 循环第一轮各试一次，状态复位且记录错误
        assertEquals(2, client.pushAttempts)
        val failed = engine.status.value
        assertFalse(failed.syncing)
        assertFalse(failed.connected)
        assertEquals("network down", failed.lastError)
        // 第一次失败后退避 2s -> 4s：4s 内不再重试
        advanceTimeBy(3_999)
        runCurrent()
        assertEquals(2, client.pushAttempts)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(3, client.pushAttempts)
        // 第二次失败后退避翻倍为 8s：8s 内不再重试
        advanceTimeBy(7_999)
        runCurrent()
        assertEquals(3, client.pushAttempts)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(4, client.pushAttempts)
    }

    @Test
    fun syncNowIsIdempotentWhileRunning() = runTest {
        val repo = FakeTodoRepository()
        val client = FakeSyncClient().apply { pushDelayMs = 1_000 }
        val engine = SyncEngine(backgroundScope, repo, FakeClock(1_000_000)) { client.right() }
        engine.configure(supabaseConfig())
        runCurrent() // 自动对齐完成（outbox 为空）
        repo.outboxState.value = listOf(syncRow(seq = 1, rowId = 10))
        engine.syncNow()
        engine.syncNow() // 第二次调用：上一次仍在跑，直接忽略
        runCurrent()
        assertTrue(engine.status.value.syncing)
        assertEquals(1, client.pushAttempts)
        advanceTimeBy(1_000)
        runCurrent()
        assertFalse(engine.status.value.syncing)
        assertEquals(1, client.pushAttempts)
        assertEquals(1, client.pushed.size)
    }
}
