package com.myapplication.shared.di

import app.cash.sqldelight.db.SqlDriver
import com.myapplication.shared.data.TodoDb
import com.myapplication.shared.data.TodoRepositoryImpl
import com.myapplication.shared.domain.repository.TodoRepository

class AppGraph(driver: SqlDriver) {
    val repository: TodoRepository by lazy { TodoRepositoryImpl(TodoDb(driver)) }
}

expect fun createAppGraph(): AppGraph
