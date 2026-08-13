package com.myapplication.shared.data.sync

import com.myapplication.shared.domain.sync.SyncAction
import com.myapplication.shared.domain.sync.SyncRow
import kotlin.test.Test
import kotlin.test.assertEquals

class SupabaseSyncClientTest {
    @Test
    fun pushBatchesPreserveOutboxOrderAcrossActions() {
        val rows = listOf(
            row(seq = 1, table = "todo", action = SyncAction.UPSERT),
            row(seq = 2, table = "reminder_list", action = SyncAction.DELETE),
            row(seq = 3, table = "todo", action = SyncAction.UPSERT),
        )

        val batches = orderedSyncPushBatches(rows)

        assertEquals(listOf(listOf(1L), listOf(2L), listOf(3L)), batches.map { batch -> batch.map { it.seq } })
    }

    @Test
    fun pushBatchesMergeAdjacentUpsertsForSameTableOnly() {
        val rows = listOf(
            row(seq = 1, table = "todo", action = SyncAction.UPSERT),
            row(seq = 2, table = "todo", action = SyncAction.UPSERT),
            row(seq = 3, table = "reminder_list", action = SyncAction.UPSERT),
            row(seq = 4, table = "reminder_list", action = SyncAction.UPSERT),
        )

        val batches = orderedSyncPushBatches(rows)

        assertEquals(listOf(listOf(1L, 2L), listOf(3L, 4L)), batches.map { batch -> batch.map { it.seq } })
    }

    @Test
    fun pushBatchesMergeAdjacentDeletesWithoutReordering() {
        val rows = listOf(
            row(seq = 1, table = "todo", action = SyncAction.DELETE),
            row(seq = 2, table = "reminder_list", action = SyncAction.DELETE),
            row(seq = 3, table = "todo", action = SyncAction.UPSERT),
        )

        val batches = orderedSyncPushBatches(rows)

        assertEquals(listOf(listOf(1L, 2L), listOf(3L)), batches.map { batch -> batch.map { it.seq } })
    }

    private fun row(
        seq: Long,
        table: String,
        action: SyncAction,
    ): SyncRow =
        SyncRow(
            seq = seq,
            table = table,
            rowId = seq,
            action = action,
            payload = null,
            updatedAt = seq,
            updatedBy = "device-a",
        )
}
