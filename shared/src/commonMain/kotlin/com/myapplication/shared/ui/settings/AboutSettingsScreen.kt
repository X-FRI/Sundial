package com.myapplication.shared.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myapplication.shared.AppInfo
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemType

@Composable
internal fun AboutSettingsScreen() {
    val colors = LocalRemColors.current
    SettingsPageScaffold(
        title = "关于",
        subtitle = "版本、许可和平台能力",
    ) {
        SettingsPanel {
            BasicText("Sundial", style = RemType.title20.copy(color = colors.textHigh, fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(4.dp))
            BasicText(AppInfo.DESCRIPTION, style = RemType.text12.copy(color = colors.textLow))
            Spacer(Modifier.height(14.dp))
            AboutFactRow("版本", AppInfo.VERSION)
            AboutFactRow("许可", AppInfo.LICENSE)
            AboutFactRow("版权", AppInfo.COPYRIGHT)
        }
        Spacer(Modifier.height(14.dp))
        SettingsPanel {
            BasicText("平台能力", style = RemType.label12.copy(color = colors.textLow))
            Spacer(Modifier.height(12.dp))
            CapabilityRow(
                icon = IconName.Device,
                title = "桌面端",
                detail = "macOS 桌面应用已支持本地优先待办、同步配置、分析和列表管理。",
            )
            Spacer(Modifier.height(12.dp))
            CapabilityRow(
                icon = IconName.Today,
                title = "Android 今日小组件",
                detail = "已支持今日摘要快照，供 Android 小组件展示今天、逾期和下一项待办。",
            )
            Spacer(Modifier.height(12.dp))
            CapabilityRow(
                icon = IconName.Clock,
                title = "macOS 小组件",
                detail = "还未接入 WidgetKit Extension；需要独立的 macOS 原生扩展工程才能真正显示在桌面。",
            )
        }
    }
}

@Composable
private fun AboutFactRow(label: String, value: String) {
    val colors = LocalRemColors.current
    Row(
        Modifier.fillMaxWidth().height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, style = RemType.text12.copy(color = colors.textLow))
        Spacer(Modifier.weight(1f))
        BasicText(value, style = RemType.text12.copy(color = colors.textHigh))
    }
}

@Composable
private fun CapabilityRow(
    icon: IconName,
    title: String,
    detail: String,
) {
    val colors = LocalRemColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        RemIcon(icon, colors.brand, Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            BasicText(title, style = RemType.text14.copy(color = colors.textHigh, fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(3.dp))
            BasicText(detail, style = RemType.text12.copy(color = colors.textNormal))
        }
    }
}
