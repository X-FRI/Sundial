package com.myapplication.shared.data.sync

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.myapplication.shared.domain.sync.SyncClient
import com.myapplication.shared.domain.sync.SyncConfig
import com.myapplication.shared.domain.sync.SyncError
import com.myapplication.shared.domain.sync.SyncMode

object SyncClientFactory {
    fun create(config: SyncConfig): Either<SyncError, SyncClient> = when (config.mode) {
        SyncMode.Local -> NoopSyncClient().right()
        SyncMode.Supabase -> {
            if (config.supabaseUrl.isBlank() || config.supabaseKey.isBlank()) {
                SyncError.NotConfigured.left()
            } else {
                SupabaseSyncClient(config.supabaseUrl, config.supabaseKey, config.deviceId).right()
            }
        }
        SyncMode.SundialServer -> SyncError.NotConfigured.left()
    }
}
