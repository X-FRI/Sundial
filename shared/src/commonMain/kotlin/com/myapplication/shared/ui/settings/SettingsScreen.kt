package com.myapplication.shared.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.sync.SyncMode
import com.myapplication.shared.ui.components.RemButton
import com.myapplication.shared.ui.components.RemTextField
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemType

@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val colors = LocalRemColors.current
    val form by vm.form.collectAsState()
    val status by vm.syncStatus.collectAsState()

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.text.BasicText("同步设置", style = RemType.title18.copy(color = colors.textHigh))
            Spacer(Modifier.weight(1f))
            RemButton("返回", onClick = onBack)
        }
        Spacer(Modifier.height(16.dp))
        ModeOption("本地模式（不同步）", SyncMode.Local, form.mode, vm::setMode)
        ModeOption("Supabase 云端", SyncMode.Supabase, form.mode, vm::setMode)
        ModeOption("自建服务器（即将支持）", SyncMode.SundialServer, form.mode, vm::setMode) {
            androidx.compose.foundation.text.BasicText("Sundial-Server 模式开发中，敬请期待", style = RemType.text12.copy(color = colors.textLow))
        }
        Spacer(Modifier.height(12.dp))
        if (form.mode == SyncMode.Supabase) {
            RemTextField(value = form.supabaseUrl, onValueChange = vm::setSupabaseUrl, placeholder = "Supabase URL（https://xxx.supabase.co）", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            RemTextField(value = form.supabaseKey, onValueChange = vm::setSupabaseKey, placeholder = "Supabase anon key", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        if (form.mode == SyncMode.SundialServer) {
            androidx.compose.foundation.text.BasicText(
                "请先在客户端配置页选择此模式（服务端尚未发布）",
                style = RemType.text12.copy(color = colors.textLow),
            )
        }
        Spacer(Modifier.height(16.dp))
        RemButton("保存", onClick = vm::save)
        Spacer(Modifier.height(24.dp))
        androidx.compose.foundation.text.BasicText("同步状态", style = RemType.label12.copy(color = colors.textLow))
        Spacer(Modifier.height(4.dp))
        androidx.compose.foundation.text.BasicText(
            "模式：${when (status.mode) { SyncMode.Local -> "本地"; SyncMode.Supabase -> "Supabase"; SyncMode.SundialServer -> "Sundial-Server" }}" +
                " · 连接：${if (status.connected) "已连接" else "未连接"}" +
                " · 待同步：${status.pendingCount}" +
                (status.lastSyncAt?.let { " · 上次同步：$it" } ?: ""),
            style = RemType.text12.copy(color = colors.textNormal),
        )
        status.lastError?.let {
            Spacer(Modifier.height(4.dp))
            androidx.compose.foundation.text.BasicText("错误：$it", style = RemType.text12.copy(color = colors.error))
        }
    }
}

@Composable
private fun ModeOption(
    label: String,
    mode: SyncMode,
    current: SyncMode,
    onSelect: (SyncMode) -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    val colors = LocalRemColors.current
    val selected = mode == current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(mode) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.text.BasicText(
            (if (selected) "◉ " else "○ ") + label,
            style = RemType.text14.copy(color = if (selected) colors.brand else colors.textNormal),
        )
    }
    extra?.invoke()
}
