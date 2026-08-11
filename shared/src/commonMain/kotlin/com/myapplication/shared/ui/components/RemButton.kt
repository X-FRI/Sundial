package com.myapplication.shared.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemType

/**
 * 按钮视觉变体：
 * - Default：品牌色实底按钮（主操作，白字）；
 * - Ghost：无背景的次操作按钮，hover 时浮现次级背景；
 * - Danger：危险操作，红色文字 + 1dp 边框，hover 时叠加红色低透明度背景。
 */
enum class RemButtonVariant { Default, Ghost, Danger }

/**
 * 通用文字按钮（32dp 高）。
 *
 * 设计要点：
 * - 自绘交互状态：通过 [MutableInteractionSource] 收集 hover/pressed，
 *   背景与前景色均用 animateColorAsState 做 200ms 过渡；
 * - 关闭默认 indication（水波纹），风格统一为纯色渐变；
 * - 禁用态按变体处理：Default 变灰的品牌色，其余变体完全透明 + 弱化文字；
 * - Danger 变体仅画边框，底色靠 hover 叠加，避免常驻红色块过重。
 */
@Composable
fun RemButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: RemButtonVariant = RemButtonVariant.Ghost,
    enabled: Boolean = true,
) {
    val colors = LocalRemColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    // 背景色状态机：禁用 > 变体主色 > hover 反馈 > 透明兜底
    val bg by animateColorAsState(
        when {
            !enabled && variant == RemButtonVariant.Default -> colors.brand.copy(alpha = 0.4f)
            !enabled -> Color.Transparent
            variant == RemButtonVariant.Default && hovered -> colors.brandHover
            variant == RemButtonVariant.Default -> colors.brand
            variant == RemButtonVariant.Danger && hovered -> colors.error.copy(alpha = 0.08f)
            hovered -> colors.bgSecondary
            else -> Color.Transparent
        },
        tween(200),
        label = "btn-bg",
    )
    // 前景色状态机：禁用态弱化、Default 恒白字、Danger 恒红字
    val fg by animateColorAsState(
        when {
            !enabled && variant == RemButtonVariant.Default -> Color.White.copy(alpha = 0.7f)
            !enabled -> colors.textLow.copy(alpha = 0.6f)
            variant == RemButtonVariant.Default -> Color.White
            variant == RemButtonVariant.Danger -> colors.error
            else -> colors.textNormal
        },
        tween(200),
        label = "btn-fg",
    )
    val shape = RoundedCornerShape(RemRadii.r4)
    Box(
        modifier
            .clip(shape)
            .background(bg)
            .border(if (variant == RemButtonVariant.Danger) 1.dp else 0.dp, colors.border, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .height(32.dp)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 按压时前景再降 20% 透明度，模拟"按下去"的反馈
        androidx.compose.foundation.text.BasicText(
            text,
            style = RemType.label12.copy(color = if (pressed) fg.copy(alpha = 0.8f) else fg),
        )
    }
}

/**
 * 图标按钮：默认 14dp 图标、点击热区为图标 + 8dp 内边距。
 *
 * 与 [RemButton] 相同的交互自绘思路：hover 浮现 bgSecondary，pressed 时
 * 图标透明度降为 80%。contentDescription 通过 semantics 暴露给无障碍层，
 * 传 null 时该按钮对屏幕阅读器完全不可见（纯装饰图标用）。
 */
@Composable
fun RemIconButton(
    icon: IconName,
    contentDescription: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    size: Dp = 14.dp,
) {
    val colors = LocalRemColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val bg by animateColorAsState(if (hovered) colors.bgSecondary else Color.Transparent, tween(200), label = "ib-bg")
    Box(
        modifier
            .size(size + 8.dp)
            .clip(RoundedCornerShape(RemRadii.r2))
            .background(bg)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        RemIcon(icon, if (pressed) (tint ?: colors.textLow).copy(alpha = 0.8f) else tint ?: colors.textLow, Modifier.size(size))
    }
}
