package com.myapplication.shared.ui.sync

import com.myapplication.shared.domain.sync.SyncMode
import com.myapplication.shared.domain.sync.SyncStatus
import com.myapplication.shared.ui.components.SyncIndicatorState

// 同步状态 → 指示器相位：本地模式恒 Idle；同步中→Syncing；有错→Error；已连接→Synced；其余 Idle
fun SyncStatus.phase(): SyncIndicatorState =
    when {
        mode == SyncMode.Local -> SyncIndicatorState.Idle
        syncing -> SyncIndicatorState.Syncing
        lastError != null -> SyncIndicatorState.Error
        connected -> SyncIndicatorState.Synced
        else -> SyncIndicatorState.Idle
    }
