package com.myapplication.shared.domain.usecase

import arrow.core.Either
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.repository.TodoCommands
import com.myapplication.shared.util.todayDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

class ScheduleTodoUseCase(
    private val commands: TodoCommands,
    private val clock: Clock,
    private val timeZone: TimeZone,
) {
    suspend fun scheduleToday(item: TodoItem): Either<TodoError, Unit> = scheduleOn(item, todayDate(clock, timeZone))

    suspend fun scheduleTomorrow(item: TodoItem): Either<TodoError, Unit> = scheduleOn(item, todayDate(clock, timeZone).plus(1, DateTimeUnit.DAY))

    private suspend fun scheduleOn(
        item: TodoItem,
        date: LocalDate,
    ): Either<TodoError, Unit> {
        val time =
            item.dueDate
                ?.toLocalDateTime(timeZone)
                ?.time
                ?.takeIf { it != LocalTime(0, 0) }
                ?: LocalTime(9, 0)
        return commands.setDueDate(item.id, LocalDateTime(date, time).toInstant(timeZone))
    }
}
