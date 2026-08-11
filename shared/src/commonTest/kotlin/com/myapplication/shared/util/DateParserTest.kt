package com.myapplication.shared.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DateParserTest {

    private val today: LocalDate = LocalDate(2026, Month.AUGUST, 11) // 周二

    @Test
    fun noDateToken() {
        val r = DateParser.parse("买牛奶", today)
        assertEquals("买牛奶", r.title)
        assertNull(r.dueDate)
    }

    @Test
    fun tomorrow() {
        val r = DateParser.parse("明天 交报告", today)
        assertEquals("交报告", r.title)
        assertEquals(LocalDate(2026, 8, 12), r.dueDate?.date)
    }

    @Test
    fun todayWithTime() {
        val r = DateParser.parse("买牛奶 今天", today)
        assertEquals("买牛奶", r.title)
        assertEquals(LocalDate(2026, 8, 11), r.dueDate?.date)
    }

    @Test
    fun tomorrowWithTime() {
        val r = DateParser.parse("交报告 明天15:00", today)
        assertEquals("交报告", r.title)
        assertEquals(LocalDate(2026, 8, 12), r.dueDate?.date)
        assertEquals(LocalTime(15, 0), r.dueDate?.time)
    }

    @Test
    fun dayAfterTomorrowWithTime() {
        val r = DateParser.parse("后天 10:30 开会", today)
        assertEquals("开会", r.title)
        assertEquals(LocalDate(2026, 8, 13), r.dueDate?.date)
        assertEquals(LocalTime(10, 30), r.dueDate?.time)
    }

    @Test
    fun weekdayLaterThisWeek() {
        // 今天是周二，周五 = 本周五
        val r = DateParser.parse("周五开会", today)
        assertEquals("开会", r.title)
        assertEquals(LocalDate(2026, 8, 14), r.dueDate?.date)
    }

    @Test
    fun weekdayEarlierThisWeekRollsToNextWeek() {
        // 今天是周二，周一 = 下周一
        val r = DateParser.parse("周一见客户", today)
        assertEquals("见客户", r.title)
        assertEquals(LocalDate(2026, 8, 17), r.dueDate?.date)
    }

    @Test
    fun sameWeekdayMeansToday() {
        val r = DateParser.parse("周二晨会", today)
        assertEquals("晨会", r.title)
        assertEquals(LocalDate(2026, 8, 11), r.dueDate?.date)
    }

    @Test
    fun nextWeek() {
        val r = DateParser.parse("下周一看医生", today)
        assertEquals("看医生", r.title)
        assertEquals(LocalDate(2026, 8, 17), r.dueDate?.date)
    }

    @Test
    fun monthDayThisYear() {
        val r = DateParser.parse("12月25日 圣诞采购", today)
        assertEquals("圣诞采购", r.title)
        assertEquals(LocalDate(2026, 12, 25), r.dueDate?.date)
    }

    @Test
    fun monthDayNextYearWhenPassed() {
        val r = DateParser.parse("1月1日 新年快乐", today)
        assertEquals("新年快乐", r.title)
        assertEquals(LocalDate(2027, 1, 1), r.dueDate?.date)
    }

    @Test
    fun afternoonTime() {
        val r = DateParser.parse("下午3点 开会", today)
        assertEquals("开会", r.title)
        assertEquals(LocalTime(15, 0), r.dueDate?.time)
        assertEquals(LocalDate(2026, 8, 11), r.dueDate?.date)
    }

    @Test
    fun englishRelative() {
        val r = DateParser.parse("buy milk tomorrow", today)
        assertEquals("buy milk", r.title)
        assertEquals(LocalDate(2026, 8, 12), r.dueDate?.date)
    }

    @Test
    fun titleOnlyKeptWhenBlank() {
        val r = DateParser.parse("明天", today)
        assertEquals("明天", r.title)
        assertEquals(LocalDate(2026, 8, 12), r.dueDate?.date)
    }
}
