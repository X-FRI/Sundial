package com.myapplication.shared.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SettingsPreferencesTest {
    @Test
    fun settingsSectionsDoNotExposeStandaloneWidgetsDestination() {
        assertEquals(
            listOf("同步", "列表", "数据", "外观", "关于"),
            SettingsSection.entries.map { it.title },
        )
        assertFalse(SettingsSection.entries.any { it.title == "小组件" })
    }

    @Test
    fun appearancePreferencesNormalizeBlankFontFamilyToSystem() {
        assertEquals("system", normalizeFontFamily(""))
        assertEquals("system", normalizeFontFamily("   "))
        assertEquals("SF Pro Text", normalizeFontFamily(" SF Pro Text "))
    }

    @Test
    fun appearancePreferencesRoundTripSettingKeys() {
        val preferences = SettingsPreferences(
            themeMode = ThemeMode.Dark,
            displayDensity = DisplayDensity.Compact,
            fontFamily = "Inter",
        )
        val stored = preferences.toSettingsMap()
        assertEquals(preferences, SettingsPreferences.fromSettingsMap(stored))
    }
}
