package com.myapplication.shared.ui.settings

import com.myapplication.shared.data.sync.SyncEngine
import com.myapplication.shared.domain.settings.SaveSettingsUseCase
import com.myapplication.shared.domain.settings.SettingsError
import com.myapplication.shared.test.FakeTodoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsPreferencesTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

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
        val preferences =
            SettingsPreferences(
                themeMode = ThemeMode.Dark,
                displayDensity = DisplayDensity.Compact,
                fontFamily = "Inter",
            )
        val stored = preferences.toSettingsMap()
        assertEquals(preferences, SettingsPreferences.fromSettingsMap(stored))
    }

    @Test
    fun settingsLoadFailureExposesErrorAndDoesNotSaveDefaults() =
        runTest(dispatcher) {
            val repository =
                FakeTodoRepository().apply {
                    failGetSettings = true
                }
            val engine = SyncEngine(backgroundScope, repository, kotlin.time.Clock.System)
            val saveSettings = SaveSettingsUseCase(repository) { "generated-device" }
            val viewModel = SettingsViewModel(repository, engine, saveSettings)

            advanceUntilIdle()
            viewModel.save()
            advanceUntilIdle()

            assertEquals(SettingsError.Persistence("读取设置失败"), viewModel.lastSettingsError.value)
            assertEquals(emptyMap(), repository.settingsState.value)
        }

    @Test
    fun preferenceWriteFailureExposesSettingsError() =
        runTest(dispatcher) {
            val repository =
                FakeTodoRepository().apply {
                    failSetSetting = true
                }
            val engine = SyncEngine(backgroundScope, repository, kotlin.time.Clock.System)
            val saveSettings = SaveSettingsUseCase(repository) { "generated-device" }
            val viewModel = SettingsViewModel(repository, engine, saveSettings)

            advanceUntilIdle()
            viewModel.setThemeMode(ThemeMode.Dark)
            advanceUntilIdle()

            assertEquals(SettingsError.Persistence("settings write failed"), viewModel.lastSettingsError.value)
            assertEquals(ThemeMode.Dark, viewModel.preferences.value.themeMode)
        }
}
