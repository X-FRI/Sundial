package com.myapplication.shared.domain.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 同步领域的模型与 DTO。
 *
 * 设计要点：
 * - [TodoRowDto]/[ListRowDto] 是 outbox payload 与远端行共用的序列化形态，
 *   时间统一为 epoch 毫秒 Long；@SerialName 用 snake_case 是因为远端
 *   PostgREST 列名与 Supabase realtime record 都是 snake_case（list_id 等），
 *   序列化必须与列名严格对齐；
 * - [SyncRow] 是传输层通用行：seq 仅本地 outbox 有意义（水位线），
 *   远端行恒为 0（见 SupabaseSyncClient.toSyncRow）；
 * - [SyncStatus] 是 UI 状态卡片的数据源：connected 表示实时订阅/push 是否健康，
 *   pendingCount 为待推送条数，lastError 展示给用户的最近一次错误。
 */
sealed interface SyncMode {
    data object Local : SyncMode
    data object Supabase : SyncMode
    data object SundialServer : SyncMode

    companion object {
        /** 由设置里的模式字符串（"supabase"/"sundial"）解析，未知值回退 Local。 */
        fun fromKey(key: String): SyncMode = when (key) {
            "supabase" -> Supabase
            "sundial" -> SundialServer
            else -> Local
        }
    }
}

enum class SyncAction { UPSERT, DELETE }

@Serializable
data class TodoRowDto(
    @SerialName("id") val id: Long,
    @SerialName("list_id") val listId: Long,
    @SerialName("title") val title: String,
    @SerialName("note") val note: String,
    @SerialName("due_date") val dueDate: Long?,
    @SerialName("is_completed") val isCompleted: Boolean,
    @SerialName("completed_at") val completedAt: Long?,
    @SerialName("is_trashed") val isTrashed: Boolean,
    @SerialName("trashed_at") val trashedAt: Long?,
    @SerialName("parent_id") val parentId: Long?,
    @SerialName("sort_position") val sortPosition: Double,
    @SerialName("flag") val flag: Boolean,
    @SerialName("recurrence_frequency") val recurrence_frequency: String? = null,
    @SerialName("recurrence_interval") val recurrence_interval: Long? = null,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("updated_by") val updatedBy: String,
)

@Serializable
data class ListRowDto(
    @SerialName("id") val id: Long,
    @SerialName("name") val name: String,
    @SerialName("color_key") val colorKey: String,
    @SerialName("position") val position: Int,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("updated_by") val updatedBy: String,
)

/**
 * 同步传输行：既能表示 outbox 中的待推送记录，也能表示一条远端变更。
 *
 * - seq：本地 outbox 行号（单调递增，作为水位线）；来自远端的行恒为 0；
 * - payload：UPSERT 时为整行快照的 JSON（key 与 DTO @SerialName 对齐），
 *   DELETE 时为 null；
 * - updatedBy：写入者设备 id，远端行用它做回声过滤与 LWW。
 */
data class SyncRow(
    val seq: Long,
    val table: String,
    val rowId: Long,
    val action: SyncAction,
    val payload: String?,
    val updatedAt: Long,
    val updatedBy: String,
)

data class SyncConfig(
    val mode: SyncMode,
    val supabaseUrl: String = "",
    val supabaseKey: String = "",
    val sundialUrl: String = "",
    val deviceId: String = "",
)

/** 同步层统一错误：Transport 为网络/服务端/解析错误，NotConfigured 为配置缺失。 */
sealed interface SyncError {
    data class Transport(val message: String) : SyncError
    data object NotConfigured : SyncError
}

data class SyncStatus(
    val mode: SyncMode,
    val connected: Boolean,
    val pendingCount: Int,
    val lastSyncAt: Long?,
    val lastError: String?,
    val syncing: Boolean = false,
) {
    companion object {
        /** 初始状态：Local 模式、未连接、无待推送、从未同步。 */
        val initial = SyncStatus(SyncMode.Local, false, 0, null, null)
    }
}
