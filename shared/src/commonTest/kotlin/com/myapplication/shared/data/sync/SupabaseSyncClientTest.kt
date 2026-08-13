package com.myapplication.shared.data.sync

import com.myapplication.shared.domain.sync.SyncAction
import com.myapplication.shared.domain.sync.SyncRow
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SupabaseSyncClientTest {
    @Test
    fun upsertPayloadBatchKeepsExplicitNulls() {
        val batch = buildUpsertPayloadBatch(
            listOf(
                row(
                    payload = """
                        {
                          "id": 1,
                          "title": "清除重复",
                          "recurrence_frequency": null,
                          "recurrence_interval": null
                        }
                    """.trimIndent(),
                ),
            ),
        )

        val payload = batch.body.single().jsonObject

        assertEquals(0, batch.skipped)
        assertTrue(payload.containsKey("recurrence_frequency"))
        assertTrue(payload.containsKey("recurrence_interval"))
        assertEquals(JsonNull, payload["recurrence_frequency"])
        assertEquals(JsonNull, payload["recurrence_interval"])
    }

    @Test
    fun upsertPayloadBatchSkipsMalformedPayloads() {
        val batch = buildUpsertPayloadBatch(
            listOf(
                row(payload = "not-json"),
                row(payload = """{"id":2,"title":"有效"}"""),
            ),
        )

        assertEquals(1, batch.skipped)
        assertEquals(1, batch.body.size)
        assertEquals("有效", batch.body.single().jsonObject["title"]?.jsonPrimitive?.content)
    }

    private fun row(payload: String): SyncRow =
        SyncRow(
            seq = 1,
            table = "todo",
            rowId = 1,
            action = SyncAction.UPSERT,
            payload = payload,
            updatedAt = 100,
            updatedBy = "device-a",
        )
}
