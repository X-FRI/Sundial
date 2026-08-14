package com.myapplication.shared.ui.recurrence

import com.myapplication.shared.domain.recurrence.RecurrenceRule
import com.myapplication.shared.domain.recurrence.label

fun recurrenceSummary(rule: RecurrenceRule?): String = rule?.label() ?: "不重复"
