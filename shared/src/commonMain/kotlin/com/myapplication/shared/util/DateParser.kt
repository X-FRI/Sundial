package com.myapplication.shared.util

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus

data class ParsedInput(val title: String, val dueDate: LocalDateTime?)

object DateParser {

    private val weekdayZh = mapOf(
        "一" to 1, "二" to 2, "三" to 3, "四" to 4,
        "五" to 5, "六" to 6, "日" to 7, "天" to 7,
    )
    private val weekdayEn = mapOf(
        "monday" to 1, "tuesday" to 2, "wednesday" to 3, "thursday" to 4,
        "friday" to 5, "saturday" to 6, "sunday" to 7,
    )
    private val timeRegex = Regex("""(上午|中午|下午|晚上|早上)?\s*(\d{1,2})\s*[:：点时]\s*(\d{1,2})?\s*分?""")

    fun parse(input: String, today: LocalDate): ParsedInput {
        var title = input.trim()
        var date: LocalDate? = null

        for ((kw, offset) in listOf("今天" to 0L, "明天" to 1L, "后天" to 2L)) {
            if (title.contains(kw)) {
                date = today.plus(offset, DateTimeUnit.DAY)
                title = removeToken(title, kw)
                break
            }
        }

        if (date == null) {
            Regex("下周([一二三四五六日天])").find(title)?.let { m ->
                val target = weekdayZh.getValue(m.groupValues[1])
                date = today.plus(
                    (7 - today.dayOfWeek.isoDayNumber + target).toLong(),
                    DateTimeUnit.DAY,
                )
                title = removeToken(title, m.value)
            }
        }

        if (date == null) {
            Regex("周([一二三四五六日天])").find(title)?.let { m ->
                val target = weekdayZh.getValue(m.groupValues[1])
                val todayDow = today.dayOfWeek.isoDayNumber
                date = when {
                    target == todayDow -> today
                    target > todayDow -> today.plus((target - todayDow).toLong(), DateTimeUnit.DAY)
                    else -> today.plus((7 - todayDow + target).toLong(), DateTimeUnit.DAY)
                }
                title = removeToken(title, m.value)
            }
        }

        if (date == null) {
            Regex("""(\d{1,2})月(\d{1,2})[日号]""").find(title)?.let { m ->
                runCatching {
                    val month = m.groupValues[1].toInt()
                    val day = m.groupValues[2].toInt()
                    val candidate = LocalDate(today.year, month, day)
                    if (candidate < today) LocalDate(today.year + 1, month, day) else candidate
                }.onSuccess {
                    date = it
                    title = removeToken(title, m.value)
                }
            }
        }

        if (date == null) {
            val lower = title.lowercase()
            when {
                lower.contains("day after tomorrow") -> {
                    date = today.plus(2, DateTimeUnit.DAY)
                    title = removeToken(title, "day after tomorrow")
                }
                lower.contains("tomorrow") -> {
                    date = today.plus(1, DateTimeUnit.DAY)
                    title = removeToken(title, "tomorrow")
                }
                lower.contains("today") -> {
                    date = today
                    title = removeToken(title, "today")
                }
                else -> {
                    Regex("next\\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)")
                        .find(lower)?.let { m ->
                            val target = weekdayEn.getValue(m.groupValues[1])
                            date = today.plus(
                                (7 - today.dayOfWeek.isoDayNumber + target).toLong(),
                                DateTimeUnit.DAY,
                            )
                            title = removeToken(title, m.value)
                        }
                }
            }
        }

        val time = extractTime(title)
        if (time != null) {
            title = timeRegex.replace(title, " ")
        }

        val due = when {
            date != null -> LocalDateTime(date, time ?: LocalTime(0, 0))
            time != null -> LocalDateTime(today, time)
            else -> null
        }
        val cleanTitle = title.replace(Regex("\\s+"), " ").trim()
        return ParsedInput(cleanTitle.ifBlank { input.trim() }, due)
    }

    private fun extractTime(text: String): LocalTime? {
        val m = timeRegex.find(text) ?: return null
        val h = m.groupValues[2].toInt()
        val min = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt() ?: 0
        val marker = m.groupValues[1]
        val hour = when (marker) {
            "下午", "晚上" -> if (h < 12) h + 12 else h
            "中午" -> 12
            else -> h
        }
        return runCatching { LocalTime(hour, min) }.getOrNull()
    }

    private fun removeToken(text: String, token: String): String =
        text.replace(token, " ").replace(Regex("\\s+"), " ").trim()
}
