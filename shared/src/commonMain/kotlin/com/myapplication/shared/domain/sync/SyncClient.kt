package com.myapplication.shared.domain.sync

import arrow.core.Either
import kotlinx.coroutines.flow.Flow

/**
 * 同步传输层抽象：屏蔽「本地(Noop) / Supabase / 自建服务」的差异。
 *
 * 设计要点：
 * - [push] 一次推一批 outbox 行，成功后由 coordinator 清水位线；失败返回
 *   SyncError.Transport，行保留在 outbox 等待重试；
 * - [observeRemoteChanges] 持续流式输出远端变更（推一帧代表一条远端变更），
 *   由 SyncEngine 的 remote 循环收集并交给 coordinator 应用；
 * - [close] 释放连接资源，需在协程中调用（内部可能挂起）。
 */
interface SyncClient {
    suspend fun push(rows: List<SyncRow>): Either<SyncError, Unit>
    fun observeRemoteChanges(): Flow<SyncRow>
    suspend fun close()
}
