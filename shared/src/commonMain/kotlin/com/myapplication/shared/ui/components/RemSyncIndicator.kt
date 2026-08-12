package com.myapplication.shared.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myapplication.shared.ui.theme.LocalRemColors

// 同步状态指示器：Syncing=旋转双箭头，Synced=对勾，Error=叹号(暂用警告色箭头)，Idle=静态灰箭头
enum class SyncIndicatorState { Idle, Syncing, Synced, Error }

@Composable
fun RemSyncIndicator(
    state: SyncIndicatorState,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
) {
    val colors = LocalRemColors.current
    val tint = when (state) {
        SyncIndicatorState.Syncing -> colors.brand
        SyncIndicatorState.Synced -> colors.success
        SyncIndicatorState.Error -> colors.error
        SyncIndicatorState.Idle -> colors.textLow
    }
    val rotation = if (state == SyncIndicatorState.Syncing) {
        val transition = rememberInfiniteTransition(label = "sync-rotation")
        val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "sync-angle")
        angle
    } else 0f
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        RemIcon(IconName.Sync, tint, Modifier.fillMaxSize().graphicsLayer { rotationZ = rotation })
    }
}
