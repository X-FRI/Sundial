package com.myapplication.shared.util

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus

/**
 * 快速输入解析结果：清洗后的标题 + 可选到期时间（无时间部分则 null）。
 */
data class ParsedInput(val title: String, val dueDate: LocalDateTime?)

/**
 * 自然语言日期解析器：把"明天 交报告"、"周五开会"、"12月25日 圣诞采购"
 * 等中文/英文混合输入解析为（标题, 到期时间）。
 *
 * 解析策略（顺序即优先级，命中即停）：
 * 1. 中文相对日："今天/明天/后天"；
 * 2. "下周X"：固定推到未来第 1 个目标星期几（绝不下周同日）；
 * 3. "周X"：本周内目标星期几（晚于今天→本周；早于→下周；等于→今天）；
 * 4. "X月X日"：今年该日期，已过则顺延到明年；
 * 5. 英文相对日：day after tomorrow / tomorrow / today / next Monday；
 * 6. 时间提取（独立于日期）："下午3点"、"15:00"、"中午12点"等；
 * 7. 组合：日期 + 时间 → 完整 LocalDateTime；只有时间 → 当天该时刻；
 *    都无 → dueDate = null（不设置到期）。
 *
 * 所有被消费的关键词都会从标题中剔除（替换为空格再压缩），
 * 标题被清空时回退为原始输入（保证"明天"单独成行也能保留可读标题）。
 */
object DateParser {

    // 中文星期→ISO 星期号（1=周一…7=周日）；"天"是"日"的口语变体
    private val weekdayZh = mapOf(
        "一" to 1, "二" to 2, "三" to 3, "四" to 4,
        "五" to 5, "六" to 6, "日" to 7, "天" to 7,
    )
    // 英文星期→ISO 星期号
    private val weekdayEn = mapOf(
        "monday" to 1, "tuesday" to 2, "wednesday" to 3, "thursday" to 4,
        "friday" to 5, "saturday" to 6, "sunday" to 7,
    )
    // 时间格式：可选时段词（上午/中午/下午/晚上/早上）+ 1-2 位数字 +
    // 分隔符（: 或 ：或 点 或 时）+ 可选分钟 + 可选"分"
    private val timeRegex = Regex("""(上午|中午|下午|晚上|早上)?\s*(\d{1,2})\s*[:：点时]\s*(\d{1,2})?\s*分?""")

    fun parse(input: String, today: LocalDate): ParsedInput {
        var title = input.trim()
        var date: LocalDate? = null

        // 步骤 1：中文相对日（今天/明天/后天）
        for ((kw, offset) in listOf("今天" to 0L, "明天" to 1L, "后天" to 2L)) {
            if (title.contains(kw)) {
                date = today.plus(offset, DateTimeUnit.DAY)
                title = removeToken(title, kw)
                break
            }
        }

        // 步骤 2："下周X"——始终是未来第 1 个目标星期几：
        // (7 - 今天星期号 + 目标星期号) 天
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

        // 步骤 3："周X"——优先本周：目标 > 今天 → 本周内；目标 == 今天 → 今天；
        // 目标 < 今天 → 下周一跳（下个周期的同一天）
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

        // 步骤 4："X月X日"——今年内，已过则顺延到明年；
        // runCatching 兜底非法日期（如 13月40日），失败时保持原样不消费该 token
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

        // 步骤 5：英文相对日；\b 词边界防止误伤 "tomorrowland" 这类子串
        if (date == null) {
            val datMatch = Regex("""\bday after tomorrow\b""", RegexOption.IGNORE_CASE).find(title)
            val tomorrowMatch = Regex("""\btomorrow\b""", RegexOption.IGNORE_CASE).find(title)
            val todayMatch = Regex("""\btoday\b""", RegexOption.IGNORE_CASE).find(title)
            when {
                datMatch != null -> {
                    date = today.plus(2, DateTimeUnit.DAY)
                    title = removeToken(title, datMatch.value)
                }
                tomorrowMatch != null -> {
                    date = today.plus(1, DateTimeUnit.DAY)
                    title = removeToken(title, tomorrowMatch.value)
                }
                todayMatch != null -> {
                    date = today
                    title = removeToken(title, todayMatch.value)
                }
                else -> {
                    // "next Monday"：与"下周X"同规则（未来第 1 个目标星期几）
                    Regex("""\bnext\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""", RegexOption.IGNORE_CASE)
                        .find(title)?.let { m ->
                            val target = weekdayEn.getValue(m.groupValues[1].lowercase())
                            date = today.plus(
                                (7 - today.dayOfWeek.isoDayNumber + target).toLong(),
                                DateTimeUnit.DAY,
                            )
                            title = removeToken(title, m.value)
                        }
                }
            }
        }

        // 步骤 6：提取时间并剔除时间 token（时间与日期互不干扰）
        val time = extractTime(title)
        if (time != null) {
            title = timeRegex.replace(title, " ")
        }

        // 步骤 7：组装结果——有日期则时间缺省为 0 点；无日期只有时间 → 当天
        val due = when {
            date != null -> LocalDateTime(date, time ?: LocalTime(0, 0))
            time != null -> LocalDateTime(today, time)
            else -> null
        }
        // 压缩连续空白；标题被清空时回退为原始输入，避免空标题
        val cleanTitle = title.replace(Regex("\\s+"), " ").trim()
        return ParsedInput(cleanTitle.ifBlank { input.trim() }, due)
    }

    /**
     * 从文本中提取时间（仅第一个匹配）。
     *
     * 时段词规则：
     * - 下午/晚上：小时 < 12 则 +12（12点下午 = 12 不翻倍）；
     * - 中午：恒为 12 点（忽略数字，符合口语习惯）；
     * - 上午/早上/无词：原样保留（12点上午会被当作 12 点，属已知取舍）；
     * - 非法小时（如 25 点）经 runCatching 返回 null，不污染结果。
     */
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

    // 从标题中移除已消费的关键词：先替换为空格（防止粘连"明天开"→"开"），
    // 再压缩连续空白并去首尾空格
    private fun removeToken(text: String, token: String): String =
        text.replace(token, " ").replace(Regex("\\s+"), " ").trim()
}
