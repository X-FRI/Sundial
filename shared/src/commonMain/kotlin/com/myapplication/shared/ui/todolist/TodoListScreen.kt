package com.myapplication.shared.ui.todolist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemBadge
import com.myapplication.shared.ui.components.RemButton
import com.myapplication.shared.ui.components.RemCheckbox
import com.myapplication.shared.ui.components.RemEmptyState
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.components.RemTextField
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.main.Scope
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemSpacing
import com.myapplication.shared.ui.theme.RemType
import com.myapplication.shared.util.DueBucket
import com.myapplication.shared.util.bucketLabel
import com.myapplication.shared.util.bucketOf
import com.myapplication.shared.util.formatDueDate
import com.myapplication.shared.util.todayDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun TodoListScreen(mainVm: MainViewModel, modifier: Modifier = Modifier) {
    val colors = LocalRemColors.current
    val todos by mainVm.todos.collectAsState()
    val scope by mainVm.scope.collectAsState()
    val query by mainVm.searchQuery.collectAsState()

    Column(modifier) {
        val activeCount = todos.count { !it.isCompleted }
        androidx.compose.foundation.text.BasicText(
            scopeTitle(scope, query),
            style = RemType.title17.copy(color = colors.textPrimary),
            modifier = Modifier.padding(horizontal = RemSpacing.s16, vertical = 14.dp),
        )
        if (scope != Scope.Trash) {
            androidx.compose.foundation.text.BasicText(
                if (scope == Scope.Completed) "${todos.size} 项" else "$activeCount 项未完成",
                style = RemType.text12.copy(color = colors.textTertiary),
                modifier = Modifier.padding(horizontal = RemSpacing.s16).padding(bottom = 8.dp),
            )
            QuickAddRow(mainVm)
        }
        val today = todayDate()
        when {
            todos.isEmpty() && query.isNotBlank() -> RemEmptyState("没有找到结果", "换个关键词试试", IconName.Search)
            todos.isEmpty() -> RemEmptyState("没有待办", "", IconName.Tray)
            scope == Scope.Scheduled -> ScheduledGrouped(todos, today, mainVm)
            scope == Scope.Trash -> TrashList(todos, mainVm)
            else -> PlainList(todos, today, mainVm)
        }
    }
}

fun scopeTitle(scope: Scope, query: String): String = when {
    query.isNotBlank() -> "搜索"
    scope == Scope.Today -> "今天"
    scope == Scope.Scheduled -> "计划"
    scope == Scope.All -> "全部待办"
    scope == Scope.Completed -> "已完成"
    scope == Scope.Trash -> "垃圾箱"
    scope is Scope.List -> "列表"
    else -> "待办"
}

@Composable
private fun QuickAddRow(mainVm: MainViewModel) {
    var text by remember { mutableStateOf("") }
    RemTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = "添加待办…（支持“明天 15:00”等日期）",
        leadingIcon = IconName.Plus,
        onEnter = {
            mainVm.addQuick(text)
            text = ""
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RemSpacing.s16),
    )
}

@Composable
private fun PlainList(todos: List<TodoItem>, today: kotlinx.datetime.LocalDate, mainVm: MainViewModel) {
    val colors = LocalRemColors.current
    val active = todos.filter { !it.isCompleted && it.parentId == null }
    val childrenByParent = todos.filter { it.parentId != null }.groupBy { it.parentId!! }
    val completed = todos.filter { it.isCompleted && it.parentId == null }
    var expanded by remember { mutableStateOf(true) }

    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        items(active, key = { it.id }) { parent ->
            TodoRow(parent, mainVm, today, showChevron = childrenByParent[parent.id] != null, expanded = expanded, onToggleExpand = { expanded = !expanded }, subtaskCount = childrenByParent[parent.id]?.size ?: 0)
            if (expanded) {
                childrenByParent[parent.id]?.forEach { child ->
                    TodoRow(child, mainVm, today, indent = true)
                }
            }
        }
        if (completed.isNotEmpty()) {
            item {
                androidx.compose.foundation.text.BasicText(
                    "已完成",
                    style = RemType.label13.copy(color = colors.textTertiary),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                )
            }
            items(completed, key = { it.id }) { item -> TodoRow(item, mainVm, today) }
        }
    }
}

@Composable
private fun ScheduledGrouped(todos: List<TodoItem>, today: kotlinx.datetime.LocalDate, mainVm: MainViewModel) {
    val colors = LocalRemColors.current
    val tz = TimeZone.currentSystemDefault()
    val grouped = todos
        .filter { it.dueDate != null }
        .groupBy { bucketOf(it.dueDate!!.toLocalDateTime(tz).date, today) }
    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        listOf(
            com.myapplication.shared.util.DueBucket.OVERDUE,
            com.myapplication.shared.util.DueBucket.TODAY,
            com.myapplication.shared.util.DueBucket.TOMORROW,
            com.myapplication.shared.util.DueBucket.THIS_WEEK,
            com.myapplication.shared.util.DueBucket.LATER,
        ).forEach { bucket ->
            val items = grouped[bucket].orEmpty()
            if (items.isNotEmpty()) {
                item {
                    androidx.compose.foundation.text.BasicText(
                        bucketLabel(bucket),
                        style = RemType.label13.copy(color = colors.textTertiary),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                    )
                }
                items(items, key = { it.id }) { item -> TodoRow(item, mainVm, today) }
            }
        }
    }
}

@Composable
private fun TrashList(todos: List<TodoItem>, mainVm: MainViewModel) {
    val colors = LocalRemColors.current
    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        items(todos, key = { it.id }) { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.foundation.text.BasicText(
                    item.title,
                    style = RemType.text13.copy(color = colors.textPrimary),
                    modifier = Modifier.weight(1f),
                )
                RemButton("恢复", onClick = { mainVm.restore(item) })
                Spacer(Modifier.width(8.dp))
                RemButton("彻底删除", onClick = { mainVm.deleteForever(item) }, danger = true)
            }
        }
    }
}

@Composable
private fun TodoBadge(item: TodoItem, today: kotlinx.datetime.LocalDate) {
    val colors = LocalRemColors.current
    val due = item.dueDate ?: return
    val date = due.toLocalDateTime(TimeZone.currentSystemDefault()).date
    val bucket = bucketOf(date, today)
    val label = formatDueDate(due)
    val (bg, fg) = when (bucket) {
        DueBucket.OVERDUE -> colors.overdueBadgeBg to colors.overdueBadgeText
        DueBucket.TODAY -> colors.todayBadgeBg to colors.todayBadgeText
        else -> colors.upcomingBadgeBg to colors.upcomingBadgeText
    }
    RemBadge(
        label = label,
        bg = bg,
        tint = fg,
        icon = { RemIcon(IconName.Calendar, fg, Modifier.size(10.dp)) },
    )
}

@Composable
fun TodoRow(
    item: TodoItem,
    mainVm: MainViewModel,
    today: kotlinx.datetime.LocalDate,
    indent: Boolean = false,
    showChevron: Boolean = false,
    expanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
    subtaskCount: Int = 0,
) {
    val colors = LocalRemColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interactionSource, indication = null) {
                if (showChevron) onToggleExpand() else mainVm.openDetail(item.id)
            }
            .background(if (hovered) colors.selectedBg.copy(alpha = 0.6f) else Color.Transparent)
            .drawBehind {
                drawLine(colors.rowDivider, Offset(0f, size.height), Offset(size.width, size.height), 1f)
            }
            .padding(start = if (indent) 16.dp else 0.dp)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemCheckbox(item.isCompleted, { mainVm.toggleCompleted(item) })
        Spacer(Modifier.width(10.dp))
        Column(
            Modifier.weight(1f).clickable { mainVm.openDetail(item.id) },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.text.BasicText(
                    item.title,
                    style = RemType.text13.copy(
                        color = if (item.isCompleted) colors.textTertiary else colors.textPrimary,
                        textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
                        fontWeight = if (item.isCompleted) FontWeight.Normal else FontWeight.Medium,
                    ),
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.flag) {
                    Spacer(Modifier.width(6.dp))
                    RemIcon(IconName.Flag, colors.flagColor, Modifier.size(14.dp))
                }
            }
            if (item.isCompleted) {
                item.completedAt?.let {
                    androidx.compose.foundation.text.BasicText(
                        "已完成 ${formatDueDate(it, TimeZone.currentSystemDefault(), today)}",
                        style = RemType.text12.copy(color = colors.textTertiary),
                    )
                }
            } else if (item.note.isNotBlank() || subtaskCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.note.isNotBlank()) {
                        androidx.compose.foundation.text.BasicText(
                            item.note,
                            style = RemType.text12.copy(color = colors.textTertiary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (subtaskCount > 0) {
                        Spacer(Modifier.width(6.dp))
                        androidx.compose.foundation.text.BasicText(
                            "⌄ $subtaskCount",
                            style = RemType.text12.copy(color = colors.textTertiary),
                        )
                    }
                }
            }
        }
        if (showChevron) {
            RemIcon(if (expanded) IconName.ChevronDown else IconName.ChevronRight, colors.textTertiary, Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
        }
        TodoBadge(item, today)
    }
}
