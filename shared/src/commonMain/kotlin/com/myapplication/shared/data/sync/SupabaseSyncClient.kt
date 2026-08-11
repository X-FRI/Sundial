package com.myapplication.shared.data.sync

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.myapplication.shared.domain.sync.ListRowDto
import com.myapplication.shared.domain.sync.SyncAction
import com.myapplication.shared.domain.sync.SyncClient
import com.myapplication.shared.domain.sync.SyncError
import com.myapplication.shared.domain.sync.SyncRow
import com.myapplication.shared.domain.sync.TodoRowDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class SupabaseSyncClient(
    url: String,
    key: String,
    private val deviceId: String,
) : SyncClient {

    private val client: SupabaseClient = createSupabaseClient(url, key) {
        install(Postgrest)
        install(Realtime)
    }

    override suspend fun push(rows: List<SyncRow>): Either<SyncError, Unit> =
        try {
            rows.groupBy { it.action }.forEach { (action, group) ->
                when (action) {
                    SyncAction.UPSERT -> pushUpserts(group)
                    SyncAction.DELETE -> pushDeletes(group)
                }
            }
            Unit.right()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SyncError.Transport(e.message ?: "同步失败").left()
        }

    private suspend fun pushUpserts(rows: List<SyncRow>) {
        rows.groupBy { it.table }.forEach { (table, group) ->
            when (table) {
                "todo" -> client.from("todo").upsert(group.map { row ->
                    if (row.payload == null) {
                        throw IllegalStateException("UPSERT payload missing for row ${row.rowId}")
                    }
                    Json.decodeFromString<TodoRowDto>(row.payload)
                })
                "reminder_list" -> client.from("reminder_list").upsert(group.map { row ->
                    if (row.payload == null) {
                        throw IllegalStateException("UPSERT payload missing for row ${row.rowId}")
                    }
                    Json.decodeFromString<ListRowDto>(row.payload)
                })
            }
        }
    }

    private suspend fun pushDeletes(rows: List<SyncRow>) {
        rows.forEach { row ->
            when (row.table) {
                "todo", "reminder_list" -> client.from(row.table).delete { filter { eq("id", row.rowId) } }
            }
        }
    }

    override fun observeRemoteChanges(): Flow<SyncRow> = flow {
        val tables = listOf(
            "todo" to "sundial-todo",
            "reminder_list" to "sundial-lists",
        )
        val channels = tables.map { (table, channelId) -> client.channel(channelId) to table }
        val flows = channels.map { (channel, table) ->
            channel.postgresChangeFlow<PostgresAction>("public") { this.table = table }
                .filter { it !is PostgresAction.Select }
                .map { it.toSyncRow(table) }
                .filter { it.updatedBy != deviceId }
        }
        try {
            channels.forEach { (channel, _) -> channel.subscribe() }
            merge(*flows.toTypedArray()).collect { emit(it) }
        } finally {
            channels.forEach { (channel, _) -> runCatching { channel.unsubscribe() } }
        }
    }

    override suspend fun close() {
        runCatching { client.close() }
    }

    private fun PostgresAction.toSyncRow(table: String): SyncRow {
        val isDelete = this is PostgresAction.Delete
        val record = when (this) {
            is PostgresAction.Delete -> oldRecord
            is PostgresAction.Update -> record
            is PostgresAction.Insert -> record
            is PostgresAction.Select -> record
        }
        return SyncRow(
            seq = 0L,
            table = table,
            rowId = record["id"].asLong(),
            action = if (isDelete) SyncAction.DELETE else SyncAction.UPSERT,
            payload = if (isDelete) null else record.toString(),
            updatedAt = record["updated_at"].asLong(),
            updatedBy = record["updated_by"].asText(),
        )
    }

    private fun JsonElement?.asLong(): Long = when (this) {
        null, is JsonNull -> 0L
        else -> jsonPrimitive.longOrNull ?: jsonPrimitive.content.toLongOrNull() ?: 0L
    }

    private fun JsonElement?.asText(): String = when (this) {
        null, is JsonNull -> ""
        else -> jsonPrimitive.content
    }
}
