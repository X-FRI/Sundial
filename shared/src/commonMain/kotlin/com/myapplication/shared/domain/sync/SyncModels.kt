package com.myapplication.shared.domain.sync

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
    val id: Long,
    val listId: Long,
    val title: String,
    val note: String,
    val dueDate: Long?,
    val isCompleted: Boolean,
    val completedAt: Long?,
    val isTrashed: Boolean,
    val trashedAt: Long?,
    val parentId: Long?,
    val sortPosition: Double,
    val flag: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val updatedBy: String,
)

@Serializable
data class ListRowDto(
    val id: Long,
    val name: String,
    val colorKey: String,
    val position: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val updatedBy: String,
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
