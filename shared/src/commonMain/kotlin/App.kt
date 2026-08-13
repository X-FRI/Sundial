package com.myapplication.shared.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.myapplication.shared.ui.main.LaunchTarget
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.main.Route
import com.myapplication.shared.ui.settings.SettingsScreen
import com.myapplication.shared.ui.settings.SettingsViewModel
import com.myapplication.shared.ui.shell.DesktopShell
import com.myapplication.shared.ui.shell.MobileShell
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemType
import com.myapplication.shared.ui.theme.RemindersTheme
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
fun App(launchTarget: String? = null, launchNonce: Int = 0) {
    val graph = remember { createAppGraph() }
    val settingsVm: SettingsViewModel = viewModel { graph.settingsViewModelFactory() }
    val preferences by settingsVm.preferences.collectAsState()
    RemindersTheme(
        themeMode = preferences.themeMode,
        displayDensity = preferences.displayDensity,
        fontFamily = preferences.fontFamily,
    ) {
        AppRoot(graph, settingsVm, launchTarget, launchNonce)
    }
}

/**
 * 应用根容器：单一 `when` 路由状态机 + 900dp 宽度断点的响应式布局。
 *
 * 路由三分支（按优先级）：
 * 1. 设置页全屏覆盖（任何屏宽下优先）；
 * 2. 宽屏桌面外壳：DesktopShell（Sidebar + 主台账 + 右侧详情检查器）；
 * 3. 窄屏手机外壳：MobileShell（顶部栏 + 台账 + 详情底部抽屉）。
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppRoot(
    graph: AppGraph,
    settingsVm: SettingsViewModel,
    launchTarget: String? = null,
    launchNonce: Int = 0,
) {
    val mainVm: MainViewModel = viewModel {
        MainViewModel(graph.repository, graph.addTodo, graph.timeZone, graph.completeRecurringTodo)
    }
    LaunchedEffect(launchEffectKey(launchTarget, launchNonce)) {
        when (launchTarget) {
            "today" -> mainVm.applyLaunchTarget(LaunchTarget.Today)
            "workbench" -> mainVm.applyLaunchTarget(LaunchTarget.Workbench)
        }
    }
    val route by mainVm.route.collectAsState()
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
        when {
            // 分支 1：设置页全屏覆盖（不参与宽/窄分屏）。
            route == Route.Settings -> SettingsScreen(
                vm = settingsVm,
                mainVm = mainVm,
                onBack = mainVm::back,
            )
            // 分支 2：宽屏桌面外壳（Sidebar / 台账 / 详情检查器）。
            wide -> {
                DesktopShell(graph = graph, mainVm = mainVm)
            }
            // 分支 3：窄屏手机外壳——顶部栏 + 台账 + 底部导航，详情以 ModalBottomSheet 滑出。
            else -> MobileShell(graph = graph, mainVm = mainVm)
        }
    }

    // 全局错误提示：MainViewModel 的命令失败统一经 lastError 通道上报，这里映射成中文弹窗。
    // 详情页错误由 DetailContent 自己消费，不走这里。
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

internal data class LaunchEffectKey(val target: String?, val nonce: Int)

internal fun launchEffectKey(target: String?, nonce: Int): LaunchEffectKey =
    LaunchEffectKey(target, nonce)
