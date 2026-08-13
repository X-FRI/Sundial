package com.myapplication.shared.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.myapplication.shared.di.AppGraph
import com.myapplication.shared.domain.sync.SyncMode
import com.myapplication.shared.domain.sync.SyncStatus
import com.myapplication.shared.ui.analytics.AnalyticsScreen
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.components.RemIconButton
import com.myapplication.shared.ui.components.RemSyncIndicator
import com.myapplication.shared.ui.components.RemTextField
import com.myapplication.shared.ui.detail.DetailContent
import com.myapplication.shared.ui.ledger.ContextTimelineCompact
import com.myapplication.shared.ui.ledger.MainLedger
import com.myapplication.shared.ui.ledger.buildContextTimelineState
import com.myapplication.shared.ui.ledger.buildTodayTimelineState
import com.myapplication.shared.ui.ledger.toTimelineScope
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.main.Route
import com.myapplication.shared.ui.main.Scope
import com.myapplication.shared.ui.main.scopeTitle
import com.myapplication.shared.ui.sync.phase
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemType
import com.myapplication.shared.ui.todolist.TodoFormDialog
import kotlinx.coroutines.delay

/**
 * 手机端产品外壳：独立移动信息架构，而不是复用旧窄屏导航。
 * 顶部处理范围标题/搜索/同步，首屏给出下一件事，底部导航保持拇指可达。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileShell(
    graph: AppGraph,
    mainVm: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val colors = LocalRemColors.current
    val route by mainVm.route.collectAsState()
    val todos by mainVm.todos.collectAsState()
    val lists by mainVm.lists.collectAsState()
    val scope by mainVm.scope.collectAsState()
    val syncStatus by graph.engine.status.collectAsState()
    val selectedId = (route as? Route.Detail)?.todoId
    var now by remember(graph.clock) { mutableStateOf(graph.clock.now()) }
    LaunchedEffect(graph.clock) {
        while (true) {
            now = graph.clock.now()
            delay(60_000)
        }
    }
    val timeline = remember(todos, now) { buildTodayTimelineState(todos, now, graph.timeZone) }
    val inboxListId = remember(lists) { lists.firstOrNull { it.name == "收件箱" }?.id }
    val contextScope = remember(scope, inboxListId) { scope.toTimelineScope(inboxListId) }
    val contextTimeline = remember(todos, contextScope, now, inboxListId) {
        buildContextTimelineState(todos, contextScope, now, graph.timeZone, inboxListId)
    }
    var showCreate by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize().background(colors.bgSecondary)) {
        Column(Modifier.fillMaxSize()) {
            MobileTopBar(mainVm, syncStatus, onSyncNow = { graph.engine.syncNow() }, onCreate = { showCreate = true })
            if (scope == Scope.Analytics) {
                PullToRefreshBox(
                    isRefreshing = syncStatus.syncing,
                    onRefresh = { graph.engine.syncNow() },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    AnalyticsScreen(
                        mainVm = mainVm,
                        clock = graph.clock,
                        timeZone = graph.timeZone,
                        compact = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                if (scope is Scope.List) {
                    MobileListStrip(mainVm)
                } else {
                    MobileWorkbenchFilters(mainVm)
                }
                ContextTimelineCompact(
                    state = contextTimeline,
                    timeline = timeline,
                    showTodayLabels = scope == Scope.Today,
                    modifier = Modifier.background(colors.surface),
                )
                PullToRefreshBox(
                    isRefreshing = syncStatus.syncing,
                    onRefresh = { graph.engine.syncNow() },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    MainLedger(
                        mainVm = mainVm,
                        selectedId = selectedId,
                        modifier = Modifier.fillMaxSize(),
                        clock = graph.clock,
                        timeZone = graph.timeZone,
                        showHeader = false,
                        showRhythm = false,
                        compactRows = true,
                        edgeToEdgeRows = true,
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                    )
                }
            }
            MobileBottomNav(mainVm)
        }
        if (selectedId != null) {
            ModalBottomSheet(
                onDismissRequest = mainVm::closeDetail,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                dragHandle = null,
                contentWindowInsets = { WindowInsets(0.dp) },
            ) {
                DetailContent(
                    mainVm = mainVm,
                    graph = graph,
                    todoId = selectedId,
                    showCloseButton = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .fillMaxWidth()
                        .background(colors.bgPrimary)
                        .statusBarsPadding()
                        .navigationBarsPadding(),
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
private fun MobileTopBar(
    mainVm: MainViewModel,
    syncStatus: SyncStatus,
    onSyncNow: () -> Unit,
    onCreate: () -> Unit,
) {
    val colors = LocalRemColors.current
    val scope by mainVm.scope.collectAsState()
    val query by mainVm.searchQuery.collectAsState()
    val lists by mainVm.lists.collectAsState()
    var searching by remember { mutableStateOf(false) }
    val inboxListId = remember(lists) { lists.firstOrNull { it.name == "收件箱" }?.id }
    val title = remember(scope, query, lists, inboxListId) {
        if (query.isNotBlank()) {
            scopeTitle(scope, query)
        } else when (val currentScope = scope) {
            is Scope.List -> {
                val list = lists.firstOrNull { it.id == currentScope.listId }
                if (currentScope.listId == inboxListId) "收件箱 · 待整理" else list?.name ?: "列表"
            }
            else -> scopeTitle(scope, query)
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.bgPrimary)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (searching) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RemTextField(
                    value = query,
                    onValueChange = mainVm::setSearch,
                    placeholder = "搜索待办",
                    leadingIcon = IconName.Search,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                RemIconButton(
                    IconName.Close,
                    "退出搜索",
                    onClick = {
                        searching = false
                        mainVm.setSearch("")
                    },
                    size = 18.dp,
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    androidx.compose.foundation.text.BasicText("Sundial", style = RemType.label12.copy(color = colors.brand))
                    androidx.compose.foundation.text.BasicText(title, style = RemType.title20.copy(color = colors.textHigh))
                }
                if (syncStatus.mode != SyncMode.Local) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clickable(onClick = onSyncNow),
                        contentAlignment = Alignment.Center,
                    ) {
                        RemSyncIndicator(syncStatus.phase(), size = 13.dp)
                    }
                }
                RemIconButton(IconName.Plus, "添加待办", onClick = onCreate, size = 18.dp)
                RemIconButton(IconName.Settings, "设置", onClick = mainVm::openSettings, size = 18.dp)
                RemIconButton(IconName.Search, "搜索", onClick = { searching = true }, size = 18.dp)
            }
        }
    }
}

@Composable
private fun MobileBottomNav(mainVm: MainViewModel) {
    val colors = LocalRemColors.current
    val scope by mainVm.scope.collectAsState()
    val lists by mainVm.lists.collectAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.bgPrimary)
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MobileNavItem(IconName.Layers, "工作台", scope !is Scope.List && scope != Scope.Analytics, Modifier.weight(1f)) { mainVm.selectScope(Scope.All) }
        MobileNavItem(IconName.Tray, "列表", scope is Scope.List, Modifier.weight(1f)) {
            lists.firstOrNull()?.let { mainVm.selectScope(Scope.List(it.id)) } ?: mainVm.selectScope(Scope.All)
        }
        MobileNavItem(IconName.Chart, "分析", scope == Scope.Analytics, Modifier.weight(1f)) { mainVm.selectScope(Scope.Analytics) }
    }
}

@Composable
private fun MobileWorkbenchFilters(mainVm: MainViewModel) {
    val scope by mainVm.scope.collectAsState()
    val todayCount by mainVm.todayCount.collectAsState()
    val scheduledCount by mainVm.scheduledCount.collectAsState()
    val allCount by mainVm.allCount.collectAsState()
    val completedCount by mainVm.completedCount.collectAsState()
    val trashCount by mainVm.trashCount.collectAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .background(LocalRemColors.current.bgPrimary)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MobileFilterChip(IconName.Layers, "全部", allCount, scope == Scope.All) { mainVm.selectScope(Scope.All) }
        MobileFilterChip(IconName.Today, "今天", todayCount, scope == Scope.Today) { mainVm.selectScope(Scope.Today) }
        MobileFilterChip(IconName.Scheduled, "计划", scheduledCount, scope == Scope.Scheduled) { mainVm.selectScope(Scope.Scheduled) }
        MobileFilterChip(IconName.CheckCircle, "完成", completedCount, scope == Scope.Completed) { mainVm.selectScope(Scope.Completed) }
        MobileFilterChip(IconName.Trash, "垃圾箱", trashCount, scope == Scope.Trash) { mainVm.selectScope(Scope.Trash) }
    }
}

@Composable
private fun MobileListStrip(mainVm: MainViewModel) {
    val scope by mainVm.scope.collectAsState()
    val lists by mainVm.lists.collectAsState()
    val listCounts by mainVm.listCounts.collectAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .background(LocalRemColors.current.bgPrimary)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        lists.forEach { list ->
            val label = if (list.name == "收件箱") "收件箱 · 待整理" else list.name
            MobileFilterChip(
                icon = IconName.Inbox,
                label = label,
                count = listCounts[list.id] ?: 0,
                selected = scope == Scope.List(list.id),
            ) { mainVm.selectScope(Scope.List(list.id)) }
        }
    }
}

@Composable
private fun MobileFilterChip(
    icon: IconName,
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalRemColors.current
    Row(
        Modifier
            .height(34.dp)
            .background(if (selected) colors.brandSubtle else colors.bgSecondary, RoundedCornerShape(RemRadii.r4))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemIcon(icon, if (selected) colors.brand else colors.textLow, Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        androidx.compose.foundation.text.BasicText(label, style = RemType.text12.copy(color = if (selected) colors.brand else colors.textNormal))
        Spacer(Modifier.width(6.dp))
        androidx.compose.foundation.text.BasicText(count.toString(), style = RemType.text10.copy(color = colors.textLow))
    }
}

@Composable
private fun MobileNavItem(
    icon: IconName,
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalRemColors.current
    Column(
        modifier
            .height(50.dp)
            .background(if (selected) colors.brandSubtle else colors.bgPrimary, RoundedCornerShape(RemRadii.r4))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RemIcon(icon, if (selected) colors.brand else colors.textLow, Modifier.size(17.dp))
        Spacer(Modifier.height(2.dp))
        androidx.compose.foundation.text.BasicText(label, style = RemType.text10.copy(color = if (selected) colors.brand else colors.textLow))
    }
}
