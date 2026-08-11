package com.myapplication.shared.ui

import androidx.compose.runtime.Composable

/**
 * 平台返回键处理的 expect 声明：统一各端「物理返回/退出」手势的入口。
 *
 * expect/actual 语义（三端差异见各 actual 文件）：
 * - Android：桥接 androidx.activity BackHandler，拦截系统返回键；
 * - Desktop / iOS：无系统返回键概念，actual 为 no-op（桌面返回交给
 *   App.kt 的 Escape 键处理，iOS 手势返回由导航栈负责）。
 *
 * AppRoot 以 `enabled = route != Route.Main` 调用：仅在非主路由时拦截，
 * 主路由下的返回键放行给系统（退出应用）。
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
