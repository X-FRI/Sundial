package com.myapplication.shared.di

import com.myapplication.shared.data.createSqlDriver

/**
 * Desktop（JVM）平台入口：使用 JDBC SQLite 驱动创建 SqlDriver。
 * 数据库落在用户数据目录，由 createSqlDriver 的 desktop actual 实现决定。
 */
actual fun createAppGraph(): AppGraph = AppGraph(createSqlDriver())
