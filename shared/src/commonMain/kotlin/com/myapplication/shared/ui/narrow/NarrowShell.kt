package com.myapplication.shared.ui.narrow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.myapplication.shared.ui.todolist.scopeTitle

/**
 * 窄屏（<900dp）导航壳：顶部栏 + 底部五格导航。
 *
 * - [NarrowTopBar]：标题 / 搜索两种模式互斥切换（本地 searching 状态），
 *   标题随 scope 与搜索词动态变化；标题行右侧常驻同步状态指示器
 *   （旋转=同步中 / 绿勾=已连接 / 红=错误 / 灰=本地模式），点击触发立即同步；
 * - [NarrowBottomNav]：今天 / 计划 / 全部 / 已完成 / 垃圾箱 五个常驻入口，
 *   是桌面 Sidebar 智能列表在窄屏的等价物——切页只是改 mainVm.scope，
 *   不改变路由（详情/设置仍是全屏二级页面）。
 */
@Composable
fun NarrowTopBar(
    mainVm: MainViewModel,
    modifier: Modifier = Modifier,
    syncStatus: SyncStatus = SyncStatus.initial,
    onSyncNow: (() -> Unit)? = null,
) {
    val colors = LocalRemColors.current
    val scope by mainVm.scope.collectAsState()
    val query by mainVm.searchQuery.collectAsState()
    var searching by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.bgPrimary)
            .drawBehind {
                drawLine(colors.border, Offset(0f, size.height), Offset(size.width, size.height), 1f)
            }
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (searching) {
            // 搜索模式：输入框 + 退出按钮（退出时同步清空搜索词，避免残留）。
            Row(verticalAlignment = Alignment.CenterVertically) {
                RemTextField(
                    value = query,
                    onValueChange = mainVm::setSearch,
                    placeholder = "搜索",
                    leadingIcon = IconName.Search,
                    modifier = Modifier.weight(1f),
                )
                RemIconButton(
                    IconName.Close,
                    "退出搜索",
                    onClick = {
                        searching = false
                        mainVm.setSearch("")
                    },
                    size = 16.dp,
                )
            }
        } else {
            // 标题模式：当前范围标题 + 同步指示器 + 设置 / 搜索两个入口。
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.text.BasicText(
                    scopeTitle(scope, query),
                    style = RemType.title18.copy(color = colors.textHigh),
                    modifier = Modifier.weight(1f),
                )
                if (syncStatus.mode != SyncMode.Local) {
                    val syncInteraction = remember { MutableInteractionSource() }
                    val syncHovered by syncInteraction.collectIsHoveredAsState()
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(RemRadii.r2))
                            .background(if (syncHovered) colors.bgSecondary else Color.Transparent)
                            .clickable(
                                interactionSource = syncInteraction,
                                indication = null,
                                enabled = onSyncNow != null,
                            ) { onSyncNow?.invoke() }
                            .semantics { contentDescription = "同步状态，点击立即同步" },
                        contentAlignment = Alignment.Center,
                    ) {
                        RemSyncIndicator(state = syncStatus.phase(), size = 12.dp)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                RemIconButton(IconName.Settings, "设置", onClick = mainVm::openSettings, size = 18.dp)
                Spacer(Modifier.width(8.dp))
                RemIconButton(IconName.Search, "搜索", onClick = { searching = true }, size = 18.dp)
            }
        }
    }
}

/**
 * 窄屏底部导航：五个智能范围常驻入口，与 Sidebar 的 SmartGrid 同源（同一 scope 状态机）。
 */
@Composable
fun NarrowBottomNav(mainVm: MainViewModel, modifier: Modifier = Modifier) {
    val colors = LocalRemColors.current
    val scope by mainVm.scope.collectAsState()
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(colors.bgPrimary)
            .drawBehind {
                drawLine(colors.border, Offset(0f, 0f), Offset(size.width, 0f), 1f)
            }
            .navigationBarsPadding(),
    ) {
        NavItem(IconName.Today, "今天", scope == Scope.Today, Modifier.weight(1f)) { mainVm.selectScope(Scope.Today) }
        NavItem(IconName.Scheduled, "计划", scope == Scope.Scheduled, Modifier.weight(1f)) { mainVm.selectScope(Scope.Scheduled) }
        NavItem(IconName.Tray, "全部", scope == Scope.All, Modifier.weight(1f)) { mainVm.selectScope(Scope.All) }
        NavItem(IconName.CheckCircle, "已完成", scope == Scope.Completed, Modifier.weight(1f)) { mainVm.selectScope(Scope.Completed) }
        NavItem(IconName.Trash, "垃圾箱", scope == Scope.Trash, Modifier.weight(1f)) { mainVm.selectScope(Scope.Trash) }
    }
}

/** 单个导航项：图标 + 文字，选中态用品牌色与背景高亮。 */
@Composable
private fun NavItem(icon: IconName, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalRemColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val tint = if (selected) colors.brand else colors.textLow
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(RemRadii.r2))
            .background(if (selected) colors.bgSecondary else Color.Transparent)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RemIcon(icon, tint, Modifier.size(16.dp))
        Spacer(Modifier.height(2.dp))
        androidx.compose.foundation.text.BasicText(
            label,
            style = TextStyle(fontSize = 10.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                .copy(color = tint),
        )
    }
}
