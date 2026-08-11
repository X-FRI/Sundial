package com.myapplication.shared.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.nio.file.Files
import java.nio.file.Paths

actual fun createSqlDriver(): SqlDriver {
    val home = System.getProperty("user.home") ?: "."
    val dir = Paths.get(home, ".reminders")
    Files.createDirectories(dir)
    val dbFile = dir.resolve("reminders.db")
    val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile}")
    if (!Files.exists(dbFile) || Files.size(dbFile) == 0L) {
        TodoDb.Schema.create(driver)
    }
    return driver
}
