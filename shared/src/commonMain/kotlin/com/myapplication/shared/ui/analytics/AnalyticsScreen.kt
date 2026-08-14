package com.myapplication.shared.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.analytics.ChartBucket
import com.myapplication.shared.domain.analytics.ChartPoint
import com.myapplication.shared.domain.analytics.ChartTone
import com.myapplication.shared.ui.analytics.charts.CompletionTrendChart
import com.myapplication.shared.ui.analytics.charts.EnergyOutputChart
import com.myapplication.shared.ui.analytics.charts.PressureDistributionChart
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemBadge
import com.myapplication.shared.ui.components.RemBadgeTone
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemColors
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemType
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

@Composable
fun AnalyticsScreen(
    mainVm: MainViewModel,
    clock: Clock,
    timeZone: TimeZone,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colors = LocalRemColors.current
    val todos by mainVm.analyticsTodos.collectAsState()
    val lists by mainVm.lists.collectAsState()
    var now by remember(clock) { mutableStateOf(clock.now()) }
    LaunchedEffect(clock) {
        while (true) {
            now = clock.now()
            delay(60_000)
        }
    }
    val model =
        remember(todos, lists, now, timeZone) {
            buildAnalyticsModel(todos, lists, now.toLocalDateTime(timeZone).date, timeZone)
        }
    LazyColumn(
        modifier.fillMaxSize().background(colors.bgSecondary),
        contentPadding =
            androidx.compose.foundation.layout.PaddingValues(
                horizontal = if (compact) 14.dp else 24.dp,
                vertical = if (compact) 14.dp else 20.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp),
    ) {
        item {
            AnalyticsHeader(model, compact)
        }
        item {
            KpiGrid(model, compact)
        }
        item {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth > 760.dp) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        CompletionTrendCard(model, Modifier.weight(1f))
                        EnergyTrendCard(model, Modifier.weight(1f))
                    }
                } else {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CompletionTrendCard(model)
                        EnergyTrendCard(model)
                    }
                }
            }
        }
        item {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth > 760.dp) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        PressureCard(model, Modifier.weight(1f))
                        EnergyStructureCard(model, Modifier.weight(1f))
                    }
                } else {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PressureCard(model)
                        EnergyStructureCard(model)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsHeader(
    model: AnalyticsModel,
    compact: Boolean,
) {
    val colors = LocalRemColors.current
    Column {
        androidx.compose.foundation.text.BasicText(
            model.encouragement,
            style = if (compact) RemType.text14.copy(color = colors.textLow) else RemType.title18.copy(color = colors.textHigh),
        )
    }
}

@Composable
private fun KpiGrid(
    model: AnalyticsModel,
    compact: Boolean,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val useGrid = !compact && maxWidth > 720.dp
        if (useGrid) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KpiCard("今天完成", model.completedToday.toString(), "件", IconName.CheckCircle, Modifier.weight(1f))
                KpiCard("连续完成", model.streakDays.toString(), "天", IconName.Clock, Modifier.weight(1f))
                KpiCard("7 天输出", model.weekEnergy.toString(), "点", IconName.Chart, Modifier.weight(1f))
                KpiCard("完成率", "${model.completionRate}%", "", IconName.Layers, Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiCard("今天完成", model.completedToday.toString(), "件", IconName.CheckCircle, Modifier.weight(1f))
                    KpiCard("连续完成", model.streakDays.toString(), "天", IconName.Clock, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiCard("7 天输出", model.weekEnergy.toString(), "点", IconName.Chart, Modifier.weight(1f))
                    KpiCard("完成率", "${model.completionRate}%", "", IconName.Layers, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun KpiCard(
    label: String,
    value: String,
    unit: String,
    icon: IconName,
    modifier: Modifier = Modifier,
) {
    val colors = LocalRemColors.current
    Column(
        modifier
            .background(colors.surface, RoundedCornerShape(RemRadii.r4))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RemIcon(icon, colors.brand, Modifier.size(15.dp))
            Spacer(Modifier.width(8.dp))
            androidx.compose.foundation.text
                .BasicText(label, style = RemType.label12.copy(color = colors.textLow))
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            androidx.compose.foundation.text
                .BasicText(value, style = RemType.title24.copy(color = colors.textHigh))
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                androidx.compose.foundation.text
                    .BasicText(unit, style = RemType.text12.copy(color = colors.textLow))
            }
        }
    }
}

@Composable
private fun CompletionTrendCard(
    model: AnalyticsModel,
    modifier: Modifier = Modifier,
) {
    AnalyticsCard("完成趋势", "最近 7 天每天完成的任务数量", modifier) {
        CompletionTrendChart(
            points = model.days.map { ChartPoint(it.label, it.completedCount) },
        )
    }
}

@Composable
private fun EnergyTrendCard(
    model: AnalyticsModel,
    modifier: Modifier = Modifier,
) {
    AnalyticsCard("精力输出", "按完成任务复杂度估算的输出点数", modifier) {
        EnergyOutputChart(
            points = model.days.map { ChartPoint(it.label, it.energy) },
        )
    }
}

@Composable
private fun PressureCard(
    model: AnalyticsModel,
    modifier: Modifier = Modifier,
) {
    AnalyticsCard("待办压力", "当前未完成任务的时间分布", modifier) {
        PressureDistributionChart(
            buckets = model.pressure.map { ChartBucket(it.label, it.count, it.tone.toChartTone()) },
        )
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            model.pressure.forEach { bucket ->
                LegendRow(bucket.label, bucket.count, bucket.tone)
            }
        }
    }
}

@Composable
private fun EnergyStructureCard(
    model: AnalyticsModel,
    modifier: Modifier = Modifier,
) {
    AnalyticsCard("输出结构", "让你看到完成的不是数量，而是推进量", modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            InsightRow("深度任务", model.deepWorkCount, "完成后输出点数 >= 3")
            InsightRow("快速推进", model.quickWinCount, "轻量任务，适合维持手感")
            InsightRow("已标记完成", model.flaggedCompletedCount, "重要任务中的已完成项")
        }
        Spacer(Modifier.height(12.dp))
        RemBadge(model.outputSummary, tone = RemBadgeTone.Brand)
    }
}

@Composable
private fun AnalyticsCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalRemColors.current
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(RemRadii.r4))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.text
                .BasicText(title, style = RemType.title18.copy(color = colors.textHigh))
            Spacer(Modifier.weight(1f))
            RemIcon(IconName.Chart, colors.textLow, Modifier.size(15.dp))
        }
        Spacer(Modifier.height(4.dp))
        androidx.compose.foundation.text
            .BasicText(subtitle, style = RemType.text12.copy(color = colors.textLow))
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
private fun LegendRow(
    label: String,
    count: Int,
    tone: AnalyticsTone,
) {
    val colors = LocalRemColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(tone.color(colors), CircleShape))
        Spacer(Modifier.width(8.dp))
        androidx.compose.foundation.text
            .BasicText(label, style = RemType.text12.copy(color = colors.textNormal), modifier = Modifier.weight(1f))
        androidx.compose.foundation.text
            .BasicText(count.toString(), style = RemType.label12.copy(color = tone.color(colors)))
    }
}

@Composable
private fun InsightRow(
    label: String,
    value: Int,
    hint: String,
) {
    val colors = LocalRemColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            androidx.compose.foundation.text
                .BasicText(label, style = RemType.label12.copy(color = colors.textNormal))
            androidx.compose.foundation.text
                .BasicText(hint, style = RemType.text10.copy(color = colors.textLow))
        }
        androidx.compose.foundation.text
            .BasicText(value.toString(), style = RemType.title18.copy(color = colors.textHigh))
    }
}

private fun AnalyticsTone.color(colors: RemColors): Color =
    when (this) {
        AnalyticsTone.Danger -> colors.error
        AnalyticsTone.Brand -> colors.brand
        AnalyticsTone.Info -> colors.info
        AnalyticsTone.Neutral -> colors.textLow
    }

internal fun AnalyticsTone.toChartTone(): ChartTone =
    when (this) {
        AnalyticsTone.Danger -> ChartTone.Danger
        AnalyticsTone.Brand -> ChartTone.Primary
        AnalyticsTone.Info -> ChartTone.Info
        AnalyticsTone.Neutral -> ChartTone.Neutral
    }
