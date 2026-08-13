package com.myapplication.shared.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.domain.organize.OrganizationAction
import com.myapplication.shared.domain.organize.OrganizationSuggestion
import com.myapplication.shared.domain.organize.buildOrganizationSuggestions
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemDialog
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.components.RemButton
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.main.Scope
import com.myapplication.shared.ui.main.scopeTitle
import com.myapplication.shared.ui.organize.OrganizePanel
import com.myapplication.shared.ui.theme.ListColorOf
import com.myapplication.shared.ui.theme.RemControlSize
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemType
import com.myapplication.shared.ui.todolist.TodoFormDialog
import com.myapplication.shared.util.todayDate
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

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
    edgeToEdgeRows: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
) {
    val colors = LocalRemColors.current
    val todos by mainVm.todos.collectAsState()
    val scope by mainVm.scope.collectAsState()
    val query by mainVm.searchQuery.collectAsState()
    val lists by mainVm.lists.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var movingSuggestion by remember { mutableStateOf<OrganizationSuggestion?>(null) }
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
    val taskSections = remember(scope, groups, timeline, today, timeZone, inboxListId) {
        buildLedgerTaskSections(scope, groups, timeline, today, timeZone, inboxListId)
    }
    val trashSections = remember(scope, trashGroups, today, timeZone) {
        buildLedgerTrashSections(scope, trashGroups, today, timeZone)
    }
    val organizationSuggestions = remember(scope, query, todos, today, timeZone, inboxListId) {
        if (scope == Scope.All && query.isBlank()) {
            buildOrganizationSuggestions(todos, inboxListId, today, timeZone)
        } else {
            emptyList()
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(if (edgeToEdgeRows) colors.bgPrimary else colors.bgSecondary)
            .padding(contentPadding),
    ) {
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
            Spacer(Modifier.height(12.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (trashScope) {
                trashSections.forEach { section ->
                    item(key = "trash-${section.title}") {
                        TrashSection(
                            title = section.title,
                            rows = section.items,
                            onRestore = mainVm::restore,
                            onDeleteForever = mainVm::deleteForever,
                            headerColor = section.tone.timelineColor(),
                            emptyText = section.emptyText,
                        )
                    }
                    item { Spacer(Modifier.height(if (edgeToEdgeRows) 4.dp else 8.dp)) }
                }
            } else {
                if (organizationSuggestions.isNotEmpty()) {
                    item(key = "organize-panel") {
                        OrganizePanel(
                            suggestions = organizationSuggestions,
                            selectedId = selectedId,
                            onOpen = mainVm::openDetail,
                            onAction = { suggestion, action ->
                                when (action) {
                                    OrganizationAction.ScheduleToday -> mainVm.scheduleToday(suggestion.todo)
                                    OrganizationAction.ScheduleTomorrow -> mainVm.scheduleTomorrow(suggestion.todo)
                                    OrganizationAction.MoveToList -> movingSuggestion = suggestion
                                    OrganizationAction.EditTitle -> mainVm.openDetail(suggestion.todo.id)
                                    OrganizationAction.Trash -> mainVm.trash(suggestion.todo)
                                }
                            },
                            showRowContainer = !edgeToEdgeRows,
                        )
                    }
                    item { Spacer(Modifier.height(if (edgeToEdgeRows) 4.dp else 8.dp)) }
                }
                taskSections.forEach { section ->
                    item(key = "task-${section.title}") {
                        TaskSection(
                            title = section.title,
                            rows = section.rows,
                            today = today,
                            selectedId = selectedId,
                            completed = section.completed,
                            onOpen = mainVm::openDetail,
                            onToggleCompleted = mainVm::toggleCompleted,
                            onToggleFlag = mainVm::toggleFlag,
                            rowMinHeight = rowMinHeight,
                            checkboxSize = checkboxSize,
                            showRowContainer = !edgeToEdgeRows,
                            headerColor = section.tone.timelineColor(),
                            emptyText = section.emptyText,
                        )
                    }
                    item { Spacer(Modifier.height(if (edgeToEdgeRows) 4.dp else 8.dp)) }
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

    movingSuggestion?.let { suggestion ->
        MoveSuggestionListDialog(
            suggestion = suggestion,
            lists = lists,
            onDismiss = { movingSuggestion = null },
            onMove = { list ->
                movingSuggestion = null
                mainVm.moveTodoToList(suggestion.todo, list.id)
            },
        )
    }
}

@Composable
private fun MoveSuggestionListDialog(
    suggestion: OrganizationSuggestion,
    lists: List<TodoList>,
    onDismiss: () -> Unit,
    onMove: (TodoList) -> Unit,
) {
    val colors = LocalRemColors.current
    RemDialog(
        title = "移动到列表",
        onDismiss = onDismiss,
        confirmText = "确定",
        onConfirm = onDismiss,
        showButtons = false,
        content = {
            androidx.compose.foundation.text.BasicText(
                suggestion.todo.title,
                style = RemType.text12.copy(color = colors.textLow),
                modifier = Modifier.padding(bottom = 8.dp),
            )
            lists.forEach { list ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = androidx.compose.runtime.remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            onMove(list)
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).background(ListColorOf[list.colorKey] ?: Color.Gray, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.foundation.text.BasicText(
                        list.name,
                        style = RemType.text14.copy(color = colors.textHigh),
                        modifier = Modifier.weight(1f),
                    )
                    if (list.id == suggestion.todo.listId) {
                        RemIcon(IconName.CheckCircle, colors.brand, Modifier.size(16.dp))
                    }
                }
            }
        },
    )
}

fun Scope.toTimelineScope(inboxListId: Long?): TimelineScope = when (this) {
    Scope.Today -> TimelineScope.Today
    Scope.Scheduled -> TimelineScope.Scheduled
    Scope.All -> TimelineScope.Workbench
    Scope.Analytics -> TimelineScope.Workbench
    Scope.Completed -> TimelineScope.Completed
    Scope.Trash -> TimelineScope.Trash
    is Scope.List -> TimelineScope.List(listId = listId, isInbox = inboxListId == listId)
}

private fun ledgerTitle(
    scope: Scope,
    query: String,
    lists: List<TodoList>,
    inboxListId: Long?,
): String {
    if (query.isNotBlank()) return scopeTitle(scope, query)
    return when (scope) {
        is Scope.List -> {
            val list = lists.firstOrNull { it.id == scope.listId }
            list?.name ?: "列表"
        }
        Scope.Analytics -> "分析"
        else -> scopeTitle(scope, query)
    }
}

private data class LedgerTaskSection(
    val title: String,
    val tone: TimelineTone,
    val rows: List<TaskRowModel>,
    val completed: Boolean = false,
    val emptyText: String = "无",
)

private data class LedgerTrashSection(
    val title: String,
    val tone: TimelineTone,
    val items: List<TodoItem>,
    val emptyText: String = "无",
)

private fun buildLedgerTaskSections(
    scope: Scope,
    groups: TaskGroups,
    timeline: TodayTimelineState,
    today: LocalDate,
    timeZone: TimeZone,
    inboxListId: Long?,
): List<LedgerTaskSection> {
    val active = groups.active
    val completed = groups.completed
    return when (scope) {
        Scope.Today -> {
            val activeById = active.associateBy { it.item.id }
            val pastRows = timeline.past.mapNotNull { activeById[it.item.id] ?: TaskRowModel(it.item, emptyList()) }
            val upcomingRows = timeline.upcoming.mapNotNull { activeById[it.item.id] ?: TaskRowModel(it.item, emptyList()) }
            listOf(
                LedgerTaskSection("已错过", TimelineTone.Danger, pastRows, emptyText = "没有错过的待办"),
                LedgerTaskSection("接下来", TimelineTone.Brand, upcomingRows, emptyText = "没有接下来的定时待办"),
                LedgerTaskSection("未安排时间", TimelineTone.Neutral, timeline.unscheduled, emptyText = "没有未安排时间的待办"),
                LedgerTaskSection("已完成", TimelineTone.Success, completed, completed = true, emptyText = "今天还没有完成记录"),
            )
        }
        Scope.Scheduled -> listOf(
            LedgerTaskSection("今天", TimelineTone.Brand, active.filter { it.isDueOn(today, timeZone) }, emptyText = "今天没有计划待办"),
            LedgerTaskSection("明天", TimelineTone.Info, active.filter { it.isDueOn(today.plus(1, DateTimeUnit.DAY), timeZone) }, emptyText = "明天没有计划待办"),
            LedgerTaskSection("本周", TimelineTone.Warning, active.filter { it.isDueBetween(today, 2, 7, timeZone) }, emptyText = "本周没有更多计划"),
            LedgerTaskSection("以后", TimelineTone.Neutral, active.filter { it.isDueAfter(today.plus(7, DateTimeUnit.DAY), timeZone) }, emptyText = "没有更晚的计划"),
        )
        Scope.Completed -> listOf(
            LedgerTaskSection("今天完成", TimelineTone.Success, completed.filter { it.item.completedAt?.toLocalDateTime(timeZone)?.date == today }, completed = true, emptyText = "今天还没有完成记录"),
            LedgerTaskSection("本周完成", TimelineTone.Info, completed.filter { row ->
                row.item.completedAt?.toLocalDateTime(timeZone)?.date?.let { it < today && it.daysUntil(today) in 1..7 } == true
            }, completed = true, emptyText = "本周没有其他完成记录"),
            LedgerTaskSection("更早完成", TimelineTone.Neutral, completed.filter { row ->
                row.item.completedAt?.toLocalDateTime(timeZone)?.date?.let { it.daysUntil(today) > 7 } ?: true
            }, completed = true, emptyText = "没有更早完成记录"),
        )
        Scope.Trash -> emptyList()
        Scope.Analytics -> workbenchSections(active, today, timeZone, inboxListId)
        Scope.All -> workbenchSections(active, today, timeZone, inboxListId)
        is Scope.List -> {
            val isInbox = inboxListId == scope.listId
            if (isInbox) {
                listOf(
                    LedgerTaskSection("待整理", TimelineTone.Warning, active, emptyText = "收件箱已清空"),
                    LedgerTaskSection("已有日期", TimelineTone.Info, active.filter { it.item.dueDate != null }, emptyText = "没有已安排日期的收件箱待办"),
                    LedgerTaskSection("无日期", TimelineTone.Neutral, active.filter { it.item.dueDate == null }, emptyText = "没有无日期待办"),
                    LedgerTaskSection("逾期", TimelineTone.Danger, active.filter { it.isOverdue(today, timeZone) }, emptyText = "没有逾期待整理项"),
                )
            } else {
                workbenchSections(active, today, timeZone, inboxListId = null)
            }
        }
    }
}

private fun workbenchSections(
    active: List<TaskRowModel>,
    today: LocalDate,
    timeZone: TimeZone,
    inboxListId: Long?,
): List<LedgerTaskSection> = listOf(
    LedgerTaskSection("逾期", TimelineTone.Danger, active.filter { it.isOverdue(today, timeZone) }, emptyText = "没有逾期待办"),
    LedgerTaskSection("今天", TimelineTone.Brand, active.filter { it.isDueOn(today, timeZone) }, emptyText = "今天没有待办"),
    LedgerTaskSection("未来 7 天", TimelineTone.Info, active.filter { it.isDueBetween(today, 1, 7, timeZone) }, emptyText = "未来 7 天没有待办"),
    LedgerTaskSection("无日期", TimelineTone.Neutral, active.filter { it.item.dueDate == null }, emptyText = "没有无日期待办"),
    LedgerTaskSection("待整理", TimelineTone.Warning, active.filter { inboxListId != null && it.item.listId == inboxListId }, emptyText = "没有待整理任务"),
)

private fun buildLedgerTrashSections(
    scope: Scope,
    trashGroups: List<TaskRowModel>,
    today: LocalDate,
    timeZone: TimeZone,
): List<LedgerTrashSection> {
    if (scope != Scope.Trash) return emptyList()
    val items = trashGroups.map { it.item }
    return listOf(
        LedgerTrashSection("最近删除", TimelineTone.Warning, items.filter { it.trashedAt?.toLocalDateTime(timeZone)?.date == today }, emptyText = "今天没有删除项目"),
        LedgerTrashSection("可恢复", TimelineTone.Info, items, emptyText = "垃圾箱为空"),
        LedgerTrashSection("无日期", TimelineTone.Neutral, items.filter { it.dueDate == null }, emptyText = "没有无日期项目"),
    )
}

private fun TaskRowModel.localDueDate(timeZone: TimeZone): LocalDate? =
    item.dueDate?.toLocalDateTime(timeZone)?.date

private fun TaskRowModel.isOverdue(today: LocalDate, timeZone: TimeZone): Boolean =
    localDueDate(timeZone)?.let { it < today } == true

private fun TaskRowModel.isDueOn(date: LocalDate, timeZone: TimeZone): Boolean =
    localDueDate(timeZone) == date

private fun TaskRowModel.isDueBetween(today: LocalDate, startOffset: Int, endOffset: Int, timeZone: TimeZone): Boolean {
    val due = localDueDate(timeZone) ?: return false
    val from = today.plus(startOffset, DateTimeUnit.DAY)
    val to = today.plus(endOffset, DateTimeUnit.DAY)
    return due >= from && due <= to
}

private fun TaskRowModel.isDueAfter(date: LocalDate, timeZone: TimeZone): Boolean =
    localDueDate(timeZone)?.let { it > date } == true
