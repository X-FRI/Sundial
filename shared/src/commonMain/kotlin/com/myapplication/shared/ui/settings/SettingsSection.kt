package com.myapplication.shared.ui.settings

import com.myapplication.shared.ui.components.IconName

internal enum class SettingsSection(
    val title: String,
    val subtitle: String,
    val icon: IconName,
) {
    Sync("同步", "连接、状态和手动同步", IconName.Sync),
    Lists("列表", "管理列表、颜色和统计", IconName.Inbox),
    Data("数据", "导出、备份和垃圾箱", IconName.Tray),
    Appearance("外观", "主题和显示密度", IconName.Settings),
    About("关于", "版本和许可证", IconName.Device),
}
