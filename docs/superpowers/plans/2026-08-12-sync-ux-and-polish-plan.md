# 同步体验与移动端交互完善 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
> 规格：`docs/superpowers/specs/2026-08-12-sync-ux-and-polish-design.md`（先读规格再动手）

**Goal:** 解决「同步时不可用、另一端无反馈」+ 移动端下拉刷新（精美动效）+ 双端同步动画指示 + TODO.md 全部条目（Drawer 详情、同步体验五项、Bug 1–5）。

**验证命令:** `./gradlew :shared:desktopTest --rerun-tasks :androidApp:assembleDebug`（预期全绿；存量 74 测试 + 新增）

**当前 HEAD:** `48eeec5`（注：之后有未推送的 README/TODO 提交，实施前先 `git status` 确认干净）。

**提交策略:** 每 Task 验证后 commit，前缀 `feat(sync-ux):` / `fix(sync-ux):` / `test(sync-ux):` / `docs(sync-ux):`。

---

### Task 1: 单线程 DB 收敛（修复并发根因）

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/data/TodoRepositoryImpl.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/di/AppGraph.kt`

**背景**：`JdbcDriver` 非线程安全（ThreadLocal 事务、无锁）；`Transacter.Transaction.endTransaction$runtime` 为 final，驱动级 synchronized 包装无法拦截提交 → 采用**单线程收敛**：所有 DB 访问只发生在一条专用线程上。

**Step 1: TodoRepositoryImpl 收敛**

- 类内新增：`private val dbDispatcher: CoroutineDispatcher = newSingleThreadContext("sqlite-db")`（构造函数参数，默认值如上；`import kotlinx.coroutines.CoroutineDispatcher`、`kotlinx.coroutines.newSingleThreadContext`）。
- 每个 `suspend fun` 的 DB 主体包 `withContext(dbDispatcher) { ... }`（所有 `guard`/`either` 调用点：`guard { db.todoDbQueries... }` → `guard { withContext(dbDispatcher) { ... } }`；事务命令的 try/catch 块内 DB 调用同样包 `withContext`）。
- 每个 `Flow` 链尾加 `.flowOn(dbDispatcher)`（`observeLists`、`observeAllActive`、`observeByList`、`observeToday`、`observeScheduled`、`observeCompleted`、`observeTrashed`、`observeSubTasks`、`observeTodo`、`search`、`observeOutboxCount`）。
- `asFlow()` 的查询监听线程随之落在 dbDispatcher。

**Step 2: AppGraph 接线**

- `AppGraph` 持有共享 `private val dbDispatcher = newSingleThreadContext("sqlite-db")`，传给 `TodoRepositoryImpl(db, clock, timeZone, loadDeviceId(), dbDispatcher)`。
- `loadDeviceId()`/`loadSyncConfig()` 的 `runBlocking { ... }` 改为 `runBlocking(dbDispatcher) { ... }`。
- 注意：不能重复创建两条单线程（AppGraph 的实例传参优先；TodoRepositoryImpl 默认值仅测试路径使用）。

**Step 3: 验证**

- `./gradlew :shared:compileKotlinDesktop` BUILD SUCCESSFUL；`./gradlew :shared:desktopTest --rerun-tasks` 74 测试全绿（测试构造 `TodoRepositoryImpl(TodoDb(driver))` 走默认 dispatcher，应无需改动；若 `runTest` 卡死/死锁，测试内传入共享 dispatcher 或 `Dispatchers.Unconfined` 处理）。

**Step 4: Commit** `feat(sync-ux): confine all sqlite access to a single thread`

---

### Task 2: material3 依赖 + Sync 图标 + RemSyncIndicator 组件

**Files:**
- Modify: `shared/build.gradle.kts`（commonMain 加 `implementation(compose.material3)`）
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/RemIcons.kt`（IconName.Sync）
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/RemSyncIndicator.kt`

**Step 1: 依赖**：commonMain dependencies 追加 `implementation(compose.material3)`（CMP 1.11.1 映射 material3 1.9.0，缓存已验证存在）。

**Step 2: IconName.Sync**（沿用 24 网格、stroke 1.8 约定）：外圈 `circle(12,12,7.5)` + 顶部实心箭头 `poly(12,3.5, 8.4,7, 15.6,7, filled=true)` + 底部实心箭头 `poly(12,20.5, 8.4,17, 15.6,17, filled=true)`。

**Step 3: RemSyncIndicator.kt**

```kotlin
package com.myapplication.shared.ui.components

// 同步状态指示器：Syncing=旋转双箭头，Synced=对勾，Error=叹号(暂用警告色箭头)，Idle=静态灰箭头
enum class SyncIndicatorState { Idle, Syncing, Synced, Error }

@Composable
fun RemSyncIndicator(
    state: SyncIndicatorState,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
) {
    val colors = LocalRemColors.current
    val tint = when (state) {
        SyncIndicatorState.Syncing -> colors.brand
        SyncIndicatorState.Synced -> colors.success
        SyncIndicatorState.Error -> colors.error
        SyncIndicatorState.Idle -> colors.textLow
    }
    val rotation = if (state == SyncIndicatorState.Syncing) {
        val transition = rememberInfiniteTransition(label = "sync-rotation")
        val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "sync-angle")
        angle
    } else 0f
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        RemIcon(IconName.Sync, tint, Modifier.fillMaxSize().graphicsLayer { rotationZ = rotation })
    }
}
```

imports：`androidx.compose.animation.core.*`（rememberInfiniteTransition/animateFloat/infiniteRepeatable/tween/LinearEasing）、`androidx.compose.ui.draw.rotate` 或 `graphicsLayer`、`LocalRemColors`。

**Step 4: 编译** `./gradlew :shared:compileKotlinDesktop` BUILD SUCCESSFUL。

**Step 5: Commit** `feat(sync-ux): add sync indicator component and material3 dep`

---

### Task 3: pull 端口 + Supabase 全量拉取 + 协调器应用

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/sync/SyncClient.kt`（+pull）
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/data/sync/NoopSyncClient.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/data/sync/SupabaseSyncClient.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/sync/SyncCoordinator.kt`（+pullFromRemote）
- Modify: `shared/src/commonTest/kotlin/com/myapplication/shared/domain/sync/SyncCoordinatorTest.kt`（FakeSyncClient +pull + 新测试）

**Step 1: 端口**：`SyncClient` 追加 `suspend fun pull(): Either<SyncError, List<SyncRow>>`。

**Step 2: Noop**：`override suspend fun pull(): Either<SyncError, List<SyncRow>> = Either.Right(emptyList())`。

**Step 3: SupabaseSyncClient.pull**（全量拉取两张表；`data` 为 String JSON）：

```kotlin
override suspend fun pull(): Either<SyncError, List<SyncRow>> =
    try {
        buildList {
            addAll(pullTable("todo"))
            addAll(pullTable("reminder_list"))
        }.right()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        SyncError.Transport(e.message ?: "拉取失败").left()
    }

private suspend fun pullTable(table: String): List<SyncRow> {
    val result = client.from(table).select { filter { gt("updated_at", 0) } }
    val elements = Json.parseToJsonElement(result.data).jsonArray
    return elements.map { el ->
        val obj = el.jsonObject
        SyncRow(
            seq = 0L,
            table = table,
            rowId = (obj["id"]!!.jsonPrimitive.longOrNull ?: 0L),
            action = SyncAction.UPSERT,
            payload = el.toString(),
            updatedAt = obj["updated_at"]!!.jsonPrimitive.longOrNull ?: 0L,
            updatedBy = obj["updated_by"]?.jsonPrimitive?.contentOrNull ?: "",
        )
    }
}
```

> 注：`select { filter { gt(...) } }` 的 DSL 以编译为准（postgrest-kt 3.7.0 签名：`select(columns: String = ..., requestBuilder: SelectRequestBuilder.() -> Unit): PostgrestResult`；`result.data: String` 已验证）。`jsonPrimitive.longOrNull`/`contentOrNull` 为 kotlinx-serialization JSON 扩展（`import kotlinx.serialization.json.*`）。`filter`/`gt` 在 `SelectRequestBuilder` DSL 内。

**Step 4: SyncCoordinator.pullFromRemote**（复用 applyRemote——LWW 保护 + 自身设备行跳过安全，见规格 §4）：

```kotlin
suspend fun pullFromRemote(): Either<SyncError, Int> = either {
    val rows = client.pull().bind()
    var applied = 0
    rows.forEach { row ->
        applyRemote(row).onRight { applied++ }
    }
    applied
}
```

> 说明：`applyRemote` 逐行已是 `Either`，错误不中断整体（隔离单行失败）；返回成功应用数。

**Step 5: 测试**（SyncCoordinatorTest）：

- `FakeSyncClient` 增加 `var pullResult: List<SyncRow> = emptyList()` + `override suspend fun pull(): Either<SyncError, List<SyncRow>> = Either.Right(pullResult)`。
- 新测试 `pullAppliesRemoteRows`：`pullResult = listOf(row(...), row(...))` → `pullFromRemote()` 返回 2，`repo.appliedUpserts.size == 2`。
- 新测试 `pullSkipsOwnDeviceRows`：`pullResult` 含 `updatedBy = "device-a"` 行 → 该行不进入 appliedUpserts。

**Step 6: 编译 + `:shared:desktopTest` 全绿。**

**Step 7: Commit** `feat(sync-ux): pull port with supabase full fetch and coordinator apply`

---

### Task 4: SyncEngine 体验重构（状态机 + syncNow + 退避 + 隔离 + 真实连接）

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/sync/SyncModels.kt`（SyncStatus +syncing）
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/data/sync/SyncEngine.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/data/sync/SupabaseSyncClient.kt`（push 隔离 + observeConnectionStatus）
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/data/sync/NoopSyncClient.kt`（+observeConnectionStatus）
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/sync/SyncClient.kt`（+observeConnectionStatus 端口）
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/sync/SyncModels.kt`（+observeConnectionStatus 相关无——仅端口）

**Step 1: SyncStatus 增字段**：`val syncing: Boolean = false`（末尾，默认值，兼容全部既有构造点）。

**Step 2: 端口重加 `observeConnectionStatus(): Flow<Boolean>`**（Task 5 审查时删过，现接入真实 Realtime 状态——D3）：
- Noop：`MutableStateFlow(false)`（新实例或共享均可）。
- Supabase：`client.realtime.status.map { it == Realtime.Status.CONNECTED }`（`Realtime.Status` 枚举已 javap 验证；`import io.github.jan.supabase.realtime.Realtime`）。
- 保留 push 成功置 connected 的兜底（realtime 状态滞后时 push 结果先行）。

**Step 3: SyncEngine**：

```kotlin
class SyncEngine(
    private val scope: CoroutineScope,
    private val repository: TodoRepository,
    private val clock: Clock,
) {
    private val _status = MutableStateFlow(SyncStatus.initial)
    val status: StateFlow<SyncStatus> = _status
    private val backoffBaseMs = 2_000L
    private var backoffMs = backoffBaseMs

    private var client: SyncClient = NoopSyncClient()
    private var coordinator: SyncCoordinator? = null
    private var pushJob: Job? = null
    private var remoteJob: Job? = null
    private var statusJob: Job? = null
    private var syncNowJob: Job? = null

    fun configure(newConfig: SyncConfig) {
        stopCurrent()
        _status.value = _status.value.copy(mode = newConfig.mode, connected = false)
        SyncClientFactory.create(newConfig).fold(
            onLeft = { error ->
                client = NoopSyncClient()
                _status.value = SyncStatus(newConfig.mode, false, 0, _status.value.lastSyncAt, when (error) { ... }, syncing = false)
            },
            onRight = { newClient ->
                client = newClient
                if (newConfig.mode == SyncMode.Local) {
                    coordinator = null
                } else {
                    coordinator = SyncCoordinator(repository, newClient, newConfig.deviceId)
                    startPushLoop()
                    startRemoteLoop()
                    startStatusWatchers()
                    syncNow()  // D7 首次启用自动对齐
                }
            },
        )
    }

    /** 立即同步：推本地 + 拉远端；幂等，可重复调用 */
    fun syncNow() {
        if (coordinator == null || syncNowJob?.isActive == true) return
        syncNowJob = scope.launch {
            _status.update { it.copy(syncing = true) }
            val drainResult = runCatching { coordinator?.drainOutbox() }.getOrNull()
            val pullResult = runCatching { coordinator?.pullFromRemote() }.getOrNull()
            // 汇总：drain/pull 任一 Left 或异常 → lastError；成功 → connected=true, lastError=null, lastSyncAt
            val drainFailed = drainResult?.isLeft() == true
            val pullFailed = pullResult?.isLeft() == true
            if (drainFailed || pullFailed) {
                val msg = when {
                    drainFailed -> (drainResult as Either.Left).value.message()
                    else -> (pullResult as Either.Left).value.message()
                }
                _status.update { it.copy(syncing = false, connected = false, lastError = msg) }
            } else {
                val pending = repository.observeOutboxCount().first()
                _status.update {
                    it.copy(
                        syncing = pending > 0,
                        connected = true,
                        pendingCount = pending,
                        lastSyncAt = clock.now().toEpochMilliseconds(),
                        lastError = null,
                    )
                }
            }
        }
    }

    private fun startStatusWatchers() {
        // outbox 计数即时反馈：本地操作后立刻进入同步中动画
        statusJob = scope.launch {
            repository.observeOutboxCount().collect { count ->
                if (count > 0 && !_status.value.syncing) {
                    _status.update { it.copy(syncing = true) }
                }
            }
        }
        // Realtime 真实连接状态（D3）
        statusJob2 = scope.launch {
            client.observeConnectionStatus().collect { connected ->
                _status.update { it.copy(connected = connected) }
            }
        }
    }
    // ... stopCurrent 同步取消 statusJob/statusJob2/syncNowJob，其余逻辑沿用
}
```

**Step 3a: 推送循环加指数退避**（startPushLoop）：

```kotlin
pushJob = scope.launch {
    while (isActive) {
        try {
            when (val result = coordinator?.drainOutbox()) {
                null -> Unit
                is Either.Left -> {
                    _status.update { it.copy(connected = false, lastError = result.value.message(), syncing = false) }
                    backoffMs = (backoffMs * 2).coerceAtMost(30_000L)   // 2s→4s→8s→…→30s 封顶
                }
                is Either.Right -> {
                    backoffMs = backoffBaseMs
                    val pending = repository.observeOutboxCount().first()
                    _status.update {
                        it.copy(
                            connected = true,
                            pendingCount = pending,
                            syncing = pending > 0,
                            lastSyncAt = clock.now().toEpochMilliseconds(),
                            lastError = null,
                        )
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _status.update { it.copy(connected = false, lastError = "同步失败: ${e.message}", syncing = false) }
            backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
        }
        delay(backoffMs)
    }
}
```

**Step 4: push 失败行隔离（Bug 5）**：`pushUpserts` 改为逐行校验：

```kotlin
private suspend fun pushUpserts(rows: List<SyncRow>) {
    rows.groupBy { it.table }.forEach { (table, group) ->
        when (table) {
            "todo" -> client.from("todo").upsert(group.mapNotNull { row ->
                runCatching { Json.decodeFromString<TodoRowDto>(row.payload ?: "") }.getOrNull()
            })
            "reminder_list" -> client.from("reminder_list").upsert(group.mapNotNull { row ->
                runCatching { Json.decodeFromString<ListRowDto>(row.payload ?: "") }.getOrNull()
            })
        }
    }
}
```

> 语义：坏行（payload 缺失/损坏）被跳过，其余正常推送；整批不再因单行失败阻塞。坏行会滞留 outbox 反复重试——接受（配合水位线清理正常行；后续版本可加行级 quarantine）。同时删除原 `IllegalStateException` 抛掷。

**Step 5: pendingCount 失真修正**（观察项）：`configure` 的失败分支 `pendingCount` 保留现值：`_status.value.copy(pendingCount = _status.value.pendingCount, ...)`（即不要重置为 0；上方代码已用 `_status.value.copy` 语义）。

**Step 6: 编译 + desktopTest 全绿**（既有 SyncCoordinatorTest 的 FakeSyncClient 需补 `observeConnectionStatus` 覆盖）。

**Step 7: Commit** `feat(sync-ux): engine syncing state, backoff, row isolation, realtime status`

---

### Task 5: 侧边栏同步状态动画 + 点击立即同步

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/sidebar/Sidebar.kt`
- Modify: `shared/src/commonMain/kotlin/App.kt`（传 onSyncNow）

**Step 1: Sidebar 签名**：`fun Sidebar(mainVm: MainViewModel, syncStatus: SyncStatus = SyncStatus.initial, onSyncNow: (() -> Unit)? = null)`。

**Step 2: 状态行**（现「设置」行）改为：

- 状态点 `Box(6.dp Circle)` 替换为 `RemSyncIndicator(state = syncStatus.phase(), size = 10.dp)`。
- 行 `clickable`：`onClick = { onSyncNow?.invoke() }`（在设置行点击时触发同步；保留跳转设置的入口文本——**决策：设置行保持跳设置，动画指示器所在行新增独立点击目标改为整行「点击同步」？不——为不混淆交互，指示器行即设置行，点击行为：单点进入设置；指示器旁加 12dp 的独立同步按钮（RemIconButton Sync 图标）触发 syncNow**。实现以最小改动为原则：设置行尾部在 `syncSummary` 文本旁加 `RemIconButton(IconName.Sync, "立即同步")`，仅当 `onSyncNow != null` 且 mode != Local 时显示）。
- `syncSummary` 文案逻辑保留。

**Step 3: 相位派生**（放 `SyncModels.kt` 或 UI 层；**决策：UI 层**，`ui/components/RemSyncIndicator.kt` 同文件或 `ui/sync/SyncPhase.kt`）：

```kotlin
// ui/sync/SyncPhase.kt
fun SyncStatus.phase(): SyncIndicatorState = when {
    mode == SyncMode.Local -> SyncIndicatorState.Idle
    syncing -> SyncIndicatorState.Syncing
    lastError != null -> SyncIndicatorState.Error
    connected -> SyncIndicatorState.Synced
    else -> SyncIndicatorState.Idle
}
```

**Step 4: App.kt**：宽屏 `Sidebar(mainVm, syncStatus, onSyncNow = { graph.engine.syncNow() })`。

**Step 5: 编译 + desktopTest。**

**Step 6: Commit** `feat(sync-ux): animated sync indicator and manual sync in sidebar`

---

### Task 6: 设置页同步状态动画

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/SettingsScreen.kt`

**Step 1: StatusCard 改造**：

- 头部行：状态点替换为 `RemSyncIndicator(status.phase(), size = 12.dp)`。
- headline 文案：`syncing → "同步中…"`（品牌色），其余分支沿用（本地模式/已连接/未连接）。
- 「同步中」时显示 spinner 文案行：「正在将本地变更同步到云端…」。
- 其余 StatRow 逻辑不变。

**Step 2: 编译。**

**Step 3: Commit** `feat(sync-ux): syncing animation in settings status card`

---

### Task 7: 移动端下拉刷新（PullToRefreshBox）

**Files:**
- Modify: `shared/src/commonMain/kotlin/App.kt`（窄屏分支）
- （依赖已含：material3 1.9.0 pulltorefresh）

**Step 1: App.kt 窄屏主列表分支**：

```kotlin
else -> {
    Column(Modifier.fillMaxSize().background(colors.bgPrimary)) {
        NarrowTopBar(mainVm)
        PullToRefreshBox(
            isRefreshing = syncStatus.syncing,
            onRefresh = { graph.engine.syncNow() },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            Box(Modifier.fillMaxSize()) {
                TodoListScreen(mainVm, Modifier.fillMaxSize(), showHeader = false)
                TodoFAB(mainVm, Modifier.align(Alignment.BottomEnd).padding(16.dp))
            }
        }
        NarrowBottomNav(mainVm)
    }
}
```

> 说明：`TodoListScreen` 用 LazyColumn（已验证），PullToRefreshBox 通过 nested scroll 拦截下拉；`isRefreshing` 绑定 `syncing`，旋转指示器在 `syncNow()` 期间持续动画，结束后消失。import：`androidx.compose.material3.pulltorefresh.PullToRefreshBox`。

**Step 2: 编译 + 窄窗口手测（`maxWidth < 900dp`）。**

**Step 3: Commit** `feat(sync-ux): pull-to-refresh on narrow list`

---

### Task 8: 移动端抽屉式待办详情（TODO A）

**Files:**
- Modify: `shared/src/commonMain/kotlin/App.kt`（窄屏分支）
- 可能 Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/detail/DetailScreen.kt`（窄屏下无改动则不动）

**Step 1: App.kt 窄屏分支重构**（替换现有 `selectedId != null` 全屏分支）：

```kotlin
else -> {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val detailId = selectedId  // route as? Route.Detail
    LaunchedEffect(detailId) {
        if (detailId != null) drawerState.open() else drawerState.close()
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerState = drawerState, modifier = Modifier.fillMaxHeight().width(340.dp)) {
                if (detailId != null) {
                    DetailScreen(
                        mainVm, graph, detailId,
                        Modifier.fillMaxSize().background(colors.bgPrimary).statusBarsPadding().navigationBarsPadding(),
                    )
                }
            }
        },
        onDismissRequest = { if (drawerState.isOpen) mainVm.back() },
        modifier = Modifier.fillMaxSize(),
        gesturesEnabled = true,
    ) {
        Column(Modifier.fillMaxSize().background(colors.bgPrimary)) {
            NarrowTopBar(mainVm)
            Box(Modifier.weight(1f)) {
                TodoListScreen(mainVm, Modifier.fillMaxSize(), showHeader = false)
                TodoFAB(mainVm, Modifier.align(Alignment.BottomEnd).padding(16.dp))
            }
            NarrowBottomNav(mainVm)
        }
    }
}
```

> 注意：现有 `route == Settings` 分支在最前不变；`when` 顺序：Settings → wide → 窄屏（drawer 化后无需再单列 `selectedId != null` 分支，统一进 else 分支内部处理）。`rememberDrawerState`/`ModalNavigationDrawer`/`ModalDrawerSheet` 来自 material3。`PlatformBackHandler` 保持 `route != Route.Main` 时 `back()`（关闭抽屉语义正确）。抽屉滑出动画为 material3 标准（约 300ms 缓动）。

**Step 2: 验证**：编译 + 窄窗口手测（点开待办 → 抽屉滑出；返回键/Escape → 关闭；遮罩点击 → 关闭；宽屏行为不变）。

**Step 3: Commit** `feat(sync-ux): drawer-style detail on narrow screens`

---

### Task 9: Formatting 时间桶修复（Bug 1+2）

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/util/Formatting.kt`
- Modify: `shared/src/commonTest/kotlin/com/myapplication/shared/util/FormattingTest.kt`

**Step 1: `bucketOf` 重写**（周日问题）：不再用 `8 - isoDayNumber` 的周内日差公式，改为基于「距今天的天数」：

```kotlin
// 目标：周一~周日统一——距今天 0→今天，1→明天，2~6 且目标未过 → 本周X；跨过本周的 2~7 → 周X（下周）；其余 LATER
fun bucketOf(today: LocalDate, date: LocalDate): Bucket {
    val diff = Days.between(today, date).days
    return when {
        diff <= 0 -> if (diff == 0) Bucket.TODAY else Bucket.OVERDUE
        diff == 1 -> Bucket.TOMORROW
        diff <= 6 -> if (date.dayOfWeek > today.dayOfWeek || date.dayOfWeek == today.dayOfWeek) Bucket.THIS_WEEK_DAY(date.dayOfWeek)
                      else Bucket.NEXT_WEEK_DAY(date.dayOfWeek)
        diff <= 7 -> Bucket.NEXT_WEEK_DAY(date.dayOfWeek)
        else -> Bucket.LATER
    }
}
```

> 语义核对：周日（today=周日）看周一（diff=1）→ TOMORROW ✓；周日看周三（diff=3）→ 周三 dayOfWeek(3) < 周日(7) → NEXT_WEEK_DAY「周三」（下周三）✓ 合理；周一（diff=2）→ dayOfWeek(1)>1? 否 → NEXT_WEEK_DAY（下周一）✓。修正 Bug 1（周日今天不再把 diff==1 当本周而丢失）。以现有 `Bucket` 枚举/函数签名为准调整，不破坏调用方。

**Step 2: `formatDueDate` 修正**（Bug 2）：显示「周X」的条件改为 `days in 2..7 && 目标不为今天/明天`（跨周也显示周X）：

```kotlin
// 若 days in 2..7 且不是 OVERDUE/今天/明天 → 显示「周X」；否则沿用现有月日/今天/明天/过期分支
```

> 具体以现有实现结构落地；测试覆盖：周二看下周一 → 「周一」；周二看周一 → 过期或月日（按现有 LATER 语义）。

**Step 3: 测试**：FormattingTest 补：周日今天+3 天 → 下周X；周二看下周一 → 周X 文案。运行 `:shared:desktopTest` 全绿（存量测试若因语义修正失败，先确认新语义正确再更新断言）。

**Step 4: Commit** `fix(sync-ux): correct week bucket boundaries (bugs 1-2)`

---

### Task 10: DateParser 上午/中午边界（Bug 3）

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/util/DateParser.kt`
- Modify: `shared/src/commonTest/kotlin/com/myapplication/shared/util/DateParserTest.kt`

**Step 1: 修复两处**：
- 「上午12点」→ 0 点（`上午/早上` + `h == 12` → `h = 0`）。
- 「中午N点」：`N == 12 → 12`；否则 `N + 12`（中午1点=13点，依口语习惯）；「中午」无数字 → 12。
- 注意：**只改 DateParser.kt 的解析逻辑行，其余不动**（该文件受保护，改动后必须全量测试验证）。

**Step 2: 测试**：`DateParserTest` 补「上午12点 → 00:00」「中午3点 → 15:00」「中午12点 → 12:00」；`:shared:desktopTest` 全绿。

**Step 3: Commit** `fix(sync-ux): date parser noon/am-12 boundary (bug 3)`

---

### Task 11: 软删除父任务过滤（Bug 4）

**Files:**
- Modify: `shared/src/commonMain/sqldelight/com/myapplication/shared/data/TodoDb.sq`（+selectByIdActive）
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/usecase/AddSubTask.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/usecase/AddTodo.kt`
- Modify: `shared/src/commonTest/kotlin/com/myapplication/shared/domain/usecase/AddSubTaskUseCaseTest.kt`（+ 新用例）

**Step 1: TodoDb.sq**：`selectById` 后追加：

```sql
selectByIdActive:
SELECT * FROM todo WHERE id = ? AND is_trashed = 0;
```

**Step 2: AddTodo.kt / AddSubTask.kt**：父任务查询 `selectById` → `selectByIdActive`；查不到（不存在或已软删）时返回既有错误（`TodoError` 具体类型按现有实现——通常为 InboxNotFound 或新增语义；**决策：复用现有「父任务不存在」错误分支，不新增错误类型**）。

**Step 3: 测试**：AddSubTaskUseCaseTest 补「父任务已软删除 → 失败」；运行测试全绿。

**Step 4: Commit** `fix(sync-ux): reject subtasks under trashed parents (bug 4)`

---

### Task 12: 文档 + 最终全量验证

**Files:**
- Modify: `docs/adr/0002-sync-backend.md`（追加：pull 路径、syncing 状态、单线程 DB、退避、行隔离决策）
- Modify: `README.md`（功能特性追加：下拉刷新、同步动画指示）
- Modify: `TODO.md`（勾选 A/B 子项、Bug 1–5；遗留项：pull 增量水位线、行级 quarantine、Realtime 补偿窗口）

**Step 1: ADR 0002 追加「更新（2026-08-12）」小节**，记录 D1–D7 决策。

**Step 2: README 功能特性**追加两条：`下拉刷新`（移动端，精美动效拉取云端最新数据）、`同步动画指示`（操作后即时反馈同步进度，双端）。

**Step 3: TODO.md**：A、B、Bug 1–5 标记完成或按实际情况更新；遗留观察项（pull 增量、quarantine、补偿窗口）写入。

**Step 4: 最终全量验证**：`./gradlew :shared:desktopTest --rerun-tasks :androidApp:assembleDebug` → BUILD SUCCESSFUL，存量 74 + 新增全部通过。

**Step 5: Commit** `docs(sync-ux): update ADR, README, TODO`

---

## Self-Review 清单

**规格覆盖**：
- 线程安全根因（D1）→ Task 1 ✓
- pull 全量拉取（D2）→ Task 3 ✓
- Realtime 真实连接（D3）→ Task 4 ✓
- material3 + 下拉刷新 + 抽屉（D4）→ Task 2/7/8 ✓
- syncing 状态机与动画（D5 + §6）→ Task 4/5/6 ✓
- 首次对齐（D7）→ Task 4 ✓
- 失败行隔离（D6 / Bug 5）→ Task 4 ✓
- TODO A（Drawer）→ Task 8 ✓；TODO B 五项 → Task 3/4 ✓；Bug 1–4 → Task 9/10/11 ✓

**类型一致性**：
- `SyncStatus` 加字段放**末尾**并带默认值（全部既有构造点兼容）
- `SyncClient` 端口加 `pull`/`observeConnectionStatus` → 两个适配器 + 测试 Fake 同步补齐（编译器兜底）
- `phase()` 派生放 UI 层，domain 不新增枚举
- `newSingleThreadContext` 为 KMP 通用 API（JVM/Native 均可用）

**已知风险（编译时验证）**：
- `compose.material3` 版本映射（1.9.0）与 foundation 1.11.1 的二进制兼容性——编译/运行验证
- postgrest `select { filter { gt(...) } }` DSL 具体签名（`result.data: String` 已验证）
- `PullToRefreshBox` 在 CMP desktop 窄窗口的行为（若 desktop 异常则仅在 Android 应用，用 `expect/actual` 包一层——不，优先统一；仅当编译或运行失败再降级）
- `runTest` 与 `newSingleThreadContext` 的配合（Task 1 Step 3 已列处理）
- Drawer 与 `PlatformBackHandler` 的时序（关闭动画 vs route 置 Main——LaunchedEffect 驱动 state，可接受轻微竞态）
