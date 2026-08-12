package com.myapplication.shared.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemButton
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.components.RemTextField
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.main.Scope
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemType
import com.myapplication.shared.ui.todolist.TodoFormDialog
import com.myapplication.shared.ui.todolist.scopeTitle
import com.myapplication.shared.util.todayDate
import kotlin.time.Clock
import kotlinx.datetime.TimeZone

@Composable
fun MainLedger(
    mainVm: MainViewModel,
    selectedId: Long?,
    modifier: Modifier = Modifier,
    clock: Clock = Clock.System,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val colors = LocalRemColors.current
    val todos by mainVm.todos.collectAsState()
    val scope by mainVm.scope.collectAsState()
    val query by mainVm.searchQuery.collectAsState()
    val lists by mainVm.lists.collectAsState()
    val todayCount by mainVm.todayCount.collectAsState()
    val scheduledCount by mainVm.scheduledCount.collectAsState()
    val completedCount by mainVm.completedCount.collectAsState()
    val listCounts by mainVm.listCounts.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    val groups = remember(todos) { buildTaskGroups(todos) }
    val rhythm = remember(todos, clock, timeZone) { buildTodayRhythmState(todos, clock.now(), timeZone) }
    val today = todayDate()

    Column(modifier.fillMaxSize().background(colors.bgSecondary).padding(horizontal = 24.dp, vertical = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                androidx.compose.foundation.text.BasicText(
                    scopeTitle(scope, query),
                    style = RemType.title24.copy(color = colors.textHigh),
                )
                androidx.compose.foundation.text.BasicText(
                    "${today.year}-${today.monthNumber.toString().padStart(2, '0')}-${today.dayOfMonth.toString().padStart(2, '0')}",
                    style = RemType.text12.copy(color = colors.textLow),
                )
            }
            RemButton("添加待办", onClick = { showCreate = true })
        }
        Spacer(Modifier.height(16.dp))
        TodayRhythm(rhythm)
        Spacer(Modifier.height(12.dp))
        CompactOverview(
            todayCount = todayCount,
            scheduledCount = scheduledCount,
            completedCount = completedCount,
            inboxCount = listCounts.values.firstOrNull() ?: 0,
            onScope = mainVm::selectScope,
        )
        Spacer(Modifier.height(12.dp))
        QuickAddBar(onClick = { showCreate = true })
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                TaskSection(
                    title = "待办",
                    rows = groups.active,
                    today = today,
                    selectedId = selectedId,
                    completed = false,
                    onOpen = mainVm::openDetail,
                    onToggleCompleted = mainVm::toggleCompleted,
                    onToggleFlag = mainVm::toggleFlag,
                )
            }
            item { Spacer(Modifier.height(12.dp)) }
            item {
                TaskSection(
                    title = "已完成",
                    rows = groups.completed,
                    today = today,
                    selectedId = selectedId,
                    completed = true,
                    onOpen = mainVm::openDetail,
                    onToggleCompleted = mainVm::toggleCompleted,
                    onToggleFlag = mainVm::toggleFlag,
                )
            }
        }
    }

    if (showCreate) {
        TodoFormDialog(
            lists = lists,
            defaultListId = (scope as? Scope.List)?.listId,
            onDismiss = { showCreate = false },
            onConfirm = { title, note, due, flag, listId ->
                mainVm.createTodo(title, note, due, flag, listId)
                showCreate = false
            },
        )
    }
}

@Composable
private fun CompactOverview(
    todayCount: Int,
    scheduledCount: Int,
    completedCount: Int,
    inboxCount: Int,
    onScope: (Scope) -> Unit,
) {
    Row(Modifier.fillMaxWidth()) {
        OverviewCell("待办", todayCount, IconName.Today) { onScope(Scope.Today) }
        OverviewCell("计划", scheduledCount, IconName.Scheduled) { onScope(Scope.Scheduled) }
        OverviewCell("已完成", completedCount, IconName.CheckCircle) { onScope(Scope.Completed) }
        OverviewCell("收件箱", inboxCount, IconName.Inbox) { onScope(Scope.All) }
    }
}

@Composable
private fun RowScope.OverviewCell(label: String, count: Int, icon: IconName, onClick: () -> Unit) {
    Row(
        Modifier.weight(1f).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemIcon(icon, LocalRemColors.current.brand)
        androidx.compose.foundation.text.BasicText(" $label $count", style = RemType.text12.copy(color = LocalRemColors.current.textNormal))
    }
}

@Composable
private fun QuickAddBar(onClick: () -> Unit) {
    RemTextField(
        value = "",
        onValueChange = {},
        placeholder = "添加待办…",
        leadingIcon = IconName.Plus,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}
