package com.myapplication.shared.ui.ledger

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemBadge
import com.myapplication.shared.ui.components.RemBadgeTone
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemType

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
            androidx.compose.foundation.text.BasicText("今日时间线", style = RemType.label12.copy(color = colors.textNormal))
            Spacer(Modifier.width(8.dp))
            RemIcon(IconName.Clock, colors.textLow, Modifier.size(14.dp))
            Spacer(Modifier.weight(1f))
            RemBadge("现在 ${timeline.nowLabel}", tone = RemBadgeTone.Neutral, monospace = true)
        }
        TimelineRail(timeline, Modifier.padding(top = 14.dp))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            androidx.compose.foundation.text.BasicText(timeline.startLabel, style = RemType.text10.copy(color = colors.textLow))
            androidx.compose.foundation.text.BasicText("12:00", style = RemType.text10.copy(color = colors.textLow))
            androidx.compose.foundation.text.BasicText("18:00", style = RemType.text10.copy(color = colors.textLow))
            androidx.compose.foundation.text.BasicText(timeline.endLabel, style = RemType.text10.copy(color = colors.textLow))
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RhythmMetric("下一件", state.nextDueLabel ?: "无", RemBadgeTone.Brand)
            RhythmMetric("今日待办", state.pendingTodayCount.toString(), RemBadgeTone.Warning)
            RhythmMetric("已完成", timeline.completedTodayCount.toString(), RemBadgeTone.Success)
            RhythmMetric("以后", timeline.futureCount.toString(), RemBadgeTone.Neutral)
        }
        timeline.upcoming.firstOrNull()?.let { next ->
            TimelineCallout(next, onOpen, Modifier.padding(top = 12.dp))
        } ?: state.nextTitle?.let {
            androidx.compose.foundation.text.BasicText(it, style = RemType.text12.copy(color = colors.textLow), modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
fun TodayRhythmCompact(
    state: TodayRhythmState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RemBadge("下一件 ${state.nextDueLabel ?: "无"}", tone = RemBadgeTone.Brand)
        RemBadge("今日 ${state.pendingTodayCount}", tone = RemBadgeTone.Warning)
        RemBadge("已完成 ${state.completedTodayCount}", tone = RemBadgeTone.Success)
    }
}

@Composable
private fun TimelineRail(timeline: TodayTimelineState, modifier: Modifier = Modifier) {
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
private fun TimelineCallout(task: TimelineTask, onOpen: (Long) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalRemColors.current
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.brandSubtle, RoundedCornerShape(RemRadii.r4))
            .clickable { onOpen(task.item.id) }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemIcon(IconName.Clock, colors.brand, Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        androidx.compose.foundation.text.BasicText("下一件", style = RemType.label12.copy(color = colors.brand))
        Spacer(Modifier.width(8.dp))
        androidx.compose.foundation.text.BasicText(task.timeLabel, style = RemType.text12.copy(color = colors.textLow))
        Spacer(Modifier.width(10.dp))
        androidx.compose.foundation.text.BasicText(task.item.title, style = RemType.text12.copy(color = colors.textHigh), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RhythmMetric(label: String, value: String, tone: RemBadgeTone) {
    Column(horizontalAlignment = Alignment.Start, modifier = Modifier.width(74.dp)) {
        androidx.compose.foundation.text.BasicText(label, style = RemType.text12.copy(color = LocalRemColors.current.textLow))
        RemBadge(value, tone = tone, monospace = true)
    }
}
