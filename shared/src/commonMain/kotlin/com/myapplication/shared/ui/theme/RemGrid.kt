package com.myapplication.shared.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 调试用辅助网格：在任意 Composable 上叠加等间距网格线，
 * 用于检查布局对齐（组件间距是否落在 8dp 步长上）。
 *
 * 实现：drawBehind 按 spacing 步长在宽/高方向画 1px 竖线与横线，
 * 线色 = border 色 + 透明度参数；默认 alpha 0.35 足够可见又不遮挡内容。
 */
fun Modifier.remGrid(colors: RemColors, spacing: Dp = 8.dp, alpha: Float = 0.35f): Modifier =
    drawBehind {
        val s = spacing.toPx()
        val line = colors.border.copy(alpha = alpha)
        // 竖线：从 spacing 开始递增到画布宽度
        var x = s
        while (x < size.width) {
            drawLine(line, Offset(x, 0f), Offset(x, size.height), 1f)
            x += s
        }
        // 横线：从 spacing 开始递增到画布高度
        var y = s
        while (y < size.height) {
            drawLine(line, Offset(0f, y), Offset(size.width, y), 1f)
            y += s
        }
    }
