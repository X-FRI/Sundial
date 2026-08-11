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

/**
 * 新建待办表单对话框：标题 / 备注 / 日期 / 旗标 / 所属列表。
 *
 * 设计要点：
 * - 表单状态全部是对话框级本地 state，确认时一次性回传给上层（onConfirm），
 *   因此本组件与具体数据源解耦，列表页 FAB 与侧栏都能复用；
 * - 日期分「日期」与「时间」两个字段维护（dueDate / dueTime），确认时拼成
 *   LocalDateTime；未选时间默认 9:00；
 * - 打开即自动聚焦标题输入框（FocusRequester + LaunchedEffect）；
 * - 标题为空时确认无效（直接 return，不回调）。
 */
@Composable
fun TodoFormDialog(
    lists: List<TodoList>,
    defaultListId: Long?,
    onDismiss: () -> Unit,
    onConfirm: (title: String, note: String, due: LocalDateTime?, flag: Boolean, listId: Long?) -> Unit,
) {
    val colors = LocalRemColors.current
    // 表单字段：日期/时间分离保存，只在确认时合并。
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf<LocalDate?>(null) }
    var dueTime by remember { mutableStateOf<LocalTime?>(null) }
    var flag by remember { mutableStateOf(false) }
    // 默认列表：来自调用方（FAB 在列表范围内时预选该列表，否则为收件箱 null）。
    var listId by remember { mutableStateOf(defaultListId) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showListPicker by remember { mutableStateOf(false) }
    // 自动聚焦标题输入框，打开即可直接打字。
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    RemDialog(
        title = "新建待办",
        onDismiss = onDismiss,
        confirmText = "添加",
        onConfirm = {
            // 标题必填；组装完整参数后一次性回调。
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
            // 标题输入（自动聚焦）与备注输入。
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

            // 日期行：已选时显示徽标 + 清除；未选显示「无」。
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

            // 旗标行：点击切换标记状态。
            FormOptionRow(
                icon = IconName.Flag,
                label = "旗标",
                value = if (flag) "已标记" else "未标记",
                valueColor = if (flag) colors.warning else colors.textLow,
                onClick = { flag = !flag },
            )

            // 列表行：显示当前所选列表（默认「收件箱」），点击打开列表选择。
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

    // 日期选择器：只选日期时若尚无时间，默认补 9:00；
    // onPickTime(h=-1,m=-1) 表示清除时间（保留日期）。
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

    // 列表选择弹窗：点击行即选中（当前选中项带勾选标记）。
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

/** 表单行容器：图标 + 标签 + 右侧内容（value 或自定义 trailing），整行可点击。 */
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
