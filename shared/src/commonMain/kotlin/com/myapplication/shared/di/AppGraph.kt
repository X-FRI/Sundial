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
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone

/**
 * 应用依赖图（手写 DI，无框架）：集中创建 SQLite 数据库、repository、
 * 用例与同步引擎，并管理它们的生命周期与初始化顺序。
 *
 * 设计要点：
 * - 依赖全部 by lazy 惰性初始化：只有真正用到时才创建，启动路径短，
 *   也避免了构造顺序上的循环依赖；
 * - [loadDeviceId] 是绕开 repository 走裸 db 查询/写入的：
 *   repository 的构造参数需要 deviceId，而 loadSyncConfig 又需要 repository
 *   ——若 loadDeviceId 走 repository 会形成初始化环；
 * - 同步引擎持有独立 [engineScope]（SupervisorJob），子协程失败不会
 *   级联取消图内其他部分；
 * - 平台差异收敛在 [createAppGraph]：各平台 actual 只负责提供 SqlDriver，
 *   图结构本身全平台一致。
 */
class AppGraph(
    driver: SqlDriver,
    val clock: Clock = Clock.System,
    val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    private val db = TodoDb(driver)
    // 所有 SQLite 访问收敛到专用单线程（JdbcDriver 非线程安全，单线程串行化全部 DB 调用）
    private val dbDispatcher = newSingleThreadContext("sqlite-db")
    // repository 构造时即同步读取/生成 deviceId（见 loadDeviceId 说明）
    val repository: TodoRepository by lazy { TodoRepositoryImpl(db, clock, timeZone, loadDeviceId(), dbDispatcher) }
    val addTodo: AddTodoUseCase by lazy { AddTodoUseCase(repository) }
    val addSubTask: AddSubTaskUseCase by lazy { AddSubTaskUseCase(repository) }

    // 引擎专属作用域：与 UI 作用域隔离，引擎内部错误互不影响
    private val engineScope = CoroutineScope(SupervisorJob())

    // 引擎首次被访问时才创建并加载同步配置
    val engine: SyncEngine by lazy {
        SyncEngine(engineScope, repository, clock).also { it.configure(loadSyncConfig()) }
    }

    val settingsViewModelFactory: () -> SettingsViewModel = {
        SettingsViewModel(repository, engine)
    }

    /**
     * 读取或生成设备 ID（持久化于 settings 表 "sync.deviceId"）。
     *
     * 为什么用裸 db 而非 repository：见类注释；写入用事务包裹，
     * 先 updateSetting 再 insertSettingIfMissing，覆盖"旧库已有行"与
     * "全新库无行"两种情况（update 无行不报错）。
     */
    private fun loadDeviceId(): String = runBlocking(dbDispatcher) {
        val existing = db.todoDbQueries.getSetting("sync.deviceId").executeAsOneOrNull()
        if (existing != null) return@runBlocking existing
        val id = createDeviceId()
        db.transaction {
            db.todoDbQueries.updateSetting(id, "sync.deviceId")
            db.todoDbQueries.insertSettingIfMissing("sync.deviceId", id, "sync.deviceId")
        }
        id
    }

    /**
     * 从 settings 表装配同步配置。
     *
     * 注意：这里用 runBlocking 同步等待 repository（惰性字段首次访问会触发
     * loadDeviceId 等初始化）；仅当 engine 首次被创建时执行一次，且调用方
     * 通常在后台协程中触发，属可接受的折衷。配置缺失的键回落默认值
     * （mode=local，url/key 空串），deviceId 兜底再走一次 loadDeviceId。
     */
    private fun loadSyncConfig(): SyncConfig {
        val settings = runBlocking(dbDispatcher) { repository.getSettings().getOrNull() ?: emptyMap() }
        return SyncConfig(
            mode = SyncMode.fromKey(settings["sync.mode"] ?: "local"),
            supabaseUrl = settings["sync.supabase.url"] ?: "",
            supabaseKey = settings["sync.supabase.key"] ?: "",
            sundialUrl = settings["sync.sundial.url"] ?: "",
            deviceId = settings["sync.deviceId"] ?: loadDeviceId(),
        )
    }
}

/** 平台入口：各平台 actual 负责创建 SqlDriver（见各 AppGraph.<platform>.kt）。 */
expect fun createAppGraph(): AppGraph
