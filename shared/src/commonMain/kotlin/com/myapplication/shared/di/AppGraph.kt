package com.myapplication.shared.di

import app.cash.sqldelight.db.SqlDriver
import com.myapplication.shared.data.TodoDb
import com.myapplication.shared.data.TodoRepositoryImpl
import com.myapplication.shared.data.sync.SyncEngine
import com.myapplication.shared.domain.repository.TodoRepository
import com.myapplication.shared.domain.sync.SyncConfig
import com.myapplication.shared.domain.sync.SyncMode
import com.myapplication.shared.domain.usecase.AddSubTaskUseCase
import com.myapplication.shared.domain.usecase.AddTodoUseCase
import com.myapplication.shared.ui.settings.SettingsViewModel
import com.myapplication.shared.util.createDeviceId
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone

class AppGraph(
    driver: SqlDriver,
    val clock: Clock = Clock.System,
    val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val repository: TodoRepository by lazy { TodoRepositoryImpl(TodoDb(driver), clock, timeZone, loadDeviceId()) }
    val addTodo: AddTodoUseCase by lazy { AddTodoUseCase(repository) }
    val addSubTask: AddSubTaskUseCase by lazy { AddSubTaskUseCase(repository) }

    private val engineScope = CoroutineScope(SupervisorJob())

    val engine: SyncEngine by lazy {
        SyncEngine(engineScope, repository, clock).also { it.configure(loadSyncConfig()) }
    }

    val settingsViewModelFactory: () -> SettingsViewModel = {
        SettingsViewModel(repository, engine)
    }

    private fun loadDeviceId(): String {
        val existing = runBlocking { repository.getSetting("sync.deviceId").getOrNull() }
        if (existing != null) return existing
        val id = createDeviceId()
        runBlocking { repository.setSetting("sync.deviceId", id) }
        return id
    }

    private fun loadSyncConfig(): SyncConfig {
        val settings = runBlocking { repository.getSettings().getOrNull() ?: emptyMap() }
        return SyncConfig(
            mode = SyncMode.fromKey(settings["sync.mode"] ?: "local"),
            supabaseUrl = settings["sync.supabase.url"] ?: "",
            supabaseKey = settings["sync.supabase.key"] ?: "",
            sundialUrl = settings["sync.sundial.url"] ?: "",
            deviceId = settings["sync.deviceId"] ?: loadDeviceId(),
        )
    }
}

expect fun createAppGraph(): AppGraph
