package com.myapplication.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemColors
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemType

/**
 * 徽章语义色调：Neutral 保持无强调色的中性灰底，其余映射到对应状态色。
 */
enum class RemBadgeTone { Neutral, Brand, Success, Warning, Error }

private fun badgeToneColor(
    tone: RemBadgeTone,
    colors: RemColors,
): Color? =
    when (tone) {
        RemBadgeTone.Neutral -> null
        RemBadgeTone.Brand -> colors.brand
        RemBadgeTone.Success -> colors.success
        RemBadgeTone.Warning -> colors.warning
        RemBadgeTone.Error -> colors.error
    }

/**
 * 通用小徽章，用于列表颜色、旗标等轻量标签场景。
 *
 * 设计要点：
 * - 无交互状态（不可点击），纯展示；点击语义由调用方自行决定；
 * - 颜色自适应：传入 [color] 时取其 8% 透明度作背景、原色作文字，未传时
 *   落到面板背景 + 弱化文字，保证任意主题下都可用；[tone] 提供语义色调，
 *   有效色 = [color] ?: 色调映射色（Neutral 为 null，保持无强调色行为）；
 * - 通过 [monospace] 支持等宽数字（如剩余条数），宽度由内容撑开。
 */
@Composable
fun RemBadge(
    label: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
    tone: RemBadgeTone = RemBadgeTone.Neutral,
    monospace: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
) {
    val colors = LocalRemColors.current
    // 背景 = 强调色低透明度；前景 = 强调色本身（无强调色时退化为面板灰底 + 弱文字）
    val effectiveColor = color ?: badgeToneColor(tone, colors)
    val bg = effectiveColor?.copy(alpha = 0.08f) ?: colors.bgPanel
    val fg = effectiveColor ?: colors.textLow
    Row(
        modifier
            .clip(RoundedCornerShape(RemRadii.r2))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 可选前置图标（如颜色圆点），图标与文字间距固定 3dp
        if (icon != null) {
            icon()
            Spacer(Modifier.width(3.dp))
        }
        androidx.compose.foundation.text.BasicText(
            label,
            style =
                RemType.text10.copy(
                    color = fg,
                    fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                ),
        )
    }
}
