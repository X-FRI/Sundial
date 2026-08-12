package com.myapplication.shared.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myapplication.shared.di.AppGraph
import com.myapplication.shared.ui.detail.DetailContent
import com.myapplication.shared.ui.ledger.MainLedger
import com.myapplication.shared.ui.ledger.TodayRhythmCompact
import com.myapplication.shared.ui.ledger.buildTodayRhythmState
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.main.Route
import com.myapplication.shared.ui.narrow.NarrowBottomNav
import com.myapplication.shared.ui.narrow.NarrowTopBar
import com.myapplication.shared.ui.theme.LocalRemColors

/**
 * 手机端产品外壳：顶部栏 + 今日节奏徽标 + 下拉刷新台账 + 底部五格导航，
 * 详情以 ModalBottomSheet 从底部滑出（与宽屏详情栏共用同一路由语义）。
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
    val syncStatus by graph.engine.status.collectAsState()
    val selectedId = (route as? Route.Detail)?.todoId
    val rhythm = buildTodayRhythmState(todos, graph.clock.now(), graph.timeZone)
    Box(modifier.fillMaxSize().background(colors.bgPrimary)) {
        Column(Modifier.fillMaxSize()) {
            NarrowTopBar(mainVm, syncStatus = syncStatus, onSyncNow = { graph.engine.syncNow() })
            TodayRhythmCompact(rhythm)
            PullToRefreshBox(
                isRefreshing = syncStatus.syncing,
                onRefresh = { graph.engine.syncNow() },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                MainLedger(
                    mainVm = mainVm,
                    selectedId = selectedId,
                    modifier = Modifier.fillMaxSize().padding(bottom = 72.dp),
                    clock = graph.clock,
                    timeZone = graph.timeZone,
                    showHeader = false,
                    showRhythm = false,
                    showOverview = false,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            NarrowBottomNav(mainVm)
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
                    modifier = Modifier.fillMaxSize().fillMaxWidth().background(colors.bgPrimary).statusBarsPadding().navigationBarsPadding(),
                )
            }
        }
    }
}
