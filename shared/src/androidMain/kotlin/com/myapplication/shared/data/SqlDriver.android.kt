package com.myapplication.shared.data

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

private var appContext: Context? = null

fun setAndroidAppContext(context: Context) {
    appContext = context.applicationContext
}

actual fun createSqlDriver(): SqlDriver {
    val context = requireNotNull(appContext) {
        "setAndroidAppContext() must be called before createSqlDriver()"
    }
    return AndroidSqliteDriver(TodoDb.Schema, context, "reminders.db")
}
