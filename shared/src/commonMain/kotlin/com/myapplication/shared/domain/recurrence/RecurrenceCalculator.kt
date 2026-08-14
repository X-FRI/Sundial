package com.myapplication.shared.domain.recurrence

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

fun nextOccurrence(
    baseDate: LocalDate,
    rule: RecurrenceRule,
): LocalDate =
    when (rule) {
        is RecurrenceRule.Daily -> baseDate + DatePeriod(days = rule.interval)
        is RecurrenceRule.Weekly -> baseDate + DatePeriod(days = rule.interval * 7)
        is RecurrenceRule.Monthly -> baseDate + DatePeriod(months = rule.interval)
    }
