package com.myapplication.shared.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.myapplication.shared.ui.settings.DisplayDensity
import com.myapplication.shared.ui.settings.ThemeMode

/**
 * 应用主题根节点：根据系统深浅色选择对应色表，并通过
 * CompositionLocalProvider 把 [LocalRemColors] 注入整棵组件树。
 *
 * 说明：
 * - 所有组件通过 `LocalRemColors.current` 取色，不直接引用 Light/Dark 表，
 *   保证主题切换（未来支持手动切换）只改这里一处；
 * - 当前仅跟随系统主题，无手动覆盖。
 */
@Composable
fun RemindersTheme(
    themeMode: ThemeMode = ThemeMode.System,
    displayDensity: DisplayDensity = DisplayDensity.Comfortable,
    fontFamily: String = "system",
    content: @Composable () -> Unit,
) {
    applyRemFontFamilyPreference(fontFamily)
    applyRemDisplayDensityPreference(displayDensity.key)
    val colors =
        when (themeMode) {
            ThemeMode.System -> if (isSystemInDarkTheme()) DarkRemColors else LightRemColors
            ThemeMode.Light -> LightRemColors
            ThemeMode.Dark -> DarkRemColors
        }
    CompositionLocalProvider(
        LocalRemColors provides colors,
    ) {
        content()
    }
}
