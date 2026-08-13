package com.myapplication.shared.domain.analytics

data class ChartPoint(
    val label: String,
    val value: Int,
)

data class ChartBucket(
    val label: String,
    val value: Int,
    val tone: ChartTone = ChartTone.Neutral,
)

enum class ChartTone {
    Primary,
    Info,
    Warning,
    Danger,
    Neutral,
}

data class ChartSeries(
    val title: String,
    val points: List<ChartPoint>,
) {
    val maxValue: Int = points.maxOfOrNull { it.value } ?: 1
}

data class BucketSeries(
    val title: String,
    val buckets: List<ChartBucket>,
) {
    val total: Int = buckets.sumOf { it.value }
}
