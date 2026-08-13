package com.myapplication.shared.domain.analytics

import kotlin.test.Test
import kotlin.test.assertEquals

class ChartSeriesTest {
    @Test
    fun weekRangeUsesSevenDays() {
        assertEquals(7, AnalyticsRange.Week.dayCount)
    }

    @Test
    fun monthRangeUsesThirtyDays() {
        assertEquals(30, AnalyticsRange.Month.dayCount)
    }

    @Test
    fun maxValueFallsBackToOneForEmptySeries() {
        assertEquals(1, ChartSeries("完成趋势", emptyList()).maxValue)
    }

    @Test
    fun maxValueUsesLargestPointValue() {
        val series = ChartSeries(
            title = "精力输出",
            points = listOf(
                ChartPoint("8/11", 2),
                ChartPoint("8/12", 7),
                ChartPoint("8/13", 4),
            ),
        )

        assertEquals(7, series.maxValue)
    }
}
