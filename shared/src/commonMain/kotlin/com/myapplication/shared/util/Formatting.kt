package com.myapplication.shared.util

import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime

/**
 * 到期日分组（智能清单的分桶依据）：
 * OVERDUE=过期 / TODAY=今天 / TOMORROW=明天 / THIS_WEEK=本周 / LATER=计划。
 */
enum class DueBucket { OVERDUE, TODAY, TOMORROW, THIS_WEEK, LATER }

// 取"今天"（依赖注入版本供测试固定时钟用）
fun todayDate(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

fun todayDate(clock: Clock, timeZone: TimeZone): LocalDate =
    clock.now().toLocalDateTime(timeZone).date

/**
 * 按截止日期分桶。
 *
 * 规则：
 * - diff < 0 → OVERDUE；0 → TODAY；1 → TOMORROW；
 * - 2..（本周日距今天的天数）→ THIS_WEEK，边界 = 8 - 今天星期号
 *   （周一为 1，则本周余下 6 天；周日为 7，则余下 0 天——周日没有"本周"桶，
 *   明天起全部落入 LATER，属已知取舍）；
 * - 其余 → LATER。
 */
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

/** 分桶的展示文案（与智能清单侧栏文案一致）。 */
fun bucketLabel(bucket: DueBucket): String = when (bucket) {
    DueBucket.OVERDUE -> "过期"
    DueBucket.TODAY -> "今天"
    DueBucket.TOMORROW -> "明天"
    DueBucket.THIS_WEEK -> "本周"
    DueBucket.LATER -> "计划"
}

// 星期号 → 中文星期名；下标 0 为占位（ISO 星期号从 1 开始）
private val weekdaysZh = arrayOf("", "一", "二", "三", "四", "五", "六", "日")

// 便捷重载：用真实时钟与当前时区
fun formatDueDate(due: Instant?, tz: TimeZone = TimeZone.currentSystemDefault()): String =
    formatDueDate(due, tz, todayDate())

/**
 * 相对时间文案。
 *
 * 日期部分规则（以 today 为参照）：
 * - 差 0/-1/+1 天 → "今天/昨天/明天"；
 * - 未来 2..7 天内且目标星期号 > 今天星期号 → "周X"
 *   （注意：跨周的未来日期即使很近也走"X月X日"，如周二看到下周一 → 8月17日）；
 * - 其余 → "X月X日"。
 *
 * 时间部分：0 点整不显示时间；否则 " HH:MM"（分钟补零，小时不补）。
 */
fun formatDueDate(due: Instant?, tz: TimeZone, today: LocalDate): String {
    if (due == null) return ""
    val ldt = due.toLocalDateTime(tz)
    val date = ldt.date
    val days = today.daysUntil(date)
    // 日期文案：相对词 → 本周星期 → 月日
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
    // 0 点整视为"只定日期不定时间"，隐藏时间部分
    val timeLabel = if (time.hour == 0 && time.minute == 0) {
        ""
    } else {
        " ${time.hour}:${time.minute.toString().padStart(2, '0')}"
    }
    return dateLabel + timeLabel
}
