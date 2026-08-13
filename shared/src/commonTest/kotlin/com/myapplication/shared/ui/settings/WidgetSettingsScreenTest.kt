package com.myapplication.shared.ui.settings

import kotlin.test.Test
import kotlin.test.assertTrue

class WidgetSettingsScreenTest {
    @Test
    fun widgetSettingsFactsCoverSupportedPlatformsAndRoadmap() {
        val text = widgetSettingsFacts.joinToString(separator = "\n") { fact ->
            "${fact.title}\n${fact.description}"
        }

        assertTrue("small" in text && "medium" in text && "large" in text)
        assertTrue("snapshot cache" in text && "最近一次今日摘要" in text)
        assertTrue("工作台" in text && "今天" in text)
        assertTrue("WidgetKit extension" in text && "技术方案阶段" in text)
    }
}
