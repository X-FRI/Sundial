package com.myapplication.shared.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.list.DeleteListPolicy
import com.myapplication.shared.domain.list.ListStats
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.ui.components.RemButton
import com.myapplication.shared.ui.components.RemButtonVariant
import com.myapplication.shared.ui.components.RemDialog
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemType

@Composable
internal fun DeleteListDialog(
    list: TodoList,
    stats: ListStats?,
    onDismiss: () -> Unit,
    onDelete: (DeleteListPolicy) -> Unit,
) {
    val colors = LocalRemColors.current
    val statsReady = stats != null
    RemDialog(
        title = "删除 ${list.name}",
        onDismiss = onDismiss,
        confirmText = "",
        onConfirm = {},
        showButtons = false,
        content = {
            Column {
                BasicText(
                    text = "删除列表前，先选择里面任务的去向。",
                    style = RemType.text14.copy(color = colors.textNormal),
                )
                Spacer(Modifier.height(8.dp))
                BasicText(
                    text =
                        stats?.let {
                            "影响：未完成 ${it.activeCount} · 已完成 ${it.completedCount} · 已在垃圾箱 ${it.trashedCount}\n" +
                                "时间：逾期 ${it.overdueCount} · 今天 ${it.todayCount} · 无日期 ${it.noDateCount}"
                        } ?: "正在读取列表统计，加载完成后才能删除…",
                    style = RemType.text12.copy(color = colors.textLow),
                )
                Spacer(Modifier.height(16.dp))
                PolicyChoice(
                    title = "任务移到收件箱",
                    description = "只删除列表，列表里的任务保留，稍后可以重新整理。",
                    tint = colors.brand,
                    background = colors.brandSubtle,
                    buttonText = "删除列表",
                    buttonVariant = RemButtonVariant.Default,
                    enabled = statsReady,
                    onClick = {
                        onDelete(DeleteListPolicy.MoveTasksToInbox)
                        onDismiss()
                    },
                )
                Spacer(Modifier.height(8.dp))
                PolicyChoice(
                    title = "任务移到垃圾箱",
                    description = "删除列表，并把列表内未删除的任务一起移到垃圾箱。",
                    tint = colors.error,
                    background = colors.error.copy(alpha = 0.08f),
                    buttonText = "删除并移到垃圾箱",
                    buttonVariant = RemButtonVariant.Danger,
                    enabled = statsReady,
                    onClick = {
                        onDelete(DeleteListPolicy.MoveTasksToTrash)
                        onDismiss()
                    },
                )
            }
        },
    )
}

@Composable
private fun PolicyChoice(
    title: String,
    description: String,
    tint: Color,
    background: Color,
    buttonText: String,
    buttonVariant: RemButtonVariant,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalRemColors.current
    val shape = RoundedCornerShape(RemRadii.r4)
    Box(
        Modifier
            .fillMaxWidth()
            .background(background, shape)
            .border(1.dp, tint.copy(alpha = 0.34f), shape)
            .padding(12.dp),
    ) {
        Column {
            BasicText(title, style = RemType.label12.copy(color = tint))
            Spacer(Modifier.height(4.dp))
            BasicText(description, style = RemType.text12.copy(color = colors.textNormal))
            Spacer(Modifier.height(10.dp))
            RemButton(
                text = buttonText,
                onClick = onClick,
                variant = buttonVariant,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
