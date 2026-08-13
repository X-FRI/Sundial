package com.myapplication.shared.ui.analytics.charts

import com.myapplication.shared.domain.analytics.ChartBucket
import com.myapplication.shared.domain.analytics.ChartPoint
import com.myapplication.shared.domain.analytics.ChartTone
import kotlin.test.Test
import kotlin.test.assertEquals

class AnalyticsChartDataTest {
    @Test
    fun pointSeriesPreservesLabelsAndUsesBaselineForAllZeroValues() {
        val series = preparePointSeries(
            listOf(
                ChartPoint("Mon", 0),
                ChartPoint("Tue", 0),
            ),
        )

        assertEquals(listOf("Mon", "Tue"), series.labels)
        assertEquals(listOf(MinimumVisibleChartValue, MinimumVisibleChartValue), series.values)
    }

    @Test
    fun pointSeriesKeepsZeroValuesWhenSeriesHasRealSignal() {
        val series = preparePointSeries(
            listOf(
                ChartPoint("Mon", 0),
                ChartPoint("Tue", 4),
            ),
        )

        assertEquals(listOf(0f, 4f), series.values)
    }

    @Test
    fun emptyPointSeriesStillProducesAVisibleVicoEntry() {
        val series = preparePointSeries(emptyList())

        assertEquals(listOf(""), series.labels)
        assertEquals(listOf(MinimumVisibleChartValue), series.values)
    }

    @Test
    fun bucketSeriesPreservesLabelsTonesAndUsesBaselineForAllZeroValues() {
        val series = prepareBucketSeries(
            listOf(
                ChartBucket("Overdue", 0, ChartTone.Danger),
                ChartBucket("Today", 0, ChartTone.Primary),
            ),
        )

        assertEquals(listOf("Overdue", "Today"), series.labels)
        assertEquals(listOf(ChartTone.Danger, ChartTone.Primary), series.tones)
        assertEquals(listOf(MinimumVisibleChartValue, MinimumVisibleChartValue), series.values)
    }
}
