package com.myapplication.shared.data.sync

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.myapplication.shared.domain.sync.SyncClient
import com.myapplication.shared.domain.sync.SyncConfig
import com.myapplication.shared.domain.sync.SyncError
import com.myapplication.shared.domain.sync.SyncMode

/**
 * 按 SyncConfig.mode 创建对应的 SyncClient 实现。
 *
 * - Local：NoopSyncClient（无远端，保住 outbox 不堆积）；
 * - Supabase：配置缺 URL/Key 时返回 NotConfigured，避免运行时炸网络请求；
 * - SundialServer：自建服务尚未实现，统一返回 NotConfigured。
 */
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
