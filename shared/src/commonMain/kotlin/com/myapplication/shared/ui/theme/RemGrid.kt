package com.myapplication.shared.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.remGrid(colors: RemColors, spacing: Dp = 8.dp, alpha: Float = 0.35f): Modifier =
    drawBehind {
        val s = spacing.toPx()
        val line = colors.border.copy(alpha = alpha)
        var x = s
        while (x < size.width) {
            drawLine(line, Offset(x, 0f), Offset(x, size.height), 1f)
            x += s
        }
        var y = s
        while (y < size.height) {
            drawLine(line, Offset(0f, y), Offset(size.width, y), 1f)
            y += s
        }
    }
