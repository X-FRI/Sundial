package com.myapplication.shared.domain.sync

import kotlin.test.Test
import kotlin.test.assertEquals

class SyncStatusTransitionsTest {
    @Test
    fun configuringModePreservesHistoricalFieldsAndStopsActiveSync() {
        val status = SyncStatus(
            mode = SyncMode.Supabase,
            connected = true,
            pendingCount = 7,
            lastSyncAt = 1_000L,
            lastError = "old error",
            syncing = true,
        )

        assertEquals(
            SyncStatus(
                mode = SyncMode.Local,
                connected = false,
                pendingCount = 7,
                lastSyncAt = 1_000L,
                lastError = "old error",
                syncing = false,
            ),
            status.configuring(SyncMode.Local),
        )
    }

    @Test
    fun configurationFailureKeepsPendingAndLastSyncForAccurateOfflineState() {
        val status = SyncStatus(
            mode = SyncMode.Local,
            connected = true,
            pendingCount = 3,
            lastSyncAt = 2_000L,
            lastError = null,
            syncing = true,
        )

        assertEquals(
            SyncStatus(
                mode = SyncMode.Supabase,
                connected = false,
                pendingCount = 3,
                lastSyncAt = 2_000L,
                lastError = "同步配置不完整",
                syncing = false,
            ),
            status.configurationFailed(SyncMode.Supabase, SyncError.NotConfigured),
        )
    }

    @Test
    fun successfulSyncMarksConnectedClearsErrorAndKeepsSpinnerOnlyWhenPendingRemains() {
        val status = SyncStatus(
            mode = SyncMode.Supabase,
            connected = false,
            pendingCount = 5,
            lastSyncAt = null,
            lastError = "network down",
            syncing = true,
        )

        assertEquals(
            SyncStatus(
                mode = SyncMode.Supabase,
                connected = true,
                pendingCount = 2,
                lastSyncAt = 3_000L,
                lastError = null,
                syncing = true,
            ),
            status.syncSucceeded(pendingCount = 2, atMillis = 3_000L),
        )
        assertEquals(false, status.syncSucceeded(pendingCount = 0, atMillis = 3_000L).syncing)
    }

    @Test
    fun failuresAndRemoteEventsMapToConsistentUserFacingStatus() {
        val status = SyncStatus.initial.copy(mode = SyncMode.Supabase, syncing = true)

        assertEquals(
            status.copy(connected = false, syncing = false, lastError = "network down"),
            status.syncFailed("network down"),
        )
        assertEquals(
            status.copy(connected = true, pendingCount = 4, lastError = null),
            status.remoteApplied(pendingCount = 4),
        )
        assertEquals(
            status.copy(lastError = "应用远端变更失败: bad row"),
            status.remoteApplyFailed("bad row"),
        )
        assertEquals(
            status.copy(connected = false, lastError = "实时订阅断开: socket closed"),
            status.remoteSubscriptionLost("socket closed"),
        )
    }

    @Test
    fun observedOutboxCountOnlyStartsSyncingWhenThereIsNewWork() {
        val idle = SyncStatus.initial.copy(syncing = false)
        val alreadySyncing = SyncStatus.initial.copy(syncing = true)

        assertEquals(false, idle.outboxObserved(0).syncing)
        assertEquals(true, idle.outboxObserved(1).syncing)
        assertEquals(alreadySyncing, alreadySyncing.outboxObserved(4))
    }
}
