package com.myapplication.shared.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.myapplication.shared.ui.theme.LocalRemColors
import kotlinx.coroutines.delay

@Composable
fun rememberHoverBackground(
    interactionSource: InteractionSource,
    enterDelayMs: Long = 60,
    exitDelayMs: Long = 180,
): Color {
    val colors = LocalRemColors.current
    val hovered by interactionSource.collectIsHoveredAsState()
    var stable by remember { mutableStateOf(false) }
    LaunchedEffect(hovered) {
        if (hovered) {
            delay(enterDelayMs)
            stable = true
        } else {
            delay(exitDelayMs)
            stable = false
        }
    }
    return animateColorAsState(
        if (stable) colors.bgSecondary else Color.Transparent,
        tween(120),
        label = "hover-bg",
    ).value
}
