package com.myapplication.shared.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            androidx.compose.foundation.text.BasicText("今日节奏", style = RemType.label12.copy(color = colors.textNormal))
            Spacer(Modifier.width(8.dp))
            RemIcon(IconName.Clock, colors.textLow)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RhythmMetric("现在", state.nowLabel, RemBadgeTone.Neutral)
            RhythmMetric("下一件", state.nextDueLabel ?: "无", RemBadgeTone.Brand)
            RhythmMetric("待办", state.pendingTodayCount.toString(), RemBadgeTone.Warning)
            RhythmMetric("已完成", state.completedTodayCount.toString(), RemBadgeTone.Success)
        }
        if (state.nextTitle != null) {
            androidx.compose.foundation.text.BasicText(
                state.nextTitle,
                style = RemType.text12.copy(color = colors.textLow),
                modifier = Modifier.padding(top = 8.dp),
            )
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
private fun RhythmMetric(label: String, value: String, tone: RemBadgeTone) {
    Column(horizontalAlignment = Alignment.Start) {
        androidx.compose.foundation.text.BasicText(label, style = RemType.text12.copy(color = LocalRemColors.current.textLow))
        RemBadge(value, tone = tone, monospace = true)
    }
}
