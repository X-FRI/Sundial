package com.myapplication.shared.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myapplication.shared.ui.components.RemTextField
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemType
import com.myapplication.shared.ui.theme.remFontFamilyFromPreference

@Composable
internal fun AppearanceSettingsScreen(vm: SettingsViewModel) {
    val preferences by vm.preferences.collectAsState()
    val colors = LocalRemColors.current
    SettingsPageScaffold(
        title = "外观",
        subtitle = "配置主题、密度和字体偏好",
    ) {
        SettingsPanel {
            BasicText("主题", style = RemType.label12.copy(color = colors.textLow))
            Spacer(Modifier.height(10.dp))
            ThemeMode.entries.forEach { mode ->
                ChoiceRow(
                    title = mode.label,
                    subtitle = when (mode) {
                        ThemeMode.System -> "自动跟随系统深浅色"
                        ThemeMode.Light -> "始终使用浅色界面"
                        ThemeMode.Dark -> "始终使用深色界面"
                    },
                    selected = preferences.themeMode == mode,
                    onClick = { vm.setThemeMode(mode) },
                )
                Spacer(Modifier.height(6.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        SettingsPanel {
            BasicText("显示密度", style = RemType.label12.copy(color = colors.textLow))
            Spacer(Modifier.height(10.dp))
            DisplayDensity.entries.forEach { density ->
                ChoiceRow(
                    title = density.label,
                    subtitle = when (density) {
                        DisplayDensity.Comfortable -> "更高的触控和阅读余量"
                        DisplayDensity.Compact -> "更高的信息密度，适合桌面"
                    },
                    selected = preferences.displayDensity == density,
                    onClick = { vm.setDisplayDensity(density) },
                )
                Spacer(Modifier.height(6.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        SettingsPanel {
            BasicText("字体", style = RemType.label12.copy(color = colors.textLow))
            Spacer(Modifier.height(8.dp))
            RemTextField(
                value = if (preferences.fontFamily == DefaultFontFamily) "" else preferences.fontFamily,
                onValueChange = vm::setFontFamily,
                placeholder = "system / SF Pro Text / Inter / PingFang SC",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            BasicText(
                "Sundial 让待办回到清楚、可执行、低压力的节奏里。",
                style = RemType.title20.copy(
                    color = colors.textHigh,
                    fontFamily = remFontFamilyFromPreference(preferences.fontFamily),
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Spacer(Modifier.height(4.dp))
            BasicText(
                "当前字体偏好：${preferences.fontFamily}",
                style = RemType.text12.copy(color = colors.textLow),
            )
        }
    }
}

@Composable
internal fun SettingsPageScaffold(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
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
            BasicText(title, style = RemType.title20.copy(color = colors.textHigh))
            Spacer(Modifier.height(2.dp))
            BasicText(subtitle, style = RemType.text12.copy(color = colors.textLow))
            Spacer(Modifier.height(18.dp))
            content()
        }
    }
}

@Composable
internal fun SettingsPanel(content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalRemColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RemRadii.r4))
            .background(colors.surface)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(RemRadii.r4))
            .padding(14.dp),
    ) {
        content()
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalRemColors.current
    val shape = RoundedCornerShape(RemRadii.r4)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) colors.brandSubtle else colors.bgPrimary)
            .border(1.dp, if (selected) colors.brand.copy(alpha = 0.35f) else colors.borderSubtle, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(title, style = RemType.text14.copy(color = colors.textHigh, fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(2.dp))
            BasicText(subtitle, style = RemType.text12.copy(color = colors.textLow))
        }
        Spacer(Modifier.width(12.dp))
        BasicText(if (selected) "已选" else "", style = RemType.label12.copy(color = colors.brand))
    }
}
