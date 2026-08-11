package com.myapplication.shared.domain.sync

import arrow.core.Either
import kotlinx.coroutines.flow.Flow

interface SyncClient {
    suspend fun push(rows: List<SyncRow>): Either<SyncError, Unit>
    fun observeRemoteChanges(): Flow<SyncRow>
    fun observeConnectionStatus(): Flow<Boolean>
    suspend fun close()
}
