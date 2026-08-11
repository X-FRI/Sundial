package com.myapplication.shared.ui.narrow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.components.RemIconButton
import com.myapplication.shared.ui.components.RemTextField
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.main.Scope
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemType
import com.myapplication.shared.ui.todolist.scopeTitle

@Composable
fun NarrowTopBar(mainVm: MainViewModel, modifier: Modifier = Modifier) {
    val colors = LocalRemColors.current
    val scope by mainVm.scope.collectAsState()
    val query by mainVm.searchQuery.collectAsState()
    var searching by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.bgPrimary)
            .drawBehind {
                drawLine(colors.border, Offset(0f, size.height), Offset(size.width, size.height), 1f)
            }
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (searching) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RemTextField(
                    value = query,
                    onValueChange = mainVm::setSearch,
                    placeholder = "搜索",
                    leadingIcon = IconName.Search,
                    modifier = Modifier.weight(1f),
                )
                RemIconButton(
                    IconName.Close,
                    "退出搜索",
                    onClick = {
                        searching = false
                        mainVm.setSearch("")
                    },
                    size = 16.dp,
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.text.BasicText(
                    scopeTitle(scope, query),
                    style = RemType.title18.copy(color = colors.textHigh),
                    modifier = Modifier.weight(1f),
                )
                RemIconButton(IconName.Settings, "设置", onClick = mainVm::openSettings, size = 18.dp)
                Spacer(Modifier.width(8.dp))
                RemIconButton(IconName.Search, "搜索", onClick = { searching = true }, size = 18.dp)
            }
        }
    }
}

@Composable
fun NarrowBottomNav(mainVm: MainViewModel, modifier: Modifier = Modifier) {
    val colors = LocalRemColors.current
    val scope by mainVm.scope.collectAsState()
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(colors.bgPrimary)
            .drawBehind {
                drawLine(colors.border, Offset(0f, 0f), Offset(size.width, 0f), 1f)
            }
            .navigationBarsPadding(),
    ) {
        NavItem(IconName.Today, "今天", scope == Scope.Today, Modifier.weight(1f)) { mainVm.selectScope(Scope.Today) }
        NavItem(IconName.Scheduled, "计划", scope == Scope.Scheduled, Modifier.weight(1f)) { mainVm.selectScope(Scope.Scheduled) }
        NavItem(IconName.Tray, "全部", scope == Scope.All, Modifier.weight(1f)) { mainVm.selectScope(Scope.All) }
        NavItem(IconName.CheckCircle, "已完成", scope == Scope.Completed, Modifier.weight(1f)) { mainVm.selectScope(Scope.Completed) }
        NavItem(IconName.Trash, "垃圾箱", scope == Scope.Trash, Modifier.weight(1f)) { mainVm.selectScope(Scope.Trash) }
    }
}

@Composable
private fun NavItem(icon: IconName, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalRemColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val tint = if (selected) colors.brand else colors.textLow
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(RemRadii.r2))
            .background(if (selected) colors.bgSecondary else Color.Transparent)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RemIcon(icon, tint, Modifier.size(16.dp))
        Spacer(Modifier.height(2.dp))
        androidx.compose.foundation.text.BasicText(
            label,
            style = TextStyle(fontSize = 10.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                .copy(color = tint),
        )
    }
}
