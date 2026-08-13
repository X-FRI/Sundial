package com.myapplication.shared.ui.recurrence

import com.myapplication.shared.domain.recurrence.RecurrenceRule
import kotlin.test.Test
import kotlin.test.assertEquals

class RecurrenceSummaryTest {

    @Test
    fun summarizesEmptyAndPresetRules() {
        assertEquals("不重复", recurrenceSummary(null))
        assertEquals("每天", recurrenceSummary(RecurrenceRule.Daily()))
        assertEquals("每 2 周", recurrenceSummary(RecurrenceRule.Weekly(interval = 2)))
        assertEquals("每月", recurrenceSummary(RecurrenceRule.Monthly()))
    }
}
