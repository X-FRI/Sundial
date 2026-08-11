package com.myapplication.shared.data

import app.cash.sqldelight.db.SqlDriver

/**
 * 平台相关工厂：创建 SQLDelight 驱动并保证 schema 已就绪。
 *
 * 各平台差异仅在于「驱动类型 + 建库位置 + schema 创建时机」，见各
 * SqlDriver.<platform>.kt 的 actual 实现。
 */
expect fun createSqlDriver(): SqlDriver
