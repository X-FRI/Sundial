package com.myapplication.shared.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

actual fun createSqlDriver(): SqlDriver = NativeSqliteDriver(TodoDb.Schema, "reminders.db")
