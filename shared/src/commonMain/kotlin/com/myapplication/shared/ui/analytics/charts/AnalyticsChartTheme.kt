package com.myapplication.shared.ui.analytics.charts

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.myapplication.shared.ui.theme.LocalRemColors

internal data class AnalyticsChartColors(
    val primary: Color,
    val secondary: Color,
    val warning: Color,
    val danger: Color,
    val grid: Color,
    val label: Color,
)

@Composable
internal fun rememberAnalyticsChartColors(): AnalyticsChartColors {
    val colors = LocalRemColors.current
    return AnalyticsChartColors(
        primary = colors.brand,
        secondary = colors.info,
        warning = colors.warning,
        danger = colors.error,
        grid = colors.border,
        label = colors.textLow,
    )
}
