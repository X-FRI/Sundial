package com.myapplication.shared.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.nio.file.Files
import java.nio.file.Paths

/**
 * 桌面驱动：JDBC SQLite，数据库文件位于 ~/.reminders/reminders.db。
 *
 * schema 创建时机特殊：JdbcSqliteDriver 不会自动建表，这里仅当数据库文件
 * 不存在或为空（全新库）时手动执行 Schema.create；已有库则由
 * Schema.migrate 的版本化机制负责迁移。
 */
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
