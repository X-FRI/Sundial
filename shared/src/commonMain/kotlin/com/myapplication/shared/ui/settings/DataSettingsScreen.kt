package com.myapplication.shared.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.sync.SyncMode
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemButton
import com.myapplication.shared.ui.components.RemButtonVariant
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemType

@Composable
internal fun DataSettingsScreen(
    settingsVm: SettingsViewModel,
    mainVm: MainViewModel,
) {
    val colors = LocalRemColors.current
    val syncStatus by settingsVm.syncStatus.collectAsState()
    val trashCount by mainVm.trashCount.collectAsState()
    val allCount by mainVm.allCount.collectAsState()
    val completedCount by mainVm.completedCount.collectAsState()
    SettingsPageScaffold(
        title = "数据",
        subtitle = "查看本地数据、同步和清理状态",
    ) {
        SettingsPanel {
            DataFactRow("存储模式", "本地优先，所有改动先写入设备数据库")
            DataFactRow(
                "同步目标",
                when (syncStatus.mode) {
                    SyncMode.Local -> "仅本地"
                    SyncMode.Supabase -> "Supabase"
                    SyncMode.SundialServer -> "Sundial Server"
                },
            )
            DataFactRow("待同步", "${syncStatus.pendingCount} 项")
            DataFactRow("垃圾箱", "$trashCount 项可恢复")
        }
        Spacer(Modifier.height(14.dp))
        SettingsPanel {
            BasicText("数据概览", style = RemType.label12.copy(color = colors.textLow))
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                DataMetric("全部", allCount.toString(), Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                DataMetric("完成", completedCount.toString(), Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                DataMetric("垃圾箱", trashCount.toString(), Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(14.dp))
        SettingsPanel {
            BasicText("维护", style = RemType.label12.copy(color = colors.textLow))
            Spacer(Modifier.height(10.dp))
            BasicText(
                "导出、导入和备份需要先定义跨平台文件格式；当前版本先保证同步状态、垃圾箱和列表数据透明可见。",
                style = RemType.text12.copy(color = colors.textNormal),
            )
            Spacer(Modifier.height(12.dp))
            RemButton("导出数据", onClick = {}, enabled = false)
            Spacer(Modifier.height(8.dp))
            RemButton("清空垃圾箱", onClick = {}, variant = RemButtonVariant.Danger, enabled = false)
        }
    }
}

@Composable
private fun DataFactRow(
    label: String,
    value: String,
) {
    val colors = LocalRemColors.current
    Row(
        Modifier.fillMaxWidth().height(34.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, style = RemType.text12.copy(color = colors.textLow))
        Spacer(Modifier.weight(1f))
        BasicText(value, style = RemType.text12.copy(color = colors.textHigh, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun DataMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalRemColors.current
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RemIcon(IconName.Layers, colors.textLow, Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            BasicText(label, style = RemType.text12.copy(color = colors.textLow))
        }
        Spacer(Modifier.height(5.dp))
        BasicText(value, style = RemType.title20.copy(color = colors.textHigh))
    }
}
