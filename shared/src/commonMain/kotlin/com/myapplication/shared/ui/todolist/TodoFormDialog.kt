package com.myapplication.shared.ui.todolist

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemBadge
import com.myapplication.shared.ui.components.RemButton
import com.myapplication.shared.ui.components.RemButtonVariant
import com.myapplication.shared.ui.components.RemDatePicker
import com.myapplication.shared.ui.components.RemDialog
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.components.RemTextField
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemType
import com.myapplication.shared.util.formatDueDate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

@Composable
fun TodoFormDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, note: String, due: LocalDateTime?) -> Unit,
) {
    val colors = LocalRemColors.current
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf<LocalDate?>(null) }
    var dueTime by remember { mutableStateOf<LocalTime?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
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
            )
        },
        content = {
            RemTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = "标题",
                style = RemType.text14,
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
            val dateInteraction = remember { MutableInteractionSource() }
            val dateHovered by dateInteraction.collectIsHoveredAsState()
            val dateBg by animateColorAsState(
                if (dateHovered) colors.bgSecondary else Color.Transparent,
                tween(200),
                label = "form-date-bg",
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(dateBg)
                    .clickable(
                        interactionSource = dateInteraction,
                        indication = null,
                    ) { showDatePicker = true }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RemIcon(IconName.Calendar, colors.textLow, Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                androidx.compose.foundation.text.BasicText(
                    "日期",
                    style = RemType.text12.copy(color = colors.textNormal),
                )
                Spacer(Modifier.weight(1f))
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
            }
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
}
