package com.myapplication.shared.ui.todolist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemBadge
import com.myapplication.shared.ui.components.RemButton
import com.myapplication.shared.ui.components.RemDatePicker
import com.myapplication.shared.ui.components.RemDialog
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.components.RemTextField
import com.myapplication.shared.ui.components.rememberHoverBackground
import com.myapplication.shared.ui.theme.ListColorOf
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemType
import com.myapplication.shared.util.formatDueDate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

@Composable
fun TodoFormDialog(
    lists: List<TodoList>,
    defaultListId: Long?,
    onDismiss: () -> Unit,
    onConfirm: (title: String, note: String, due: LocalDateTime?, flag: Boolean, listId: Long?) -> Unit,
) {
    val colors = LocalRemColors.current
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf<LocalDate?>(null) }
    var dueTime by remember { mutableStateOf<LocalTime?>(null) }
    var flag by remember { mutableStateOf(false) }
    var listId by remember { mutableStateOf(defaultListId) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showListPicker by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    RemDialog(
        title = "新建待办",
        onDismiss = onDismiss,
        confirmText = "添加",
        onConfirm = {
            if (title.isBlank()) return@RemDialog
            onConfirm(
                title.trim(),
                note.trim(),
                dueDate?.let { LocalDateTime(it, dueTime ?: LocalTime(9, 0)) },
                flag,
                listId,
            )
        },
        content = {
            RemTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = "标题",
                style = RemType.text16.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                focusRequester = focusRequester,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            RemTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = "备注（可选）…",
                singleLine = false,
                minLines = 2,
                style = RemType.text12,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            FormOptionRow(
                icon = IconName.Calendar,
                label = "日期",
                trailing = {
                    if (dueDate != null) {
                        RemBadge(
                            label = formatDueDate(
                                LocalDateTime(dueDate!!, dueTime ?: LocalTime(9, 0))
                                    .toInstant(TimeZone.currentSystemDefault()),
                            ),
                            color = colors.warning,
                            monospace = true,
                        )
                        Spacer(Modifier.width(8.dp))
                        RemButton("清除", onClick = {
                            dueDate = null
                            dueTime = null
                        })
                    } else {
                        androidx.compose.foundation.text.BasicText(
                            "无",
                            style = RemType.text12.copy(color = colors.textLow),
                        )
                    }
                },
                onClick = { showDatePicker = true },
            )

            FormOptionRow(
                icon = IconName.Flag,
                label = "旗标",
                value = if (flag) "已标记" else "未标记",
                valueColor = if (flag) colors.warning else colors.textLow,
                onClick = { flag = !flag },
            )

            FormOptionRow(
                icon = IconName.Tray,
                label = "列表",
                trailing = {
                    val selected = lists.firstOrNull { it.id == listId }
                    Box(Modifier.size(10.dp).clip(CircleShape).background(ListColorOf[selected?.colorKey] ?: Color.Gray))
                    Spacer(Modifier.width(6.dp))
                    androidx.compose.foundation.text.BasicText(
                        selected?.name ?: "收件箱",
                        style = RemType.text12.copy(color = colors.textHigh),
                    )
                },
                onClick = { showListPicker = true },
            )
        },
    )

    if (showDatePicker) {
        RemDatePicker(
            initialDate = dueDate,
            initialTime = dueTime,
            onPick = { date ->
                dueDate = date
                if (dueTime == null) dueTime = LocalTime(9, 0)
            },
            onPickTime = { h, m ->
                if (h == -1 && m == -1) {
                    dueTime = null
                } else {
                    dueTime = LocalTime(h, m)
                }
            },
            onDismiss = { showDatePicker = false },
        )
    }

    if (showListPicker) {
        RemDialog(
            title = "选择列表",
            onDismiss = { showListPicker = false },
            confirmText = "确定",
            onConfirm = { showListPicker = false },
            showButtons = false,
            content = {
                lists.forEach { list ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                listId = list.id
                                showListPicker = false
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(ListColorOf[list.colorKey] ?: Color.Gray))
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.foundation.text.BasicText(
                            list.name,
                            style = RemType.text14.copy(color = colors.textHigh),
                            modifier = Modifier.weight(1f),
                        )
                        if (list.id == listId) {
                            RemIcon(IconName.CheckCircle, colors.brand, Modifier.size(16.dp))
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun FormOptionRow(
    icon: IconName,
    label: String,
    onClick: () -> Unit,
    value: String? = null,
    valueColor: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalRemColors.current
    val interaction = remember { MutableInteractionSource() }
    val bg = rememberHoverBackground(interaction)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RemRadii.r4))
            .background(bg)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemIcon(icon, colors.textLow, Modifier.size(14.dp))
        Spacer(Modifier.width(10.dp))
        androidx.compose.foundation.text.BasicText(
            label,
            style = RemType.text12.copy(color = colors.textNormal),
        )
        Spacer(Modifier.weight(1f))
        if (value != null) {
            androidx.compose.foundation.text.BasicText(
                value,
                style = RemType.text12.copy(color = valueColor ?: colors.textLow),
            )
        }
        trailing?.invoke()
    }
}
