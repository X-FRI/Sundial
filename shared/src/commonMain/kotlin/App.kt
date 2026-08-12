package com.myapplication.shared.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myapplication.shared.di.AppGraph
import com.myapplication.shared.di.createAppGraph
import com.myapplication.shared.ui.PlatformBackHandler
import com.myapplication.shared.ui.components.RemDialog
import com.myapplication.shared.ui.detail.DetailScreen
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.main.Route
import com.myapplication.shared.ui.narrow.NarrowBottomNav
import com.myapplication.shared.ui.narrow.NarrowTopBar
import com.myapplication.shared.ui.settings.SettingsScreen
import com.myapplication.shared.ui.shell.DesktopShell
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemType
import com.myapplication.shared.ui.theme.RemindersTheme
import com.myapplication.shared.ui.todolist.TodoFAB
import com.myapplication.shared.ui.todolist.TodoListScreen
import com.myapplication.shared.ui.uiMessage

/**
 * 应用入口：包一层主题与依赖图（AppGraph），再交给 [AppRoot] 做响应式布局路由。
 *
 * 设计要点：
 * - AppGraph 用 `remember` 创建一次，随组合生命周期存续，避免重复初始化数据库与同步引擎；
 * - 所有界面（Sidebar / 列表 / 详情 / 设置）都由 [MainViewModel] 的 [Route] 状态机驱动，
 *   本文件只负责「根据路由与屏宽决定画什么」，不持有业务状态。
 */
@Composable
fun App() {
    RemindersTheme {
        val graph = remember { createAppGraph() }
        AppRoot(graph)
    }
}

/**
 * 应用根容器：单一 `when` 路由状态机 + 900dp 宽度断点的响应式布局。
 *
 * 路由三分支（按优先级）：
 * 1. 设置页全屏覆盖（任何屏宽下优先）；
 * 2. 宽屏桌面外壳：DesktopShell（Sidebar + 主台账 + 右侧详情检查器）；
 * 3. 窄屏：主列表常驻，详情以底部抽屉（ModalBottomSheet）形式从底部滑出。
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(graph: AppGraph) {
    val mainVm: MainViewModel = viewModel { MainViewModel(graph.repository, graph.addTodo, graph.timeZone) }
    val route by mainVm.route.collectAsState()
    val syncStatus by graph.engine.status.collectAsState()
    val colors = LocalRemColors.current

    // 平台返回键：仅在非主路由时拦截（主路由返回应直接退出应用，交还给系统处理）。
    // Android 走 BackHandler 实现，桌面/桌面 Escape 由下方 onPreviewKeyEvent 负责。
    PlatformBackHandler(enabled = route != Route.Main) { mainVm.back() }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(colors.bgSecondary)
            // 桌面端没有系统返回键，这里统一把 Escape 映射为 back()；KeyUp 避免按住 Esc 时重复触发。
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && event.key == Key.Escape) {
                    mainVm.back()
                    true
                } else {
                    false
                }
            },
    ) {
        // 900dp 宽度断点：大于等于为桌面宽屏三栏布局，否则走窄屏（手机）两页式布局。
        val wide = maxWidth >= 900.dp
        // 从路由中提取当前选中的待办 id；宽屏下详情栏靠它决定可见性。
        val selectedId = (route as? Route.Detail)?.todoId
        when {
            // 分支 1：设置页全屏覆盖（不参与宽/窄分屏）。
            route == Route.Settings -> SettingsScreen(
                viewModel { graph.settingsViewModelFactory() },
                onBack = mainVm::back,
            )
            // 分支 2：宽屏桌面外壳（Sidebar / 台账 / 详情检查器）。
            wide -> {
                DesktopShell(graph = graph, mainVm = mainVm)
            }
            // 分支 3：窄屏——主列表常驻，详情以 ModalBottomSheet 从底部滑出（带遮罩与下滑手势）。
            // 可见性完全由路由派生：Route.Detail 时组合底部抽屉，mainVm.back()（返回键/Escape/
            // 遮罩点击/下滑/详情页关闭按钮）回到 Route.Main 即移除抽屉，天然满足「先关抽屉，再回主列表」。
            else -> {
                val detailId = selectedId
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().background(colors.bgPrimary)) {
                        NarrowTopBar(mainVm, syncStatus = syncStatus, onSyncNow = { graph.engine.syncNow() })
                        // 下拉刷新：手势触发立即同步，旋转指示器跟随 syncStatus.syncing 动画。
                        PullToRefreshBox(
                            isRefreshing = syncStatus.syncing,
                            onRefresh = { graph.engine.syncNow() },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                TodoListScreen(mainVm, Modifier.fillMaxSize(), showHeader = false)
                                // FAB 叠在列表右下角。
                                TodoFAB(
                                    mainVm,
                                    Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(16.dp),
                                )
                            }
                        }
                        NarrowBottomNav(mainVm)
                    }
                    // 选中待办时从底部滑出详情抽屉；Dismiss（下滑/遮罩/返回）统一收敛到
                    // onDismissRequest → mainVm.back()，与宽屏详情栏同一路由语义。
                    if (detailId != null) {
                        ModalBottomSheet(
                            onDismissRequest = mainVm::back,
                            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                            modifier = Modifier.fillMaxWidth(),
                            dragHandle = null,
                            // 抽屉内部不再吃系统 insets：DetailScreen 自带
                            // statusBarsPadding/navigationBarsPadding（与旧全屏详情分支一致），
                            // 避免上下各重复避让一次。
                            contentWindowInsets = { WindowInsets(0.dp) },
                        ) {
                            DetailScreen(
                                mainVm,
                                graph,
                                detailId,
                                Modifier
                                    .fillMaxSize()
                                    .background(colors.bgPrimary)
                                    .statusBarsPadding()
                                    .navigationBarsPadding(),
                            )
                        }
                    }
                }
            }
        }
    }

    // 全局错误提示：MainViewModel 的命令失败统一经 lastError 通道上报，这里映射成中文弹窗。
    // 详情页错误由 DetailScreen 自己消费，不走这里。
    val error by mainVm.lastError.collectAsState()
    val errorMsg = error?.uiMessage()
    if (errorMsg != null) {
        RemDialog(
            title = "出错了",
            onDismiss = mainVm::dismissError,
            confirmText = "知道了",
            onConfirm = mainVm::dismissError,
            showButtons = false,
            content = {
                androidx.compose.foundation.text.BasicText(errorMsg, style = RemType.text14.copy(color = colors.textNormal))
            },
        )
    }
}
