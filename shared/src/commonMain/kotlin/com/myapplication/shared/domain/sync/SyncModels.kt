package com.myapplication.shared.domain.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed interface SyncMode {
    data object Local : SyncMode
    data object Supabase : SyncMode
    data object SundialServer : SyncMode

    companion object {
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
) {
    companion object {
        val initial = SyncStatus(SyncMode.Local, false, 0, null, null)
    }
}
