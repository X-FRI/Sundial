package com.myapplication.shared.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.sync.SyncMode
import com.myapplication.shared.domain.sync.SyncStatus
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemBadge
import com.myapplication.shared.ui.components.RemButton
import com.myapplication.shared.ui.components.RemButtonVariant
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.components.RemTextField
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemType
import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * 设置页（同步设置）：表单编辑 + 保存 + 实时同步状态展示。
 *
 * 布局要点：
 * - 全屏 Column 可滚动，内容宽度限制在 520dp 内居中，适配桌面与窄屏；
 * - 上半部分为同步方式选择（本地 / Supabase / 自建服务器，后两者显示连接信息输入）；
 * - 保存按钮在 Supabase 配置不完整时禁用，并给出提示文案；
 * - 下半部分 [StatusCard] 实时展示 SyncEngine 的连接状态。
 */
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val colors = LocalRemColors.current
    val form by vm.form.collectAsState()
    val status by vm.syncStatus.collectAsState()
    // 校验：选了 Supabase 但 URL / key 任一为空 → 禁用保存。
    val supabaseIncomplete = form.mode == SyncMode.Supabase && (form.supabaseUrl.isBlank() || form.supabaseKey.isBlank())

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 40.dp),
        ) {
            // 页头：标题 + 返回按钮。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    BasicText("同步设置", style = RemType.title18.copy(color = colors.textHigh))
                    Spacer(Modifier.height(2.dp))
                    BasicText("选择数据同步方式，保存后立即生效", style = RemType.text12.copy(color = colors.textLow))
                }
                RemButton("返回", onClick = onBack)
            }
            Spacer(Modifier.height(20.dp))
            SectionLabel("同步方式")
            Spacer(Modifier.height(8.dp))
            // 三种同步方式卡片：选中态用品牌色描边 + 勾选图标标识。
            SettingsCard {
                ModeOption(
                    icon = IconName.Device,
                    title = "本地模式",
                    desc = "数据仅保存在当前设备，不进行同步",
                    mode = SyncMode.Local,
                    tint = colors.textLow,
                    selected = form.mode == SyncMode.Local,
                    onClick = { vm.setMode(SyncMode.Local) },
                )
                RowDivider()
                ModeOption(
                    icon = IconName.Cloud,
                    title = "Supabase 云端",
                    desc = "通过 Supabase 在多台设备间实时同步",
                    mode = SyncMode.Supabase,
                    tint = colors.info,
                    selected = form.mode == SyncMode.Supabase,
                    onClick = { vm.setMode(SyncMode.Supabase) },
                )
                RowDivider()
                ModeOption(
                    icon = IconName.Server,
                    title = "自建服务器",
                    desc = "连接自建的 Sundial-Server 实例",
                    mode = SyncMode.SundialServer,
                    tint = colors.textLow,
                    selected = form.mode == SyncMode.SundialServer,
                    comingSoon = true,
                    onClick = { vm.setMode(SyncMode.SundialServer) },
                )
            }
            // 连接信息区：仅非本地模式时出现，且表单随当前模式切换。
            if (form.mode != SyncMode.Local) {
                Spacer(Modifier.height(20.dp))
                SectionLabel("连接信息")
                Spacer(Modifier.height(8.dp))
                SettingsCard {
                    Column(Modifier.padding(12.dp)) {
                        when (form.mode) {
                            SyncMode.Supabase -> {
                                // Supabase 模式：URL + anon 公钥两个输入框，附获取指引。
                                BasicText("Supabase 项目 URL", style = RemType.label12.copy(color = colors.textLow))
                                Spacer(Modifier.height(6.dp))
                                RemTextField(
                                    value = form.supabaseUrl,
                                    onValueChange = vm::setSupabaseUrl,
                                    placeholder = "https://xxx.supabase.co",
                                    leadingIcon = IconName.Cloud,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(12.dp))
                                BasicText("anon 公钥", style = RemType.label12.copy(color = colors.textLow))
                                Spacer(Modifier.height(6.dp))
                                RemTextField(
                                    value = form.supabaseKey,
                                    onValueChange = vm::setSupabaseKey,
                                    placeholder = "eyJhbGciOi...",
                                    leadingIcon = IconName.Key,
                                    style = RemType.text14.copy(fontFamily = FontFamily.Monospace),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(10.dp))
                                BasicText(
                                    "在 Supabase Dashboard → Project Settings → API 中获取。anon 公钥可安全地内嵌在客户端。",
                                    style = RemType.text12.copy(color = colors.textLow),
                                )
                            }
                            SyncMode.SundialServer -> {
                                // 自建服务器尚未发布：只展示占位说明，不提供输入。
                                BasicText(
                                    "Sundial-Server 尚未发布，暂时无法连接自建服务器。",
                                    style = RemType.text12.copy(color = colors.textLow),
                                )
                            }
                            SyncMode.Local -> Unit
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            // 保存按钮：Supabase 配置不完整时禁用并提示。
            RemButton(
                text = "保存",
                onClick = vm::save,
                variant = RemButtonVariant.Default,
                enabled = !supabaseIncomplete,
                modifier = Modifier.fillMaxWidth(),
            )
            if (supabaseIncomplete) {
                Spacer(Modifier.height(6.dp))
                BasicText(
                    "请先填写 Supabase URL 与 anon 公钥",
                    style = RemType.text12.copy(color = colors.textLow),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            Spacer(Modifier.height(24.dp))
            SectionLabel("同步状态")
            Spacer(Modifier.height(8.dp))
            StatusCard(status)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val colors = LocalRemColors.current
    BasicText(text, style = RemType.label10.copy(color = colors.textLow))
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
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
private fun RowDivider() {
    val colors = LocalRemColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(1.dp)
            .background(colors.border.copy(alpha = 0.6f)),
    )
}

@Composable
private fun ModeOption(
    icon: IconName,
    title: String,
    desc: String,
    mode: SyncMode,
    tint: Color,
    selected: Boolean,
    onClick: () -> Unit,
    comingSoon: Boolean = false,
) {
    val colors = LocalRemColors.current
    val shape = RoundedCornerShape(RemRadii.r4)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) colors.brand.copy(alpha = 0.06f) else Color.Transparent)
            .border(
                if (selected) 1.dp else 0.dp,
                colors.brand.copy(alpha = 0.45f),
                shape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .semantics { this.selected = selected }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(shape)
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            RemIcon(icon, tint, Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    title,
                    style = RemType.text14.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = colors.textHigh,
                    ),
                )
                if (comingSoon) {
                    Spacer(Modifier.width(6.dp))
                    RemBadge("即将推出", color = colors.warning)
                }
            }
            Spacer(Modifier.height(2.dp))
            BasicText(desc, style = RemType.text12.copy(color = colors.textLow))
        }
        Spacer(Modifier.width(12.dp))
        if (selected) {
            Box(
                Modifier
                    .size(20.dp)
                    .background(colors.brand, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                RemIcon(IconName.CheckCircle, Color.White, Modifier.size(12.dp))
            }
        } else {
            Box(
                Modifier
                    .size(20.dp)
                    .border(1.5.dp, colors.border, CircleShape),
            )
        }
    }
}

/**
 * 同步状态卡片：按引擎状态分支展示。
 *
 * 状态机（由 SyncEngine.status 驱动）：
 * - 本地模式：固定灰点 + 「本地模式」说明，不显示同步细节；
 * - 云端模式：connected → 绿点「已连接」，否则黄点「未连接」；
 *   下方细分为待同步条数、上次同步相对时间、最近一次错误（有才显示）。
 */
@Composable
private fun StatusCard(status: SyncStatus) {
    val colors = LocalRemColors.current
    // 徽标文案：当前同步模式名。
    val modeLabel = when (status.mode) {
        SyncMode.Local -> "本地"
        SyncMode.Supabase -> "Supabase"
        SyncMode.SundialServer -> "Sundial-Server"
    }
    // 状态点颜色：本地灰、已连接绿、断开黄。
    val dotColor = when {
        status.mode == SyncMode.Local -> colors.textLow
        status.connected -> colors.success
        else -> colors.warning
    }
    // 主标题：本地模式 / 已连接 / 未连接。
    val headline = when {
        status.mode == SyncMode.Local -> "本地模式"
        status.connected -> "已连接"
        else -> "未连接"
    }
    SettingsCard {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(dotColor, CircleShape))
                Spacer(Modifier.width(8.dp))
                BasicText(headline, style = RemType.label12.copy(color = colors.textHigh))
                Spacer(Modifier.weight(1f))
                RemBadge(modeLabel, color = if (status.mode == SyncMode.Supabase) colors.info else null)
            }
            if (status.mode == SyncMode.Local) {
                // 本地模式：只给一句隐私说明，不需要同步指标。
                Spacer(Modifier.height(10.dp))
                BasicText(
                    "当前模式下数据不会上传到任何服务器。",
                    style = RemType.text12.copy(color = colors.textLow),
                )
            } else {
                // 云端模式：分隔线 + 待同步数 / 上次同步时间 / 错误三行统计。
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border.copy(alpha = 0.6f)))
                Spacer(Modifier.height(10.dp))
                StatRow("待同步", if (status.pendingCount > 0) "${status.pendingCount} 条" else "无", if (status.pendingCount > 0) colors.warning else null)
                Spacer(Modifier.height(6.dp))
                StatRow("上次同步", status.lastSyncAt?.let { formatRelative(it, Clock.System.now().toEpochMilliseconds()) } ?: "从未")
                status.lastError?.let {
                    Spacer(Modifier.height(6.dp))
                    StatRow("错误", it, colors.error)
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, valueColor: Color? = null) {
    val colors = LocalRemColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, style = RemType.text12.copy(color = colors.textLow))
        Spacer(Modifier.weight(1f))
        BasicText(value, style = RemType.text12.copy(color = valueColor ?: colors.textNormal))
    }
}

/** 把时间戳格式化为相对时间：刚/分钟前/小时前，超过一天显示绝对日期时间。 */
private fun formatRelative(epochMillis: Long, now: Long): String {
    val diff = now - epochMillis
    return when {
        diff < 60_000L -> "刚刚"
        diff < 3_600_000L -> "${diff / 60_000L} 分钟前"
        diff < 86_400_000L -> "${diff / 3_600_000L} 小时前"
        else -> {
            val dt = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
            val hh = dt.hour.toString().padStart(2, '0')
            val mm = dt.minute.toString().padStart(2, '0')
            "${dt.monthNumber}月${dt.dayOfMonth}日 $hh:$mm"
        }
    }
}
