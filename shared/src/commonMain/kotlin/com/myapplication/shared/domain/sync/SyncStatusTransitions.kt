package com.myapplication.shared.domain.sync

fun SyncStatus.configuring(mode: SyncMode): SyncStatus =
    copy(mode = mode, connected = false, syncing = false)

fun SyncStatus.configurationFailed(
    mode: SyncMode,
    error: SyncError,
): SyncStatus =
    copy(mode = mode, connected = false, lastError = error.userMessage(), syncing = false)

fun SyncStatus.syncStarted(): SyncStatus =
    copy(syncing = true)

fun SyncStatus.syncFailed(message: String): SyncStatus =
    copy(connected = false, lastError = message, syncing = false)

fun SyncStatus.syncSucceeded(
    pendingCount: Int,
    atMillis: Long,
): SyncStatus =
    copy(
        connected = true,
        pendingCount = pendingCount,
        lastSyncAt = atMillis,
        lastError = null,
        syncing = pendingCount > 0,
    )

fun SyncStatus.outboxObserved(count: Int): SyncStatus =
    if (count > 0 && !syncing) copy(syncing = true) else this

fun SyncStatus.connectionObserved(connected: Boolean): SyncStatus =
    copy(connected = connected)

fun SyncStatus.remoteApplied(pendingCount: Int): SyncStatus =
    copy(connected = true, pendingCount = pendingCount, lastError = null)

fun SyncStatus.remoteApplyFailed(message: String): SyncStatus =
    copy(lastError = "应用远端变更失败: $message")

fun SyncStatus.remoteSubscriptionLost(message: String?): SyncStatus =
    copy(connected = false, lastError = "实时订阅断开: $message")

fun SyncError.userMessage(): String = when (this) {
    is SyncError.NotConfigured -> "同步配置不完整"
    is SyncError.Transport -> message
}
