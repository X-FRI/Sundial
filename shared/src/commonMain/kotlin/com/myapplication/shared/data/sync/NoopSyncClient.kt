package com.myapplication.shared.data.sync

import arrow.core.Either
import arrow.core.right
import com.myapplication.shared.domain.sync.SyncClient
import com.myapplication.shared.domain.sync.SyncError
import com.myapplication.shared.domain.sync.SyncRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

class NoopSyncClient : SyncClient {
    override suspend fun push(rows: List<SyncRow>): Either<SyncError, Unit> = Unit.right()
    override fun observeRemoteChanges(): Flow<SyncRow> = flow {}
    override fun observeConnectionStatus(): Flow<Boolean> = MutableStateFlow(false)
    override suspend fun close() = Unit
}
