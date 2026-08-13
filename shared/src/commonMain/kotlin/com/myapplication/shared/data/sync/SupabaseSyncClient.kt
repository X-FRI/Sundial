package com.myapplication.shared.data.sync

import arrow.core.Either
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.resource
import arrow.fx.coroutines.use
import com.myapplication.shared.effects.catchTransport
import com.myapplication.shared.effects.runSyncEffect
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
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Supabase 传输层实现：PostgREST 推送 + Realtime 订阅拉取。
 *
 * 设计要点：
 * - 逐表订阅（todo / reminder_list 各开一个 channel）是因为 PostgresAction
 *   事件本身不带表名，只有 channel 的 table 配置能区分来源表；
 * - Realtime 默认也会收到本设备写操作（服务端广播），用 updated_by == deviceId
 *   做回声过滤，把本设备的写操作排除在应用流程外；
 * - push 保持 outbox seq 顺序，只把相邻且可安全合并的行批量发送。UPSERT 用
 *   PostgREST 批量 upsert（service 端按主键冲突更新），DELETE 逐行删；不能先
 *   全局按 action 分组，否则列表删除可能跑到 todo 归属更新前面。
 */
class SupabaseSyncClient(
    url: String,
    key: String,
    private val deviceId: String,
) : SyncClient {

    private val client: SupabaseClient = createSupabaseClient(url, key) {
        install(Postgrest)
        install(Realtime)
    }

    /**
     * 推送一批 outbox 行。任一步失败抛异常，整批返回 Left，
     * 由协调层保留 outbox 行等待重试。
     *
     * 坏行隔离：payload 无法解码的行由 [pushUpserts] 跳过（不送远端），
     * 正常行照常上送。整批全部损坏时返回 Left（跳过数计入错误文案，
     * 由协调层保留水位线，错误可被引擎暴露给 UI）；部分损坏时仍返回
     * Right——损坏行随后会随水位线被清除、不参与重试（重试也无法修复），
     * 跳过数作为内部细节仅用于判定整批失败。
     */
    override suspend fun push(rows: List<SyncRow>): Either<SyncError, Unit> =
        runSyncEffect {
            var skipped = 0
            catchTransport("同步失败") {
                orderedSyncPushBatches(rows).forEach { batch ->
                    when (batch.first().action) {
                        SyncAction.UPSERT -> skipped += pushUpserts(batch)
                        SyncAction.DELETE -> pushDeletes(batch)
                    }
                }
            }
            if (skipped > 0 && skipped == rows.size) {
                raise(SyncError.Transport("${rows.size} 行 payload 损坏被全部跳过，无数据上送"))
            }
        }

    /**
     * UPSERT 推送：再按表分组，把同表行合并成一次批量 upsert 请求。
     * payload 是 outbox 里序列化好的整行快照，直接反序列化为 DTO 上送。
     *
     * 坏行隔离：payload 缺失/损坏的行逐行 runCatching 跳过（这类行重试
     * 也无法修复，阻塞整批只会让全量同步停摆），其余行照常批量上送。
     * 返回被跳过的行数，由 push() 汇总并计入错误（全部跳过视为失败），
     * 避免损坏行静默消失零痕迹。
     */
    private suspend fun pushUpserts(rows: List<SyncRow>): Int {
        var skipped = 0
        rows.groupBy { it.table }.forEach { (table, group) ->
            when (table) {
                "todo" -> {
                    val dtos = group.mapNotNull { row ->
                        runCatching { Json.decodeFromString<TodoRowDto>(row.payload ?: "") }
                            .getOrElse { skipped++; null }
                    }
                    if (dtos.isNotEmpty()) client.from("todo").upsert(dtos)
                }
                "reminder_list" -> {
                    val dtos = group.mapNotNull { row ->
                        runCatching { Json.decodeFromString<ListRowDto>(row.payload ?: "") }
                            .getOrElse { skipped++; null }
                    }
                    if (dtos.isNotEmpty()) client.from("reminder_list").upsert(dtos)
                }
            }
        }
        return skipped
    }

    /** DELETE 推送：按行 id 逐条删除（PostgREST 删除需指定主键）。 */
    private suspend fun pushDeletes(rows: List<SyncRow>) {
        rows.forEach { row ->
            when (row.table) {
                "todo", "reminder_list" -> client.from(row.table).delete { filter { eq("id", row.rowId) } }
            }
        }
    }

    /**
     * 全量拉取两张业务表，对齐 Realtime 流之外的远端状态。
     *
     * 返回的行统一为 seq = 0 的 SyncRow（seq 只属于本地 outbox，见
     * toSyncRow），action 恒为 UPSERT——已删除的远端行不在表中，
     * 全量拉取天然看不到删除，只做状态对齐兜底；
     * 回声过滤（updatedBy == deviceId）交给 coordinator 的 applyRemote。
     */
    override suspend fun pull(): Either<SyncError, List<SyncRow>> =
        runSyncEffect {
            catchTransport("拉取失败") {
                buildList {
                    addAll(pullTable("todo"))
                    addAll(pullTable("reminder_list"))
                }
            }
        }

    private suspend fun pullTable(table: String): List<SyncRow> {
        val result = client.from(table).select { filter { gt("updated_at", 0) } }
        val elements = Json.parseToJsonElement(result.data).jsonArray
        return elements.mapNotNull { el ->
            runCatching {
                val obj = el.jsonObject
                SyncRow(
                    seq = 0L,
                    table = table,
                    rowId = obj["id"]?.jsonPrimitive?.longOrNull ?: 0L,
                    action = SyncAction.UPSERT,
                    payload = el.toString(),
                    updatedAt = obj["updated_at"]?.jsonPrimitive?.longOrNull ?: 0L,
                    updatedBy = obj["updated_by"]?.jsonPrimitive?.contentOrNull ?: "",
                )
            }.getOrNull()
        }
    }

    /**
     * Realtime 变更流：逐表订阅 postgres_change 事件，合并为一个 SyncRow 流。
     *
     * 过滤链：
     * 1. 丢弃 Select 事件（订阅时的初始快照，不是真实变更）；
     * 2. 回声过滤 updated_by == deviceId——本设备的写操作直接跳过。
     * 退订收尾：channel 生命周期由 Resource 管理，流结束/取消时统一释放。
     */
    override fun observeRemoteChanges(): Flow<SyncRow> =
        flow { remoteChangesResource().use { emitAll(it) } }

    private fun remoteChangesResource(): Resource<Flow<SyncRow>> = resource {
        val tables = listOf(
            "todo" to "sundial-todo",
            "reminder_list" to "sundial-lists",
        )
        val channels = tables.map { (table, channelId) -> client.channel(channelId) to table }
        onRelease {
            channels.forEach { (channel, _) -> runCatching { channel.unsubscribe() } }
        }
        val flows = channels.map { (channel, table) ->
            channel.postgresChangeFlow<PostgresAction>("public") { this.table = table }
                .filter { it !is PostgresAction.Select }
                .map { it.toSyncRow(table) }
                .filter { it.updatedBy != deviceId }
        }
        channels.forEach { (channel, _) -> channel.subscribe() }
        merge(*flows.toTypedArray())
    }

    /**
     * 连接健康度：直接映射 Realtime 连接状态。
     * Realtime 状态可能滞后于一次成功的 push（状态更新有延迟），引擎的
     * push 成功置 connected=true 作为兜底，本流负责状态真实变化时收敛。
     */
    override fun observeConnectionStatus(): Flow<Boolean> =
        client.realtime.status.map { it == Realtime.Status.CONNECTED }

    override suspend fun close() {
        runCatching { client.close() }
    }

    /**
     * PostgresAction -> SyncRow。
     * DELETE 用 oldRecord（行已删除，新值不存在），其余用 record；
     * seq 恒为 0——seq 只属于本地 outbox，远端行不参与水位线。
     */
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
            // UPSERT 保留整行 JSON 文本作为 payload（后续按表反序列化为 DTO）
            payload = if (isDelete) null else record.toString(),
            updatedAt = record["updated_at"].asLong(),
            updatedBy = record["updated_by"].asText(),
        )
    }

    // Realtime record 字段取值：null/JsonNull 归零值，数值/字符串统一转 Long

    private fun JsonElement?.asLong(): Long = when (this) {
        null, is JsonNull -> 0L
        else -> jsonPrimitive.longOrNull ?: jsonPrimitive.content.toLongOrNull() ?: 0L
    }

    private fun JsonElement?.asText(): String = when (this) {
        null, is JsonNull -> ""
        else -> jsonPrimitive.content
    }
}

internal fun orderedSyncPushBatches(rows: List<SyncRow>): List<List<SyncRow>> {
    val batches = mutableListOf<MutableList<SyncRow>>()
    rows.forEach { row ->
        val last = batches.lastOrNull()
        if (last != null && last.first().canBatchWith(row)) {
            last += row
        } else {
            batches += mutableListOf(row)
        }
    }
    return batches.map { it.toList() }
}

private fun SyncRow.canBatchWith(other: SyncRow): Boolean =
    action == other.action && (action == SyncAction.DELETE || table == other.table)
