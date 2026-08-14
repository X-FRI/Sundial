package com.myapplication.shared.domain.recurrence

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RecurrenceCalculatorTest {
    @Test
    fun dailyIntervalAddsDays() {
        assertEquals(
            LocalDate(2026, 8, 16),
            nextOccurrence(LocalDate(2026, 8, 13), RecurrenceRule.Daily(interval = 3)),
        )
    }

    @Test
    fun weeklyIntervalAddsWeeks() {
        assertEquals(
            LocalDate(2026, 8, 27),
            nextOccurrence(LocalDate(2026, 8, 13), RecurrenceRule.Weekly(interval = 2)),
        )
    }

    @Test
    fun monthlyIntervalAddsMonths() {
        assertEquals(
            LocalDate(2026, 11, 13),
            nextOccurrence(LocalDate(2026, 8, 13), RecurrenceRule.Monthly(interval = 3)),
        )
    }

    @Test
    fun monthlyIntervalClampsMonthEnd() {
        assertEquals(
            LocalDate(2026, 2, 28),
            nextOccurrence(LocalDate(2026, 1, 31), RecurrenceRule.Monthly()),
        )
    }

    @Test
    fun labelsDescribeDefaultAndCustomIntervals() {
        assertEquals("每天", RecurrenceRule.Daily().label())
        assertEquals("每 3 天", RecurrenceRule.Daily(interval = 3).label())
        assertEquals("每周", RecurrenceRule.Weekly().label())
        assertEquals("每 2 周", RecurrenceRule.Weekly(interval = 2).label())
        assertEquals("每月", RecurrenceRule.Monthly().label())
        assertEquals("每 6 个月", RecurrenceRule.Monthly(interval = 6).label())
    }

    @Test
    fun intervalsMustBePositive() {
        assertFailsWith<IllegalArgumentException> { RecurrenceRule.Daily(interval = 0) }
        assertFailsWith<IllegalArgumentException> { RecurrenceRule.Weekly(interval = -1) }
        assertFailsWith<IllegalArgumentException> { RecurrenceRule.Monthly(interval = 0) }
    }
}
