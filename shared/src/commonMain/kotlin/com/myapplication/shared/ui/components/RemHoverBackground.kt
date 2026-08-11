package com.myapplication.shared.ui.components

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.myapplication.shared.ui.theme.LocalRemColors

@Composable
fun rememberHoverBackground(interactionSource: InteractionSource): Color {
    val colors = LocalRemColors.current
    val hovered by interactionSource.collectIsHoveredAsState()
    return if (hovered) colors.bgSecondary else Color.Transparent
}
