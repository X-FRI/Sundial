package com.myapplication.shared.domain.recurrence

sealed interface RecurrenceRule {
    val interval: Int

    data class Daily(
        override val interval: Int = 1,
    ) : RecurrenceRule {
        init {
            require(interval > 0) { "Recurrence interval must be positive." }
        }
    }

    data class Weekly(
        override val interval: Int = 1,
    ) : RecurrenceRule {
        init {
            require(interval > 0) { "Recurrence interval must be positive." }
        }
    }

    data class Monthly(
        override val interval: Int = 1,
    ) : RecurrenceRule {
        init {
            require(interval > 0) { "Recurrence interval must be positive." }
        }
    }
}

fun RecurrenceRule.label(): String =
    when (this) {
        is RecurrenceRule.Daily -> if (interval == 1) "每天" else "每 $interval 天"
        is RecurrenceRule.Weekly -> if (interval == 1) "每周" else "每 $interval 周"
        is RecurrenceRule.Monthly -> if (interval == 1) "每月" else "每 $interval 个月"
    }
