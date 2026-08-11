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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemBadge
import com.myapplication.shared.ui.components.RemDialog
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.components.RemTextField
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.main.Scope
import com.myapplication.shared.ui.theme.ListColorKeys
import com.myapplication.shared.ui.theme.ListColorOf
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemSpacing
import com.myapplication.shared.ui.theme.RemType

@Composable
fun Sidebar(mainVm: MainViewModel) {
    val colors = LocalRemColors.current
    val lists by mainVm.lists.collectAsState()
    val scope by mainVm.scope.collectAsState()
    val query by mainVm.searchQuery.collectAsState()
    val todayCount by mainVm.todayCount.collectAsState()
    val scheduledCount by mainVm.scheduledCount.collectAsState()
    val allCount by mainVm.allCount.collectAsState()
    val completedCount by mainVm.completedCount.collectAsState()
    val trashCount by mainVm.trashCount.collectAsState()
    val listCounts by mainVm.listCounts.collectAsState()
    var showAddList by remember { mutableStateOf(false) }
    var listsExpanded by remember { mutableStateOf(true) }

    Column(
        Modifier
            .fillMaxHeight()
            .width(220.dp)
            .background(colors.bgPrimary)
            .padding(vertical = RemSpacing.s16, horizontal = 10.dp),
    ) {
        androidx.compose.foundation.text.BasicText(
            "提醒事项",
            style = RemType.text16.copy(fontWeight = FontWeight.SemiBold, color = colors.textHigh),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(RemSpacing.s12))
        RemTextField(value = query, onValueChange = mainVm::setSearch, placeholder = "搜索", leadingIcon = IconName.Search)
        Spacer(Modifier.height(RemSpacing.s8))
        ScopeRow(IconName.Today, "今天", todayCount, scope == Scope.Today, countBadge = true) { mainVm.selectScope(Scope.Today) }
        ScopeRow(IconName.Scheduled, "计划", scheduledCount, scope == Scope.Scheduled) { mainVm.selectScope(Scope.Scheduled) }
        ScopeRow(IconName.Tray, "全部待办", allCount, scope == Scope.All) { mainVm.selectScope(Scope.All) }
        ScopeRow(IconName.CheckCircle, "已完成", completedCount, scope == Scope.Completed) { mainVm.selectScope(Scope.Completed) }
        ScopeRow(IconName.Trash, "垃圾箱", trashCount, scope == Scope.Trash) { mainVm.selectScope(Scope.Trash) }
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
    }

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
private fun ScopeRow(icon: IconName, label: String, count: Int, selected: Boolean, countBadge: Boolean = false, onClick: () -> Unit) {
    val colors = LocalRemColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val bg by animateColorAsState(
        when {
            selected -> colors.bgSecondary
            hovered -> colors.bgSecondary
            else -> Color.Transparent
        },
        tween(200),
        label = "scope-row-bg",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(RemRadii.r2))
            .background(bg)
            .border(if (focused) 1.dp else 0.dp, colors.focusRing, RoundedCornerShape(RemRadii.r2))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemIcon(icon, if (selected) colors.brand else colors.textLow, Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        androidx.compose.foundation.text.BasicText(
            label,
            style = RemType.text12.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) colors.textHigh else colors.textNormal,
            ),
            modifier = Modifier.weight(1f),
        )
        if (count > 0) {
            if (countBadge) {
                RemBadge(
                    label = count.toString(),
                    color = colors.error,
                    monospace = true,
                )
            } else {
                androidx.compose.foundation.text.BasicText(count.toString(), style = RemType.text12.copy(color = if (selected) colors.brand else colors.textLow))
            }
        }
    }
}

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
