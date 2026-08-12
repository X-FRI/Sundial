package com.myapplication.shared.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemBadge
import com.myapplication.shared.ui.components.RemBadgeTone
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
) {
    val colors = LocalRemColors.current
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.text.BasicText(
                title,
                style = RemType.label12.copy(color = if (completed) colors.textLow else colors.textHigh),
            )
            Spacer(Modifier.width(6.dp))
            androidx.compose.foundation.text.BasicText(
                rows.size.toString(),
                style = RemType.text12.copy(color = colors.textLow),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surface),
        ) {
            rows.forEach { row ->
                TaskRow(
                    model = row,
                    today = today,
                    selected = selectedId == row.item.id,
                    onOpen = { onOpen(row.item.id) },
                    onToggleCompleted = { onToggleCompleted(row.item) },
                    onToggleFlag = { onToggleFlag(row.item) },
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
            .heightIn(min = RemControlSize.rowDesktop)
            .background(rowBg)
            .clickable(interactionSource = interaction, indication = null, onClick = onOpen)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemCheckbox(item.isCompleted, onToggleCompleted, size = 16.dp)
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
private fun DueBadge(item: TodoItem, today: LocalDate) {
    val due = item.dueDate ?: return
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
