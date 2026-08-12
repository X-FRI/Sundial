package com.myapplication.shared.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemBadge
import com.myapplication.shared.ui.components.RemBadgeTone
import com.myapplication.shared.ui.components.RemButton
import com.myapplication.shared.ui.components.RemButtonVariant
import com.myapplication.shared.ui.components.RemCheckbox
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.components.RemIconButton
import com.myapplication.shared.ui.components.rememberHoverBackground
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemControlSize
import com.myapplication.shared.ui.theme.RemType
import com.myapplication.shared.util.DueBucket
import com.myapplication.shared.util.bucketOf
import com.myapplication.shared.util.formatDueDate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun TaskSection(
    title: String,
    rows: List<TaskRowModel>,
    today: LocalDate,
    selectedId: Long?,
    completed: Boolean,
    onOpen: (Long) -> Unit,
    onToggleCompleted: (TodoItem) -> Unit,
    onToggleFlag: (TodoItem) -> Unit,
    modifier: Modifier = Modifier,
    rowMinHeight: androidx.compose.ui.unit.Dp = RemControlSize.rowDesktop,
    checkboxSize: androidx.compose.ui.unit.Dp = 16.dp,
    showRowContainer: Boolean = true,
    headerColor: Color? = null,
    emptyText: String = "没有待办",
) {
    val colors = LocalRemColors.current
    var expanded by remember(title) { mutableStateOf(true) }
    val titleColor = headerColor ?: if (completed) colors.textLow else colors.textHigh
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = if (showRowContainer) 4.dp else 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemIcon(if (expanded) IconName.ChevronDown else IconName.ChevronRight, titleColor, Modifier.size(14.dp))
            Spacer(Modifier.width(7.dp))
            androidx.compose.foundation.text.BasicText(
                title,
                style = RemType.label12.copy(color = titleColor),
            )
            Spacer(Modifier.width(6.dp))
            androidx.compose.foundation.text.BasicText(
                rows.size.toString(),
                style = RemType.label12.copy(color = titleColor),
            )
            Spacer(Modifier.weight(1f))
        }
        if (!expanded) return@Column
        val rowContainerModifier = if (showRowContainer) {
            Modifier
                .fillMaxWidth()
                .background(colors.surface)
        } else {
            Modifier.fillMaxWidth()
        }
        Column(
            rowContainerModifier,
        ) {
            if (rows.isEmpty()) {
                androidx.compose.foundation.text.BasicText(
                    emptyText,
                    style = RemType.text12.copy(color = colors.textLow),
                    modifier = Modifier.padding(
                        horizontal = if (showRowContainer) 10.dp else 37.dp,
                        vertical = 10.dp,
                    ),
                )
            }
            rows.forEachIndexed { index, row ->
                TaskRow(
                    model = row,
                    today = today,
                    selected = selectedId == row.item.id,
                    onOpen = { onOpen(row.item.id) },
                    onToggleCompleted = { onToggleCompleted(row.item) },
                    onToggleFlag = { onToggleFlag(row.item) },
                    rowMinHeight = rowMinHeight,
                    checkboxSize = checkboxSize,
                )
                if (!showRowContainer && index < rows.lastIndex) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 64.dp)
                            .height(1.dp)
                            .background(colors.borderSubtle),
                    )
                }
            }
        }
    }
}

@Composable
fun TrashSection(
    title: String,
    rows: List<TodoItem>,
    onRestore: (TodoItem) -> Unit,
    onDeleteForever: (TodoItem) -> Unit,
    modifier: Modifier = Modifier,
    headerColor: Color? = null,
    emptyText: String = "没有待清理项目",
) {
    val colors = LocalRemColors.current
    var expanded by remember(title) { mutableStateOf(true) }
    val titleColor = headerColor ?: colors.textHigh
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemIcon(if (expanded) IconName.ChevronDown else IconName.ChevronRight, titleColor, Modifier.size(14.dp))
            Spacer(Modifier.width(7.dp))
            androidx.compose.foundation.text.BasicText(title, style = RemType.label12.copy(color = titleColor))
            Spacer(Modifier.width(6.dp))
            androidx.compose.foundation.text.BasicText(rows.size.toString(), style = RemType.label12.copy(color = titleColor))
            Spacer(Modifier.weight(1f))
        }
        if (!expanded) return@Column
        if (rows.isEmpty()) {
            androidx.compose.foundation.text.BasicText(
                emptyText,
                style = RemType.text12.copy(color = colors.textLow),
                modifier = Modifier.padding(horizontal = 37.dp, vertical = 10.dp),
            )
        }
        rows.forEachIndexed { index, item ->
            TrashRow(
                item = item,
                onRestore = { onRestore(item) },
                onDeleteForever = { onDeleteForever(item) },
            )
            if (index < rows.lastIndex) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp)
                        .height(1.dp)
                        .background(colors.borderSubtle),
                )
            }
        }
    }
}

@Composable
fun TaskRow(
    model: TaskRowModel,
    today: LocalDate,
    selected: Boolean,
    onOpen: () -> Unit,
    onToggleCompleted: () -> Unit,
    onToggleFlag: () -> Unit,
    modifier: Modifier = Modifier,
    rowMinHeight: androidx.compose.ui.unit.Dp = RemControlSize.rowDesktop,
    checkboxSize: androidx.compose.ui.unit.Dp = 16.dp,
) {
    val colors = LocalRemColors.current
    val item = model.item
    val interaction = remember { MutableInteractionSource() }
    val hover = rememberHoverBackground(interaction)
    val rowBg = when {
        selected -> colors.brandSubtle
        else -> hover
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = rowMinHeight)
            .background(rowBg)
            .clickable(interactionSource = interaction, indication = null, onClick = onOpen)
            .semantics { contentDescription = "打开待办详情：${item.title}" }
            .padding(horizontal = 10.dp, vertical = if (rowMinHeight > RemControlSize.rowDesktop) 9.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemCheckbox(item.isCompleted, onToggleCompleted, size = checkboxSize)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            androidx.compose.foundation.text.BasicText(
                item.title,
                style = RemType.text14.copy(
                    color = if (item.isCompleted) colors.textLow else colors.textHigh,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val support = when {
                item.note.isNotBlank() -> item.note
                model.subtasks.isNotEmpty() -> "${model.subtasks.size} 个子任务"
                item.isCompleted && item.completedAt != null -> "已完成 ${formatDueDate(item.completedAt)}"
                else -> null
            }
            if (support != null) {
                androidx.compose.foundation.text.BasicText(
                    support,
                    style = RemType.text12.copy(color = colors.textLow),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DueBadge(item, today)
        Spacer(Modifier.width(8.dp))
        RemIconButton(
            IconName.Flag,
            if (item.flag) "取消旗标" else "添加旗标",
            onClick = onToggleFlag,
            tint = if (item.flag) colors.warning else null,
            size = 14.dp,
            containerSize = RemControlSize.iconSmall,
        )
    }
}

@Composable
fun TrashRow(
    item: TodoItem,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalRemColors.current
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = RemControlSize.rowDesktop)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            androidx.compose.foundation.text.BasicText(
                item.title,
                style = RemType.text14.copy(color = colors.textHigh),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.trashedAt?.let {
                androidx.compose.foundation.text.BasicText(
                    "删除于 ${formatDueDate(it)}",
                    style = RemType.text12.copy(color = colors.textLow),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        RemButton("恢复", onClick = onRestore)
        Spacer(Modifier.width(8.dp))
        RemButton("彻底删除", onClick = onDeleteForever, variant = RemButtonVariant.Danger)
    }
}

@Composable
private fun DueBadge(item: TodoItem, today: LocalDate) {    val due = item.dueDate ?: return
    val tz = TimeZone.currentSystemDefault()
    val bucket = bucketOf(due.toLocalDateTime(tz).date, today)
    val tone = when (bucket) {
        DueBucket.OVERDUE -> RemBadgeTone.Error
        DueBucket.TODAY -> RemBadgeTone.Brand
        else -> RemBadgeTone.Neutral
    }
    RemBadge(
        label = formatDueDate(due, tz, today),
        tone = tone,
        monospace = true,
        icon = { RemIcon(IconName.Calendar, LocalRemColors.current.brand, Modifier.size(10.dp)) },
    )
}
