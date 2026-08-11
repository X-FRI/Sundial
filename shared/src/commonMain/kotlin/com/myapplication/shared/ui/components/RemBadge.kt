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
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemType

@Composable
fun RemBadge(
    label: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
    monospace: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
) {
    val colors = LocalRemColors.current
    val bg = color?.copy(alpha = 0.08f) ?: colors.bgPanel
    val fg = color ?: colors.textLow
    Row(
        modifier
            .clip(RoundedCornerShape(RemRadii.r2))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(3.dp))
        }
        androidx.compose.foundation.text.BasicText(
            label,
            style = RemType.text10.copy(
                color = fg,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            ),
        )
    }
}
