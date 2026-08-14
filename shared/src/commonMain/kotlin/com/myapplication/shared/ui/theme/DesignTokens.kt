package com.myapplication.shared.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 设计令牌（Design Tokens）定义：全应用配色/字体/间距/圆角均从这里取值，
 * 组件内部不得出现魔法数字颜色。
 *
 * 分层结构：
 * 1. 列表颜色（ListColorKeys / ListColorOf）：7 种 iOS 风格列表主题色，
 *    以字符串 key 存储进数据库（列表模型只存 key，颜色映射集中在此）；
 * 2. 语义颜色（[RemColors]）：按"用途"命名（bg/text/border/brand/status），
 *    与具体色值解耦，组件只消费语义名；
 * 3. 明暗两套实例（[LightRemColors] / [DarkRemColors]）由 [RemindersTheme] 切换，
 *    经 [LocalRemColors]（CompositionLocal）向下分发；
 * 4. 排版/间距/圆角（[RemType] / [RemSpacing] / [RemRadii]）：小步长离散值，
 *    保证界面节奏统一。
 */

// 列表颜色 key 集合：与数据库存储的 colorKey 对应，顺序即"颜色选择器"的展示顺序
val ListColorKeys = listOf("blue", "red", "orange", "yellow", "green", "teal", "purple")

// 列表颜色 key → 具体色值；深浅色主题共用同一套色（品牌色除外）
val ListColorOf =
    mapOf(
        "blue" to Color(0xFF0A84FF),
        "red" to Color(0xFFFF3B30),
        "orange" to Color(0xFFFF9500),
        "yellow" to Color(0xFFFFCC00),
        "green" to Color(0xFF34C759),
        "teal" to Color(0xFF5AC8FA),
        "purple" to Color(0xFFAF52DE),
    )

/**
 * 语义颜色表（供组件消费的完整契约）。
 *
 * 命名规则：
 * - bg*：背景层级（bgPrimary 页面底 / bgSecondary hover 反馈 / bgPanel 面板）；
 * - text*：文字层级（textHigh 标题 / textNormal 正文 / textLow 次要与占位）；
 * - border / inputBg：边框与输入框底色；
 * - brand*：品牌主色及 hover/次级派生色（brandHover 由 Light 品牌色派生）；
 * - error / success / warning / info：状态色；
 * - focusRing：键盘焦点描边色（预留，无障碍导航用）。
 */
data class RemColors(
    val bgPrimary: Color,
    val bgSecondary: Color,
    val bgPanel: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val surfaceRaised: Color,
    val surfaceInset: Color,
    val overlay: Color,
    val brandSubtle: Color,
    val borderSubtle: Color,
    val rowHover: Color,
    val rowSelected: Color,
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
    val destructiveSubtle: Color,
    val successSubtle: Color,
    val infoSubtle: Color,
    val warningSubtle: Color,
    val focusRing: Color,
)

/**
 * 浅色主题：白底 + 浅灰层次，品牌橙 #EA7A2A；
 * 深色主题：近黑底 + 深灰层次，品牌色保持同色以保证识别度（仅 hover 亮化）。
 */
val LightRemColors =
    RemColors(
        bgPrimary = Color(0xFFFFFFFF),
        bgSecondary = Color(0xFFF2F2F2),
        bgPanel = Color(0xFFE3E3E3),
        surface = Color(0xFFFFFFFF),
        surfaceAlt = Color(0xFFF7F7F5),
        surfaceRaised = Color(0xFFFFFFFF),
        surfaceInset = Color(0xFFF6F6F4),
        overlay = Color(0x66000000),
        brandSubtle = Color(0xFFFFF2E8),
        borderSubtle = Color(0xFFEAE7E2),
        rowHover = Color(0xFFF4F4F2),
        rowSelected = Color(0xFFFFF2E8),
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
        destructiveSubtle = Color(0xFFFFEEEE),
        successSubtle = Color(0xFFEFF8EF),
        infoSubtle = Color(0xFFEFF5FF),
        warningSubtle = Color(0xFFFFF4E3),
        focusRing = Color(0xFFEA7A2A),
    )

val DarkRemColors =
    RemColors(
        bgPrimary = Color(0xFF212121),
        bgSecondary = Color(0xFF1C1C1C),
        bgPanel = Color(0xFF292929),
        surface = Color(0xFF242424),
        surfaceAlt = Color(0xFF1B1B1B),
        surfaceRaised = Color(0xFF282828),
        surfaceInset = Color(0xFF1B1B1B),
        overlay = Color(0x99000000),
        brandSubtle = Color(0xFF3A2416),
        borderSubtle = Color(0xFF363331),
        rowHover = Color(0xFF2C2C2C),
        rowSelected = Color(0xFF3A2416),
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
        destructiveSubtle = Color(0xFF3A2020),
        successSubtle = Color(0xFF1E3320),
        infoSubtle = Color(0xFF1B2A44),
        warningSubtle = Color(0xFF352717),
        focusRing = Color(0xFFE79255),
    )

/**
 * 排版令牌：text10~text16 为正文阶梯，title18 为标题（半粗），
 * label10/label12 为标签/按钮文字（半粗）。字体统一系统默认，
 * 等宽场景（如徽章数字）由调用方覆盖 FontFamily。
 */
object RemType {
    val text10 get() = TextStyle(fontFamily = CurrentRemFontFamily, fontSize = 10.sp)
    val text12 get() = TextStyle(fontFamily = CurrentRemFontFamily, fontSize = 12.sp)
    val text14 get() = TextStyle(fontFamily = CurrentRemFontFamily, fontSize = 14.sp)
    val text16 get() = TextStyle(fontFamily = CurrentRemFontFamily, fontSize = 16.sp)
    val title18 get() = TextStyle(fontFamily = CurrentRemFontFamily, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    val title20 get() = TextStyle(fontFamily = CurrentRemFontFamily, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    val title24 get() = TextStyle(fontFamily = CurrentRemFontFamily, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
    val label10 get() = TextStyle(fontFamily = CurrentRemFontFamily, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    val label12 get() = TextStyle(fontFamily = CurrentRemFontFamily, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}

private var CurrentRemFontFamily: FontFamily = FontFamily.Default

fun applyRemFontFamilyPreference(value: String) {
    CurrentRemFontFamily = remFontFamilyFromPreference(value)
}

fun remFontFamilyFromPreference(value: String): FontFamily {
    val trimmed = value.trim()
    return when (trimmed.lowercase()) {
        "monospace" -> FontFamily.Monospace
        "serif" -> FontFamily.Serif
        "sans", "sansserif", "sans-serif", "system", "" -> FontFamily.Default
        else -> platformFontFamilyFromName(trimmed) ?: FontFamily.Default
    }
}

expect fun platformFontFamilyFromName(fontFamilyName: String): FontFamily?

/**
 * 控件尺寸令牌：图标按钮外触达热区（iconSmall/iconMedium/touch）、
 * 行高（rowDesktop/rowMobile），组件触达尺寸应从这里取。
 */
object RemControlSize {
    val iconSmall get() = if (CurrentRemCompactDensity) 28.dp else 32.dp
    val iconMedium get() = if (CurrentRemCompactDensity) 32.dp else 36.dp
    val touch get() = if (CurrentRemCompactDensity) 40.dp else 44.dp
    val rowDesktop get() = if (CurrentRemCompactDensity) 38.dp else 42.dp
    val rowMobile get() = if (CurrentRemCompactDensity) 44.dp else 48.dp
}

private var CurrentRemCompactDensity: Boolean = false

fun applyRemDisplayDensityPreference(value: String) {
    CurrentRemCompactDensity = value.trim().lowercase() == "compact"
}

/** 间距令牌：4 档幂级步长（2/4/8/12/16），组件间距应从这里取。 */
object RemSpacing {
    val s2 = 2.dp
    val s4 = 4.dp
    val s8 = 8.dp
    val s12 = 12.dp
    val s16 = 16.dp
}

/**
 * 圆角令牌：r2 用于小元素（徽章/输入框），r4 用于按钮，
 * r3 = 16dp 是对话框的大圆角（命名沿袭早期版本，数值与命名顺序无关）。
 */
object RemRadii {
    val r2 = 2.dp
    val r3 = 16.dp
    val r4 = 8.dp
}

// 主题分发点：RemindersTheme 通过 CompositionLocalProvider 注入当前明暗色表
val LocalRemColors = staticCompositionLocalOf { LightRemColors }
