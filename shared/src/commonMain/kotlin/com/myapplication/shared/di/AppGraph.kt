package com.myapplication.shared.di

import app.cash.sqldelight.db.SqlDriver
import com.myapplication.shared.data.TodoDb
import com.myapplication.shared.data.TodoRepositoryImpl
import com.myapplication.shared.domain.repository.TodoRepository
import com.myapplication.shared.domain.usecase.AddSubTaskUseCase
import com.myapplication.shared.domain.usecase.AddTodoUseCase
import kotlin.time.Clock
import kotlinx.datetime.TimeZone

class AppGraph(
    driver: SqlDriver,
    val clock: Clock = Clock.System,
    val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val repository: TodoRepository by lazy { TodoRepositoryImpl(TodoDb(driver), clock, timeZone) }
    val addTodo: AddTodoUseCase by lazy { AddTodoUseCase(repository) }
    val addSubTask: AddSubTaskUseCase by lazy { AddSubTaskUseCase(repository) }
}

expect fun createAppGraph(): AppGraph
