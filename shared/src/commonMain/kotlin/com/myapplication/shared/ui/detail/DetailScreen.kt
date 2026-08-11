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
import androidx.compose.foundation.verticalScroll
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
                style = RemType.title15,
                filled = false,
                modifier = Modifier.weight(1f),
            )
            RemIconButton(IconName.Close, "关闭详情", onClick = mainVm::back, size = 16.dp)
        }
        Spacer(Modifier.height(10.dp))
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
        Spacer(Modifier.height(4.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { showDatePicker = true }
                .drawBehind { drawLine(colors.rowDivider, Offset(0f, size.height), Offset(size.width, size.height), 1f) }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.text.BasicText("日期", style = RemType.text13.copy(color = colors.textSecondary))
            Spacer(Modifier.weight(1f))
            if (current.dueDate != null) {
                RemBadge(
                    label = formatDueDate(current.dueDate),
                    icon = { RemIcon(IconName.Calendar, colors.textTertiary, Modifier.size(10.dp)) },
                )
                Spacer(Modifier.width(8.dp))
                RemButton("清除", onClick = { detailVm.setDueDate(null) })
            } else {
                androidx.compose.foundation.text.BasicText("无", style = RemType.text12.copy(color = colors.textTertiary))
            }
        }

        val currentList = lists.firstOrNull { it.id == current.listId }
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { showListDialog = true }
                .drawBehind { drawLine(colors.rowDivider, Offset(0f, size.height), Offset(size.width, size.height), 1f) }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.text.BasicText("列表", style = RemType.text13.copy(color = colors.textSecondary))
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(10.dp).background(ListColorOf[currentList?.colorKey] ?: Color.Gray, CircleShape))
            Spacer(Modifier.width(6.dp))
            androidx.compose.foundation.text.BasicText(
                currentList?.name ?: "未知列表",
                style = RemType.text13.copy(color = colors.textPrimary),
            )
        }
        Spacer(Modifier.height(16.dp))

        androidx.compose.foundation.text.BasicText("子任务", style = RemType.label13.copy(color = colors.textTertiary))
        Spacer(Modifier.height(6.dp))
        subtasks.forEach { sub ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                RemCheckbox(sub.isCompleted, { detailVm.toggleSubTask(sub) }, size = 12.dp)
                Spacer(Modifier.width(8.dp))
                androidx.compose.foundation.text.BasicText(
                    sub.title,
                    style = RemType.text13.copy(
                        color = if (sub.isCompleted) colors.textTertiary else colors.textPrimary,
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
        Spacer(Modifier.height(16.dp))
        RemButton(
            "移到垃圾箱",
            onClick = {
                mainVm.trash(current)
                mainVm.back()
            },
            danger = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showDatePicker) {
        RemDatePicker(
            initialDate = current?.dueDate?.toLocalDateTime(TimeZone.currentSystemDefault())?.date,
            onPick = { date ->
                val time = current?.dueDate
                    ?.toLocalDateTime(TimeZone.currentSystemDefault())?.time
                    ?: LocalTime(9, 0)
                detailVm.setDueDate(LocalDateTime(date, time))
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
                            style = RemType.text13.copy(color = colors.textPrimary),
                            modifier = Modifier.weight(1f),
                        )
                        if (list.id == current?.listId) {
                            RemIcon(IconName.CheckCircle, colors.accent, Modifier.size(16.dp))
                        }
                    }
                }
            },
        )
    }
}
