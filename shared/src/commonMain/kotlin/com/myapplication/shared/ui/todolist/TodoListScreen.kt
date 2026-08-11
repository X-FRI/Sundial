package com.myapplication.shared.ui.todolist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.main.Scope
import com.myapplication.shared.util.bucketLabel
import com.myapplication.shared.util.bucketOf
import com.myapplication.shared.util.formatDueDate
import com.myapplication.shared.util.todayDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun TodoListScreen(mainVm: MainViewModel, modifier: Modifier = Modifier) {
    val todos by mainVm.todos.collectAsState()
    val scope by mainVm.scope.collectAsState()
    val query by mainVm.searchQuery.collectAsState()

    Column(modifier) {
        Text(
            scopeTitle(scope, query),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        if (scope != Scope.Trash) {
            QuickAddRow(mainVm)
        }
        val today = todayDate()
        when (scope) {
            Scope.Scheduled -> ScheduledGrouped(todos, today, mainVm)
            Scope.Trash -> TrashList(todos, mainVm)
            else -> PlainList(todos, today, mainVm)
        }
    }
}

fun scopeTitle(scope: Scope, query: String): String = when {
    query.isNotBlank() -> "搜索"
    scope == Scope.Today -> "今天"
    scope == Scope.Scheduled -> "计划"
    scope == Scope.All -> "全部待办"
    scope == Scope.Completed -> "已完成"
    scope == Scope.Trash -> "垃圾箱"
    scope is Scope.List -> "列表"
    else -> "待办"
}

@Composable
private fun QuickAddRow(mainVm: MainViewModel) {
    var text by remember { mutableStateOf("") }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text("＋ 添加待办…（支持“明天 15:00”等日期）") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            mainVm.addQuick(text)
            text = ""
        }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    )
}

@Composable
private fun PlainList(todos: List<TodoItem>, today: kotlinx.datetime.LocalDate, mainVm: MainViewModel) {
    val active = todos.filter { !it.isCompleted && it.parentId == null }
    val childrenByParent = todos.filter { it.parentId != null }.groupBy { it.parentId!! }
    val completed = todos.filter { it.isCompleted && it.parentId == null }
    var expanded by remember { mutableStateOf(true) }

    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        items(active) { parent ->
            TodoRow(parent, mainVm, today, showChevron = childrenByParent[parent.id] != null, expanded = expanded, onToggleExpand = { expanded = !expanded })
            if (expanded) {
                childrenByParent[parent.id]?.forEach { child ->
                    TodoRow(child, mainVm, today, indent = true)
                }
            }
        }
        if (completed.isNotEmpty()) {
            item {
                Text(
                    "已完成",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                )
            }
            items(completed) { item ->
                TodoRow(item, mainVm, today)
            }
        }
    }
}

@Composable
private fun ScheduledGrouped(todos: List<TodoItem>, today: kotlinx.datetime.LocalDate, mainVm: MainViewModel) {
    val tz = TimeZone.currentSystemDefault()
    val grouped = todos
        .filter { it.dueDate != null }
        .groupBy { bucketOf(it.dueDate!!.toLocalDateTime(tz).date, today) }
    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        listOf(
            com.myapplication.shared.util.DueBucket.OVERDUE,
            com.myapplication.shared.util.DueBucket.TODAY,
            com.myapplication.shared.util.DueBucket.TOMORROW,
            com.myapplication.shared.util.DueBucket.THIS_WEEK,
            com.myapplication.shared.util.DueBucket.LATER,
        ).forEach { bucket ->
            val items = grouped[bucket].orEmpty()
            if (items.isNotEmpty()) {
                item {
                    Text(
                        bucketLabel(bucket),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                    )
                }
                items(items) { item -> TodoRow(item, mainVm, today) }
            }
        }
    }
}

@Composable
private fun TrashList(todos: List<TodoItem>, mainVm: MainViewModel) {
    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        items(todos) { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(item.title, Modifier.weight(1f))
                TextButton(onClick = { mainVm.restore(item) }) { Text("恢复") }
                TextButton(onClick = { mainVm.deleteForever(item) }) { Text("彻底删除") }
            }
        }
    }
}

@Composable
fun TodoRow(
    item: TodoItem,
    mainVm: MainViewModel,
    today: kotlinx.datetime.LocalDate,
    indent: Boolean = false,
    showChevron: Boolean = false,
    expanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
) {
    val isOverdue = item.dueDate?.let {
        it.toLocalDateTime(TimeZone.currentSystemDefault()).date < today
    } == true
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { if (showChevron) onToggleExpand() else mainVm.openDetail(item.id) }
            .padding(start = if (indent) 28.dp else 0.dp)
            .padding(vertical = 6.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(18.dp)
                .background(
                    if (item.isCompleted) MaterialTheme.colorScheme.primary else Color.Transparent,
                    CircleShape,
                )
                .clickable { mainVm.toggleCompleted(item) },
        ) {
            if (item.isCompleted) {
                Text(
                    "✓",
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(start = 3.dp, top = 1.dp),
                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                )
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(3.dp)
                        .background(MaterialTheme.colorScheme.outline, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).clickable { mainVm.openDetail(item.id) }) {
            Text(
                item.title,
                textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
                color = if (item.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            if (item.note.isNotBlank()) {
                Text(
                    item.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        if (showChevron) {
            Text(if (expanded) "⌄" else "›", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item.dueDate?.let {
            val label = formatDueDate(it)
            val overdueColor = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = overdueColor,
                modifier = Modifier
                    .background(
                        if (isOverdue) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        CircleShape,
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}
