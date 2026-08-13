package com.myapplication.shared.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.nio.file.Files
import java.nio.file.Paths

/**
 * 桌面驱动：JDBC SQLite，数据库文件位于 ~/.reminders/reminders.db。
 *
 * schema 创建时机特殊：JdbcSqliteDriver 不会自动建表，这里仅当数据库文件
 * 不存在或为空（全新库）时手动执行 Schema.create；已有库会先读取
 * PRAGMA user_version，旧版本安装包没有写 user_version 时则从实际表结构
 * 推断版本，再交给 Schema.migrate 做增量迁移。
 */
actual fun createSqlDriver(): SqlDriver {
    val home = System.getProperty("user.home") ?: "."
    val dir = Paths.get(home, ".reminders")
    Files.createDirectories(dir)
    val dbFile = dir.resolve("reminders.db")
    val freshDatabase = !Files.exists(dbFile) || Files.size(dbFile) == 0L
    val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile}")
    val targetVersion = TodoDb.Schema.version

    if (freshDatabase) {
        TodoDb.Schema.create(driver)
        driver.setUserVersion(targetVersion)
    } else {
        val currentVersion = driver.normalizedSchemaVersion()
        when {
            currentVersion == 0L -> TodoDb.Schema.create(driver)
            currentVersion < targetVersion -> TodoDb.Schema.migrate(driver, currentVersion, targetVersion)
        }
        if (currentVersion != targetVersion) {
            driver.setUserVersion(targetVersion)
        }
    }
    return driver
}

private fun SqlDriver.userVersion(): Long =
    executeQuery(null, "PRAGMA user_version", { cursor ->
        QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
    }, 0).value

private fun SqlDriver.setUserVersion(version: Long) {
    execute(null, "PRAGMA user_version = $version", 0)
}

private fun SqlDriver.normalizedSchemaVersion(): Long {
    val version = userVersion()
    return if (version > 0L) version else inferSchemaVersion()
}

private fun SqlDriver.inferSchemaVersion(): Long {
    if (!hasTable("todo")) return 0L

    val todoColumns = tableColumns("todo")
    val listColumns = tableColumns("reminder_list")
    return when {
        "recurrence_frequency" in todoColumns && "recurrence_interval" in todoColumns -> TodoDb.Schema.version
        "updated_at" in todoColumns &&
            "updated_by" in todoColumns &&
            "updated_at" in listColumns &&
            "updated_by" in listColumns &&
            hasTable("outbox") &&
            hasTable("settings") -> 2L
        else -> 1L
    }
}

private fun SqlDriver.hasTable(name: String): Boolean =
    executeQuery(null, "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = '$name'", { cursor ->
        QueryResult.Value(cursor.next().value)
    }, 0).value

private fun SqlDriver.tableColumns(table: String): Set<String> =
    executeQuery(null, "PRAGMA table_info($table)", { cursor ->
        val columns = mutableSetOf<String>()
        while (cursor.next().value) {
            cursor.getString(1)?.let(columns::add)
        }
        QueryResult.Value(columns)
    }, 0).value
