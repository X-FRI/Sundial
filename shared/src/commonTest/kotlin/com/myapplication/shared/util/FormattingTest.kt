package com.myapplication.shared.util

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 时间格式化工具（bucketOf / bucketLabel / formatDueDate）的契约测试。
 *
 * 固定参考日 = 2026-08-11（周二）、时区 = UTC，覆盖：
 * - 分桶边界：过期/今天/明天/本周/计划五档的精确分界（7 日视界内一律本周，含跨周与周日）；
 * - 相对日期文案：昨天/今天/明天；
 * - 未来 2..7 天内的星期文案（周六、跨周的下周一/周二）与 8 天外的月日文案；
 * - 时间部分：0 点隐藏、其余 "HH:MM"（分钟补零、小时不补零）；
 * - 无到期时间 → 空串。
 */
class FormattingTest {
    private val today: LocalDate = LocalDate(2026, Month.AUGUST, 11) // 周二
    private val tz: TimeZone = TimeZone.UTC

    private fun instantOf(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 0,
        minute: Int = 0,
    ): Instant = LocalDateTime(year, month, day, hour, minute).toInstant(tz)

    @Test
    fun bucketBoundaries() {
        // 逐日验证五档边界；8/17（下周一）在 7 日视界内，属本周档（修复前为 LATER）
        assertEquals(DueBucket.OVERDUE, bucketOf(LocalDate(2026, 8, 10), today))
        assertEquals(DueBucket.TODAY, bucketOf(LocalDate(2026, 8, 11), today))
        assertEquals(DueBucket.TOMORROW, bucketOf(LocalDate(2026, 8, 12), today))
        // 周三~周日（8/12 后 4 天）均属本周
        assertEquals(DueBucket.THIS_WEEK, bucketOf(LocalDate(2026, 8, 14), today))
        assertEquals(DueBucket.THIS_WEEK, bucketOf(LocalDate(2026, 8, 15), today))
        assertEquals(DueBucket.THIS_WEEK, bucketOf(LocalDate(2026, 8, 16), today))
        // 跨周但差 ≤ 7 天：下周一（diff=6）、下周二（diff=7）仍属本周档
        assertEquals(DueBucket.THIS_WEEK, bucketOf(LocalDate(2026, 8, 17), today))
        assertEquals(DueBucket.THIS_WEEK, bucketOf(LocalDate(2026, 8, 18), today))
        // diff ≥ 8 → 计划
        assertEquals(DueBucket.LATER, bucketOf(LocalDate(2026, 8, 19), today))
    }

    @Test
    fun sundayTodayUsesDaysDiffHorizon() {
        // Bug 1 回归：周日当天不再因 8 - isoDayNumber = 1 把近未来全部丢进 LATER
        val sunday = LocalDate(2026, 8, 16) // 周日
        assertEquals(DueBucket.TOMORROW, bucketOf(LocalDate(2026, 8, 17), sunday)) // +1 天（周一）→ 明天
        assertEquals(DueBucket.THIS_WEEK, bucketOf(LocalDate(2026, 8, 19), sunday)) // +3 天（下周三）→ 本周档
        assertEquals(DueBucket.THIS_WEEK, bucketOf(LocalDate(2026, 8, 23), sunday)) // +7 天（下周日）→ 边界仍属本周
        assertEquals(DueBucket.LATER, bucketOf(LocalDate(2026, 8, 24), sunday)) // +8 天 → 计划
    }

    @Test
    fun bucketLabels() {
        assertEquals("过期", bucketLabel(DueBucket.OVERDUE))
        assertEquals("今天", bucketLabel(DueBucket.TODAY))
        assertEquals("明天", bucketLabel(DueBucket.TOMORROW))
        assertEquals("本周", bucketLabel(DueBucket.THIS_WEEK))
        assertEquals("计划", bucketLabel(DueBucket.LATER))
    }

    @Test
    fun formatRelativeDays() {
        assertEquals("昨天", formatDueDate(instantOf(2026, 8, 10), tz, today))
        assertEquals("今天", formatDueDate(instantOf(2026, 8, 11), tz, today))
        assertEquals("明天", formatDueDate(instantOf(2026, 8, 12), tz, today))
    }

    @Test
    fun formatThisWeekWeekdayLabel() {
        // 本周六（8/15，星期号 6 > 周二 2，且差 4 天）→ 周X 文案
        assertEquals("周六", formatDueDate(instantOf(2026, 8, 15), tz, today))
    }

    @Test
    fun formatNextWeekWeekdayLabel() {
        // Bug 2 回归：周二看下周一（8/17，跨周但差 6 天）→ 周X 文案（修复前为 8月17日）
        assertEquals("周一", formatDueDate(instantOf(2026, 8, 17), tz, today))
        // diff=7 边界（8/18 下周二）仍显示 周X
        assertEquals("周二", formatDueDate(instantOf(2026, 8, 18), tz, today))
        // diff=8 → 月日文案
        assertEquals("8月19日", formatDueDate(instantOf(2026, 8, 19), tz, today))
    }

    @Test
    fun formatMidnightSuppressesTime() {
        // 0 点整 → 只显示日期，不显示时间
        assertEquals("今天", formatDueDate(instantOf(2026, 8, 11, 0, 0), tz, today))
    }

    @Test
    fun formatWithTime() {
        // 时间格式：小时不补零、分钟补零
        assertEquals("今天 15:00", formatDueDate(instantOf(2026, 8, 11, 15, 0), tz, today))
        assertEquals("今天 9:05", formatDueDate(instantOf(2026, 8, 11, 9, 5), tz, today))
    }

    @Test
    fun formatNullDue() {
        // 无到期时间 → 空串（UI 据此隐藏日期区）
        assertEquals("", formatDueDate(null, tz, today))
    }
}
