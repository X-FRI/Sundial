package com.myapplication.shared.domain.analytics

enum class AnalyticsRange(
    val dayCount: Int,
) {
    Week(7),
    Month(30),
}
