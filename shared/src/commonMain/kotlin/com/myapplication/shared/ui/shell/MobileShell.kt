package com.myapplication.shared.ui.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.myapplication.shared.di.AppGraph
import com.myapplication.shared.domain.sync.SyncMode
import com.myapplication.shared.domain.sync.SyncStatus
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemBadge
import com.myapplication.shared.ui.components.RemBadgeTone
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.components.RemIconButton
import com.myapplication.shared.ui.components.RemSyncIndicator
import com.myapplication.shared.ui.components.RemTextField
import com.myapplication.shared.ui.detail.DetailContent
import com.myapplication.shared.ui.ledger.MainLedger
import com.myapplication.shared.ui.ledger.TodayRhythmState
import com.myapplication.shared.ui.ledger.TodayTimelineState
import com.myapplication.shared.ui.ledger.buildTodayRhythmState
import com.myapplication.shared.ui.ledger.buildTodayTimelineState
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.main.Route
import com.myapplication.shared.ui.main.Scope
import com.myapplication.shared.ui.main.scopeTitle
import com.myapplication.shared.ui.sync.phase
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemType
import com.myapplication.shared.ui.todolist.TodoFormDialog

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
    val rhythm = buildTodayRhythmState(todos, graph.clock.now(), graph.timeZone)
    val timeline = buildTodayTimelineState(todos, graph.clock.now(), graph.timeZone)
    var showCreate by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize().background(colors.bgSecondary)) {
        Column(Modifier.fillMaxSize()) {
            MobileTopBar(mainVm, syncStatus, onSyncNow = { graph.engine.syncNow() }, onCreate = { showCreate = true })
            MobileRhythmCard(rhythm, timeline, onOpen = mainVm::openDetail)
            PullToRefreshBox(
                isRefreshing = syncStatus.syncing,
                onRefresh = { graph.engine.syncNow() },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                MainLedger(
                    mainVm = mainVm,
                    selectedId = selectedId,
                    modifier = Modifier.fillMaxSize().padding(bottom = 74.dp),
                    clock = graph.clock,
                    timeZone = graph.timeZone,
                    showHeader = false,
                    showRhythm = false,
                    compactRows = true,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                )
            }
            MobileBottomNav(mainVm)
        }
        if (selectedId != null) {
            ModalBottomSheet(
                onDismissRequest = mainVm::back,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                dragHandle = null,
                contentWindowInsets = { WindowInsets(0.dp) },
            ) {
                DetailContent(
                    mainVm = mainVm,
                    graph = graph,
                    todoId = selectedId,
                    showCloseButton = true,
                    modifier = Modifier.fillMaxSize().fillMaxWidth().background(colors.bgPrimary).navigationBarsPadding(),
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
    var searching by remember { mutableStateOf(false) }
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
                    androidx.compose.foundation.text.BasicText(scopeTitle(scope, query), style = RemType.title20.copy(color = colors.textHigh))
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
private fun MobileRhythmCard(
    rhythm: TodayRhythmState,
    timeline: TodayTimelineState,
    onOpen: (Long) -> Unit,
) {
    val colors = LocalRemColors.current
    val next = timeline.upcoming.firstOrNull()
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RemIcon(IconName.Clock, colors.brand, Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            androidx.compose.foundation.text.BasicText("现在 ${timeline.nowLabel}", style = RemType.text12.copy(color = colors.textLow))
            Spacer(Modifier.weight(1f))
            RemBadge("今日 ${rhythm.pendingTodayCount}", tone = RemBadgeTone.Warning)
            Spacer(Modifier.width(6.dp))
            RemBadge("完成 ${timeline.completedTodayCount}", tone = RemBadgeTone.Success)
        }
        MobileRail(timeline, Modifier.padding(top = 12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .background(colors.brandSubtle, RoundedCornerShape(RemRadii.r4))
                .clickable(enabled = next != null) { next?.let { onOpen(it.item.id) } }
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Column {
                androidx.compose.foundation.text.BasicText("下一件事", style = RemType.label12.copy(color = colors.brand))
                Spacer(Modifier.height(3.dp))
                androidx.compose.foundation.text.BasicText(
                    next?.let { "${it.timeLabel}  ${it.item.title}" } ?: "今天没有定时待办",
                    style = RemType.text14.copy(color = colors.textHigh),
                )
            }
        }
    }
}

@Composable
private fun MobileRail(timeline: TodayTimelineState, modifier: Modifier = Modifier) {
    val colors = LocalRemColors.current
    BoxWithConstraints(modifier.fillMaxWidth().height(26.dp)) {
        Canvas(Modifier.fillMaxWidth().height(26.dp)) {
            val railY = size.height / 2f
            drawLine(colors.borderSubtle, Offset(0f, railY), Offset(size.width, railY), strokeWidth = 2.dp.toPx())
            timeline.past.forEach { drawCircle(colors.warning.copy(alpha = 0.5f), radius = 4.dp.toPx(), center = Offset(size.width * it.progress, railY)) }
            timeline.upcoming.forEach { drawCircle(if (it.isNext) colors.brand else colors.info, radius = if (it.isNext) 5.dp.toPx() else 4.dp.toPx(), center = Offset(size.width * it.progress, railY)) }
        }
        Box(
            Modifier
                .offset(x = maxWidth * timeline.nowProgress - 1.dp)
                .width(2.dp)
                .height(26.dp)
                .background(colors.brand, RoundedCornerShape(1.dp))
        )
    }
}

@Composable
private fun MobileBottomNav(mainVm: MainViewModel) {
    val colors = LocalRemColors.current
    val scope by mainVm.scope.collectAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.bgPrimary)
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MobileNavItem(IconName.Today, "今天", scope == Scope.Today, Modifier.weight(1f)) { mainVm.selectScope(Scope.Today) }
        MobileNavItem(IconName.Scheduled, "计划", scope == Scope.Scheduled, Modifier.weight(1f)) { mainVm.selectScope(Scope.Scheduled) }
        MobileNavItem(IconName.Layers, "全部", scope == Scope.All, Modifier.weight(1f)) { mainVm.selectScope(Scope.All) }
        MobileNavItem(IconName.CheckCircle, "完成", scope == Scope.Completed, Modifier.weight(1f)) { mainVm.selectScope(Scope.Completed) }
        MobileNavItem(IconName.Trash, "删除", scope == Scope.Trash, Modifier.weight(1f)) { mainVm.selectScope(Scope.Trash) }
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
