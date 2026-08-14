package com.myapplication.shared.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/**
 * Android actual：直接桥接 androidx.activity 的 [BackHandler]。
 *
 * 语义：enabled=true 时拦截系统返回键并调用 onBack（回到主列表），
 * enabled=false（主路由）时不拦截，返回键交给系统退出应用。
 * BackHandler 本身感知组合生命周期：离开组合时自动解绑。
 */
@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    BackHandler(enabled = enabled, onBack = onBack)
}
