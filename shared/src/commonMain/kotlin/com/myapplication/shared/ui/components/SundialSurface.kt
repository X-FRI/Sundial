package com.myapplication.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemRadii

enum class SundialSurfaceTone { Page, Panel, Raised, Inset, Transparent }

@Composable
fun SundialSurface(
    tone: SundialSurfaceTone,
    modifier: Modifier = Modifier,
    border: Boolean = false,
    radius: Dp = RemRadii.r4,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalRemColors.current
    val bg = when (tone) {
        SundialSurfaceTone.Page -> colors.bgPrimary
        SundialSurfaceTone.Panel -> colors.surface
        SundialSurfaceTone.Raised -> colors.surfaceRaised
        SundialSurfaceTone.Inset -> colors.surfaceInset
        SundialSurfaceTone.Transparent -> Color.Transparent
    }
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .clip(shape)
            .background(bg)
            .border(if (border) 1.dp else 0.dp, colors.borderSubtle, shape),
        content = content,
    )
}
