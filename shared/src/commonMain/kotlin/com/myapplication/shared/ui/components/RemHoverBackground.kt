package com.myapplication.shared.ui.components

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.myapplication.shared.ui.theme.LocalRemColors

/**
 * 行级 hover 背景色工具：把交互源上的 hover 状态映射为统一的
 * [LocalRemColors.bgSecondary] 背景色（非 hover 时透明）。
 *
 * 适用场景：列表行/菜单项的悬停反馈；与 RemButton/RemIconButton 内部
 * 的 hover 逻辑同源，保证全应用 hover 视觉一致。
 */
@Composable
fun rememberHoverBackground(interactionSource: InteractionSource): Color {
    val colors = LocalRemColors.current
    val hovered by interactionSource.collectIsHoveredAsState()
    return if (hovered) colors.bgSecondary else Color.Transparent
}
