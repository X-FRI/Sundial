package com.myapplication.shared.ui.sidebar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.domain.sync.SyncMode
import com.myapplication.shared.domain.sync.SyncStatus
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemDialog
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.components.RemIconButton
import com.myapplication.shared.ui.components.RemSyncIndicator
import com.myapplication.shared.ui.components.RemTextField
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.main.Scope
import com.myapplication.shared.ui.sync.phase
import com.myapplication.shared.ui.theme.ListColorKeys
import com.myapplication.shared.ui.theme.ListColorOf
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemSpacing
import com.myapplication.shared.ui.theme.RemType

@Composable
private fun SidebarLogo(modifier: Modifier = Modifier) {
    val colors = LocalRemColors.current
    androidx.compose.foundation.Canvas(modifier.size(20.dp)) {
        val w = size.minDimension
        val stroke = w * 0.05f
        val dotR = w * 0.08f
        drawRoundRect(
            color = colors.brand,
            cornerRadius = CornerRadius(w * 0.24f, w * 0.24f),
            style = Stroke(width = stroke),
        )
        listOf(0.22f, 0.5f, 0.78f).forEachIndexed { i, cy ->
            drawCircle(colors.brand, dotR, center = Offset(w * 0.15f, w * cy))
            drawLine(
                colors.brand,
                Offset(w * 0.32f, w * cy),
                Offset(w * (0.85f - i * 0.12f), w * cy),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * 侧边栏（宽屏三栏布局的最左栏）：Logo、搜索框、智能列表九宫格、自定义列表、设置入口。
 *
 * 布局结构（自上而下）：
 * 1. 品牌区（Logo + 名称）与搜索框；
 * 2. 「智能列表」[SmartGrid]：今天 / 计划 / 全部 / 已完成 / 垃圾箱 五张卡片；
 * 3. 「我的列表」：可展开/收起，每行一个自定义列表（带计数、hover 快捷操作、长按删除）；
 * 4. 底部固定：添加列表入口 + 设置入口（带同步状态指示灯与摘要文案）。
 *
 * 交互要点：
 * - 列表展开状态 [listsExpanded] 是本地 remember 状态，仅影响本栏显示；
 * - 添加列表弹窗的显隐 [showAddList] 同样为本地状态，确认后回调 mainVm.addList；
 * - 所有 hover 背景用 animateColorAsState 做 200ms 过渡，减少生硬跳变。
 */
@Composable
fun Sidebar(mainVm: MainViewModel, syncStatus: SyncStatus = SyncStatus.initial, onSyncNow: (() -> Unit)? = null) {
    val colors = LocalRemColors.current
    val lists by mainVm.lists.collectAsState()
    val scope by mainVm.scope.collectAsState()
    val query by mainVm.searchQuery.collectAsState()
    // 智能列表计数：全部来自 mainVm 的独立计数流，避免在此订阅完整列表。
    val todayCount by mainVm.todayCount.collectAsState()
    val scheduledCount by mainVm.scheduledCount.collectAsState()
    val allCount by mainVm.allCount.collectAsState()
    val completedCount by mainVm.completedCount.collectAsState()
    val trashCount by mainVm.trashCount.collectAsState()
    val listCounts by mainVm.listCounts.collectAsState()
    // 设置入口右侧的同步摘要文案：本地模式 / 同步中 / 已同步 / 同步中断 + 待同步条数。
    val syncSummary = when {
        syncStatus.mode == SyncMode.Local -> "本地模式"
        syncStatus.syncing -> "同步中…"
        syncStatus.connected -> "已同步"
        else -> "同步中断"
    } + if (syncStatus.pendingCount > 0 && syncStatus.mode != SyncMode.Local) " · 待同步 ${syncStatus.pendingCount}" else ""
    var showAddList by remember { mutableStateOf(false) }
    // 「我的列表」默认展开；用户点击头部可折叠。
    var listsExpanded by remember { mutableStateOf(true) }

    Column(
        Modifier
            .fillMaxHeight()
            .width(232.dp)
            .background(colors.bgPrimary)
            .statusBarsPadding()
            .padding(vertical = RemSpacing.s16, horizontal = 10.dp),
    ) {
        // 1. 品牌区：Canvas 绘制的太阳钟 Logo + 应用名。
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            SidebarLogo()
            Spacer(Modifier.width(6.dp))
            androidx.compose.foundation.text.BasicText(
                "Sundial",
                style = RemType.text16.copy(fontWeight = FontWeight.SemiBold, color = colors.textHigh),
            )
        }
        Spacer(Modifier.height(RemSpacing.s12))
        // 2. 搜索框：直接写回 mainVm.searchQuery，触发主列表切换到搜索查询。
        RemTextField(value = query, onValueChange = mainVm::setSearch, placeholder = "搜索", leadingIcon = IconName.Search)
        Spacer(Modifier.height(RemSpacing.s12))
        androidx.compose.foundation.text.BasicText(
            "智能列表",
            style = RemType.label10.copy(color = colors.textLow),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
        // 3. 智能列表网格：5 张卡片，选中态由 scope 决定。
        SmartGrid(
            todayCount = todayCount,
            scheduledCount = scheduledCount,
            allCount = allCount,
            completedCount = completedCount,
            trashCount = trashCount,
            selectedScope = scope,
            onSelect = mainVm::selectScope,
        )
        Spacer(Modifier.height(RemSpacing.s8))
        // 4. 「我的列表」折叠头：点击切换展开/收起，箭头随状态旋转 90°。
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { listsExpanded = !listsExpanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemIcon(
                IconName.ChevronRight,
                colors.textLow,
                Modifier.size(12.dp).graphicsLayer { rotationZ = if (listsExpanded) 90f else 0f },
            )
            Spacer(Modifier.width(4.dp))
            androidx.compose.foundation.text.BasicText("我的列表", style = RemType.label10.copy(color = colors.textLow))
        }
        // 5. 展开状态下逐行渲染自定义列表（收件箱 position==0 不可删除）。
        if (listsExpanded) {
            lists.forEach { list ->
                ListRow(
                    list = list,
                    count = listCounts[list.id] ?: 0,
                    selected = scope == Scope.List(list.id),
                    canDelete = list.position != 0,
                    onSelect = { mainVm.selectScope(Scope.List(list.id)) },
                    onDelete = { mainVm.deleteList(list) },
                )
            }
        }
        Spacer(Modifier.weight(1f))
        // 6. 底部「添加列表」：hover 背景动画，点击弹出 AddListDialog。
        val addListSource = remember { MutableInteractionSource() }
        val addListHovered by addListSource.collectIsHoveredAsState()
        val addListBg by animateColorAsState(
            if (addListHovered) colors.bgSecondary else Color.Transparent,
            tween(200),
            label = "add-list-bg",
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(RemRadii.r2))
                .background(addListBg, RoundedCornerShape(RemRadii.r2))
                .clickable(interactionSource = addListSource, indication = null) { showAddList = true }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemIcon(IconName.Plus, colors.brand, Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            androidx.compose.foundation.text.BasicText("添加列表", style = RemType.label12.copy(color = colors.brand))
        }
        Spacer(Modifier.height(RemSpacing.s8))
        // 7. 设置入口：同步状态指示器（本地灰 / 同步中旋转 / 已同步绿 / 出错红）+ 摘要文案 + 立即同步按钮。
        val settingsSource = remember { MutableInteractionSource() }
        val settingsHovered by settingsSource.collectIsHoveredAsState()
        val settingsBg by animateColorAsState(
            if (settingsHovered) colors.bgSecondary else Color.Transparent,
            tween(200),
            label = "settings-bg",
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(RemRadii.r2))
                .background(settingsBg, RoundedCornerShape(RemRadii.r2))
                .clickable(interactionSource = settingsSource, indication = null) { mainVm.openSettings() }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemSyncIndicator(state = syncStatus.phase(), size = 10.dp)
            Spacer(Modifier.width(6.dp))
            androidx.compose.foundation.text.BasicText("设置", style = RemType.label12.copy(color = colors.textNormal))
            Spacer(Modifier.weight(1f))
            androidx.compose.foundation.text.BasicText(syncSummary, style = RemType.text10.copy(color = colors.textLow))
            if (onSyncNow != null && syncStatus.mode != SyncMode.Local) {
                Spacer(Modifier.width(2.dp))
                RemIconButton(IconName.Sync, "立即同步", onClick = onSyncNow, size = 12.dp)
            }
        }
    }

    // 添加列表弹窗：确认后回调 mainVm.addList 并关闭本弹窗。
    if (showAddList) {
        AddListDialog(
            onDismiss = { showAddList = false },
        ) { name, color ->
            mainVm.addList(name, color)
            showAddList = false
        }
    }
}

@Composable
private fun SmartIcon(icon: IconName, base: Color, size: Dp = 44.dp, iconSize: Dp = 22.dp, modifier: Modifier = Modifier) {    val shape = RoundedCornerShape(percent = 24)
    val brush = remember(base) {
        Brush.verticalGradient(
            listOf(base, darken(base, 0.14f)),
        )
    }
    Box(
        modifier
            .shadow(
                elevation = 2.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.20f),
                spotColor = Color.Black.copy(alpha = 0.20f),
            )
            .size(size)
            .clip(shape)
            .background(brush),
        contentAlignment = Alignment.Center,
    ) {
        RemIcon(icon, Color.White, Modifier.size(iconSize))
    }
}

private fun darken(c: Color, f: Float): Color = Color(c.red * (1f - f), c.green * (1f - f), c.blue * (1f - f), c.alpha)

/**
 * 智能列表九宫格：五行两列的卡片网格（垃圾箱独占一行）。
 *
 * 布局：两行 2×2（今天/计划，全部/已完成）+ 最后一行 1 格（垃圾箱），
 * 每张卡片等宽（weight(1f)），行内用 8dp 间隔。
 */
@Composable
private fun SmartGrid(
    todayCount: Int,
    scheduledCount: Int,
    allCount: Int,
    completedCount: Int,
    trashCount: Int,
    selectedScope: Scope,
    onSelect: (Scope) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            SmartCard(
                IconName.Today, "今天", todayCount, selectedScope == Scope.Today, Color(0xFFFF3B30),
                Modifier.weight(1f),
            ) { onSelect(Scope.Today) }
            Spacer(Modifier.width(8.dp))
            SmartCard(
                IconName.Scheduled, "计划", scheduledCount, selectedScope == Scope.Scheduled, Color(0xFFFF9500),
                Modifier.weight(1f),
            ) { onSelect(Scope.Scheduled) }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            SmartCard(
                IconName.Tray, "全部", allCount, selectedScope == Scope.All, Color(0xFF8E8E93),
                Modifier.weight(1f),
            ) { onSelect(Scope.All) }
            Spacer(Modifier.width(8.dp))
            SmartCard(
                IconName.CheckCircle, "已完成", completedCount, selectedScope == Scope.Completed, Color(0xFF34C759),
                Modifier.weight(1f),
            ) { onSelect(Scope.Completed) }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            SmartCard(
                IconName.Trash, "垃圾箱", trashCount, selectedScope == Scope.Trash, Color(0xFF636366),
                Modifier.weight(1f),
            ) { onSelect(Scope.Trash) }
            Spacer(Modifier.width(8.dp).weight(1f))
        }
    }
}

@Composable
private fun SmartCard(
    icon: IconName,
    label: String,
    count: Int,
    selected: Boolean,
    iconBg: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalRemColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val bg by animateColorAsState(
        if (hovered || selected) colors.bgSecondary else colors.bgPrimary,
        tween(200),
        label = "smart-card-bg",
    )
    val ringColor = when {
        selected -> colors.brand
        focused -> colors.focusRing
        else -> Color.Transparent
    }
    Box(
        modifier
            .height(104.dp)
            .clip(RoundedCornerShape(RemRadii.r2))
            .background(bg)
            .border(
                if (ringColor != Color.Transparent) 1.dp else 0.dp,
                ringColor,
                RoundedCornerShape(RemRadii.r2),
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SmartIcon(icon, iconBg, size = 44.dp, iconSize = 22.dp)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.text.BasicText(
                    label,
                    style = RemType.text12.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) colors.textHigh else colors.textNormal,
                    ),
                )
                if (count > 0) {
                    Spacer(Modifier.width(4.dp))
                    androidx.compose.foundation.text.BasicText(
                        count.toString(),
                        style = RemType.text10.copy(color = colors.textLow, fontFamily = FontFamily.Monospace),
                    )
                }
            }
        }
    }
}

/**
 * 自定义列表行：选中高亮 + hover 快捷操作 + 长按删除。
 *
 * 交互：
 * - 单击选中该列表；有子任务的展开逻辑不在此列（列表本身无层级）；
 * - 长按弹出删除确认（仅 [canDelete] 为 true 时，即收件箱不可删）；
 * - hover 时右侧浮现两个 16dp 小按钮：「跳转」（等价于单击）与「删除」，
 *   删除同样需要二次确认，避免误触。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListRow(
    list: TodoList,
    count: Int,
    selected: Boolean,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalRemColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val plusSource = remember { MutableInteractionSource() }
    val plusHovered by plusSource.collectIsHoveredAsState()
    val trashSource = remember { MutableInteractionSource() }
    val trashHovered by trashSource.collectIsHoveredAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        when {
            selected -> colors.bgSecondary
            hovered -> colors.bgSecondary
            else -> Color.Transparent
        },
        tween(200),
        label = "list-row-bg",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(RemRadii.r2))
            .background(bg)
            .border(if (focused) 1.dp else 0.dp, colors.focusRing, RoundedCornerShape(RemRadii.r2))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelect,
                onLongClick = if (canDelete) ({ confirmDelete = true }) else null,
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(ListColorOf[list.colorKey] ?: Color.Gray, CircleShape))
        Spacer(Modifier.width(8.dp))
        androidx.compose.foundation.text.BasicText(
            list.name,
            style = RemType.text12.copy(color = if (selected) colors.textHigh else colors.textNormal),
            modifier = Modifier.weight(1f),
        )
        if (count > 0) {
            androidx.compose.foundation.text.BasicText(
                count.toString(),
                style = RemType.text12.copy(color = colors.textLow, fontFamily = FontFamily.Monospace),
            )
        }
        if (hovered) {
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(RemRadii.r2))
                    .background(
                        if (plusHovered) colors.bgSecondary else Color.Transparent,
                        RoundedCornerShape(RemRadii.r2),
                    )
                    .clickable(interactionSource = plusSource, indication = null) { onSelect() }
                    .semantics { contentDescription = "跳转到${list.name}" },
                contentAlignment = Alignment.Center,
            ) {
                RemIcon(IconName.Plus, colors.textLow, Modifier.size(12.dp))
            }
            if (canDelete) {
                Spacer(Modifier.width(2.dp))
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(RemRadii.r2))
                        .background(
                            if (trashHovered) colors.bgSecondary else Color.Transparent,
                            RoundedCornerShape(RemRadii.r2),
                        )
                        .clickable(interactionSource = trashSource, indication = null) { confirmDelete = true }
                        .semantics { contentDescription = "删除列表" },
                    contentAlignment = Alignment.Center,
                ) {
                    RemIcon(IconName.Trash, if (trashHovered) colors.error else colors.textLow, Modifier.size(12.dp))
                }
            }
        }
    }
    if (confirmDelete) {
        RemDialog(
            title = "删除列表",
            onDismiss = { confirmDelete = false },
            content = {
                androidx.compose.foundation.text.BasicText(
                    "确定删除列表「${list.name}」？该列表的所有待办将移入垃圾箱。",
                    style = RemType.text14.copy(color = colors.textNormal),
                )
            },
            confirmText = "删除",
            confirmDanger = true,
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
        )
    }
}

/**
 * 新建列表对话框：名称输入 + 8 色色板单选。
 *
 * 色板默认选中 ListColorKeys 第一项；确认时名称为空则忽略（不回调）。
 */
@Composable
private fun AddListDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    val colorNames = mapOf("blue" to "蓝色", "red" to "红色", "orange" to "橙色", "yellow" to "黄色", "green" to "绿色", "teal" to "青色", "purple" to "紫色")
    var name by remember { mutableStateOf("") }
    var colorKey by remember { mutableStateOf(ListColorKeys.first()) }
    RemDialog(
        title = "新建列表",
        onDismiss = onDismiss,
        confirmText = "确定",
        confirmDanger = false,
        onConfirm = {
            if (name.isNotBlank()) onConfirm(name.trim(), colorKey)
        },
        content = {
            RemTextField(value = name, onValueChange = { name = it }, placeholder = "列表名称")
            Spacer(Modifier.height(RemSpacing.s12))
            Row {
                ListColorKeys.forEach { key ->
                    val checkTint = if (key == "yellow") Color(0xFF3A3A3C) else Color.White
                    Box(
                        Modifier
                            .padding(end = 8.dp)
                            .size(24.dp)
                            .background(ListColorOf[key] ?: Color.Gray, CircleShape)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { colorKey = key }
                            .semantics {
                                contentDescription = colorNames[key] ?: key
                                selected = key == colorKey
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (key == colorKey) {
                            RemIcon(IconName.CheckCircle, checkTint, Modifier.size(14.dp))
                        }
                    }
                }
            }
        },
    )
}
