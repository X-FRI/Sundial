package com.myapplication.shared.util

import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime

enum class DueBucket { OVERDUE, TODAY, TOMORROW, THIS_WEEK, LATER }

fun todayDate(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

fun bucketOf(due: LocalDate, today: LocalDate): DueBucket {
    val diff = today.daysUntil(due)
    return when {
        diff < 0 -> DueBucket.OVERDUE
        diff == 0 -> DueBucket.TODAY
        diff == 1 -> DueBucket.TOMORROW
        diff < 8 - today.dayOfWeek.isoDayNumber -> DueBucket.THIS_WEEK
        else -> DueBucket.LATER
    }
}

fun bucketLabel(bucket: DueBucket): String = when (bucket) {
    DueBucket.OVERDUE -> "过期"
    DueBucket.TODAY -> "今天"
    DueBucket.TOMORROW -> "明天"
    DueBucket.THIS_WEEK -> "本周"
    DueBucket.LATER -> "计划"
}

private val weekdaysZh = arrayOf("", "一", "二", "三", "四", "五", "六", "日")

fun formatDueDate(due: Instant?, tz: TimeZone = TimeZone.currentSystemDefault()): String =
    formatDueDate(due, tz, todayDate())

fun formatDueDate(due: Instant?, tz: TimeZone, today: LocalDate): String {
    if (due == null) return ""
    val ldt = due.toLocalDateTime(tz)
    val date = ldt.date
    val days = today.daysUntil(date)
    val dateLabel = when (days) {
        0 -> "今天"
        -1 -> "昨天"
        1 -> "明天"
        else -> {
            val todayDow = today.dayOfWeek.isoDayNumber
            if (date.dayOfWeek.isoDayNumber > todayDow && days in 2..7) "周${weekdaysZh[date.dayOfWeek.isoDayNumber]}"
            else "${date.monthNumber}月${date.dayOfMonth}日"
        }
    }
    val time = ldt.time
    val timeLabel = if (time.hour == 0 && time.minute == 0) {
        ""
    } else {
        " ${time.hour}:${time.minute.toString().padStart(2, '0')}"
    }
    return dateLabel + timeLabel
}
