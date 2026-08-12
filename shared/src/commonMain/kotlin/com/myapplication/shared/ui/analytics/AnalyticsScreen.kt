package com.myapplication.shared.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemBadge
import com.myapplication.shared.ui.components.RemBadgeTone
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemColors
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemType
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

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
    val model = remember(todos, lists, now, timeZone) {
        buildAnalyticsModel(todos, lists, now.toLocalDateTime(timeZone).date, timeZone)
    }
    LazyColumn(
        modifier.fillMaxSize().background(colors.bgSecondary),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
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
private fun AnalyticsHeader(model: AnalyticsModel, compact: Boolean) {
    val colors = LocalRemColors.current
    Column {
        androidx.compose.foundation.text.BasicText(
            "分析",
            style = if (compact) RemType.title20.copy(color = colors.textHigh) else RemType.title24.copy(color = colors.textHigh),
        )
        Spacer(Modifier.height(4.dp))
        androidx.compose.foundation.text.BasicText(
            model.encouragement,
            style = RemType.text14.copy(color = colors.textLow),
        )
    }
}

@Composable
private fun KpiGrid(model: AnalyticsModel, compact: Boolean) {
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
            androidx.compose.foundation.text.BasicText(label, style = RemType.label12.copy(color = colors.textLow))
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            androidx.compose.foundation.text.BasicText(value, style = RemType.title24.copy(color = colors.textHigh))
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                androidx.compose.foundation.text.BasicText(unit, style = RemType.text12.copy(color = colors.textLow))
            }
        }
    }
}

@Composable
private fun CompletionTrendCard(model: AnalyticsModel, modifier: Modifier = Modifier) {
    AnalyticsCard("完成趋势", "最近 7 天每天完成的任务数量", modifier) {
        WeeklyBarChart(
            points = model.days,
            value = { it.completedCount },
            barColor = LocalRemColors.current.success,
            valueSuffix = "件",
        )
    }
}

@Composable
private fun EnergyTrendCard(model: AnalyticsModel, modifier: Modifier = Modifier) {
    AnalyticsCard("精力输出", "按完成任务复杂度估算的输出点数", modifier) {
        WeeklyBarChart(
            points = model.days,
            value = { it.energy },
            barColor = LocalRemColors.current.brand,
            valueSuffix = "点",
        )
    }
}

@Composable
private fun PressureCard(model: AnalyticsModel, modifier: Modifier = Modifier) {
    AnalyticsCard("待办压力", "当前未完成任务的时间分布", modifier) {
        PressureBar(model.pressure)
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            model.pressure.forEach { bucket ->
                LegendRow(bucket.label, bucket.count, bucket.tone)
            }
        }
    }
}

@Composable
private fun EnergyStructureCard(model: AnalyticsModel, modifier: Modifier = Modifier) {
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
            androidx.compose.foundation.text.BasicText(title, style = RemType.title18.copy(color = colors.textHigh))
            Spacer(Modifier.weight(1f))
            RemIcon(IconName.Chart, colors.textLow, Modifier.size(15.dp))
        }
        Spacer(Modifier.height(4.dp))
        androidx.compose.foundation.text.BasicText(subtitle, style = RemType.text12.copy(color = colors.textLow))
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
private fun WeeklyBarChart(
    points: List<DayPoint>,
    value: (DayPoint) -> Int,
    barColor: Color,
    valueSuffix: String,
) {
    val colors = LocalRemColors.current
    val maxValue = points.maxOfOrNull(value)?.coerceAtLeast(1) ?: 1
    Row(
        Modifier.fillMaxWidth().height(136.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        points.forEach { point ->
            val v = value(point)
            val fraction = if (v == 0) 0.06f else (v.toFloat() / maxValue.toFloat()).coerceIn(0.12f, 1f)
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                androidx.compose.foundation.text.BasicText(
                    if (v == 0) "0" else "$v$valueSuffix",
                    style = RemType.text10.copy(color = if (v == 0) colors.textLow else colors.textNormal),
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.62f)
                            .fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (v == 0) colors.borderSubtle else barColor.copy(alpha = 0.82f)),
                    )
                }
                Spacer(Modifier.height(6.dp))
                androidx.compose.foundation.text.BasicText(point.label, style = RemType.text10.copy(color = colors.textLow))
            }
        }
    }
}

@Composable
private fun PressureBar(buckets: List<PressureBucket>) {
    val colors = LocalRemColors.current
    val total = buckets.sumOf { it.count }
    Row(
        Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(colors.bgPanel),
    ) {
        if (total == 0) {
            Box(Modifier.weight(1f).fillMaxHeight().background(colors.borderSubtle))
        } else {
            buckets.filter { it.count > 0 }.forEach { bucket ->
                Box(
                    Modifier
                        .weight(bucket.count.toFloat())
                        .fillMaxHeight()
                        .background(bucket.tone.color(colors).copy(alpha = 0.85f)),
                )
            }
        }
    }
}

@Composable
private fun LegendRow(label: String, count: Int, tone: AnalyticsTone) {
    val colors = LocalRemColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(tone.color(colors), CircleShape))
        Spacer(Modifier.width(8.dp))
        androidx.compose.foundation.text.BasicText(label, style = RemType.text12.copy(color = colors.textNormal), modifier = Modifier.weight(1f))
        androidx.compose.foundation.text.BasicText(count.toString(), style = RemType.label12.copy(color = tone.color(colors)))
    }
}

@Composable
private fun InsightRow(label: String, value: Int, hint: String) {
    val colors = LocalRemColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            androidx.compose.foundation.text.BasicText(label, style = RemType.label12.copy(color = colors.textNormal))
            androidx.compose.foundation.text.BasicText(hint, style = RemType.text10.copy(color = colors.textLow))
        }
        androidx.compose.foundation.text.BasicText(value.toString(), style = RemType.title18.copy(color = colors.textHigh))
    }
}

private data class AnalyticsModel(
    val encouragement: String,
    val completedToday: Int,
    val streakDays: Int,
    val weekEnergy: Int,
    val completionRate: Int,
    val days: List<DayPoint>,
    val pressure: List<PressureBucket>,
    val deepWorkCount: Int,
    val quickWinCount: Int,
    val flaggedCompletedCount: Int,
    val outputSummary: String,
)

private data class DayPoint(
    val date: LocalDate,
    val label: String,
    val completedCount: Int,
    val energy: Int,
)

private data class PressureBucket(
    val label: String,
    val count: Int,
    val tone: AnalyticsTone,
)

private enum class AnalyticsTone {
    Danger,
    Brand,
    Info,
    Neutral,
}

private fun AnalyticsTone.color(colors: RemColors): Color = when (this) {
    AnalyticsTone.Danger -> colors.error
    AnalyticsTone.Brand -> colors.brand
    AnalyticsTone.Info -> colors.info
    AnalyticsTone.Neutral -> colors.textLow
}

private fun buildAnalyticsModel(
    todos: List<TodoItem>,
    lists: List<TodoList>,
    today: LocalDate,
    timeZone: TimeZone,
): AnalyticsModel {
    val visible = todos.filter { !it.isTrashed }
    val parents = visible.filter { it.parentId == null }
    val subtasksByParent = visible.filter { it.parentId != null }.groupBy { it.parentId }
    val completed = parents.filter { it.isCompleted && it.completedAt != null }
    val active = parents.filter { !it.isCompleted }
    val days = (6 downTo 0).map { offset -> today.plus(-offset, DateTimeUnit.DAY) }
    val completedByDate = completed.groupBy { it.completedAt!!.toLocalDateTime(timeZone).date }
    val dayPoints = days.map { date ->
        val done = completedByDate[date].orEmpty()
        DayPoint(
            date = date,
            label = dayLabel(date, today),
            completedCount = done.size,
            energy = done.sumOf { energyScore(it, subtasksByParent[it.id].orEmpty()) },
        )
    }
    val weekEnergy = dayPoints.sumOf { it.energy }
    val completedToday = completedByDate[today].orEmpty().size
    val completionRate = if (parents.isEmpty()) 0 else ((completed.size * 100f) / parents.size).toInt()
    val streak = completionStreak(today, completedByDate)
    val overdue = active.count { it.localDueDate(timeZone)?.let { due -> due < today } == true }
    val dueToday = active.count { it.localDueDate(timeZone) == today }
    val futureSeven = active.count { item ->
        item.localDueDate(timeZone)?.let { due ->
            due > today && due <= today.plus(7, DateTimeUnit.DAY)
        } == true
    }
    val noDate = active.count { it.dueDate == null }
    val completedScores = completed.map { energyScore(it, subtasksByParent[it.id].orEmpty()) }
    val deepWork = completedScores.count { it >= 3 }
    val quickWins = completedScores.count { it <= 1 }
    val flaggedCompleted = completed.count { it.flag }
    val listCount = lists.size
    return AnalyticsModel(
        encouragement = encouragement(completedToday, streak, overdue, listCount),
        completedToday = completedToday,
        streakDays = streak,
        weekEnergy = weekEnergy,
        completionRate = completionRate,
        days = dayPoints,
        pressure = listOf(
            PressureBucket("逾期", overdue, AnalyticsTone.Danger),
            PressureBucket("今天", dueToday, AnalyticsTone.Brand),
            PressureBucket("未来 7 天", futureSeven, AnalyticsTone.Info),
            PressureBucket("无日期", noDate, AnalyticsTone.Neutral),
        ),
        deepWorkCount = deepWork,
        quickWinCount = quickWins,
        flaggedCompletedCount = flaggedCompleted,
        outputSummary = if (weekEnergy == 0) "先完成一件小事，图表就会开始发光。" else "本周已经输出 $weekEnergy 点，继续保持节奏。",
    )
}

private fun TodoItem.localDueDate(timeZone: TimeZone): LocalDate? =
    dueDate?.toLocalDateTime(timeZone)?.date

private fun energyScore(todo: TodoItem, subtasks: List<TodoItem>): Int {
    var score = 1
    if (todo.note.isNotBlank()) score += 1
    if (todo.flag) score += 1
    score += subtasks.count { it.isCompleted }.coerceAtMost(3)
    return score
}

private fun completionStreak(today: LocalDate, completedByDate: Map<LocalDate, List<TodoItem>>): Int {
    var streak = 0
    var day = today
    while (completedByDate[day].orEmpty().isNotEmpty()) {
        streak += 1
        day = day.plus(-1, DateTimeUnit.DAY)
    }
    return streak
}

private fun dayLabel(date: LocalDate, today: LocalDate): String =
    if (date == today) "今天" else "${date.monthNumber}/${date.dayOfMonth}"

private fun encouragement(completedToday: Int, streak: Int, overdue: Int, listCount: Int): String = when {
    completedToday > 0 && streak > 1 -> "今天已经推进 $completedToday 件，连续 $streak 天有完成记录。节奏正在形成。"
    completedToday > 0 -> "今天已经完成 $completedToday 件，先把势能留住。"
    overdue > 0 -> "有 $overdue 件逾期待办，先处理最小的一件就能降低压力。"
    listCount > 1 -> "任务已经分布在 $listCount 个列表里，今天可以选一个方向集中推进。"
    else -> "先完成一件足够小的待办，让今天的曲线开始出现。"
}
