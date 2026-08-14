package com.myapplication.shared.ui.ledger

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemBadge
import com.myapplication.shared.ui.components.RemBadgeTone
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemType

@Composable
fun ContextTimeline(
    state: ContextTimelineState,
    rhythm: TodayRhythmState,
    timeline: TodayTimelineState,
    showDayRail: Boolean,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (showDayRail) {
        TodayRhythm(rhythm, timeline, onOpen, modifier)
    } else {
        ContextTimelineSummary(state, modifier)
    }
}

@Composable
fun ContextTimelineCompact(
    state: ContextTimelineState,
    timeline: TodayTimelineState,
    showTodayLabels: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalRemColors.current
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (showTodayLabels) {
            CompactDayTimeline(state = state, timeline = timeline)
            return@Column
        }
        TimelineDistributionRail(state.segments)
        TimelineLegendWithTotal(state.segments, compact = true, modifier = Modifier.padding(top = 8.dp))
        TimelineEmptyText(state, compact = true)
    }
}

@Composable
private fun CompactDayTimeline(
    state: ContextTimelineState,
    timeline: TodayTimelineState,
) {
    val colors = LocalRemColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.text
            .BasicText(state.title, style = RemType.label12.copy(color = colors.textNormal))
        Spacer(Modifier.width(8.dp))
        RemIcon(IconName.Clock, colors.textLow, Modifier.size(13.dp))
        Spacer(Modifier.weight(1f))
        RemBadge("现在 ${timeline.nowLabel}", tone = RemBadgeTone.Neutral, monospace = true)
    }
    TimelineRail(timeline, Modifier.padding(top = 10.dp))
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        androidx.compose.foundation.text
            .BasicText(timeline.startLabel, style = RemType.text10.copy(color = colors.textLow))
        androidx.compose.foundation.text
            .BasicText("12:00", style = RemType.text10.copy(color = colors.textLow))
        androidx.compose.foundation.text
            .BasicText("18:00", style = RemType.text10.copy(color = colors.textLow))
        androidx.compose.foundation.text
            .BasicText(timeline.endLabel, style = RemType.text10.copy(color = colors.textLow))
    }
}

@Composable
private fun ContextTimelineSummary(
    state: ContextTimelineState,
    modifier: Modifier = Modifier,
) {
    val colors = LocalRemColors.current
    val total = state.segments.sumOf { it.count }
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(RemRadii.r4))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        TimelineDistributionRail(state.segments)
        TimelineLegendWithTotal(state.segments, compact = false, modifier = Modifier.padding(top = 10.dp))
        TimelineEmptyText(state, compact = false)
    }
}

@Composable
private fun TimelineLegendWithTotal(
    segments: List<TimelineSegment>,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalRemColors.current
    val total = segments.sumOf { it.count }
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        segments.forEach { segment ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(segment.tone.timelineColor(), RoundedCornerShape(3.dp)),
                )
                Spacer(Modifier.width(5.dp))
                androidx.compose.foundation.text.BasicText(
                    segment.label,
                    style = (if (compact) RemType.text10 else RemType.text12).copy(color = colors.textLow),
                )
                Spacer(Modifier.width(4.dp))
                androidx.compose.foundation.text.BasicText(
                    segment.count.toString(),
                    style =
                        RemType.label10.copy(
                            color = if (segment.count > 0) segment.tone.timelineColor() else colors.textLow,
                            fontFamily = FontFamily.Monospace,
                        ),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.text.BasicText(
                "总计",
                style = (if (compact) RemType.text10 else RemType.text12).copy(color = colors.textLow),
            )
            Spacer(Modifier.width(4.dp))
            androidx.compose.foundation.text.BasicText(
                total.toString(),
                style = RemType.label10.copy(color = colors.textHigh, fontFamily = FontFamily.Monospace),
            )
        }
    }
}

@Composable
private fun TimelineEmptyText(
    state: ContextTimelineState,
    compact: Boolean,
) {
    if (state.segments.any { it.count > 0 }) return
    androidx.compose.foundation.text.BasicText(
        state.emptyText,
        style = (if (compact) RemType.text10 else RemType.text12).copy(color = LocalRemColors.current.textLow),
        modifier = Modifier.padding(top = if (compact) 8.dp else 10.dp),
    )
}

@Composable
private fun TimelineDistributionRail(
    segments: List<TimelineSegment>,
    modifier: Modifier = Modifier,
) {
    val colors = LocalRemColors.current
    val total = segments.sumOf { it.count }
    Row(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(RemRadii.r2))
            .background(colors.bgPanel),
    ) {
        if (total == 0) {
            Box(Modifier.weight(1f).height(6.dp).background(colors.borderSubtle))
        } else {
            segments.filter { it.count > 0 }.forEach { segment ->
                Box(
                    Modifier
                        .weight(segment.count.toFloat())
                        .height(6.dp)
                        .background(segment.tone.timelineColor().copy(alpha = 0.82f)),
                )
            }
        }
    }
}

@Composable
fun TodayRhythm(
    state: TodayRhythmState,
    timeline: TodayTimelineState,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalRemColors.current
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(RemRadii.r4))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.text
                .BasicText("今日时间线", style = RemType.label12.copy(color = colors.textNormal))
            Spacer(Modifier.width(8.dp))
            RemIcon(IconName.Clock, colors.textLow, Modifier.size(14.dp))
            Spacer(Modifier.weight(1f))
            RemBadge("现在 ${timeline.nowLabel}", tone = RemBadgeTone.Neutral, monospace = true)
        }
        TimelineRail(timeline, Modifier.padding(top = 14.dp))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            androidx.compose.foundation.text
                .BasicText(timeline.startLabel, style = RemType.text10.copy(color = colors.textLow))
            androidx.compose.foundation.text
                .BasicText("12:00", style = RemType.text10.copy(color = colors.textLow))
            androidx.compose.foundation.text
                .BasicText("18:00", style = RemType.text10.copy(color = colors.textLow))
            androidx.compose.foundation.text
                .BasicText(timeline.endLabel, style = RemType.text10.copy(color = colors.textLow))
        }
    }
}

@Composable
private fun TimelineRail(
    timeline: TodayTimelineState,
    modifier: Modifier = Modifier,
) {
    val colors = LocalRemColors.current
    BoxWithConstraints(modifier.fillMaxWidth().height(34.dp)) {
        Canvas(Modifier.fillMaxWidth().height(34.dp)) {
            val railY = size.height / 2f
            drawLine(colors.borderSubtle, Offset(0f, railY), Offset(size.width, railY), strokeWidth = 2.dp.toPx())
            listOf(0f, 1f / 3f, 2f / 3f, 1f).forEach { progress ->
                val x = size.width * progress
                drawLine(colors.border, Offset(x, railY - 5.dp.toPx()), Offset(x, railY + 5.dp.toPx()), strokeWidth = 1.dp.toPx())
            }
            timeline.past.forEach { task ->
                drawCircle(colors.warning.copy(alpha = 0.45f), radius = 4.dp.toPx(), center = Offset(size.width * task.progress, railY))
            }
            timeline.upcoming.forEach { task ->
                drawCircle(if (task.isNext) colors.brand else colors.info, radius = if (task.isNext) 5.dp.toPx() else 4.dp.toPx(), center = Offset(size.width * task.progress, railY))
            }
        }
        Box(
            Modifier
                .offset(x = maxWidth * timeline.nowProgress - 1.dp)
                .width(2.dp)
                .height(34.dp)
                .background(colors.brand, RoundedCornerShape(1.dp)),
        )
    }
}

@Composable
fun TimelineTone.timelineColor(): Color {
    val colors = LocalRemColors.current
    return when (this) {
        TimelineTone.Danger -> colors.error
        TimelineTone.Brand -> colors.brand
        TimelineTone.Warning -> colors.warning
        TimelineTone.Success -> colors.success
        TimelineTone.Neutral -> colors.textLow
        TimelineTone.Info -> colors.info
    }
}
