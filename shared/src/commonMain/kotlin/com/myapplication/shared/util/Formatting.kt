package com.myapplication.shared.util

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * 到期日分组（智能清单的分桶依据）：
 * OVERDUE=过期 / TODAY=今天 / TOMORROW=明天 / THIS_WEEK=本周（7 日视界内，含跨周）/ LATER=计划。
 */
enum class DueBucket { OVERDUE, TODAY, TOMORROW, THIS_WEEK, LATER }

// 取"今天"（依赖注入版本供测试固定时钟用）
fun todayDate(): LocalDate =
    Clock.System
        .now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date

fun todayDate(
    clock: Clock,
    timeZone: TimeZone,
): LocalDate = clock.now().toLocalDateTime(timeZone).date

/**
 * 按截止日期分桶（基于距今天的日差，不依赖星期算术）。
 *
 * 规则：
 * - diff < 0 → OVERDUE；0 → TODAY；1 → TOMORROW；
 * - 2..7 → THIS_WEEK（含跨周的近未来日期：如周二看下周一 diff=6、周日看下周三 diff=3；
 *   枚举无"下周"档，7 日视界内一律归本周）；
 * - 其余（≥8 天）→ LATER。
 */
fun bucketOf(
    due: LocalDate,
    today: LocalDate,
): DueBucket {
    val diff = today.daysUntil(due)
    return when {
        diff < 0 -> DueBucket.OVERDUE
        diff == 0 -> DueBucket.TODAY
        diff == 1 -> DueBucket.TOMORROW
        diff <= 7 -> DueBucket.THIS_WEEK
        else -> DueBucket.LATER
    }
}

/** 分桶的展示文案（与智能清单侧栏文案一致）。 */
fun bucketLabel(bucket: DueBucket): String =
    when (bucket) {
        DueBucket.OVERDUE -> "过期"
        DueBucket.TODAY -> "今天"
        DueBucket.TOMORROW -> "明天"
        DueBucket.THIS_WEEK -> "本周"
        DueBucket.LATER -> "计划"
    }

// 星期号 → 中文星期名；下标 0 为占位（ISO 星期号从 1 开始）
private val weekdaysZh = arrayOf("", "一", "二", "三", "四", "五", "六", "日")

// 便捷重载：用真实时钟与当前时区
fun formatDueDate(
    due: Instant?,
    tz: TimeZone = TimeZone.currentSystemDefault(),
): String = formatDueDate(due, tz, todayDate())

/**
 * 相对时间文案。
 *
 * 日期部分规则（以 today 为参照）：
 * - 差 0/-1/+1 天 → "今天/昨天/明天"；
 * - 未来 2..7 天内 → "周X"（跨周也显示：如周二看到下周一 → 周一）；
 * - 其余 → "X月X日"。
 *
 * 时间部分：0 点整不显示时间；否则 " HH:MM"（分钟补零，小时不补）。
 */
fun formatDueDate(
    due: Instant?,
    tz: TimeZone,
    today: LocalDate,
): String {
    if (due == null) return ""
    val ldt = due.toLocalDateTime(tz)
    val date = ldt.date
    val days = today.daysUntil(date)
    // 日期文案：相对词 → 周X → 月日
    val dateLabel =
        when (days) {
            0 -> "今天"
            -1 -> "昨天"
            1 -> "明天"
            else -> {
                if (days in 2..7) {
                    "周${weekdaysZh[date.dayOfWeek.isoDayNumber]}"
                } else {
                    "${date.monthNumber}月${date.dayOfMonth}日"
                }
            }
        }
    val time = ldt.time
    // 0 点整视为"只定日期不定时间"，隐藏时间部分
    val timeLabel =
        if (time.hour == 0 && time.minute == 0) {
            ""
        } else {
            " ${time.hour}:${time.minute.toString().padStart(2, '0')}"
        }
    return dateLabel + timeLabel
}
