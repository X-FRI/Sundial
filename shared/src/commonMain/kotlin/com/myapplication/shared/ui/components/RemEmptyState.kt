package com.myapplication.shared.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemType

@Composable
fun RemEmptyState(
    title: String,
    subtitle: String = "",
    icon: IconName? = null,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    val colors = LocalRemColors.current
    Column(
        modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            RemIcon(icon, colors.textTertiary, Modifier.size(40.dp))
            Spacer(Modifier.height(12.dp))
        }
        androidx.compose.material3.Text(title, style = RemType.title20, color = colors.textPrimary)
        if (subtitle.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            androidx.compose.material3.Text(subtitle, style = RemType.text12, color = colors.textTertiary)
        }
    }
}
