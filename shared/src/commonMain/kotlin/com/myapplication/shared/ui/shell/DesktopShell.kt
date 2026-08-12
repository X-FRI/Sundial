package com.myapplication.shared.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.myapplication.shared.di.AppGraph
import com.myapplication.shared.ui.detail.DetailInspector
import com.myapplication.shared.ui.ledger.MainLedger
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.main.Route
import com.myapplication.shared.ui.sidebar.Sidebar
import com.myapplication.shared.ui.theme.LocalRemColors

/**
 * 桌面端产品外壳：Sidebar + 主台账（MainLedger）+ 右侧详情检查器（DetailInspector）。
 * 详情栏可见性由 [Route.Detail] 派生，与窄屏抽屉共用同一路由语义。
 */
@Composable
fun DesktopShell(
    graph: AppGraph,
    mainVm: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val route by mainVm.route.collectAsState()
    val syncStatus by graph.engine.status.collectAsState()
    val selectedId = (route as? Route.Detail)?.todoId
    Row(modifier.fillMaxSize().background(LocalRemColors.current.bgSecondary)) {
        Sidebar(mainVm, syncStatus, onSyncNow = { graph.engine.syncNow() })
        MainLedger(
            mainVm = mainVm,
            selectedId = selectedId,
            modifier = Modifier.weight(1f).statusBarsPadding(),
            clock = graph.clock,
            timeZone = graph.timeZone,
        )
        DetailInspector(
            mainVm = mainVm,
            graph = graph,
            todoId = selectedId,
            modifier = Modifier.statusBarsPadding(),
        )
    }
}
