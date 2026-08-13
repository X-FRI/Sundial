package com.myapplication.shared.ui.settings

enum class ThemeMode(val key: String, val label: String) {
    System("system", "跟随系统"),
    Light("light", "浅色"),
    Dark("dark", "深色");

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: System
    }
}

enum class DisplayDensity(val key: String, val label: String) {
    Comfortable("comfortable", "舒适"),
    Compact("compact", "紧凑");

    companion object {
        fun fromKey(key: String?): DisplayDensity = entries.firstOrNull { it.key == key } ?: Comfortable
    }
}

data class SettingsPreferences(
    val themeMode: ThemeMode = ThemeMode.System,
    val displayDensity: DisplayDensity = DisplayDensity.Comfortable,
    val fontFamily: String = DefaultFontFamily,
) {
    fun toSettingsMap(): Map<String, String> = mapOf(
        ThemeModeKey to themeMode.key,
        DisplayDensityKey to displayDensity.key,
        FontFamilyKey to normalizeFontFamily(fontFamily),
    )

    companion object {
        fun fromSettingsMap(settings: Map<String, String>): SettingsPreferences = SettingsPreferences(
            themeMode = ThemeMode.fromKey(settings[ThemeModeKey]),
            displayDensity = DisplayDensity.fromKey(settings[DisplayDensityKey]),
            fontFamily = normalizeFontFamily(settings[FontFamilyKey].orEmpty()),
        )
    }
}

const val DefaultFontFamily = "system"

private const val ThemeModeKey = "ui.theme.mode"
private const val DisplayDensityKey = "ui.display.density"
private const val FontFamilyKey = "ui.font.family"

fun normalizeFontFamily(value: String): String {
    val trimmed = value.trim()
    return trimmed.ifBlank { DefaultFontFamily }
}
