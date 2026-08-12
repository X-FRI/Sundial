package com.myapplication.shared.ui.sidebar

import androidx.compose.runtime.Composable
import com.myapplication.shared.domain.sync.SyncStatus
import com.myapplication.shared.ui.main.MainViewModel

/**
 * 侧边栏（宽屏三栏布局的最左栏）：品牌区、搜索框、智能范围与自定义列表的紧凑导航行、同步状态页脚。
 *
 * 实现委托给 [SidebarNav]。
 */
@Composable
fun Sidebar(mainVm: MainViewModel, syncStatus: SyncStatus = SyncStatus.initial, onSyncNow: (() -> Unit)? = null) {
    SidebarNav(mainVm = mainVm, syncStatus = syncStatus, onSyncNow = onSyncNow)
}
