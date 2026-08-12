package com.myapplication.shared.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myapplication.shared.ui.components.RemButton
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.main.Scope
import com.myapplication.shared.ui.main.scopeTitle
import com.myapplication.shared.ui.theme.RemControlSize
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemType
import com.myapplication.shared.ui.todolist.TodoFormDialog
import com.myapplication.shared.util.todayDate
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone

@Composable
fun MainLedger(
    mainVm: MainViewModel,
    selectedId: Long?,
    modifier: Modifier = Modifier,
    clock: Clock = Clock.System,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    showHeader: Boolean = true,
    showRhythm: Boolean = true,
    compactRows: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
) {
    val colors = LocalRemColors.current
    val todos by mainVm.todos.collectAsState()
    val scope by mainVm.scope.collectAsState()
    val query by mainVm.searchQuery.collectAsState()
    val lists by mainVm.lists.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    val groups = remember(todos) { buildTaskGroups(todos) }
    val trashGroups = remember(todos) { buildTrashGroups(todos) }
    val trashScope = scope == Scope.Trash
    var now by remember(clock) { mutableStateOf(clock.now()) }
    LaunchedEffect(clock) {
        while (true) {
            now = clock.now()
            delay(60_000)
        }
    }
    val rhythm = remember(todos, now, timeZone) { buildTodayRhythmState(todos, now, timeZone) }
    val timeline = remember(todos, now, timeZone) { buildTodayTimelineState(todos, now, timeZone) }
    val inboxListId = remember(lists) { lists.firstOrNull { it.name == "收件箱" }?.id }
    val contextScope = remember(scope, inboxListId) { scope.toTimelineScope(inboxListId) }
    val contextTimeline = remember(todos, contextScope, now, timeZone, inboxListId) {
        buildContextTimelineState(todos, contextScope, now, timeZone, inboxListId)
    }
    val today = todayDate(clock, timeZone)
    val rowMinHeight = if (compactRows) RemControlSize.rowMobile else RemControlSize.rowDesktop
    val checkboxSize = if (compactRows) 20.dp else 16.dp
    val activeById = groups.active.associateBy { it.item.id }
    val pastRows = timeline.past.mapNotNull { activeById[it.item.id] ?: TaskRowModel(it.item, emptyList()) }
    val upcomingRows = timeline.upcoming.mapNotNull { activeById[it.item.id] ?: TaskRowModel(it.item, emptyList()) }
    val shownIds = (pastRows + upcomingRows + timeline.unscheduled).map { it.item.id }.toSet()
    val laterRows = groups.active.filter { it.item.id !in shownIds }

    Column(modifier.fillMaxSize().background(colors.bgSecondary).padding(contentPadding)) {
        if (showHeader) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    androidx.compose.foundation.text.BasicText(
                        ledgerTitle(scope, query, lists, inboxListId),
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
        }
        if (showRhythm) {
            ContextTimeline(
                state = contextTimeline,
                rhythm = rhythm,
                timeline = timeline,
                showDayRail = scope == Scope.Today && !trashScope,
                onOpen = mainVm::openDetail,
            )
            Spacer(Modifier.height(18.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (trashScope) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.text.BasicText("垃圾箱", style = RemType.label12.copy(color = colors.textHigh))
                        Spacer(Modifier.width(6.dp))
                        androidx.compose.foundation.text.BasicText(trashGroups.size.toString(), style = RemType.text12.copy(color = colors.textLow))
                    }
                }
                items(trashGroups, key = { it.item.id }) { row ->
                    TrashRow(
                        item = row.item,
                        onRestore = { mainVm.restore(row.item) },
                        onDeleteForever = { mainVm.deleteForever(row.item) },
                    )
                }
            } else {
                if (pastRows.isNotEmpty()) {
                    item {
                        TaskSection(
                            title = "已错过",
                            rows = pastRows,
                            today = today,
                            selectedId = selectedId,
                            completed = false,
                            onOpen = mainVm::openDetail,
                            onToggleCompleted = mainVm::toggleCompleted,
                            onToggleFlag = mainVm::toggleFlag,
                            rowMinHeight = rowMinHeight,
                            checkboxSize = checkboxSize,
                        )
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
                if (upcomingRows.isNotEmpty()) {
                    item {
                        TaskSection(
                            title = "接下来",
                            rows = upcomingRows,
                            today = today,
                            selectedId = selectedId,
                            completed = false,
                            onOpen = mainVm::openDetail,
                            onToggleCompleted = mainVm::toggleCompleted,
                            onToggleFlag = mainVm::toggleFlag,
                            rowMinHeight = rowMinHeight,
                            checkboxSize = checkboxSize,
                        )
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
                if (timeline.unscheduled.isNotEmpty()) {
                    item {
                        TaskSection(
                            title = "未安排时间",
                            rows = timeline.unscheduled,
                            today = today,
                            selectedId = selectedId,
                            completed = false,
                            onOpen = mainVm::openDetail,
                            onToggleCompleted = mainVm::toggleCompleted,
                            onToggleFlag = mainVm::toggleFlag,
                            rowMinHeight = rowMinHeight,
                            checkboxSize = checkboxSize,
                        )
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
                if (laterRows.isNotEmpty()) {
                    item {
                        TaskSection(
                            title = "以后",
                            rows = laterRows,
                            today = today,
                            selectedId = selectedId,
                            completed = false,
                            onOpen = mainVm::openDetail,
                            onToggleCompleted = mainVm::toggleCompleted,
                            onToggleFlag = mainVm::toggleFlag,
                            rowMinHeight = rowMinHeight,
                            checkboxSize = checkboxSize,
                        )
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
                if (pastRows.isEmpty() && upcomingRows.isEmpty() && timeline.unscheduled.isEmpty() && laterRows.isEmpty()) {
                    item {
                        androidx.compose.foundation.text.BasicText(
                            "没有待办，今天可以轻一点。",
                            style = RemType.text14.copy(color = colors.textLow),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                        )
                    }
                }
                if (groups.completed.isNotEmpty()) {
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
                            rowMinHeight = rowMinHeight,
                            checkboxSize = checkboxSize,
                        )
                    }
                }
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

fun Scope.toTimelineScope(inboxListId: Long?): TimelineScope = when (this) {
    Scope.Today -> TimelineScope.Today
    Scope.Scheduled -> TimelineScope.Scheduled
    Scope.All -> TimelineScope.Workbench
    Scope.Completed -> TimelineScope.Completed
    Scope.Trash -> TimelineScope.Trash
    is Scope.List -> TimelineScope.List(listId = listId, isInbox = inboxListId == listId)
}

private fun ledgerTitle(
    scope: Scope,
    query: String,
    lists: List<com.myapplication.shared.domain.model.TodoList>,
    inboxListId: Long?,
): String {
    if (query.isNotBlank()) return scopeTitle(scope, query)
    return when (scope) {
        is Scope.List -> {
            val list = lists.firstOrNull { it.id == scope.listId }
            if (scope.listId == inboxListId) "收件箱 · 待整理" else list?.name ?: "列表"
        }
        else -> scopeTitle(scope, query)
    }
}
