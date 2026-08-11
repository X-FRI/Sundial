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
)

object RemType {
    val text12 = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp, fontWeight = FontWeight.Normal)
    val text13 = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, fontWeight = FontWeight.Normal)
    val title15 = TextStyle(fontFamily = FontFamily.Default, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    val title17 = TextStyle(fontFamily = FontFamily.Default, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp)
    val title20 = TextStyle(fontFamily = FontFamily.Default, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp)
    val label12 = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    val label13 = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
}

val LocalRemColors = staticCompositionLocalOf { LightRemColors }
