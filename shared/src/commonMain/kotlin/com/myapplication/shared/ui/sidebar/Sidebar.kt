package com.myapplication.shared.ui.sidebar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.main.Scope
import com.myapplication.shared.ui.theme.ListColorKeys
import com.myapplication.shared.ui.theme.ListColorOf

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Sidebar(mainVm: MainViewModel) {
    val lists by mainVm.lists.collectAsState()
    val scope by mainVm.scope.collectAsState()
    val query by mainVm.searchQuery.collectAsState()
    val todayCount by mainVm.todayCount.collectAsState()
    val scheduledCount by mainVm.scheduledCount.collectAsState()
    val allCount by mainVm.allCount.collectAsState()
    val completedCount by mainVm.completedCount.collectAsState()
    val trashCount by mainVm.trashCount.collectAsState()
    val listCounts by mainVm.listCounts.collectAsState()

    var showAddList by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxHeight()
            .width(220.dp)
            .padding(12.dp),
    ) {
        Text("提醒事项", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        TextField(
            value = query,
            onValueChange = mainVm::setSearch,
            placeholder = { Text("搜索") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        ScopeRow("📅 今天", todayCount, scope == Scope.Today) { mainVm.selectScope(Scope.Today) }
        ScopeRow("🗓 计划", scheduledCount, scope == Scope.Scheduled) { mainVm.selectScope(Scope.Scheduled) }
        ScopeRow("🗂 全部待办", allCount, scope == Scope.All) { mainVm.selectScope(Scope.All) }
        ScopeRow("✓ 已完成", completedCount, scope == Scope.Completed) { mainVm.selectScope(Scope.Completed) }
        ScopeRow("🗑 垃圾箱", trashCount, scope == Scope.Trash) { mainVm.selectScope(Scope.Trash) }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("我的列表", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        lists.forEach { list ->
            ListRow(
                list = list,
                count = listCounts[list.id] ?: 0,
                selected = scope == Scope.List(list.id),
                canDelete = list.position != 0,
                onSelect = { mainVm.selectScope(Scope.List(list.id)) },
                onDelete = { mainVm.deleteList(list) },
            )
        }
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = { showAddList = true }, modifier = Modifier.fillMaxWidth()) {
            Text("＋ 添加列表")
        }
    }

    if (showAddList) {
        AddListDialog(onDismiss = { showAddList = false }) { name, color ->
            mainVm.addList(name, color)
            showAddList = false
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScopeRow(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .combinedClickable(onClick = onClick, onLongClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (selected) color else MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.weight(1f))
        if (count > 0) {
            Text(
                count.toString(),
                color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListRow(
    list: TodoList,
    count: Int,
    selected: Boolean,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .combinedClickable(onClick = onSelect, onLongClick = if (canDelete) { { menuOpen = true } } else null),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(ListColorOf[list.colorKey] ?: Color.Gray, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Text(list.name)
            Spacer(Modifier.weight(1f))
            if (count > 0) {
                Text(count.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (canDelete) {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("删除列表") }, onClick = {
                    menuOpen = false
                    confirmDelete = true
                })
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除列表") },
            text = { Text("确定删除列表「${list.name}」？该列表的所有待办将移入垃圾箱。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AddListDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var colorKey by remember { mutableStateOf(ListColorKeys.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建列表") },
        text = {
            Column {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("列表名称") },
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                Row {
                    ListColorKeys.forEach { key ->
                        Box(
                            Modifier
                                .padding(end = 8.dp)
                                .size(24.dp)
                                .background(
                                    ListColorOf[key] ?: Color.Gray,
                                    CircleShape,
                                )
                                .border(
                                    width = 2.dp,
                                    color = if (key == colorKey) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = CircleShape,
                                )
                                .combinedClickable(onClick = { colorKey = key }, onLongClick = {}),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) onConfirm(name, colorKey)
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
