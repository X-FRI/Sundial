package com.myapplication.shared.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * DateParser 的解析策略测试。
 *
 * 固定参考日 = 2026-08-11（周二），所有断言基于该日推导，
 * 覆盖每条解析规则：相对日（中/英）、"下周X"、本周"周X"回绕、
 * "X月X日"跨年、时间提取（时段词/中文分隔符/非法小时），
 * 以及"仅日期无标题"时标题回退、英文词边界不误伤子串等边界。
 */
class DateParserTest {

    private val today: LocalDate = LocalDate(2026, Month.AUGUST, 11) // 周二

    @Test
    fun noDateToken() {
        // 无日期关键词 → 标题原样保留，无到期时间
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
        // 日期 + 英文冒号时间组合
        val r = DateParser.parse("交报告 明天15:00", today)
        assertEquals("交报告", r.title)
        assertEquals(LocalDate(2026, 8, 12), r.dueDate?.date)
        assertEquals(LocalTime(15, 0), r.dueDate?.time)
    }

    @Test
    fun dayAfterTomorrowWithTime() {
        // 时间 token 在标题中间也被正确剔除
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
        // 目标星期 == 今天 → 直接今天
        val r = DateParser.parse("周二晨会", today)
        assertEquals("晨会", r.title)
        assertEquals(LocalDate(2026, 8, 11), r.dueDate?.date)
    }

    @Test
    fun nextWeek() {
        // "下周X"永远推进到下周，与本周状态无关
        val r = DateParser.parse("下周一看医生", today)
        assertEquals("看医生", r.title)
        assertEquals(LocalDate(2026, 8, 17), r.dueDate?.date)
    }

    @Test
    fun monthDayThisYear() {
        // 今年的月日 → 今年
        val r = DateParser.parse("12月25日 圣诞采购", today)
        assertEquals("圣诞采购", r.title)
        assertEquals(LocalDate(2026, 12, 25), r.dueDate?.date)
    }

    @Test
    fun monthDayNextYearWhenPassed() {
        // 已过的月日 → 顺延到明年
        val r = DateParser.parse("1月1日 新年快乐", today)
        assertEquals("新年快乐", r.title)
        assertEquals(LocalDate(2027, 1, 1), r.dueDate?.date)
    }

    @Test
    fun afternoonTime() {
        // 时段词"下午"把 3 点换算为 15 点；无日期 → 落在今天
        val r = DateParser.parse("下午3点 开会", today)
        assertEquals("开会", r.title)
        assertEquals(LocalTime(15, 0), r.dueDate?.time)
        assertEquals(LocalDate(2026, 8, 11), r.dueDate?.date)
    }

    @Test
    fun englishRelative() {
        // 英文相对日
        val r = DateParser.parse("buy milk tomorrow", today)
        assertEquals("buy milk", r.title)
        assertEquals(LocalDate(2026, 8, 12), r.dueDate?.date)
    }

    @Test
    fun titleOnlyKeptWhenBlank() {
        // 关键词被剔除后标题为空 → 回退为原始输入，保证有可读标题
        val r = DateParser.parse("明天", today)
        assertEquals("明天", r.title)
        assertEquals(LocalDate(2026, 8, 12), r.dueDate?.date)
    }

    @Test
    fun englishCapitalized() {
        // IGNORE_CASE + 首字母大写也能命中
        val r = DateParser.parse("Next Monday meeting", today)
        assertEquals("meeting", r.title)
        assertEquals(LocalDate(2026, 8, 17), r.dueDate?.date)
    }

    @Test
    fun englishDayAfterTomorrowUpper() {
        // 全大写形式命中（IGNORE_CASE）
        val r = DateParser.parse("DAY AFTER TOMORROW review", today)
        assertEquals("review", r.title)
        assertEquals(LocalDate(2026, 8, 13), r.dueDate?.date)
    }

    @Test
    fun tomorrowlandNotMangled() {
        // \b 词边界保护：tomorrowland 不是 tomorrow，标题与到期均不受影响
        val r = DateParser.parse("tomorrowland concert", today)
        assertEquals("tomorrowland concert", r.title)
        assertNull(r.dueDate)
    }

    @Test
    fun sundayZhRollsToThisWeek() {
        // 周日（7）> 周二（2）且在本周内 → 本周日
        val r = DateParser.parse("周日开会", today)
        assertEquals("开会", r.title)
        assertEquals(LocalDate(2026, 8, 16), r.dueDate?.date)
    }

    @Test
    fun middayMarker() {
        // "中午"恒为 12 点
        val r = DateParser.parse("中午12点 吃饭", today)
        assertEquals("吃饭", r.title)
        assertEquals(LocalTime(12, 0), r.dueDate?.time)
    }

    @Test
    fun invalidHourIgnored() {
        // 非法小时（25 点）→ 整体视为无时间，标题保留
        val r = DateParser.parse("25点 见", today)
        assertNull(r.dueDate)
        assertEquals("25点 见", r.title)
    }
}
