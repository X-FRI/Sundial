package com.myapplication.shared.di

import com.myapplication.shared.data.createSqlDriver

actual fun createAppGraph(): AppGraph = AppGraph(createSqlDriver())
