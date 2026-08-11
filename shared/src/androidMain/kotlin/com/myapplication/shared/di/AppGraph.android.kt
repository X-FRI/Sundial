package com.myapplication.shared.di

import com.myapplication.shared.data.createSqlDriver

/**
 * Android 平台入口：使用 Android SQLite（framework 驱动）创建 SqlDriver。
 * 数据库文件路径等细节封装在 createSqlDriver 的 Android actual 实现中。
 */
actual fun createAppGraph(): AppGraph = AppGraph(createSqlDriver())
