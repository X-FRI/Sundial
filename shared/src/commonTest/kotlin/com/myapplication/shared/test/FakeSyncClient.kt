package com.myapplication.shared.test

import arrow.core.Either
import com.myapplication.shared.domain.sync.SyncClient
import com.myapplication.shared.domain.sync.SyncError
import com.myapplication.shared.domain.sync.SyncRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

/**
 * 内存版 SyncClient 假实现（SyncCoordinatorTest 与 SyncEngineTest 共用）。
 *
 * - [pushed] 记录每次成功 push 的行，[pushAttempts] 记录 push 调用次数
 *   （含失败，供引擎测试统计重试轮次）；
 * - [failPush] 注入传输失败；[pushDelayMs] > 0 时 push 先挂起再成功，
 *   供引擎测试用虚拟时间观察 syncing 的中间态（默认 0 保持同步完成）；
 * - [remote] 是远端行列表的状态流，observeRemoteChanges 一次性重放
 *   （模拟"拉取当前远端快照"而非实时订阅）。
 */
class FakeSyncClient : SyncClient {
    val pushed = mutableListOf<List<SyncRow>>()
    var pushAttempts = 0
    var pushDelayMs = 0L
    var closeAttempts = 0
    var closeDelayMs = 0L
    var failPush = false
    val remote = MutableStateFlow<List<SyncRow>>(emptyList())
    var pullResult: List<SyncRow> = emptyList()
    var failPull = false
    var crashPull = false

    override suspend fun push(rows: List<SyncRow>): Either<SyncError, Unit> {
        pushAttempts++
        if (failPush) return Either.Left(SyncError.Transport("network down"))
        if (pushDelayMs > 0) delay(pushDelayMs)
        pushed += rows
        return Either.Right(Unit)
    }

    override suspend fun pull(): Either<SyncError, List<SyncRow>> =
        when {
            crashPull -> throw RuntimeException("boom")
            failPull -> Either.Left(SyncError.Transport("network down"))
            else -> Either.Right(pullResult)
        }

    override fun observeRemoteChanges(): Flow<SyncRow> = flow { remote.value.forEach { emit(it) } }

    override fun observeConnectionStatus(): Flow<Boolean> = MutableStateFlow(false)

    override suspend fun close() {
        closeAttempts++
        if (closeDelayMs > 0) delay(closeDelayMs)
    }
}
