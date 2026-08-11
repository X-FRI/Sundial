package com.myapplication.shared.ui

import androidx.compose.runtime.Composable

/**
 * iOS actual：no-op。
 *
 * iOS 没有系统返回键（返回靠导航栈边距手势/顶栏按钮，均通过 mainVm.back 触发），
 * 故此处不注册任何处理器。
 */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
