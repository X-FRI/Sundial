package com.myapplication.shared.ui.todolist

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemBadge
import com.myapplication.shared.ui.components.RemButton
import com.myapplication.shared.ui.components.RemButtonVariant
import com.myapplication.shared.ui.components.RemCheckbox
import com.myapplication.shared.ui.components.RemEmptyState
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.components.RemTextField
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.main.Scope
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.remGrid
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemSpacing
import com.myapplication.shared.ui.theme.RemType
import com.myapplication.shared.util.DueBucket
import com.myapplication.shared.util.bucketLabel
import com.myapplication.shared.util.bucketOf
import com.myapplication.shared.util.formatDueDate
import com.myapplication.shared.util.todayDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime

@Composable
fun TodoListScreen(mainVm: MainViewModel, modifier: Modifier = Modifier, showHeader: Boolean = true) {
    val colors = LocalRemColors.current
    val todos by mainVm.todos.collectAsState()
    val scope by mainVm.scope.collectAsState()
    val query by mainVm.searchQuery.collectAsState()
    var quickAddFocus by remember { mutableStateOf(0) }

    Column(modifier.background(colors.bgSecondary).remGrid(colors)) {
        val today = todayDate()
        val activeCount = todos.count { !it.isCompleted }
        if (showHeader) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 16.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.foundation.text.BasicText(
                    scopeTitle(scope, query),
                    style = RemType.title18.copy(color = colors.textHigh),
                    modifier = Modifier.weight(1f),
                )
                val count = if (scope == Scope.Completed) todos.size else activeCount
                if (count > 0) {
                    RemBadge(
                        label = "$count 项",
                        monospace = true,
                        color = if (scope == Scope.Today) colors.error else null,
                    )
                }
                Spacer(Modifier.width(8.dp))
                val plusInteraction = remember { MutableInteractionSource() }
                val plusHovered by plusInteraction.collectIsHoveredAsState()
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(RemRadii.r2))
                        .background(if (plusHovered) colors.bgSecondary else Color.Transparent)
                        .clickable(interactionSource = plusInteraction, indication = null) { quickAddFocus++ }
                        .semantics { contentDescription = "新建待办" },
                    contentAlignment = Alignment.Center,
                ) {
                    RemIcon(IconName.Plus, colors.textHigh, Modifier.size(14.dp))
                }
            }
        }
        androidx.compose.foundation.text.BasicText(
            if (scope == Scope.Today || scope == Scope.Scheduled) "${today.monthNumber} 月 ${today.dayOfMonth} 日 · 星期${"一二三四五六日"[today.dayOfWeek.isoDayNumber - 1]}" else if (scope == Scope.Completed) "${todos.size} 项" else "$activeCount 项未完成",
            style = RemType.text12.copy(
                color = colors.textLow,
                fontFamily = if (scope == Scope.Today || scope == Scope.Scheduled) FontFamily.Default else FontFamily.Monospace,
            ),
            modifier = Modifier.padding(start = 20.dp, top = if (showHeader) 4.dp else 10.dp, bottom = 8.dp),
        )
        if (scope != Scope.Trash) {
            QuickAddRow(mainVm, focusRequested = quickAddFocus)
        }
        when {
            todos.isEmpty() && query.isNotBlank() -> RemEmptyState("没有找到结果", "换个关键词试试", IconName.Search)
            todos.isEmpty() -> RemEmptyState("没有待办", "点击 ＋ 或输入框添加", IconName.Tray)
            scope == Scope.Today -> TodayGrouped(todos, today, mainVm)
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
private fun QuickAddRow(mainVm: MainViewModel, focusRequested: Int = 0) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequested) { if (focusRequested > 0) focusRequester.requestFocus() }
    RemTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = "添加待办…（支持“明天 15:00”等日期）",
        style = RemType.text12,
        leadingIcon = IconName.Plus,
        onEnter = {
            mainVm.addQuick(text)
            text = ""
        },
        focusRequester = focusRequester,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RemSpacing.s16),
    )
}

@Composable
private fun SectionHeader(title: String, count: Int, overdue: Boolean) {
    val colors = LocalRemColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.text.BasicText(
            title,
            style = RemType.label10.copy(color = colors.textHigh),
        )
        if (count > 0) {
            Spacer(Modifier.width(6.dp))
            RemBadge(
                label = "$count",
                monospace = true,
                color = if (overdue) colors.error else null,
            )
        }
    }
}

@Composable
private fun TodayGrouped(todos: List<TodoItem>, today: kotlinx.datetime.LocalDate, mainVm: MainViewModel) {
    val colors = LocalRemColors.current
    val tz = TimeZone.currentSystemDefault()
    val grouped = todos
        .filter { it.dueDate != null }
        .groupBy { bucketOf(it.dueDate!!.toLocalDateTime(tz).date, today) }
    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        listOf(DueBucket.OVERDUE, DueBucket.TODAY, DueBucket.LATER).forEach { bucket ->
            val items = grouped[bucket].orEmpty()
            if (items.isNotEmpty()) {
                item(key = "h-$bucket") { SectionHeader(bucketLabel(bucket), items.size, bucket == DueBucket.OVERDUE) }
                item(key = "c-$bucket") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(colors.bgPrimary, RoundedCornerShape(RemRadii.r2))
                            .padding(horizontal = 8.dp),
                    ) {
                        items.forEach { item ->
                            TodoRow(item, mainVm, today)
                        }
                    }
                }
                item(key = "sp-$bucket") { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun PlainList(todos: List<TodoItem>, today: kotlinx.datetime.LocalDate, mainVm: MainViewModel) {
    val colors = LocalRemColors.current
    val active = todos.filter { !it.isCompleted && it.parentId == null }
    val childrenByParent = todos.filter { it.parentId != null }.groupBy { it.parentId!! }
    val completed = todos.filter { it.isCompleted && it.parentId == null }
    var expanded by remember { mutableStateOf(true) }
    var completedExpanded by remember { mutableStateOf(true) }

    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (active.isNotEmpty()) {
            item(key = "active-card") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.bgPrimary, RoundedCornerShape(RemRadii.r2))
                        .padding(horizontal = 8.dp),
                ) {
                    active.forEach { parent ->
                        TodoRow(
                            parent, mainVm, today,
                            showChevron = childrenByParent[parent.id] != null,
                            expanded = expanded,
                            onToggleExpand = { expanded = !expanded },
                            subtaskCount = childrenByParent[parent.id]?.size ?: 0,
                        )
                        if (expanded) {
                            childrenByParent[parent.id]?.forEach { child ->
                                TodoRow(child, mainVm, today, indent = true)
                            }
                        }
                    }
                }
            }
            item(key = "sp-active") { Spacer(Modifier.height(8.dp)) }
        }
        if (completed.isNotEmpty()) {
            item(key = "completed-header") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { completedExpanded = !completedExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.text.BasicText("已完成", style = RemType.label12.copy(color = colors.textLow))
                    Spacer(Modifier.weight(1f))
                    RemIcon(if (completedExpanded) IconName.ChevronDown else IconName.ChevronRight, colors.textLow, Modifier.size(14.dp))
                }
            }
            if (completedExpanded) {
                item(key = "completed-card") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(colors.bgPrimary, RoundedCornerShape(RemRadii.r2))
                            .padding(horizontal = 8.dp),
                    ) {
                        completed.forEach { item -> TodoRow(item, mainVm, today) }
                    }
                }
            }
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
    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        listOf(
            DueBucket.OVERDUE,
            DueBucket.TODAY,
            DueBucket.TOMORROW,
            DueBucket.THIS_WEEK,
            DueBucket.LATER,
        ).forEach { bucket ->
            val items = grouped[bucket].orEmpty()
            if (items.isNotEmpty()) {
                item(key = "h-$bucket") { SectionHeader(bucketLabel(bucket), items.size, bucket == DueBucket.OVERDUE) }
                item(key = "c-$bucket") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(colors.bgPrimary, RoundedCornerShape(RemRadii.r2))
                            .padding(horizontal = 8.dp),
                    ) {
                        items.forEach { item ->
                            TodoRow(item, mainVm, today)
                        }
                    }
                }
                item(key = "sp-$bucket") { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun TrashList(todos: List<TodoItem>, mainVm: MainViewModel) {
    val colors = LocalRemColors.current
    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        items(todos, key = { it.id }) { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.foundation.text.BasicText(
                    item.title,
                    style = RemType.text14.copy(color = colors.textHigh),
                    modifier = Modifier.weight(1f),
                )
                RemButton("恢复", onClick = { mainVm.restore(item) })
                Spacer(Modifier.width(8.dp))
                RemButton("彻底删除", onClick = { mainVm.deleteForever(item) }, variant = RemButtonVariant.Danger)
            }
        }
    }
}

@Composable
private fun TodoBadge(item: TodoItem, today: kotlinx.datetime.LocalDate) {
    val colors = LocalRemColors.current
    val due = item.dueDate ?: return
    val tz = TimeZone.currentSystemDefault()
    val date = due.toLocalDateTime(tz).date
    val bucket = bucketOf(date, today)
    val label = formatDueDate(due, tz, today)
    val color = when (bucket) {
        DueBucket.OVERDUE -> colors.error
        DueBucket.TODAY -> colors.warning
        else -> null
    }
    RemBadge(
        label = label,
        color = color,
        monospace = true,
        icon = { RemIcon(IconName.Calendar, color ?: colors.textLow, Modifier.size(10.dp)) },
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
    val hoverBg by animateColorAsState(
        if (hovered) colors.bgSecondary else Color.Transparent,
        tween(200),
        label = "row-hover",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interactionSource, indication = null) {
                if (showChevron) onToggleExpand() else mainVm.openDetail(item.id)
            }
            .background(hoverBg)
            .padding(start = if (indent) 16.dp else 0.dp)
            .padding(vertical = 6.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemCheckbox(item.isCompleted, { mainVm.toggleCompleted(item) }, size = 14.dp)
        Spacer(Modifier.width(10.dp))
        Column(
            Modifier
                .weight(1f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { mainVm.openDetail(item.id) },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.text.BasicText(
                    item.title,
                    style = RemType.text14.copy(
                        color = if (item.isCompleted) colors.textLow else colors.textHigh,
                        textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.flag) {
                    Spacer(Modifier.width(6.dp))
                    RemIcon(IconName.Flag, colors.warning, Modifier.size(14.dp))
                }
            }
            if (item.isCompleted) {
                item.completedAt?.let {
                    androidx.compose.foundation.text.BasicText(
                        "已完成 ${formatDueDate(it, TimeZone.currentSystemDefault(), today)}",
                        style = RemType.text12.copy(color = colors.textLow),
                    )
                }
            } else if (item.note.isNotBlank() || subtaskCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.note.isNotBlank()) {
                        androidx.compose.foundation.text.BasicText(
                            item.note,
                            style = RemType.text12.copy(color = colors.textLow),
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
                            style = RemType.text12.copy(color = colors.textLow, fontFamily = FontFamily.Monospace),
                        )
                    }
                }
            }
        }
        if (showChevron) {
            RemIcon(if (expanded) IconName.ChevronDown else IconName.ChevronRight, colors.textLow, Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
        }
        TodoBadge(item, today)
    }
}
