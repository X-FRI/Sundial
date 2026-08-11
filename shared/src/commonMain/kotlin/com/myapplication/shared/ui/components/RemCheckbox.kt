package com.myapplication.shared.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.myapplication.shared.ui.theme.LocalRemColors

@Composable
fun RemCheckbox(
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 16.dp,
) {
    val colors = LocalRemColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.85f else 1f, tween(150), label = "cb-scale")
    val fill by animateColorAsState(
        if (checked) colors.accent else Color.Transparent,
        spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "cb-fill",
    )
    Box(
        modifier
            .size(size + 10.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val d = this.size.minDimension
            val r = d / 2f
            drawCircle(fill)
            drawCircle(
                color = when {
                    checked -> colors.accent
                    hovered -> colors.accent
                    else -> colors.checkboxBorder
                },
                style = Stroke(width = r * 0.18f),
            )
            if (checked) {
                val p = Path().apply {
                    moveTo(d * 0.24f, d * 0.52f)
                    lineTo(d * 0.44f, d * 0.72f)
                    lineTo(d * 0.78f, d * 0.30f)
                }
                drawPath(p, Color.White, style = Stroke(width = d * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
