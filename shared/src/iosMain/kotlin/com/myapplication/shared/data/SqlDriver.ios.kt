package com.myapplication.shared.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

/**
 * iOS 驱动：NativeSqliteDriver 自带 schema 创建/迁移（传入 Schema 即可），
 * 数据库文件由平台放到应用沙盒内。
 */
actual fun createSqlDriver(): SqlDriver = NativeSqliteDriver(TodoDb.Schema, "reminders.db")
