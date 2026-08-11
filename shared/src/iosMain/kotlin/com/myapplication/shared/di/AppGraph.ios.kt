package com.myapplication.shared.di

import com.myapplication.shared.data.createSqlDriver

/**
 * iOS 平台入口：使用 NativeSqliteDriver（调用系统 SQLite C API）创建
 * SqlDriver；数据库路径由 createSqlDriver 的 iosMain actual 实现决定
 * （应用沙盒 Documents 目录）。
 */
actual fun createAppGraph(): AppGraph = AppGraph(createSqlDriver())
