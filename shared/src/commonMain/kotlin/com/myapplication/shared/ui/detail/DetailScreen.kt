package com.myapplication.shared.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myapplication.shared.di.AppGraph
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemBadge
import com.myapplication.shared.ui.components.RemButton
import com.myapplication.shared.ui.components.RemCheckbox
import com.myapplication.shared.ui.components.RemDatePicker
import com.myapplication.shared.ui.components.RemDialog
import com.myapplication.shared.ui.components.RemEmptyState
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.components.RemIconButton
import com.myapplication.shared.ui.components.RemTextField
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.theme.ListColorOf
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemSpacing
import com.myapplication.shared.ui.theme.RemType
import com.myapplication.shared.util.formatDueDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun DetailScreen(
    mainVm: MainViewModel,
    graph: AppGraph,
    todoId: Long,
    modifier: Modifier = Modifier,
) {
    val detailVm: DetailViewModel = viewModel(key = "detail-$todoId") {
        DetailViewModel(graph.repository, todoId)
    }
    val colors = LocalRemColors.current
    val todo by detailVm.todo.collectAsState()
    val subtasks by detailVm.subtasks.collectAsState()
    val lists by detailVm.lists.collectAsState()
    val current = todo
    val currentId = current?.id
    var titleText by remember(currentId) { mutableStateOf(current?.title ?: "") }
    var noteText by remember(currentId) { mutableStateOf(current?.note ?: "") }
    var newSub by remember(currentId) { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showListDialog by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = RemSpacing.s16, horizontal = 14.dp),
    ) {
        if (current == null) {
            RemEmptyState("待办不存在或已删除")
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RemCheckbox(current.isCompleted, { mainVm.toggleCompleted(current) })
            Spacer(Modifier.width(10.dp))
            RemTextField(
                value = titleText,
                onValueChange = {
                    titleText = it
                    detailVm.setTitle(it)
                },
                style = RemType.text16,
                filled = false,
                modifier = Modifier.weight(1f),
            )
            if (current.dueDate != null) {
                Spacer(Modifier.width(8.dp))
                RemBadge(
                    label = formatDueDate(current.dueDate),
                    bg = colors.bgPanel,
                    tint = colors.textLow,
                    onClick = { showDatePicker = true },
                )
            }
            RemIconButton(IconName.Close, "关闭详情", onClick = mainVm::back, size = 16.dp)
        }
        if (current.isCompleted && current.completedAt != null) {
            Spacer(Modifier.height(4.dp))
            androidx.compose.foundation.text.BasicText(
                "已完成 ${formatDueDate(current.completedAt)}",
                style = RemType.text12.copy(color = colors.textLow),
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .background(colors.bgPrimary, RoundedCornerShape(RemRadii.r2))
                .padding(4.dp),
        ) {
            RemTextField(
                value = noteText,
                onValueChange = {
                    noteText = it
                    detailVm.setNote(it)
                },
                placeholder = "备注…",
                singleLine = false,
                minLines = 3,
                filled = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(4.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { showDatePicker = true }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemIcon(IconName.Calendar, colors.textLow, Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            androidx.compose.foundation.text.BasicText("日期", style = RemType.text14.copy(color = colors.textNormal))
            Spacer(Modifier.weight(1f))
            if (current.dueDate != null) {
                RemBadge(
                    label = formatDueDate(current.dueDate),
                    icon = { RemIcon(IconName.Calendar, colors.textLow, Modifier.size(10.dp)) },
                )
                Spacer(Modifier.width(8.dp))
                RemButton("清除", onClick = { detailVm.setDueDate(null) })
            } else {
                androidx.compose.foundation.text.BasicText("无", style = RemType.text12.copy(color = colors.textLow))
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { mainVm.toggleFlag(current) }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.text.BasicText("旗标", style = RemType.text14.copy(color = colors.textNormal))
            Spacer(Modifier.weight(1f))
            RemIcon(IconName.Flag, if (current.flag) colors.warning else colors.textLow, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            androidx.compose.foundation.text.BasicText(if (current.flag) "已标记" else "未标记", style = RemType.text14.copy(color = if (current.flag) colors.textHigh else colors.textLow))
        }

        val currentList = lists.firstOrNull { it.id == current.listId }
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { showListDialog = true }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemIcon(IconName.Tray, colors.textLow, Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            androidx.compose.foundation.text.BasicText("列表", style = RemType.text14.copy(color = colors.textNormal))
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(10.dp).background(ListColorOf[currentList?.colorKey] ?: Color.Gray, CircleShape))
            Spacer(Modifier.width(6.dp))
            androidx.compose.foundation.text.BasicText(
                currentList?.name ?: "未知列表",
                style = RemType.text14.copy(color = colors.textHigh),
            )
        }
        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            RemIcon(IconName.ChevronDown, colors.textLow, Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            androidx.compose.foundation.text.BasicText("子任务", style = RemType.label12.copy(color = colors.textLow))
        }
        Spacer(Modifier.height(6.dp))
        subtasks.forEach { sub ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                RemCheckbox(sub.isCompleted, { detailVm.toggleSubTask(sub) }, size = 12.dp)
                Spacer(Modifier.width(8.dp))
                androidx.compose.foundation.text.BasicText(
                    sub.title,
                    style = RemType.text14.copy(
                        color = if (sub.isCompleted) colors.textLow else colors.textHigh,
                        textDecoration = if (sub.isCompleted) TextDecoration.LineThrough else null,
                    ),
                    modifier = Modifier.weight(1f),
                )
                RemIconButton(IconName.Trash, "删除子任务", onClick = { detailVm.trashSubTask(sub) }, size = 14.dp)
            }
        }
        Spacer(Modifier.height(6.dp))
        RemTextField(
            value = newSub,
            onValueChange = { newSub = it },
            placeholder = "添加子任务…",
            onEnter = {
                detailVm.addSubTask(newSub)
                newSub = ""
            },
            trailing = "添加" to {
                detailVm.addSubTask(newSub)
                newSub = ""
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        androidx.compose.foundation.text.BasicText(
            "创建于 ${current.createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).let { "${it.monthNumber} 月 ${it.dayOfMonth} 日" }}",
            style = RemType.text12.copy(color = colors.textLow),
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            RemButton("移到列表", onClick = { showListDialog = true }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            RemButton(
                "移到垃圾箱",
                onClick = {
                    mainVm.trash(current)
                    mainVm.back()
                },
                danger = true,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (showDatePicker) {
        RemDatePicker(
            initialDate = current?.dueDate?.toLocalDateTime(TimeZone.currentSystemDefault())?.date,
            initialTime = current?.dueDate?.toLocalDateTime(TimeZone.currentSystemDefault())?.time?.takeIf { !(it.hour == 0 && it.minute == 0) },
            onPick = { date ->
                val time = current?.dueDate
                    ?.toLocalDateTime(TimeZone.currentSystemDefault())?.time
                    ?: LocalTime(9, 0)
                detailVm.setDueDate(LocalDateTime(date, time))
            },
            onPickTime = { h, m ->
                if (h == -1 && m == -1) detailVm.setTimeNull() else detailVm.setTime(h, m)
            },
            onDismiss = { showDatePicker = false },
        )
    }

    if (showListDialog) {
        RemDialog(
            title = "选择列表",
            onDismiss = { showListDialog = false },
            confirmText = "确定",
            onConfirm = { showListDialog = false },
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
                                showListDialog = false
                                detailVm.moveToList(list.id)
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(10.dp).background(ListColorOf[list.colorKey] ?: Color.Gray, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.foundation.text.BasicText(
                            list.name,
                            style = RemType.text14.copy(color = colors.textHigh),
                            modifier = Modifier.weight(1f),
                        )
                        if (list.id == current?.listId) {
                            RemIcon(IconName.CheckCircle, colors.brand, Modifier.size(16.dp))
                        }
                    }
                }
            },
        )
    }
}
