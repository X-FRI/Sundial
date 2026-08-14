package com.myapplication.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemType

@Composable
fun SundialAccordionSection(
    title: String,
    count: Int,
    tone: Color,
    modifier: Modifier = Modifier,
    defaultExpanded: Boolean = true,
    emptyText: String? = null,
    headerInset: Boolean = false,
    contentSurface: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalRemColors.current
    var expanded by remember(title) { mutableStateOf(defaultExpanded) }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(RemRadii.r4))
                .clickable { expanded = !expanded }
                .padding(
                    horizontal = if (headerInset) 16.dp else 4.dp,
                    vertical = 10.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .background(tone, RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.width(9.dp))
            RemIcon(
                name = if (expanded) IconName.ChevronDown else IconName.ChevronRight,
                tint = colors.textLow,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(7.dp))
            BasicText(
                title,
                style =
                    RemType.text14.copy(
                        color = tone,
                        fontWeight = FontWeight.SemiBold,
                    ),
            )
            Spacer(Modifier.width(8.dp))
            BasicText(
                count.toString(),
                style =
                    RemType.label12.copy(
                        color = colors.textLow,
                        fontFamily = FontFamily.Monospace,
                    ),
            )
            Spacer(Modifier.weight(1f))
        }
        if (!expanded) return@Column

        val bodyModifier =
            if (contentSurface) {
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(RemRadii.r4))
                    .background(colors.surface)
            } else {
                Modifier.fillMaxWidth()
            }
        Column(bodyModifier) {
            if (count == 0 && emptyText != null) {
                BasicText(
                    emptyText,
                    style = RemType.text12.copy(color = colors.textLow),
                    modifier =
                        Modifier.padding(
                            horizontal = if (contentSurface) 12.dp else 37.dp,
                            vertical = 10.dp,
                        ),
                )
            }
            content()
        }
    }
}
