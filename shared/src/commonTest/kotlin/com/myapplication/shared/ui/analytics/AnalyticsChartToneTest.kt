package com.myapplication.shared.ui.analytics

import com.myapplication.shared.domain.analytics.ChartTone
import kotlin.test.Test
import kotlin.test.assertEquals

class AnalyticsChartToneTest {
    @Test
    fun analyticsTonesMapToChartTones() {
        assertEquals(ChartTone.Danger, AnalyticsTone.Danger.toChartTone())
        assertEquals(ChartTone.Primary, AnalyticsTone.Brand.toChartTone())
        assertEquals(ChartTone.Info, AnalyticsTone.Info.toChartTone())
        assertEquals(ChartTone.Neutral, AnalyticsTone.Neutral.toChartTone())
    }
}
