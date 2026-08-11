package com.myapplication.shared.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val ListColorKeys = listOf("blue", "red", "orange", "yellow", "green", "teal", "purple")

val ListColorOf = mapOf(
    "blue" to Color(0xFF007AFF),
    "red" to Color(0xFFFF3B30),
    "orange" to Color(0xFFFF9500),
    "yellow" to Color(0xFFFFCC00),
    "green" to Color(0xFF34C759),
    "teal" to Color(0xFF5AC8FA),
    "purple" to Color(0xFFAF52DE),
)

data class RemColors(
    val sidebarBg: Color,
    val contentBg: Color,
    val selectedBg: Color,
    val rowDivider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val danger: Color,
    val checkboxBorder: Color,
    val dialogBg: Color,
    val todayBadgeBg: Color,
    val todayBadgeText: Color,
    val upcomingBadgeBg: Color,
    val upcomingBadgeText: Color,
    val overdueBadgeBg: Color,
    val overdueBadgeText: Color,
    val cardBg: Color,
    val cardBorder: Color,
    val hoverActionBg: Color,
    val flagColor: Color,
    val windowBg: Color,
)

val LightRemColors = RemColors(
    sidebarBg = Color(0xFFFFFFFF),
    contentBg = Color(0xFFFAFAFC),
    selectedBg = Color(0xFFF2F2F7),
    rowDivider = Color(0xFFF0F0F4),
    textPrimary = Color(0xFF111111),
    textSecondary = Color(0xFF3C3C43),
    textTertiary = Color(0xFF8E8E93),
    accent = Color(0xFF0A84FF),
    danger = Color(0xFFFF3B30),
    checkboxBorder = Color(0xFFC7C7CC),
    dialogBg = Color(0xFFFFFFFF),
    todayBadgeBg = Color(0x33FFD60A),
    todayBadgeText = Color(0xFFB25000),
    upcomingBadgeBg = Color(0x20C7C7CC),
    upcomingBadgeText = Color(0xFF8E8E93),
    overdueBadgeBg = Color(0x26FF3B30),
    overdueBadgeText = Color(0xFFFF3B30),
    cardBg = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFEEEEF0),
    hoverActionBg = Color(0xFFE5E5EA),
    flagColor = Color(0xFFFF9500),
    windowBg = Color(0xFFF6F6F8),
)

val DarkRemColors = RemColors(
    sidebarBg = Color(0xFF1C1C1E),
    contentBg = Color(0xFF2C2C2E),
    selectedBg = Color(0xFF3A3A3C),
    rowDivider = Color(0xFF38383A),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFD1D1D6),
    textTertiary = Color(0xFF8E8E93),
    accent = Color(0xFF0A84FF),
    danger = Color(0xFFFF453A),
    checkboxBorder = Color(0xFF636366),
    dialogBg = Color(0xFF2C2C2E),
    todayBadgeBg = Color(0x40FFD60A),
    todayBadgeText = Color(0xFFFFD60A),
    upcomingBadgeBg = Color(0x333A3A3C),
    upcomingBadgeText = Color(0xFF8E8E93),
    overdueBadgeBg = Color(0x33FF453A),
    overdueBadgeText = Color(0xFFFF453A),
    cardBg = Color(0xFF2C2C2E),
    cardBorder = Color(0xFF3A3A3C),
    hoverActionBg = Color(0xFF48484A),
    flagColor = Color(0xFFFFD60A),
    windowBg = Color(0xFF1C1C1E),
)

object RemType {
    val text12 = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp, fontWeight = FontWeight.Normal)
    val text13 = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, fontWeight = FontWeight.Normal)
    val title15 = TextStyle(fontFamily = FontFamily.Default, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    val title17 = TextStyle(fontFamily = FontFamily.Default, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp)
    val title20 = TextStyle(fontFamily = FontFamily.Default, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp)
    val label12 = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    val label13 = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    val title28 = TextStyle(fontFamily = FontFamily.Default, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
}

object RemSpacing {
    val s4 = 4.dp
    val s8 = 8.dp
    val s12 = 12.dp
    val s16 = 16.dp
    val s24 = 24.dp
    val s32 = 32.dp
}

object RemRadii {
    val r4 = 4.dp
    val r6 = 6.dp
    val r7 = 7.dp
    val r8 = 8.dp
    val r10 = 10.dp
}

val LocalRemColors = staticCompositionLocalOf { LightRemColors }
