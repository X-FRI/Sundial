package com.myapplication.shared.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.analytics.ListAnalyticsModel
import com.myapplication.shared.ui.analytics.charts.CompletionTrendChart
import com.myapplication.shared.ui.analytics.charts.EnergyOutputChart
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemType

@Composable
internal fun ListAnalyticsPanel(
    model: ListAnalyticsModel,
    modifier: Modifier = Modifier,
) {
    val colors = LocalRemColors.current
    val shape = RoundedCornerShape(RemRadii.r4)

    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(1.dp, colors.borderSubtle, shape)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText("列表分析", style = RemType.title18.copy(color = colors.textHigh))
                Spacer(Modifier.height(4.dp))
                BasicText(
                    "完成 ${model.completedTotal} 项",
                    style =
                        RemType.text12.copy(
                            color = colors.textLow,
                            fontWeight = FontWeight.Medium,
                        ),
                )
            }
            RemIcon(IconName.Chart, colors.brand, Modifier.size(17.dp), contentDescription = "列表分析")
        }

        Spacer(Modifier.height(16.dp))

        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            ChartBlock(title = model.completion.title, subtitle = "最近 7 天完成走势") {
                CompletionTrendChart(points = model.completion.points)
            }
            ChartBlock(title = model.energy.title, subtitle = "按完成任务内容估算输出") {
                EnergyOutputChart(points = model.energy.points)
            }
        }
    }
}

@Composable
private fun ChartBlock(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    val colors = LocalRemColors.current
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.borderSubtle),
        )
        Spacer(Modifier.height(12.dp))
        BasicText(title, style = RemType.label12.copy(color = colors.textHigh))
        Spacer(Modifier.height(3.dp))
        BasicText(subtitle, style = RemType.text10.copy(color = colors.textLow))
        Spacer(Modifier.height(10.dp))
        content()
    }
}
