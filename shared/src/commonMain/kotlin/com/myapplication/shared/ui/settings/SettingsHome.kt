package com.myapplication.shared.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.components.SundialBackAction
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemType

@Composable
internal fun SettingsHome(
    vm: SettingsViewModel,
    mainVm: MainViewModel,
    onBack: () -> Unit,
) {
    val colors = LocalRemColors.current
    var selected by rememberSaveable { mutableStateOf(SettingsSection.Sync) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(colors.bgPrimary),
    ) {
        if (maxWidth < 720.dp) {
            NarrowSettingsHome(
                selected = selected,
                onSelect = { selected = it },
                vm = vm,
                mainVm = mainVm,
                onBack = onBack,
            )
        } else {
            WideSettingsHome(
                selected = selected,
                onSelect = { selected = it },
                vm = vm,
                mainVm = mainVm,
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun WideSettingsHome(
    selected: SettingsSection,
    onSelect: (SettingsSection) -> Unit,
    vm: SettingsViewModel,
    mainVm: MainViewModel,
    onBack: () -> Unit,
) {
    val colors = LocalRemColors.current
    Row(Modifier.fillMaxSize()) {
        SettingsNavigationRail(
            selected = selected,
            onSelect = onSelect,
            onBack = onBack,
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight()
                .background(colors.surfaceAlt)
                .border(1.dp, colors.borderSubtle)
                .padding(16.dp),
        )
        SettingsSectionContent(
            selected = selected,
            vm = vm,
            mainVm = mainVm,
            onBack = onBack,
            compact = false,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun NarrowSettingsHome(
    selected: SettingsSection,
    onSelect: (SettingsSection) -> Unit,
    vm: SettingsViewModel,
    mainVm: MainViewModel,
    onBack: () -> Unit,
) {
    val colors = LocalRemColors.current
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surfaceAlt)
                .border(1.dp, colors.borderSubtle)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
        ) {
            SettingsHomeHeader(onBack = onBack)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).selectableGroup()) {
                SettingsSection.entries.forEach { section ->
                    SettingsSectionPill(
                        section = section,
                        selected = selected == section,
                        onClick = { onSelect(section) },
                    )
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
        SettingsSectionContent(
            selected = selected,
            vm = vm,
            mainVm = mainVm,
            onBack = onBack,
            compact = true,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

@Composable
private fun SettingsNavigationRail(
    selected: SettingsSection,
    onSelect: (SettingsSection) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
        SettingsHomeHeader(onBack = onBack)
        Spacer(Modifier.height(18.dp))
        SettingsSection.entries.forEach { section ->
            SettingsSectionRow(
                section = section,
                selected = selected == section,
                onClick = { onSelect(section) },
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun SettingsHomeHeader(onBack: () -> Unit) {
    val colors = LocalRemColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            BasicText("设置", style = RemType.title20.copy(color = colors.textHigh))
            Spacer(Modifier.height(2.dp))
            BasicText("配置 Sundial", style = RemType.text12.copy(color = colors.textLow))
        }
        SundialBackAction("返回", onClick = onBack)
    }
}

@Composable
private fun SettingsSectionContent(
    selected: SettingsSection,
    vm: SettingsViewModel,
    mainVm: MainViewModel,
    onBack: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        when (selected) {
            SettingsSection.Sync -> SyncSettingsContent(vm, onBack)
            SettingsSection.Lists -> ListSettingsScreen(mainVm, showHeader = !compact)
            SettingsSection.Data -> DataSettingsScreen(settingsVm = vm, mainVm = mainVm)
            SettingsSection.Appearance -> AppearanceSettingsScreen(vm)
            SettingsSection.About -> AboutSettingsScreen()
        }
    }
}

@Composable
private fun SettingsSectionRow(
    section: SettingsSection,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalRemColors.current
    val shape = RoundedCornerShape(RemRadii.r4)
    val tint = if (selected) colors.brand else colors.textLow
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(shape)
            .background(if (selected) colors.brandSubtle else Color.Transparent)
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemIcon(section.icon, tint, Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            BasicText(
                section.title,
                style = RemType.text14.copy(
                    color = colors.textHigh,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                ),
            )
            Spacer(Modifier.height(2.dp))
            BasicText(section.subtitle, style = RemType.text12.copy(color = colors.textLow))
        }
    }
}

@Composable
private fun SettingsSectionPill(
    section: SettingsSection,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalRemColors.current
    val shape = RoundedCornerShape(RemRadii.r4)
    Row(
        Modifier
            .height(40.dp)
            .clip(shape)
            .background(if (selected) colors.brandSubtle else Color.Transparent)
            .border(1.dp, if (selected) colors.brand.copy(alpha = 0.35f) else colors.border, shape)
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemIcon(section.icon, if (selected) colors.brand else colors.textLow, Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        BasicText(section.title, style = RemType.label12.copy(color = colors.textHigh))
    }
}
