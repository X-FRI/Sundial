package com.myapplication.shared.ui.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.sync.SyncMode
import com.myapplication.shared.domain.sync.SyncStatus
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.components.RemIconButton
import com.myapplication.shared.ui.components.RemSyncIndicator
import com.myapplication.shared.ui.components.RemTextField
import com.myapplication.shared.ui.components.SundialNavRow
import com.myapplication.shared.ui.design.SundialDestination
import com.myapplication.shared.ui.design.destinationForScope
import com.myapplication.shared.ui.design.scopeForDestination
import com.myapplication.shared.ui.design.sundialPrimaryDestinations
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.main.Scope
import com.myapplication.shared.ui.sync.phase
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemType

@Composable
fun SidebarNav(
    mainVm: MainViewModel,
    syncStatus: SyncStatus,
    onSyncNow: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalRemColors.current
    val scope by mainVm.scope.collectAsState()
    val query by mainVm.searchQuery.collectAsState()
    val lists by mainVm.lists.collectAsState()
    val todayCount by mainVm.todayCount.collectAsState()
    val scheduledCount by mainVm.scheduledCount.collectAsState()
    val allCount by mainVm.allCount.collectAsState()
    val completedCount by mainVm.completedCount.collectAsState()
    val trashCount by mainVm.trashCount.collectAsState()
    val listCounts by mainVm.listCounts.collectAsState()

    Column(
        modifier
            .fillMaxHeight()
            .width(272.dp)
            .background(colors.bgPrimary)
            .border(1.dp, colors.borderSubtle)
            .statusBarsPadding()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RemIcon(IconName.Today, colors.brand, Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            androidx.compose.foundation.text.BasicText("Sundial", style = RemType.title20.copy(color = colors.textHigh))
        }
        Spacer(Modifier.height(18.dp))
        RemTextField(value = query, onValueChange = mainVm::setSearch, placeholder = "搜索", leadingIcon = IconName.Search)
        Spacer(Modifier.height(18.dp))
        val destination = destinationForScope(scope)
        sundialPrimaryDestinations().forEach { item ->
            SundialNavRow(
                icon = item.icon,
                label = item.label,
                count = when (item.destination) {
                    SundialDestination.Workbench -> allCount
                    SundialDestination.Lists -> lists.size
                    SundialDestination.Analytics -> null
                },
                selected = destination == item.destination,
                primary = true,
                onClick = { mainVm.selectScope(scopeForDestination(item.destination, lists)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(18.dp))
        val listMode = scope is Scope.List
        if (listMode) {
            androidx.compose.foundation.text.BasicText("列表", style = RemType.label12.copy(color = colors.textLow))
            Spacer(Modifier.height(6.dp))
            lists.forEach { list ->
                SundialNavRow(
                    icon = IconName.Inbox,
                    label = list.name,
                    count = listCounts[list.id] ?: 0,
                    selected = scope == Scope.List(list.id),
                    primary = false,
                    onClick = { mainVm.selectScope(Scope.List(list.id)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            androidx.compose.foundation.text.BasicText("工作台视图", style = RemType.label12.copy(color = colors.textLow))
            Spacer(Modifier.height(6.dp))
            SundialNavRow(
                icon = IconName.Layers,
                label = "全部",
                count = allCount,
                selected = scope == Scope.All,
                primary = false,
                onClick = { mainVm.selectScope(Scope.All) },
                modifier = Modifier.fillMaxWidth(),
            )
            SundialNavRow(
                icon = IconName.Today,
                label = "今天",
                count = todayCount,
                selected = scope == Scope.Today,
                primary = false,
                onClick = { mainVm.selectScope(Scope.Today) },
                modifier = Modifier.fillMaxWidth(),
            )
            SundialNavRow(
                icon = IconName.Scheduled,
                label = "计划",
                count = scheduledCount,
                selected = scope == Scope.Scheduled,
                primary = false,
                onClick = { mainVm.selectScope(Scope.Scheduled) },
                modifier = Modifier.fillMaxWidth(),
            )
            SundialNavRow(
                icon = IconName.CheckCircle,
                label = "已完成",
                count = completedCount,
                selected = scope == Scope.Completed,
                primary = false,
                onClick = { mainVm.selectScope(Scope.Completed) },
                modifier = Modifier.fillMaxWidth(),
            )
            SundialNavRow(
                icon = IconName.Trash,
                label = "垃圾箱",
                count = trashCount,
                selected = scope == Scope.Trash,
                primary = false,
                onClick = { mainVm.selectScope(Scope.Trash) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.weight(1f))
        SyncFooter(syncStatus, mainVm::openSettings, onSyncNow)
    }
}

@Composable
private fun SyncFooter(syncStatus: SyncStatus, onSettings: () -> Unit, onSyncNow: (() -> Unit)?) {
    val colors = LocalRemColors.current
    val label = when {
        syncStatus.mode == SyncMode.Local -> "本地模式"
        syncStatus.syncing -> "同步中…"
        syncStatus.connected -> "已同步"
        else -> "同步中断"
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RemSyncIndicator(syncStatus.phase(), size = 12.dp)
        Spacer(Modifier.width(8.dp))
        androidx.compose.foundation.text.BasicText(label, style = RemType.text12.copy(color = colors.textLow), modifier = Modifier.weight(1f))
        RemIconButton(IconName.Settings, "设置", onClick = onSettings, size = 16.dp)
        if (onSyncNow != null && syncStatus.mode != SyncMode.Local) {
            RemIconButton(IconName.Sync, "立即同步", onClick = onSyncNow, size = 14.dp)
        }
    }
}
