package com.myapplication.shared.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WidgetSettingsScreenTest {
    @Test
    fun widgetSettingsCapabilitiesCoverSupportedPlatformsAndRoadmap() {
        assertEquals(
            setOf(
                WidgetCapability.AndroidResponsive,
                WidgetCapability.SnapshotCache,
                WidgetCapability.LaunchRouting,
                WidgetCapability.MacOsRoadmap,
            ),
            widgetSettingsCapabilities,
        )
    }

    @Test
    fun widgetSettingsFactsExposeEveryCapability() {
        val androidFact = widgetSettingsFacts.first { it.title == "Android 今日小组件" }
        assertTrue("small" in androidFact.description)
        assertTrue("medium" in androidFact.description)
        assertTrue("large" in androidFact.description)
        assertTrue(widgetSettingsFacts.any { it.tone == WidgetFactTone.Success && it.status == "已支持" })
        assertTrue(widgetSettingsFacts.any { it.tone == WidgetFactTone.Brand && it.status == "本地缓存" })
        assertTrue(widgetSettingsFacts.any { it.tone == WidgetFactTone.Info && it.status == "可跳转" })
        assertTrue(widgetSettingsFacts.any { it.tone == WidgetFactTone.Warning && it.status == "规划中" })
    }
}
