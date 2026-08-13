package com.myapplication.shared.ui.analytics.charts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemType

@Composable
internal fun CompletionTrendChart(
    values: List<Int>,
    modifier: Modifier = Modifier,
) {
    val colors = LocalRemColors.current
    Box(
        modifier
            .fillMaxWidth()
            .height(160.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "完成趋势 ${values.sum()}",
            style = RemType.text12.copy(color = colors.textLow),
        )
    }
}
