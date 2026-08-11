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

enum class RemButtonVariant { Default, Ghost, Danger }

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
        androidx.compose.foundation.text.BasicText(
            text,
            style = RemType.label12.copy(color = if (pressed) fg.copy(alpha = 0.8f) else fg),
        )
    }
}

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
