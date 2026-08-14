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

/**
 * 圆形复选框，用 Canvas 手绘而非图片资源，保证任意平台缩放清晰。
 *
 * 设计要点：
 * - 可访问性：通过 toggleable + Role.Checkbox 暴露给无障碍框架，
 *   onToggle 统一承接所有点击/键盘切换；
 * - 交互反馈：按压时整体缩放至 85%（150ms），选中填充用弹簧动画
 *   （低阻尼 0.6 + 高刚度 400），手感更"弹"；
 * - 描边颜色三态：选中=品牌色，hover=高对比文字色，否则=弱文字色；
 * - 外层热区比视觉尺寸大 10dp，降低小目标的点击难度。
 */
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
    // 按压缩放动画：按 85%，松开弹回 1f
    val scale by animateFloatAsState(if (pressed) 0.85f else 1f, tween(150), label = "cb-scale")
    // 填充色：选中为品牌色实心，取消时透明；弹簧动画使勾选/取消过渡自然
    val fill by animateColorAsState(
        if (checked) colors.brand else Color.Transparent,
        spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "cb-fill",
    )
    Box(
        modifier
            .size(size + 10.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }.clip(CircleShape)
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
            // 第一层：选中时的品牌色实心圆
            drawCircle(fill)
            // 第二层：描边圆，颜色随 hover/选中状态切换；线宽 = 半径的 18%
            drawCircle(
                color =
                    when {
                        checked -> colors.brand
                        hovered -> colors.textHigh
                        else -> colors.textLow
                    },
                style = Stroke(width = r * 0.18f),
            )
            // 第三层：选中时白色对勾，坐标按直径比例硬编码（24%→78% 区间）
            if (checked) {
                val p =
                    Path().apply {
                        moveTo(d * 0.24f, d * 0.52f)
                        lineTo(d * 0.44f, d * 0.72f)
                        lineTo(d * 0.78f, d * 0.30f)
                    }
                drawPath(p, Color.White, style = Stroke(width = d * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
