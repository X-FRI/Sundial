package com.myapplication.shared.ui.analytics.charts

import com.myapplication.shared.domain.analytics.ChartBucket
import com.myapplication.shared.domain.analytics.ChartPoint
import com.myapplication.shared.domain.analytics.ChartTone

internal const val MinimumVisibleChartValue = 0.08f

internal data class PreparedPointSeries(
    val values: List<Float>,
    val labels: List<String>,
)

internal data class PreparedBucketSeries(
    val values: List<Float>,
    val labels: List<String>,
    val tones: List<ChartTone>,
)

internal fun preparePointSeries(points: List<ChartPoint>): PreparedPointSeries =
    PreparedPointSeries(
        values = visibleValues(points.map { it.value.toFloat() }),
        labels = labelsOrPlaceholder(points.map { it.label }),
    )

internal fun prepareBucketSeries(buckets: List<ChartBucket>): PreparedBucketSeries =
    PreparedBucketSeries(
        values = visibleValues(buckets.map { it.value.toFloat() }),
        labels = labelsOrPlaceholder(buckets.map { it.label }),
        tones = if (buckets.isEmpty()) listOf(ChartTone.Neutral) else buckets.map { it.tone },
    )

private fun visibleValues(values: List<Float>): List<Float> =
    when {
        values.isEmpty() -> listOf(MinimumVisibleChartValue)
        values.all { it == 0f } -> values.map { MinimumVisibleChartValue }
        else -> values
    }

private fun labelsOrPlaceholder(labels: List<String>): List<String> =
    labels.ifEmpty { listOf("") }
