package com.myapplication.shared.util

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class FormattingTest {

    private val today: LocalDate = LocalDate(2026, Month.AUGUST, 11) // 周二
    private val tz: TimeZone = TimeZone.UTC

    private fun instantOf(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Instant =
        LocalDateTime(year, month, day, hour, minute).toInstant(tz)

    @Test
    fun bucketBoundaries() {
        assertEquals(DueBucket.OVERDUE, bucketOf(LocalDate(2026, 8, 10), today))
        assertEquals(DueBucket.TODAY, bucketOf(LocalDate(2026, 8, 11), today))
        assertEquals(DueBucket.TOMORROW, bucketOf(LocalDate(2026, 8, 12), today))
        assertEquals(DueBucket.THIS_WEEK, bucketOf(LocalDate(2026, 8, 14), today))
        assertEquals(DueBucket.THIS_WEEK, bucketOf(LocalDate(2026, 8, 15), today))
        assertEquals(DueBucket.THIS_WEEK, bucketOf(LocalDate(2026, 8, 16), today))
        assertEquals(DueBucket.LATER, bucketOf(LocalDate(2026, 8, 17), today))
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
        assertEquals("周六", formatDueDate(instantOf(2026, 8, 15), tz, today))
    }

    @Test
    fun formatFarDateAsMonthDay() {
        assertEquals("8月17日", formatDueDate(instantOf(2026, 8, 17), tz, today))
    }

    @Test
    fun formatMidnightSuppressesTime() {
        assertEquals("今天", formatDueDate(instantOf(2026, 8, 11, 0, 0), tz, today))
    }

    @Test
    fun formatWithTime() {
        assertEquals("今天 15:00", formatDueDate(instantOf(2026, 8, 11, 15, 0), tz, today))
        assertEquals("今天 9:05", formatDueDate(instantOf(2026, 8, 11, 9, 5), tz, today))
    }

    @Test
    fun formatNullDue() {
        assertEquals("", formatDueDate(null, tz, today))
    }
}
