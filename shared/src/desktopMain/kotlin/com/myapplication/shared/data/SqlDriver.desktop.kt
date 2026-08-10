package com.myapplication.shared.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.nio.file.Files
import java.nio.file.Paths

actual fun createSqlDriver(): SqlDriver {
    val home = System.getProperty("user.home") ?: "."
    val dir = Paths.get(home, ".reminders")
    Files.createDirectories(dir)
    val driver = JdbcSqliteDriver("jdbc:sqlite:${dir.resolve("reminders.db")}")
    TodoDb.Schema.create(driver)
    return driver
}
