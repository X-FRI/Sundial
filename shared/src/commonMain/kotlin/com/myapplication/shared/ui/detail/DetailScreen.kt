package com.myapplication.shared.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myapplication.shared.di.AppGraph
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.theme.ListColorOf
import com.myapplication.shared.util.formatDueDate
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(mainVm: MainViewModel, graph: AppGraph, todoId: Long) {
    val detailVm: DetailViewModel = viewModel(key = "detail-$todoId") {
        DetailViewModel(graph.repository, todoId)
    }
    val todo by detailVm.todo.collectAsState()
    val subtasks by detailVm.subtasks.collectAsState()
    val lists by detailVm.lists.collectAsState()
    val current = todo
    var showDatePicker by remember { mutableStateOf(false) }
    var showListMenu by remember { mutableStateOf(false) }
    val datePickerState = if (showDatePicker) rememberDatePickerState() else null

    Column(
        Modifier
            .fillMaxSize()
            .widthIn(max = 420.dp)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = mainVm::back) { Text("‹ 返回") }
            Spacer(Modifier.weight(1f))
        }
        if (current == null) {
            Text("待办不存在或已删除", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        OutlinedTextField(
            value = current.title,
            onValueChange = detailVm::setTitle,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = current.note,
            onValueChange = detailVm::setNote,
            placeholder = { Text("备注…（阶段二将支持 Markdown 富文本与图片）") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .clickable { showDatePicker = true }
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("📅 日期", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(if (current.dueDate != null) formatDueDate(current.dueDate) else "无")
            if (current.dueDate != null) {
                TextButton(onClick = { detailVm.setDueDate(null) }) { Text("清除") }
            }
        }
        Spacer(Modifier.height(8.dp))

        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { showListMenu = true }
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🗂 列表", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                val currentList = lists.firstOrNull { it.id == current.listId }
                Box(
                    Modifier
                        .size(10.dp)
                        .background(ListColorOf[currentList?.colorKey] ?: Color.Gray, CircleShape),
                )
                Spacer(Modifier.width(6.dp))
                Text(currentList?.name ?: "未知列表")
            }
            DropdownMenu(expanded = showListMenu, onDismissRequest = { showListMenu = false }) {
                lists.forEach { list ->
                    DropdownMenuItem(
                        text = { Text(list.name) },
                        onClick = {
                            showListMenu = false
                            detailVm.moveToList(list.id)
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        Text("子任务", style = MaterialTheme.typography.titleMedium)
        subtasks.forEach { sub ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(16.dp)
                        .background(
                            if (sub.isCompleted) MaterialTheme.colorScheme.primary else Color.Transparent,
                            CircleShape,
                        )
                        .clickable { detailVm.toggleSubTask(sub) },
                ) {
                    if (sub.isCompleted) Text("✓", color = MaterialTheme.colorScheme.onPrimary, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                }
                Spacer(Modifier.width(8.dp))
                Text(sub.title, Modifier.weight(1f))
                TextButton(onClick = { detailVm.trashSubTask(sub) }) { Text("🗑") }
            }
        }
        var newSub by remember { mutableStateOf("") }
        OutlinedTextField(
            value = newSub,
            onValueChange = { newSub = it },
            placeholder = { Text("＋ 添加子任务…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                TextButton(onClick = {
                    detailVm.addSubTask(newSub)
                    newSub = ""
                }) { Text("添加") }
            },
        )
        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = {
                mainVm.trash(current)
                mainVm.back()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("移到垃圾箱", color = MaterialTheme.colorScheme.error)
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState?.selectedDateMillis?.let { ms ->
                        val date = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.UTC).date
                        val time = current?.dueDate
                            ?.toLocalDateTime(TimeZone.currentSystemDefault())?.time
                            ?: LocalTime(9, 0)
                        detailVm.setDueDate(LocalDateTime(date, time))
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = datePickerState ?: rememberDatePickerState())
        }
    }
}
