package com.myapplication.shared.ui

import androidx.compose.runtime.Composable

/**
 * Desktop actual：no-op。
 *
 * 桌面没有「返回键」；返回行为由 App.kt 的 Escape 键（onPreviewKeyEvent）
 * 统一接管，因此这里什么都不做。
 */
@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) = Unit
