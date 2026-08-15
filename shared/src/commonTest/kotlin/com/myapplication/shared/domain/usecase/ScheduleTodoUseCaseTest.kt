package com.myapplication.shared.domain.usecase

import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.test.FakeTodoRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class ScheduleTodoUseCaseTest {
    private val fixedClock =
        object : kotlin.time.Clock {
            override fun now(): Instant = Instant.parse("2026-08-13T12:00:00Z")
        }

    @Test
    fun scheduleTodayWithoutTimeUsesNineAm() =
        runTest {
            val repo = FakeTodoRepository()
            val useCase = ScheduleTodoUseCase(repo, fixedClock, TimeZone.UTC)
            val item = TodoItem(6, 1, "x", "", null, false, false, null, false, null, null, 0.0, Instant.fromEpochMilliseconds(0))

            useCase.scheduleToday(item)

            assertEquals(6L, repo.lastSetDueDateId)
            assertEquals(Instant.parse("2026-08-13T09:00:00Z"), repo.lastSetDueDateValue)
        }

    @Test
    fun scheduleTomorrowPreservesExistingNonMidnightTime() =
        runTest {
            val repo = FakeTodoRepository()
            val useCase = ScheduleTodoUseCase(repo, fixedClock, TimeZone.UTC)
            val item =
                TodoItem(
                    7,
                    1,
                    "x",
                    "",
                    Instant.parse("2026-08-10T15:30:00Z"),
                    false,
                    false,
                    null,
                    false,
                    null,
                    null,
                    0.0,
                    Instant.fromEpochMilliseconds(0),
                )

            useCase.scheduleTomorrow(item)

            assertEquals(7L, repo.lastSetDueDateId)
            assertEquals(Instant.parse("2026-08-14T15:30:00Z"), repo.lastSetDueDateValue)
        }
}
