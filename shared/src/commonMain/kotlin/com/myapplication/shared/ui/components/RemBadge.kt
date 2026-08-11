package com.myapplication.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemType

@Composable
fun RemBadge(
    label: String,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    bg: Color? = null,
    icon: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalRemColors.current
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
    } else {
        Modifier
    }
    Row(
        modifier
            .then(clickModifier)
            .clip(RoundedCornerShape(RemRadii.r4))
            .background(bg ?: colors.selectedBg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(3.dp))
        }
        androidx.compose.foundation.text.BasicText(label, style = RemType.text12.copy(color = tint ?: colors.textTertiary))
    }
}
