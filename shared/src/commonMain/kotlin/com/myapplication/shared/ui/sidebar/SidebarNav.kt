package com.myapplication.shared.ui.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.sync.SyncMode
import com.myapplication.shared.domain.sync.SyncStatus
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.components.RemIconButton
import com.myapplication.shared.ui.components.RemSyncIndicator
import com.myapplication.shared.ui.components.RemTextField
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.main.Scope
import com.myapplication.shared.ui.sync.phase
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemRadii
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
            .width(260.dp)
            .background(colors.surfaceAlt)
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
        NavRow(IconName.Today, "今天", todayCount, scope == Scope.Today) { mainVm.selectScope(Scope.Today) }
        NavRow(IconName.Scheduled, "计划", scheduledCount, scope == Scope.Scheduled) { mainVm.selectScope(Scope.Scheduled) }
        NavRow(IconName.Layers, "全部", allCount, scope == Scope.All) { mainVm.selectScope(Scope.All) }
        NavRow(IconName.CheckCircle, "已完成", completedCount, scope == Scope.Completed) { mainVm.selectScope(Scope.Completed) }
        NavRow(IconName.Trash, "垃圾箱", trashCount, scope == Scope.Trash) { mainVm.selectScope(Scope.Trash) }
        Spacer(Modifier.height(18.dp))
        androidx.compose.foundation.text.BasicText("我的列表", style = RemType.label12.copy(color = colors.textLow))
        Spacer(Modifier.height(6.dp))
        lists.forEach { list ->
            NavRow(IconName.Inbox, list.name, listCounts[list.id] ?: 0, scope == Scope.List(list.id)) {
                mainVm.selectScope(Scope.List(list.id))
            }
        }
        Spacer(Modifier.weight(1f))
        SyncFooter(syncStatus, mainVm::openSettings, onSyncNow)
    }
}

@Composable
private fun NavRow(icon: IconName, label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalRemColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RemRadii.r4))
            .background(if (selected) colors.brandSubtle else colors.surfaceAlt)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemIcon(icon, if (selected) colors.brand else colors.textLow, Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        androidx.compose.foundation.text.BasicText(label, style = RemType.text14.copy(color = colors.textHigh), modifier = Modifier.weight(1f))
        androidx.compose.foundation.text.BasicText(count.toString(), style = RemType.text12.copy(color = colors.textLow))
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
