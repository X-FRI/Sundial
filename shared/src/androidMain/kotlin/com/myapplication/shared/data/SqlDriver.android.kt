package com.myapplication.shared.data

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

/**
 * Android 驱动：SQLite 文件存在应用私有目录（"reminders.db"）。
 * schema 由 AndroidSqliteDriver 的迁移机制自动创建/升级（传入 Schema 即可），
 * 无需手动建表。
 */
private var appContext: Context? = null

/** 必须在 createSqlDriver() 前调用，保存 applicationContext 供建库使用。 */
fun setAndroidAppContext(context: Context) {
    appContext = context.applicationContext
}

actual fun createSqlDriver(): SqlDriver {
    val context = requireNotNull(appContext) {
        "setAndroidAppContext() must be called before createSqlDriver()"
    }
    return AndroidSqliteDriver(TodoDb.Schema, context, "reminders.db")
}
