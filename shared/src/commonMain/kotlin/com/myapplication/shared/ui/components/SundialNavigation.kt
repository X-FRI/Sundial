package com.myapplication.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemControlSize
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemType

@Composable
fun SundialNavRow(
    icon: IconName,
    label: String,
    count: Int?,
    selected: Boolean,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalRemColors.current
    val selectedBg = if (primary) colors.brandSubtle else colors.rowHover
    val idleBg = if (primary) colors.bgPrimary else Color.Transparent
    val iconSize = if (primary) 18.dp else 16.dp
    val textStyle = if (primary) RemType.text14 else RemType.text12
    Row(
        modifier
            .clip(RoundedCornerShape(RemRadii.r4))
            .background(if (selected) selectedBg else idleBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = if (primary) 9.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemIcon(icon, if (selected) colors.brand else colors.textLow, Modifier.size(iconSize))
        Spacer(Modifier.width(10.dp))
        BasicText(label, style = textStyle.copy(color = colors.textHigh), modifier = Modifier.weight(1f))
        if (count != null) {
            BasicText(count.toString(), style = RemType.text12.copy(color = colors.textLow))
        }
    }
}

@Composable
fun SundialBottomNavItem(
    icon: IconName,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalRemColors.current
    Column(
        modifier
            .height(RemControlSize.touch + 6.dp)
            .clip(RoundedCornerShape(RemRadii.r4))
            .background(if (selected) colors.brandSubtle else colors.bgPrimary)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        RemIcon(icon, if (selected) colors.brand else colors.textLow, Modifier.size(17.dp))
        Spacer(Modifier.height(2.dp))
        BasicText(label, style = RemType.text10.copy(color = if (selected) colors.brand else colors.textLow))
    }
}
