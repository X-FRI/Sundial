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
    "blue" to Color(0xFF0A84FF),
    "red" to Color(0xFFFF3B30),
    "orange" to Color(0xFFFF9500),
    "yellow" to Color(0xFFFFCC00),
    "green" to Color(0xFF34C759),
    "teal" to Color(0xFF5AC8FA),
    "purple" to Color(0xFFAF52DE),
)

data class RemColors(
    val bgPrimary: Color,
    val bgSecondary: Color,
    val bgPanel: Color,
    val textHigh: Color,
    val textNormal: Color,
    val textLow: Color,
    val border: Color,
    val inputBg: Color,
    val brand: Color,
    val brandHover: Color,
    val brandSecondary: Color,
    val error: Color,
    val success: Color,
    val warning: Color,
    val info: Color,
    val focusRing: Color,
)

val LightRemColors = RemColors(
    bgPrimary = Color(0xFFFFFFFF),
    bgSecondary = Color(0xFFF2F2F2),
    bgPanel = Color(0xFFE3E3E3),
    textHigh = Color(0xFF0D0D0D),
    textNormal = Color(0xFF333333),
    textLow = Color(0xFF636363),
    border = Color(0xFFD9D9D9),
    inputBg = Color(0xFFF5F5F5),
    brand = Color(0xFFEA7A2A),
    brandHover = Color(0xFFE79255),
    brandSecondary = Color(0xFFAC5111),
    error = Color(0xFFD25151),
    success = Color(0xFF54B04F),
    warning = Color(0xFFDB7706),
    info = Color(0xFF3C83F6),
    focusRing = Color(0xFFEA7A2A),
)

val DarkRemColors = RemColors(
    bgPrimary = Color(0xFF212121),
    bgSecondary = Color(0xFF1C1C1C),
    bgPanel = Color(0xFF292929),
    textHigh = Color(0xFFF5F5F5),
    textNormal = Color(0xFFC4C4C4),
    textLow = Color(0xFF8F8F8F),
    border = Color(0xFF333333),
    inputBg = Color(0xFF333333),
    brand = Color(0xFFEA7A2A),
    brandHover = Color(0xFFE79255),
    brandSecondary = Color(0xFFAC5111),
    error = Color(0xFFFF6B6B),
    success = Color(0xFF54B04F),
    warning = Color(0xFFE0913E),
    info = Color(0xFF3C83F6),
    focusRing = Color(0xFFE79255),
)

object RemType {
    val text10 = TextStyle(fontFamily = FontFamily.Default, fontSize = 10.sp)
    val text12 = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp)
    val text14 = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp)
    val text16 = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp)
    val title18 = TextStyle(fontFamily = FontFamily.Default, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    val label10 = TextStyle(fontFamily = FontFamily.Default, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    val label12 = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}

object RemSpacing {
    val s2 = 2.dp
    val s4 = 4.dp
    val s8 = 8.dp
    val s12 = 12.dp
    val s16 = 16.dp
}

object RemRadii {
    val r2 = 2.dp
    val r3 = 16.dp
    val r4 = 8.dp
}

val LocalRemColors = staticCompositionLocalOf { LightRemColors }
