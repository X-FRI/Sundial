package com.myapplication.shared.data.sync

import arrow.core.Either
import arrow.core.right
import com.myapplication.shared.domain.sync.SyncClient
import com.myapplication.shared.domain.sync.SyncError
import com.myapplication.shared.domain.sync.SyncRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

/**
 * 本地模式的空实现 SyncClient。
 *
 * 作用：Local 模式下引擎仍跑 push 循环，但 push 恒成功、远端流恒为空——
 * 于是 outbox 中的行会被立即清空，不会无限堆积；
 * 同时 observeRemoteChanges 永不 emit，应用层无需区分「有无远端」。
 */
class NoopSyncClient : SyncClient {
    override suspend fun push(rows: List<SyncRow>): Either<SyncError, Unit> = Unit.right()
    override suspend fun pull(): Either<SyncError, List<SyncRow>> = emptyList<SyncRow>().right()
    override fun observeRemoteChanges(): Flow<SyncRow> = flow {}
    override fun observeConnectionStatus(): Flow<Boolean> = MutableStateFlow(false)
    override suspend fun close() = Unit
}
