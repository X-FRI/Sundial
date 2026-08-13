package com.myapplication.shared.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemBadge
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemType

internal data class WidgetSettingsFact(
    val icon: IconName,
    val title: String,
    val description: String,
    val status: String,
    val tone: WidgetFactTone,
)

internal enum class WidgetFactTone {
    Brand,
    Info,
    Success,
    Warning,
}

internal enum class WidgetCapability {
    AndroidResponsive,
    SnapshotCache,
    LaunchRouting,
    MacOsRoadmap,
}

internal val widgetSettingsCapabilities = setOf(
    WidgetCapability.AndroidResponsive,
    WidgetCapability.SnapshotCache,
    WidgetCapability.LaunchRouting,
    WidgetCapability.MacOsRoadmap,
)

internal val widgetSettingsFacts = listOf(
    WidgetSettingsFact(
        icon = IconName.Today,
        title = "Android 今日小组件",
        description = "已支持 small / medium / large 今日小组件，随尺寸展示今天数量、下一项任务、逾期和收件箱摘要。",
        status = "已支持",
        tone = WidgetFactTone.Success,
    ),
    WidgetSettingsFact(
        icon = IconName.Tray,
        title = "snapshot cache",
        description = "本地缓存最近一次今日摘要，启动或数据库暂不可用时小组件仍可读取 lastUpdatedAt、计数和任务列表。",
        status = "本地缓存",
        tone = WidgetFactTone.Brand,
    ),
    WidgetSettingsFact(
        icon = IconName.Send,
        title = "点击行为",
        description = "点击小组件主体打开工作台；点击今日任务区域打开今天，继续处理 TodayWidgetSnapshot 中的待办。",
        status = "可跳转",
        tone = WidgetFactTone.Info,
    ),
    WidgetSettingsFact(
        icon = IconName.Device,
        title = "macOS 桌面小组件",
        description = "需要 WidgetKit extension 和共享数据通道，当前为技术方案阶段；本 milestone 不实现 native WidgetKit extension。",
        status = "规划中",
        tone = WidgetFactTone.Warning,
    ),
)

@Composable
internal fun WidgetSettingsScreen() {
    val colors = LocalRemColors.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 40.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(RemRadii.r4))
                        .background(colors.brandSubtle),
                    contentAlignment = Alignment.Center,
                ) {
                    RemIcon(IconName.Today, colors.brand, Modifier.size(20.dp), contentDescription = "小组件")
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    BasicText("小组件", style = RemType.title20.copy(color = colors.textHigh))
                    Spacer(Modifier.height(2.dp))
                    BasicText("今日摘要、点击入口和 macOS WidgetKit 路线图", style = RemType.text12.copy(color = colors.textLow))
                }
            }
            Spacer(Modifier.height(18.dp))
            WidgetSettingsCard {
                widgetSettingsFacts.forEachIndexed { index, fact ->
                    WidgetFactRow(fact)
                    if (index != widgetSettingsFacts.lastIndex) WidgetRowDivider()
                }
            }
            Spacer(Modifier.height(18.dp))
            SnapshotContractCard()
        }
    }
}

@Composable
private fun WidgetSettingsCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalRemColors.current
    val shape = RoundedCornerShape(RemRadii.r4)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.bgPrimary)
            .border(1.dp, colors.border, shape)
            .padding(4.dp),
        content = content,
    )
}

@Composable
private fun WidgetFactRow(fact: WidgetSettingsFact) {
    val colors = LocalRemColors.current
    val tint = fact.tone.toneColor()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(RemRadii.r4))
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            RemIcon(fact.icon, tint, Modifier.size(17.dp), contentDescription = fact.title)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    fact.title,
                    style = RemType.text14.copy(color = colors.textHigh, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                RemBadge(fact.status, color = tint)
            }
            Spacer(Modifier.height(5.dp))
            BasicText(fact.description, style = RemType.text12.copy(color = colors.textNormal))
        }
    }
}

@Composable
private fun SnapshotContractCard() {
    val colors = LocalRemColors.current
    WidgetSettingsCard {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
            BasicText("共享快照契约", style = RemType.label12.copy(color = colors.textLow))
            Spacer(Modifier.height(8.dp))
            BasicText(
                "TodayWidgetSnapshot JSON 包含 dateLabel、pendingTodayCount、completedTodayCount、nextTaskTitle、nextTaskDueLabel、topTodayTasks、overdueCount、inboxCount、lastUpdatedAt 和 topOverdueTasks；Android 已消费该契约，macOS 方案将复用同一份 JSON。",
                style = RemType.text12.copy(color = colors.textNormal),
            )
        }
    }
}

@Composable
private fun WidgetRowDivider() {
    val colors = LocalRemColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(1.dp)
            .background(colors.borderSubtle),
    )
}

@Composable
private fun WidgetFactTone.toneColor(): Color {
    val colors = LocalRemColors.current
    return when (this) {
        WidgetFactTone.Brand -> colors.brand
        WidgetFactTone.Info -> colors.info
        WidgetFactTone.Success -> colors.success
        WidgetFactTone.Warning -> colors.warning
    }
}
