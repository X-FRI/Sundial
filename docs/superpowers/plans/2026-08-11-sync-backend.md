# 同步后端（Supabase + 预留 Sundial-Server）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 Sundial 增加多端实时同步：应用内配置页选择同步模式（本地 / Supabase / 未来 Sundial-Server），Supabase 模式实现行级 LWW 双向实时同步，`SyncClient` 端口为自建服务器预留适配器空子。

**Architecture:** 三层： (1) schema 扩展——`todo`/`reminder_list` 加 `updated_at`/`updated_by` 列，新增 `outbox`（本地变更日志，payload=完整行 JSON）与 `settings`（key-value 配置）表；(2) domain/sync 端口——`SyncClient`（push 行 + 远端变化 Flow）、`SyncMode`/`SyncError`/`SyncStatus`、`SyncCoordinator`（drain outbox→push；远端事件→SQL 层 LWW 应用，避免 ping-pong）；(3) 适配器——`NoopSyncClient`（本地模式）、`SupabaseSyncClient`（PostgREST upsert/delete + Realtime 订阅），工厂按配置切换；Sundial-Server 分支预留。同步状态经 `SyncEngine` 的 StateFlow 呈现于设置页与侧边栏。

**Tech Stack:** supabase-kt BOM 3.7.0（postgrest-kt + realtime-kt，KMP 支持 JVM/Android/iOS）+ Ktor 3.x 引擎（desktop: cio，iOS: darwin，android: okhttp）+ kotlinx-serialization 插件/JSON。合并语义：行级 LWW（`updated_at` 大者胜，delete 带 `updated_at` 守卫），SQL 层实现，不引入 CRDT。身份：无登录，RLS 放开（文档标注安全边界）。

**验证命令:** `./gradlew :shared:desktopTest --rerun-tasks :androidApp:assembleDebug`（预期 BUILD SUCCESSFUL，65 存量 + 新增测试全绿）

**当前 HEAD:** `02e751c`。**本计划允许修改**：`TodoDb.sq`（上一计划禁止，本计划解除）。**禁止修改**：`DateParser.kt`、`Formatting.kt`、`docs/adr/0001-arrow-functional-core.md`。

**提交策略:** 每个 Task 末验证通过后 commit，前缀 `feat(sync):` / `test(sync):` / `docs(sync):`。

---

### Task 1: 依赖 + Schema 扩展

**Files:**
- Modify: `gradle.properties`（版本属性）
- Modify: `shared/build.gradle.kts`（serialization 插件、supabase、ktor engines、json 依赖）
- Modify: `shared/src/commonMain/sqldelight/com/myapplication/shared/data/TodoDb.sq`（schema 扩展）

- [ ] **Step 1: gradle.properties 追加版本属性**

```properties
supabase.version=3.7.0
ktor.version=3.2.0
serialization.version=1.9.0
```

- [ ] **Step 2: shared/build.gradle.kts 追加插件与依赖**

plugins 块加：
```kotlin
kotlin("plugin.serialization").version(kotlinVersion)
```

版本属性读取（与 arrowVersion 并列）：
```kotlin
val supabaseVersion = findProperty("supabase.version") as String
val ktorVersion = findProperty("ktor.version") as String
val serializationVersion = findProperty("serialization.version") as String
```

commonMain dependencies 追加：
```kotlin
implementation(platform("io.github.jan-tennert.supabase:bom:$supabaseVersion"))
implementation("io.github.jan-tennert.supabase:postgrest-kt")
implementation("io.github.jan-tennert.supabase:realtime-kt")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
```

androidMain 追加：
```kotlin
implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
```

> **修正（质量审查发现）**：androidMain 的 SQLDelight 驱动保持 `app.cash.sqldelight:android-driver`（`sqlite-android` 构件不存在）。旧 Android（API 24–28）系统 SQLite 缺 `ON CONFLICT DO UPDATE` 的兼容性问题改由 **UPSERT-free 查询**解决（见 known-risks），而非换驱动。

desktopMain 追加：
```kotlin
implementation("io.ktor:ktor-client-cio:$ktorVersion")
```

iosMain 追加：
```kotlin
implementation("io.ktor:ktor-client-darwin:$ktorVersion")
```

> 注：supabase-kt 3.x 要求 Ktor ≥ 3.0；kotlinx-serialization 版本需与 Kotlin 2.4.10 匹配（1.9.x）。若 `realtime-kt` 与 ktor 3.2.0 有兼容冲突，退至 ktor 3.1.x。

- [ ] **Step 3: TodoDb.sq schema 扩展**

`TodoDb.sq` 全量替换为：

```sql
import kotlin.Boolean;

CREATE TABLE reminder_list (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  color_key TEXT NOT NULL,
  position INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  updated_by TEXT NOT NULL DEFAULT ''
);

CREATE TABLE todo (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  list_id INTEGER NOT NULL REFERENCES reminder_list(id),
  title TEXT NOT NULL,
  note TEXT NOT NULL DEFAULT '',
  due_date INTEGER,
  is_completed INTEGER AS Boolean NOT NULL DEFAULT 0,
  completed_at INTEGER,
  is_trashed INTEGER AS Boolean NOT NULL DEFAULT 0,
  trashed_at INTEGER,
  parent_id INTEGER,
  sort_position REAL NOT NULL DEFAULT 0,
  flag INTEGER AS Boolean NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  updated_by TEXT NOT NULL DEFAULT ''
);

CREATE TABLE outbox (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  seq INTEGER NOT NULL,
  table_name TEXT NOT NULL,
  row_id INTEGER NOT NULL,
  action TEXT NOT NULL,
  payload TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL
);
CREATE INDEX idx_outbox_seq ON outbox(seq);

CREATE TABLE settings (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL
);

CREATE INDEX idx_todo_list_trash ON todo(list_id, is_trashed);
CREATE INDEX idx_todo_trash_due ON todo(is_trashed, due_date);
CREATE INDEX idx_todo_parent ON todo(parent_id);

insertList:
INSERT INTO reminder_list(name, color_key, position, created_at, updated_at, updated_by)
VALUES (?, ?, ?, ?, ?, ?);

selectLists:
SELECT * FROM reminder_list ORDER BY position, id;

deleteList:
DELETE FROM reminder_list WHERE id = ?;

trashTodosInList:
UPDATE todo SET is_trashed = 1, trashed_at = ?, updated_at = ?, updated_by = ? WHERE list_id = ? AND is_trashed = 0;

insertTodo:
INSERT INTO todo(list_id, title, note, due_date, is_completed, is_trashed, parent_id, sort_position, flag, created_at, updated_at, updated_by)
VALUES (?, ?, ?, ?, 0, 0, ?, ?, ?, ?, ?, ?);

selectAllActive:
SELECT * FROM todo WHERE is_trashed = 0 ORDER BY is_completed, due_date IS NULL, due_date, sort_position, id;

selectByList:
SELECT * FROM todo WHERE is_trashed = 0 AND list_id = ? ORDER BY is_completed, due_date IS NULL, due_date, sort_position, id;

selectWithDueDate:
SELECT * FROM todo WHERE is_trashed = 0 AND due_date IS NOT NULL ORDER BY due_date;

selectToday:
SELECT * FROM todo WHERE is_trashed = 0 AND due_date >= ? AND due_date < ? ORDER BY due_date;

selectCompleted:
SELECT * FROM todo WHERE is_trashed = 0 AND is_completed = 1 ORDER BY completed_at DESC;

selectTrashed:
SELECT * FROM todo WHERE is_trashed = 1 ORDER BY trashed_at DESC;

selectSubTasks:
SELECT * FROM todo WHERE is_trashed = 0 AND parent_id = ? ORDER BY sort_position, id;

selectById:
SELECT * FROM todo WHERE id = ?;

selectByIdLast:
SELECT * FROM todo ORDER BY id DESC LIMIT 1;

selectByIdForList:
SELECT * FROM reminder_list WHERE id = ?;

searchTodos:
SELECT * FROM todo WHERE is_trashed = 0 AND (title LIKE ? ESCAPE '\' OR note LIKE ? ESCAPE '\')
ORDER BY is_completed, due_date IS NULL, due_date;

updateCompleted:
UPDATE todo SET is_completed = ?, completed_at = ?, updated_at = ?, updated_by = ? WHERE id = ?;

updateTitle:
UPDATE todo SET title = ?, updated_at = ?, updated_by = ? WHERE id = ?;

updateNote:
UPDATE todo SET note = ?, updated_at = ?, updated_by = ? WHERE id = ?;

updateDueDate:
UPDATE todo SET due_date = ?, updated_at = ?, updated_by = ? WHERE id = ?;

moveToList:
UPDATE todo SET list_id = ?, updated_at = ?, updated_by = ? WHERE id = ?;

trashTodo:
UPDATE todo SET is_trashed = 1, trashed_at = ?, updated_at = ?, updated_by = ? WHERE id = ?;

restoreTodo:
UPDATE todo SET is_trashed = 0, trashed_at = NULL, updated_at = ?, updated_by = ? WHERE id = ?;

deleteTodo:
DELETE FROM todo WHERE id = ?;

updateFlag:
UPDATE todo SET flag = ?, updated_at = ?, updated_by = ? WHERE id = ?;

-- 同步专用查询（SQL 层 LWW）--

upsertTodo:
INSERT INTO todo(id, list_id, title, note, due_date, is_completed, completed_at, is_trashed, trashed_at, parent_id, sort_position, flag, created_at, updated_at, updated_by)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
ON CONFLICT(id) DO UPDATE SET
  list_id = excluded.list_id,
  title = excluded.title,
  note = excluded.note,
  due_date = excluded.due_date,
  is_completed = excluded.is_completed,
  completed_at = excluded.completed_at,
  is_trashed = excluded.is_trashed,
  trashed_at = excluded.trashed_at,
  parent_id = excluded.parent_id,
  sort_position = excluded.sort_position,
  flag = excluded.flag,
  updated_at = excluded.updated_at,
  updated_by = excluded.updated_by
WHERE todo.updated_at <= excluded.updated_at;

upsertList:
INSERT INTO reminder_list(id, name, color_key, position, created_at, updated_at, updated_by)
VALUES (?, ?, ?, ?, ?, ?, ?)
ON CONFLICT(id) DO UPDATE SET
  name = excluded.name,
  color_key = excluded.color_key,
  position = excluded.position,
  updated_at = excluded.updated_at,
  updated_by = excluded.updated_by
WHERE reminder_list.updated_at <= excluded.updated_at;

deleteTodoIfOlder:
DELETE FROM todo WHERE id = ? AND updated_at <= ?;

deleteListIfOlder:
DELETE FROM reminder_list WHERE id = ? AND updated_at <= ?;

-- outbox --

insertOutbox:
INSERT INTO outbox(seq, table_name, row_id, action, payload, created_at)
VALUES (?, ?, ?, ?, ?, ?);

selectOutbox:
SELECT * FROM outbox ORDER BY seq LIMIT ?;

deleteOutboxUpTo:
DELETE FROM outbox WHERE seq <= ?;

selectOutboxCount:
SELECT COUNT(*) FROM outbox;

selectOutboxMaxSeq:
SELECT COALESCE(MAX(seq), 0) FROM outbox;

-- settings --

getSetting:
SELECT value FROM settings WHERE key = ?;

setSetting:
INSERT INTO settings(key, value) VALUES (?, ?)
ON CONFLICT(key) DO UPDATE SET value = excluded.value;

selectAllSettings:
SELECT * FROM settings;
```

> 注：开发期（v0.0.1 未发布）直接改 schema，不生成 .sqm 迁移文件。`upsertTodo` 的 `ON CONFLICT ... WHERE todo.updated_at <= excluded.updated_at` 即 SQL 层 LWW（旧数据不覆盖新数据）。

- [ ] **Step 4: 编译验证**

Run: `./gradlew :shared:compileKotlinDesktop`
Expected: BUILD SUCCESSFUL（依赖下载 + schema 重新生成 + 现有调用方报错：**预期失败**——`insertList`/`insertTodo`/各 update 语句参数增多导致 `TodoRepositoryImpl` 编译失败，Task 3 修复；本步骤仅验证依赖解析与 schema 生成无语法错误。若依赖解析失败（supabase/ktor 版本冲突）在此排查。）

- [ ] **Step 5: Commit**

```bash
git add gradle.properties shared/build.gradle.kts shared/src/commonMain/sqldelight
git commit -m "feat(sync): add supabase/ktor deps and sync schema (outbox, settings, updated_at)"
```

---

### Task 2: 同步领域模型 + 端口扩展

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/sync/SyncModels.kt`
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/sync/SyncClient.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/repository/TodoRepository.kt`（追加同步方法）

- [ ] **Step 1: 创建 SyncModels.kt**

```kotlin
package com.myapplication.shared.domain.sync

import kotlinx.serialization.Serializable

sealed interface SyncMode {
    data object Local : SyncMode
    data object Supabase : SyncMode
    data object SundialServer : SyncMode

    companion object {
        fun fromKey(key: String): SyncMode = when (key) {
            "supabase" -> Supabase
            "sundial" -> SundialServer
            else -> Local
        }
    }
}

enum class SyncAction { UPSERT, DELETE }

@Serializable
data class TodoRowDto(
    val id: Long,
    val listId: Long,
    val title: String,
    val note: String,
    val dueDate: Long?,
    val isCompleted: Boolean,
    val completedAt: Long?,
    val isTrashed: Boolean,
    val trashedAt: Long?,
    val parentId: Long?,
    val sortPosition: Double,
    val flag: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val updatedBy: String,
)

@Serializable
data class ListRowDto(
    val id: Long,
    val name: String,
    val colorKey: String,
    val position: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val updatedBy: String,
)

data class SyncRow(
    val table: String,
    val rowId: Long,
    val action: SyncAction,
    val payload: String?,
    val updatedAt: Long,
    val updatedBy: String,
)

data class SyncConfig(
    val mode: SyncMode,
    val supabaseUrl: String = "",
    val supabaseKey: String = "",
    val sundialUrl: String = "",
    val deviceId: String = "",
)

sealed interface SyncError {
    data class Transport(val message: String) : SyncError
    data object NotConfigured : SyncError
}

data class SyncStatus(
    val mode: SyncMode,
    val connected: Boolean,
    val pendingCount: Int,
    val lastSyncAt: Long?,
    val lastError: String?,
) {
    companion object {
        val initial = SyncStatus(SyncMode.Local, false, 0, null, null)
    }
}
```

- [ ] **Step 2: 创建 SyncClient.kt（端口——为 Sundial-Server 预留的 seam）**

```kotlin
package com.myapplication.shared.domain.sync

import arrow.core.Either
import kotlinx.coroutines.flow.Flow

interface SyncClient {
    suspend fun push(rows: List<SyncRow>): Either<SyncError, Unit>
    fun observeRemoteChanges(): Flow<SyncRow>
    fun observeConnectionStatus(): Flow<Boolean>
    suspend fun close()
}
```

- [ ] **Step 3: TodoRepository 追加同步/配置方法**

`TodoRepository.kt` 接口尾部追加：

```kotlin
    // Sync support
    suspend fun readOutbox(limit: Int): Either<TodoError, List<SyncRow>>
    suspend fun clearOutbox(upToSeq: Long): Either<TodoError, Unit>
    fun observeOutboxCount(): Flow<Int>
    suspend fun applyRemoteUpsert(row: TodoRowDto): Either<TodoError, Unit>
    suspend fun applyRemoteUpsertList(row: ListRowDto): Either<TodoError, Unit>
    suspend fun applyRemoteDelete(table: String, rowId: Long, updatedAt: Long): Either<TodoError, Unit>
    suspend fun getSetting(key: String): Either<TodoError, String?>
    suspend fun setSetting(key: String, value: String): Either<TodoError, Unit>
    suspend fun getSettings(): Either<TodoError, Map<String, String>>
```

> 注：`applyRemote*` 直接写库**不产生 outbox 记录**（避免 ping-pong 回环）；LWW 由 SQL 语句（upsertTodo/deleteTodoIfOlder）保障，不需要读取本地行比较。

- [ ] **Step 4: 编译验证**

Run: `./gradlew :shared:compileKotlinDesktop`
Expected: 依赖/模型编译通过；`TodoRepositoryImpl` 仍报缺方法（预期，Task 3 修复）。

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/domain/sync shared/src/commonMain/kotlin/com/myapplication/shared/domain/repository
git commit -m "feat(sync): sync domain models, SyncClient port, repository sync methods"
```

---

### Task 3: TodoRepositoryImpl 双写 outbox + 同步写路径

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/data/TodoRepositoryImpl.kt`（全量重写）

**要点**：构造函数加 `deviceId: String`；所有本地写命令在事务内双写 outbox（payload=完整新行 JSON，`@Serializable` DTO 经 kotlinx-serialization）；新增同步专用方法；settings 方法。

- [ ] **Step 1: 全量重写 TodoRepositoryImpl**

```kotlin
package com.myapplication.shared.data

import app.cash.sqldelight.coroutines.asFlow
import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.domain.repository.TodoRepository
import com.myapplication.shared.domain.sync.ListRowDto
import com.myapplication.shared.domain.sync.SyncAction
import com.myapplication.shared.domain.sync.SyncRow
import com.myapplication.shared.domain.sync.TodoRowDto
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val outboxJson = Json

class TodoRepositoryImpl(
    private val db: TodoDb,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val deviceId: String = "local",
) : TodoRepository {

    private fun Todo.toDomain() = TodoItem(
        id = id,
        listId = list_id,
        title = title,
        note = note,
        dueDate = due_date?.let { Instant.fromEpochMilliseconds(it) },
        isCompleted = is_completed,
        flag = flag,
        completedAt = completed_at?.let { Instant.fromEpochMilliseconds(it) },
        isTrashed = is_trashed,
        trashedAt = trashed_at?.let { Instant.fromEpochMilliseconds(it) },
        parentId = parent_id,
        sortPosition = sort_position,
        createdAt = Instant.fromEpochMilliseconds(created_at),
    )

    private fun SelectWithDueDate.toDomain() = TodoItem(
        id = id,
        listId = list_id,
        title = title,
        note = note,
        dueDate = Instant.fromEpochMilliseconds(due_date),
        isCompleted = is_completed,
        flag = flag,
        completedAt = completed_at?.let { Instant.fromEpochMilliseconds(it) },
        isTrashed = is_trashed,
        trashedAt = trashed_at?.let { Instant.fromEpochMilliseconds(it) },
        parentId = parent_id,
        sortPosition = sort_position,
        createdAt = Instant.fromEpochMilliseconds(created_at),
    )

    private fun Reminder_list.toDomain() = TodoList(
        id = id,
        name = name,
        colorKey = color_key,
        position = position.toInt(),
        createdAt = Instant.fromEpochMilliseconds(created_at),
    )

    private fun Todo.toDto() = TodoRowDto(
        id = id,
        listId = list_id,
        title = title,
        note = note,
        dueDate = due_date,
        isCompleted = is_completed,
        completedAt = completed_at,
        isTrashed = is_trashed,
        trashedAt = trashed_at,
        parentId = parent_id,
        sortPosition = sort_position,
        flag = flag,
        createdAt = created_at,
        updatedAt = updated_at,
        updatedBy = updated_by,
    )

    private fun Reminder_list.toDto() = ListRowDto(
        id = id,
        name = name,
        colorKey = color_key,
        position = position.toInt(),
        createdAt = created_at,
        updatedAt = updated_at,
        updatedBy = updated_by,
    )

    private fun Outbox.toSyncRow() = SyncRow(
        seq = id,
        table = table_name,
        rowId = row_id,
        action = if (action == "DELETE") SyncAction.DELETE else SyncAction.UPSERT,
        payload = payload,
        updatedAt = created_at,
        updatedBy = "",
    )

    private val now: Long get() = clock.now().toEpochMilliseconds()

    private inline fun <A> guard(block: () -> A): Either<TodoError, A> =
        try {
            block().right()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TodoError.Persistence(e.message ?: "数据库操作失败").left()
        }

    // ---- outbox 追加（事务内调用） ----

    private fun appendOutbox(table: String, rowId: Long, action: SyncAction, payload: String?) {
        val seq = db.todoDbQueries.selectOutboxMaxSeq().executeAsOne() + 1
        db.todoDbQueries.insertOutbox(
            seq, table, rowId, if (action == SyncAction.DELETE) "DELETE" else "UPSERT",
            payload ?: "", now,
        )
    }

    private fun appendTodoOutbox(row: TodoRowDto, action: SyncAction = SyncAction.UPSERT) {
        appendOutbox("todo", row.id, action, if (action == SyncAction.DELETE) null else outboxJson.encodeToString(row))
    }

    private fun appendListOutbox(row: ListRowDto, action: SyncAction = SyncAction.UPSERT) {
        appendOutbox("reminder_list", row.id, action, if (action == SyncAction.DELETE) null else outboxJson.encodeToString(row))
    }

    // ---- 查询（与既有实现一致） ----

    override fun observeLists(): Flow<List<TodoList>> =
        db.todoDbQueries.selectLists().asFlow().map { it.executeAsList() }.map { lists -> lists.map { it.toDomain() } }

    override fun observeAllActive(): Flow<List<TodoItem>> =
        db.todoDbQueries.selectAllActive().asFlow().map { it.executeAsList() }.map { todos -> todos.map { it.toDomain() } }

    override fun observeByList(listId: Long): Flow<List<TodoItem>> =
        db.todoDbQueries.selectByList(listId).asFlow().map { it.executeAsList() }.map { todos -> todos.map { it.toDomain() } }

    override fun observeToday(): Flow<List<TodoItem>> {
        val today = clock.now().toLocalDateTime(timeZone).date
        val start = today.atStartOfDayIn(timeZone).toEpochMilliseconds()
        val end = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone).toEpochMilliseconds()
        return db.todoDbQueries.selectToday(start, end).asFlow().map { it.executeAsList() }.map { todos -> todos.map { it.toDomain() } }
    }

    override fun observeScheduled(): Flow<List<TodoItem>> =
        db.todoDbQueries.selectWithDueDate().asFlow().map { it.executeAsList() }.map { todos -> todos.map { it.toDomain() } }

    override fun observeCompleted(): Flow<List<TodoItem>> =
        db.todoDbQueries.selectCompleted().asFlow().map { it.executeAsList() }.map { todos -> todos.map { it.toDomain() } }

    override fun observeTrashed(): Flow<List<TodoItem>> =
        db.todoDbQueries.selectTrashed().asFlow().map { it.executeAsList() }.map { todos -> todos.map { it.toDomain() } }

    override fun observeSubTasks(parentId: Long): Flow<List<TodoItem>> =
        db.todoDbQueries.selectSubTasks(parentId).asFlow().map { it.executeAsList() }.map { todos -> todos.map { it.toDomain() } }

    override fun observeTodo(id: Long): Flow<TodoItem?> =
        db.todoDbQueries.selectById(id).asFlow().map { it.executeAsOneOrNull() }.map { it?.toDomain() }

    override fun search(query: String): Flow<List<TodoItem>> {
        val escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val pattern = "%$escaped%"
        return db.todoDbQueries.searchTodos(pattern, pattern).asFlow().map { it.executeAsList() }
            .map { todos -> todos.map { it.toDomain() } }
    }

    override suspend fun findById(id: Long): Either<TodoError, TodoItem?> =
        guard { db.todoDbQueries.selectById(id).executeAsOneOrNull()?.toDomain() }

    // ---- 命令（双写 outbox） ----

    override suspend fun ensureInbox(): Either<TodoError, Long> = either {
        val lists = guard { db.todoDbQueries.selectLists().executeAsList() }.bind()
        if (lists.isEmpty()) {
            try {
                db.transaction {
                    db.todoDbQueries.insertList("收件箱", "blue", 0, now, now, deviceId)
                    val row = db.todoDbQueries.selectLists().executeAsList().first()
                    appendListOutbox(row.toDto())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                raise(TodoError.Persistence(e.message ?: "初始化收件箱失败"))
            }
        }
        guard { db.todoDbQueries.selectLists().executeAsList().firstOrNull()?.id }.bind()
            ?: raise(TodoError.InboxNotFound)
    }

    override suspend fun addList(name: String, colorKey: String): Either<TodoError, Unit> = either {
        try {
            db.transaction {
                val position = db.todoDbQueries.selectLists().executeAsList().size
                db.todoDbQueries.insertList(name, colorKey, position.toLong(), now, now, deviceId)
                val row = db.todoDbQueries.selectLists().executeAsList().last()
                appendListOutbox(row.toDto())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            raise(TodoError.Persistence(e.message ?: "添加列表失败"))
        }
    }

    override suspend fun deleteList(listId: Long): Either<TodoError, Unit> = either {
        try {
            db.transaction {
                val affected = db.todoDbQueries.selectByList(listId).executeAsList()
                db.todoDbQueries.trashTodosInList(now, now, deviceId, listId)
                affected.forEach { todo ->
                    val updated = db.todoDbQueries.selectById(todo.id).executeAsOne()
                    appendTodoOutbox(updated.toDto())
                }
                val list = db.todoDbQueries.selectByIdForList(listId)
                db.todoDbQueries.deleteList(listId)
                if (list != null) appendListOutbox(list.toDto(), SyncAction.DELETE)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            raise(TodoError.Persistence(e.message ?: "删除列表失败"))
        }
    }

    override suspend fun insertTodo(
        listId: Long,
        title: String,
        note: String,
        dueDate: Instant?,
        parentId: Long?,
        flag: Boolean,
    ): Either<TodoError, Unit> = either {
        try {
            db.transaction {
                db.todoDbQueries.insertTodo(listId, title, note, dueDate?.toEpochMilliseconds(), parentId, 0.0, flag, now, now, deviceId)
                val row = db.todoDbQueries.selectByIdLast().executeAsOne()
                appendTodoOutbox(row.toDto())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            raise(TodoError.Persistence(e.message ?: "添加待办失败"))
        }
    }

    override suspend fun setCompleted(id: Long, completed: Boolean): Either<TodoError, Unit> = either {
        try {
            db.transaction {
                db.todoDbQueries.updateCompleted(completed, if (completed) now else null, now, deviceId, id)
                val row = db.todoDbQueries.selectById(id).executeAsOne()
                appendTodoOutbox(row.toDto())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            raise(TodoError.Persistence(e.message ?: "更新状态失败"))
        }
    }

    override suspend fun setFlag(id: Long, flag: Boolean): Either<TodoError, Unit> = either {
        try {
            db.transaction {
                db.todoDbQueries.updateFlag(flag, now, deviceId, id)
                val row = db.todoDbQueries.selectById(id).executeAsOne()
                appendTodoOutbox(row.toDto())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            raise(TodoError.Persistence(e.message ?: "更新旗标失败"))
        }
    }

    override suspend fun setTitle(id: Long, title: String): Either<TodoError, Unit> = either {
        try {
            db.transaction {
                db.todoDbQueries.updateTitle(title, now, deviceId, id)
                val row = db.todoDbQueries.selectById(id).executeAsOne()
                appendTodoOutbox(row.toDto())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            raise(TodoError.Persistence(e.message ?: "更新标题失败"))
        }
    }

    override suspend fun setNote(id: Long, note: String): Either<TodoError, Unit> = either {
        try {
            db.transaction {
                db.todoDbQueries.updateNote(note, now, deviceId, id)
                val row = db.todoDbQueries.selectById(id).executeAsOne()
                appendTodoOutbox(row.toDto())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            raise(TodoError.Persistence(e.message ?: "更新备注失败"))
        }
    }

    override suspend fun setDueDate(id: Long, dueDate: Instant?): Either<TodoError, Unit> = either {
        try {
            db.transaction {
                db.todoDbQueries.updateDueDate(dueDate?.toEpochMilliseconds(), now, deviceId, id)
                val row = db.todoDbQueries.selectById(id).executeAsOne()
                appendTodoOutbox(row.toDto())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            raise(TodoError.Persistence(e.message ?: "更新日期失败"))
        }
    }

    override suspend fun moveToList(id: Long, listId: Long): Either<TodoError, Unit> = either {
        try {
            db.transaction {
                db.todoDbQueries.moveToList(listId, now, deviceId, id)
                val row = db.todoDbQueries.selectById(id).executeAsOne()
                appendTodoOutbox(row.toDto())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            raise(TodoError.Persistence(e.message ?: "移动列表失败"))
        }
    }

    override suspend fun trash(id: Long): Either<TodoError, Unit> = either {
        try {
            db.transaction {
                db.todoDbQueries.trashTodo(now, now, deviceId, id)
                val row = db.todoDbQueries.selectById(id).executeAsOne()
                appendTodoOutbox(row.toDto())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            raise(TodoError.Persistence(e.message ?: "移入垃圾箱失败"))
        }
    }

    override suspend fun restore(id: Long): Either<TodoError, Unit> = either {
        try {
            db.transaction {
                db.todoDbQueries.restoreTodo(now, deviceId, id)
                val row = db.todoDbQueries.selectById(id).executeAsOne()
                appendTodoOutbox(row.toDto())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            raise(TodoError.Persistence(e.message ?: "恢复待办失败"))
        }
    }

    override suspend fun deleteForever(id: Long): Either<TodoError, Unit> = either {
        try {
            db.transaction {
                val row = db.todoDbQueries.selectById(id).executeAsOneOrNull()
                db.todoDbQueries.deleteTodo(id)
                if (row != null) appendTodoOutbox(row.toDto(), SyncAction.DELETE)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            raise(TodoError.Persistence(e.message ?: "彻底删除失败"))
        }
    }

    // ---- 同步专用 ----

    override suspend fun readOutbox(limit: Int): Either<TodoError, List<SyncRow>> =
        guard { db.todoDbQueries.selectOutbox(limit.toLong()).executeAsList().map { it.toSyncRow() } }

    override suspend fun clearOutbox(upToSeq: Long): Either<TodoError, Unit> = guard {
        db.todoDbQueries.deleteOutboxUpTo(upToSeq)
    }

    override fun observeOutboxCount(): Flow<Int> =
        db.todoDbQueries.selectOutboxCount().asFlow().map { it.executeAsOne().toInt() }

    override suspend fun applyRemoteUpsert(row: TodoRowDto): Either<TodoError, Unit> = guard {
        db.todoDbQueries.upsertTodo(
            row.id, row.listId, row.title, row.note, row.dueDate, row.isCompleted, row.completedAt,
            row.isTrashed, row.trashedAt, row.parentId, row.sortPosition, row.flag,
            row.createdAt, row.updatedAt, row.updatedBy,
        )
    }

    override suspend fun applyRemoteUpsertList(row: ListRowDto): Either<TodoError, Unit> = guard {
        db.todoDbQueries.upsertList(row.id, row.name, row.colorKey, row.position.toLong(), row.createdAt, row.updatedAt, row.updatedBy)
    }

    override suspend fun applyRemoteDelete(table: String, rowId: Long, updatedAt: Long): Either<TodoError, Unit> = guard {
        when (table) {
            "todo" -> db.todoDbQueries.deleteTodoIfOlder(rowId, updatedAt)
            "reminder_list" -> db.todoDbQueries.deleteListIfOlder(rowId, updatedAt)
            else -> Unit
        }
    }

    override suspend fun getSetting(key: String): Either<TodoError, String?> =
        guard { db.todoDbQueries.getSetting(key).executeAsOneOrNull()?.value }

    override suspend fun setSetting(key: String, value: String): Either<TodoError, Unit> = guard {
        db.todoDbQueries.setSetting(key, value)
    }

    override suspend fun getSettings(): Either<TodoError, Map<String, String>> =
        guard { db.todoDbQueries.selectAllSettings().executeAsList().associate { it.key to it.value } }
}
```

> 注：本实现需要 `TodoDb.sq` 中补充两个查询——`selectByIdLast`（`SELECT * FROM todo ORDER BY id DESC LIMIT 1;`）与 `selectByIdForList`（`SELECT * FROM reminder_list WHERE id = ?;`），已在 Task 1 Step 3 的 schema 中提供。若生成的查询方法名与预期不符（如参数顺序），按编译错误修正调用。

- [ ] **Step 2: 编译验证（desktop 与 test）**

Run: `./gradlew :shared:compileKotlinDesktop`
Expected: BUILD SUCCESSFUL。
Run: `./gradlew :shared:compileTestKotlinDesktop`
Expected: 编译失败——`MainViewModelTest`/`TodoRepositoryImplTest` 构造 `TodoRepositoryImpl(TodoDb(driver))` 仍合法（deviceId 有默认值），但 `FakeTodoRepository` 缺新接口方法。此失败预期，Task 5 修复测试夹具。

> 注：若 Step 1 代码中的 `selectByIdLast`/`selectByIdForList` 命名与 SQLDelight 实际生成不符，本步骤会暴露，修正 .sq 或调用后重跑。若 `Outbox`/`Settings` 生成类型名不符（如 `Setting`），同理修正。

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/data shared/src/commonMain/sqldelight
git commit -m "feat(sync): dual-write outbox on all commands, remote apply with SQL LWW"
```

---

### Task 4: 同步适配器（Noop / Supabase / 工厂）

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/data/sync/NoopSyncClient.kt`
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/data/sync/SupabaseSyncClient.kt`
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/data/sync/SyncClientFactory.kt`

- [ ] **Step 1: 创建 NoopSyncClient.kt**

```kotlin
package com.myapplication.shared.data.sync

import arrow.core.Either
import arrow.core.right
import com.myapplication.shared.domain.sync.SyncClient
import com.myapplication.shared.domain.sync.SyncError
import com.myapplication.shared.domain.sync.SyncRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

class NoopSyncClient : SyncClient {
    override suspend fun push(rows: List<SyncRow>): Either<SyncError, Unit> = Unit.right()
    override fun observeRemoteChanges(): Flow<SyncRow> = flow {}
    override fun observeConnectionStatus(): Flow<Boolean> = MutableStateFlow(false)
    override suspend fun close() = Unit
}
```

- [ ] **Step 2: 创建 SupabaseSyncClient.kt**

```kotlin
package com.myapplication.shared.data.sync

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.myapplication.shared.domain.sync.ListRowDto
import com.myapplication.shared.domain.sync.SyncAction
import com.myapplication.shared.domain.sync.SyncClient
import com.myapplication.shared.domain.sync.SyncError
import com.myapplication.shared.domain.sync.SyncRow
import com.myapplication.shared.domain.sync.TodoRowDto
import io.github.jan_tennert.supabase.SupabaseClient
import io.github.jan_tennert.supabase.createSupabaseClient
import io.github.jan_tennert.supabase.postgrest.from
import io.github.jan_tennert.supabase.postgrest.postgrest
import io.github.jan_tennert.supabase.postgrest.query.Columns
import io.github.jan_tennert.supabase.postgrest.query.Order
import io.github.jan_tennert.supabase.realtime.Realtime
import io.github.jan_tennert.supabase.realtime.PostgresChangeAction
import io.github.jan_tennert.supabase.realtime.PostgresChangeFilter
import io.github.jan_tennert.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class SupabaseSyncClient(
    url: String,
    key: String,
    private val deviceId: String,
) : SyncClient {

    private val client: SupabaseClient = createSupabaseClient(url, key) {
        install(Postgrest)
        install(Realtime)
    }

    private val connectionStatus = MutableStateFlow(false)

    override suspend fun push(rows: List<SyncRow>): Either<SyncError, Unit> =
        try {
            rows.groupBy { it.action }.forEach { (action, group) ->
                when (action) {
                    SyncAction.UPSERT -> pushUpserts(group)
                    SyncAction.DELETE -> pushDeletes(group)
                }
            }
            Unit.right()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SyncError.Transport(e.message ?: "同步失败").left()
        }

    private suspend fun pushUpserts(rows: List<SyncRow>) {
        rows.groupBy { it.table }.forEach { (table, group) ->
            when (table) {
                "todo" -> client.from("todo").upsert(group.map { Json.decodeFromString<TodoRowDto>(it.payload ?: "") })
                "reminder_list" -> client.from("reminder_list").upsert(group.map { Json.decodeFromString<ListRowDto>(it.payload ?: "") })
            }
        }
    }

    private suspend fun pushDeletes(rows: List<SyncRow>) {
        rows.forEach { row ->
            client.from(row.table).delete { filter { eq("id", row.rowId) } }
        }
    }

    override fun observeRemoteChanges(): Flow<SyncRow> =
        client.postgresChangeFlow<PostgresChangeFilter.Database> {
            schema = "public"
        }.map { change ->
            val table = change.table
            val rowId = when (change.action) {
                PostgresChangeAction.DELETE -> change.oldRecord?.get("id")?.toString()?.toLong() ?: 0L
                else -> change.record?.get("id")?.toString()?.toLong() ?: 0L
            }
            val updatedBy = change.record?.get("updated_by")?.toString() ?: ""
            val updatedAt = (change.record?.get("updated_at") as? Number)?.toLong()
                ?: (change.oldRecord?.get("updated_at") as? Number)?.toLong()
                ?: 0L
            SyncRow(
                table = table,
                rowId = rowId,
                action = if (change.action == PostgresChangeAction.DELETE) SyncAction.DELETE else SyncAction.UPSERT,
                payload = if (change.action == PostgresChangeAction.DELETE) null else change.record.toString(),
                updatedAt = updatedAt,
                updatedBy = updatedBy,
            )
        }.filter { it.updatedBy != deviceId }

    override fun observeConnectionStatus(): Flow<Boolean> = connectionStatus

    override suspend fun close() {
        runCatching { client.close() }
    }
}
```

> 注：supabase-kt 3.x API 细节（`postgresChangeFlow` 过滤器类型、`record` 类型为 `JsonObject`、`client.close()` 存在性）以实际编译为准；若个别 API 名称不符，调整到等价调用（目标是：订阅 public schema 全部表的 INSERT/UPDATE/DELETE，产出 `SyncRow`，过滤本设备）。`filter` 需 import `kotlinx.coroutines.flow.filter`。连接状态：Realtime 的 `connectionStatus` 在 3.x 中若不可直接订阅，则退化为 push 成功时置 true、失败置 false（MVP 允许简化，在代码注释中说明——不，本仓库无注释惯例，改为在计划此处说明）。

- [ ] **Step 3: 创建 SyncClientFactory.kt**

```kotlin
package com.myapplication.shared.data.sync

import com.myapplication.shared.domain.sync.SyncClient
import com.myapplication.shared.domain.sync.SyncConfig
import com.myapplication.shared.domain.sync.SyncError
import com.myapplication.shared.domain.sync.SyncMode
import arrow.core.Either
import arrow.core.left

object SyncClientFactory {
    fun create(config: SyncConfig): Either<SyncError, SyncClient> = when (config.mode) {
        SyncMode.Local -> Either.Right(NoopSyncClient())
        SyncMode.Supabase -> {
            if (config.supabaseUrl.isBlank() || config.supabaseKey.isBlank()) {
                SyncError.NotConfigured.left()
            } else {
                Either.Right(SupabaseSyncClient(config.supabaseUrl, config.supabaseKey, config.deviceId))
            }
        }
        SyncMode.SundialServer -> SyncError.NotConfigured.left()
    }
}
```

> 注：`SyncMode.SundialServer` 分支为未来 Sundial-Server 项目预留——届时在此返回 `SundialServerSyncClient(config.sundialUrl, ...)`，其余代码零改动。

- [ ] **Step 4: 编译验证**

Run: `./gradlew :shared:compileKotlinDesktop`
Expected: BUILD SUCCESSFUL（supabase-kt API 若有偏差，按编译错误修正至等价行为）。

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/data/sync
git commit -m "feat(sync): noop and supabase SyncClient adapters with factory seam"
```

---

### Task 5: SyncCoordinator + SyncEngine

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/sync/SyncCoordinator.kt`
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/data/sync/SyncEngine.kt`

- [ ] **Step 1: 创建 SyncCoordinator.kt（领域层纯编排，注入客户端）**

Create `shared/src/commonMain/kotlin/com/myapplication/shared/domain/sync/SyncCoordinator.kt`：

```kotlin
package com.myapplication.shared.domain.sync

import arrow.core.Either
import arrow.core.raise.either
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.repository.TodoRepository
import kotlinx.serialization.json.Json

class SyncCoordinator(
    private val repository: TodoRepository,
    private val client: SyncClient,
    private val deviceId: String,
) {

    suspend fun drainOutbox(): Either<SyncError, Int> = either {
        val rows = repository.readOutbox(100).mapLeftToSync().bind()
        if (rows.isEmpty()) return@either 0
        client.push(rows).bind()
        repository.clearOutbox(rows.last().seq).bind()
        rows.size
    }

    suspend fun applyRemote(row: SyncRow): Either<SyncError, Unit> = either {
        if (row.updatedBy == deviceId) return@either
        when (row.action) {
            SyncAction.UPSERT -> when (row.table) {
                "todo" -> repository.applyRemoteUpsert(Json.decodeFromString<TodoRowDto>(row.payload ?: "")).bind()
                "reminder_list" -> repository.applyRemoteUpsertList(Json.decodeFromString<ListRowDto>(row.payload ?: "")).bind()
                else -> Unit
            }
            SyncAction.DELETE -> repository.applyRemoteDelete(row.table, row.rowId, row.updatedAt).bind()
        }
    }

    private fun Either<TodoError, List<SyncRow>>.mapLeftToSync(): Either<SyncError, List<SyncRow>> =
        mapLeft { SyncError.Transport(it.message ?: "本地读取失败") }
}
```

**前置修正（必须先做）**：`drainOutbox` 的 `clearOutbox(seq)` 需要 `SyncRow` 携带 seq：
1. `domain/sync/SyncModels.kt` 中 `SyncRow` 增加字段 `val seq: Long`（构造参数第一位）。
2. `TodoRepositoryImpl` 中 `Outbox.toSyncRow()` 的 `seq = id`（outbox 表 id 自增与 seq 单调一致，`clearOutbox(seq)` 删除 `seq <= ?` 的记录）。
3. `Task 7` 测试中的 `row(...)` 辅助函数第一个参数即为 seq。

- [ ] **Step 2: 创建 SyncEngine.kt（生命周期编排 + 状态）**

```kotlin
package com.myapplication.shared.data.sync

import com.myapplication.shared.domain.repository.TodoRepository
import com.myapplication.shared.domain.sync.SyncClient
import com.myapplication.shared.domain.sync.SyncConfig
import com.myapplication.shared.domain.sync.SyncCoordinator
import com.myapplication.shared.domain.sync.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlin.time.Clock

class SyncEngine(
    private val scope: CoroutineScope,
    private val repository: TodoRepository,
    private val clock: Clock,
) {
    private val _status = MutableStateFlow(SyncStatus.initial)
    val status: StateFlow<SyncStatus> = _status

    private var config: SyncConfig = SyncConfig(com.myapplication.shared.domain.sync.SyncMode.Local)
    private var client: SyncClient = NoopSyncClient()
    private var coordinator: SyncCoordinator? = null
    private var pushJob: kotlinx.coroutines.Job? = null
    private var remoteJob: kotlinx.coroutines.Job? = null

    fun configure(newConfig: SyncConfig) {
        config = newConfig
        stopCurrent()
        val created = SyncClientFactory.create(newConfig)
        created.fold(
            onLeft = { error ->
                client = NoopSyncClient()
                _status.value = SyncStatus(newConfig.mode, false, 0, _status.value.lastSyncAt, "配置无效")
            },
            onRight = { newClient ->
                client = newClient
                coordinator = SyncCoordinator(repository, newClient, newConfig.deviceId)
                startPushLoop()
                startRemoteLoop()
            },
        )
    }

    private fun stopCurrent() {
        pushJob?.cancel()
        remoteJob?.cancel()
        pushJob = null
        remoteJob = null
        scope.launch { runCatching { client.close() } }
        client = NoopSyncClient()
        coordinator = null
    }

    private fun startPushLoop() {
        pushJob = scope.launch {
            while (isActive) {
                val count = coordinator?.drainOutbox()
                count?.fold(
                    onLeft = { err ->
                        _status.value = _status.value.copy(
                            connected = false,
                            lastError = (err as? com.myapplication.shared.domain.sync.SyncError.Transport)?.message ?: "同步失败",
                        )
                    },
                    onRight = { pushed ->
                        if (pushed > 0) {
                            _status.value = _status.value.copy(
                                connected = true,
                                pendingCount = repository.observeOutboxCount().first().toInt(),
                                lastSyncAt = clock.now().toEpochMilliseconds(),
                                lastError = null,
                            )
                        }
                    },
                )
                delay(2_000)
            }
        }
    }

    private fun startRemoteLoop() {
        remoteJob = scope.launch {
            client.observeRemoteChanges().collect { row ->
                coordinator?.applyRemote(row)?.fold(
                    onLeft = { _status.value = _status.value.copy(lastError = "应用远端变更失败") },
                    onRight = { _status.value = _status.value.copy(pendingCount = repository.observeOutboxCount().first().toInt()) },
                )
            }
        }
    }
}
```

> 注：`fold` 为 `Either` 成员；`delay`/`isActive` 需 import `kotlinx.coroutines.*`（delay、isActive、Job）；`first()` 需 import `kotlinx.coroutines.flow.first`。`observeOutboxCount()` 每次状态更新会触发 SQL 查询（数据量小，可接受）。`SyncStatus` 更新还需 `repository.observeOutboxCount().first()` 收集——若想更简洁，可在 push 成功时直接 `pendingCount = max(0, 当前 - pushed)`；实现时二选一，保持行为正确即可。

- [ ] **Step 3: 编译验证**

Run: `./gradlew :shared:compileKotlinDesktop`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/domain/sync shared/src/commonMain/kotlin/com/myapplication/shared/data/sync
git commit -m "feat(sync): SyncCoordinator LWW orchestration and SyncEngine lifecycle"
```

---

### Task 6: 配置页 + 同步状态 UI

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/SettingsViewModel.kt`
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/SettingsScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/main/MainViewModel.kt`（Route.Settings）
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/sidebar/Sidebar.kt`（设置入口 + 状态行）
- Modify: `shared/src/commonMain/kotlin/App.kt`（Settings 路由渲染）
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/di/AppGraph.kt`（settings/engine 暴露）

- [ ] **Step 1: AppGraph 扩展**

`AppGraph.kt` 追加（构造不变，暴露同步设施）：

```kotlin
    val settingsViewModelFactory: () -> SettingsViewModel = {
        SettingsViewModel(repository, engine)
    }
    val engine: SyncEngine by lazy {
        SyncEngine(engineScope, repository, clock).also { it.configure(loadSyncConfig()) }
    }
    private val engineScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())
    private val json = kotlinx.serialization.json.Json

    private fun loadSyncConfig(): SyncConfig {
        val settings = repository.getSettings().getOrElse { emptyMap() }
        return SyncConfig(
            mode = SyncMode.fromKey(settings["sync.mode"] ?: "local"),
            supabaseUrl = settings["sync.supabase.url"] ?: "",
            supabaseKey = settings["sync.supabase.key"] ?: "",
            sundialUrl = settings["sync.sundial.url"] ?: "",
            deviceId = settings["sync.deviceId"] ?: generateDeviceId(),
        )
    }

    private fun generateDeviceId(): String {
        val id = kotlin.uuid.Uuid.random().toString()
        kotlinx.coroutines.runBlocking { repository.setSetting("sync.deviceId", id) }
        return id
    }
```

> 注：`kotlin.uuid.Uuid.random()` 若需 `@OptIn(ExperimentalUuidApi::class)` 则加注解；若在 common 不可用，改用 expect/actual `createDeviceId()`（desktop/android 用 `java.util.UUID`，ios 用 `NSUUID`）。

- [ ] **Step 2: 创建 SettingsViewModel.kt**

```kotlin
package com.myapplication.shared.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.onLeft
import com.myapplication.shared.data.sync.SyncEngine
import com.myapplication.shared.domain.repository.TodoRepository
import com.myapplication.shared.domain.sync.SyncConfig
import com.myapplication.shared.domain.sync.SyncMode
import com.myapplication.shared.domain.sync.SyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsForm(
    val mode: SyncMode = SyncMode.Local,
    val supabaseUrl: String = "",
    val supabaseKey: String = "",
    val sundialUrl: String = "",
)

class SettingsViewModel(
    private val repository: TodoRepository,
    private val engine: SyncEngine,
) : ViewModel() {

    val syncStatus: StateFlow<SyncStatus> = engine.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncStatus.initial)

    private val _form = MutableStateFlow(SettingsForm())
    val form: StateFlow<SettingsForm> = _form

    init {
        viewModelScope.launch {
            val settings = repository.getSettings().onLeft { return@launch }.getOrElse { emptyMap() }
            _form.value = SettingsForm(
                mode = SyncMode.fromKey(settings["sync.mode"] ?: "local"),
                supabaseUrl = settings["sync.supabase.url"] ?: "",
                supabaseKey = settings["sync.supabase.key"] ?: "",
                sundialUrl = settings["sync.sundial.url"] ?: "",
            )
        }
    }

    fun setMode(mode: SyncMode) {
        _form.value = _form.value.copy(mode = mode)
    }

    fun setSupabaseUrl(value: String) {
        _form.value = _form.value.copy(supabaseUrl = value)
    }

    fun setSupabaseKey(value: String) {
        _form.value = _form.value.copy(supabaseKey = value)
    }

    fun save() {
        val f = _form.value
        if (f.mode == SyncMode.Supabase && (f.supabaseUrl.isBlank() || f.supabaseKey.isBlank())) return
        viewModelScope.launch {
            val settings = mapOf(
                "sync.mode" to when (f.mode) {
                    SyncMode.Local -> "local"
                    SyncMode.Supabase -> "supabase"
                    SyncMode.SundialServer -> "sundial"
                },
                "sync.supabase.url" to f.supabaseUrl.trim(),
                "sync.supabase.key" to f.supabaseKey.trim(),
                "sync.sundial.url" to f.sundialUrl.trim(),
            )
            settings.forEach { (k, v) -> repository.setSetting(k, v).onLeft { } }
            val deviceId = repository.getSetting("sync.deviceId").getOrElse { null }
                ?: run {
                    val id = kotlin.uuid.Uuid.random().toString()
                    repository.setSetting("sync.deviceId", id)
                    id
                }
            engine.configure(
                SyncConfig(
                    mode = f.mode,
                    supabaseUrl = f.supabaseUrl.trim(),
                    supabaseKey = f.supabaseKey.trim(),
                    sundialUrl = f.sundialUrl.trim(),
                    deviceId = deviceId,
                ),
            )
        }
    }
}
```

- [ ] **Step 3: 创建 SettingsScreen.kt**

```kotlin
package com.myapplication.shared.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.sync.SyncMode
import com.myapplication.shared.ui.components.RemButton
import com.myapplication.shared.ui.components.RemTextField
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemType

@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val colors = LocalRemColors.current
    val form by vm.form.collectAsState()
    val status by vm.syncStatus.collectAsState()

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.text.BasicText("同步设置", style = RemType.title18.copy(color = colors.textHigh))
            Spacer(Modifier.weight(1f))
            RemButton("返回", onClick = onBack)
        }
        Spacer(Modifier.height(16.dp))
        ModeOption("本地模式（不同步）", SyncMode.Local, form.mode, vm::setMode)
        ModeOption("Supabase 云端", SyncMode.Supabase, form.mode, vm::setMode)
        ModeOption("自建服务器（即将支持）", SyncMode.SundialServer, form.mode, vm::setMode) {
            androidx.compose.foundation.text.BasicText("Sundial-Server 模式开发中，敬请期待", style = RemType.text12.copy(color = colors.textLow))
        }
        Spacer(Modifier.height(12.dp))
        if (form.mode == SyncMode.Supabase) {
            RemTextField(value = form.supabaseUrl, onValueChange = vm::setSupabaseUrl, placeholder = "Supabase URL（https://xxx.supabase.co）", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            RemTextField(value = form.supabaseKey, onValueChange = vm::setSupabaseKey, placeholder = "Supabase anon key", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        if (form.mode == SyncMode.SundialServer) {
            androidx.compose.foundation.text.BasicText(
                "请先在客户端配置页选择此模式（服务端尚未发布）",
                style = RemType.text12.copy(color = colors.textLow),
            )
        }
        Spacer(Modifier.height(16.dp))
        RemButton("保存", onClick = vm::save)
        Spacer(Modifier.height(24.dp))
        androidx.compose.foundation.text.BasicText("同步状态", style = RemType.label12.copy(color = colors.textLow))
        Spacer(Modifier.height(4.dp))
        androidx.compose.foundation.text.BasicText(
            "模式：${when (status.mode) { SyncMode.Local -> "本地"; SyncMode.Supabase -> "Supabase"; SyncMode.SundialServer -> "Sundial-Server" }}" +
                " · 连接：${if (status.connected) "已连接" else "未连接"}" +
                " · 待同步：${status.pendingCount}" +
                (status.lastSyncAt?.let { " · 上次同步：$it" } ?: ""),
            style = RemType.text12.copy(color = colors.textNormal),
        )
        status.lastError?.let {
            Spacer(Modifier.height(4.dp))
            androidx.compose.foundation.text.BasicText("错误：$it", style = RemType.text12.copy(color = colors.error))
        }
    }
}

@Composable
private fun ModeOption(
    label: String,
    mode: SyncMode,
    current: SyncMode,
    onSelect: (SyncMode) -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    val colors = LocalRemColors.current
    val selected = mode == current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(mode) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.text.BasicText(
            (if (selected) "◉ " else "○ ") + label,
            style = RemType.text14.copy(color = if (selected) colors.brand else colors.textNormal),
        )
    }
    extra?.invoke()
}
```

- [ ] **Step 4: 路由与入口**

`MainViewModel.kt`：`Route` 追加 `data object Settings : Route`；`openSettings()` 设 `route.value = Route.Settings`；`back()` 保持（回 Main）。

`App.kt` AppRoot：在 `wide` 分支的 Row 中、`when` 渲染处追加——在 `BoxWithConstraints` 的 `when` 中新增分支（放在现有三个分支前）：
```kotlin
        when {
            route == Route.Settings -> SettingsScreen(
                viewModel { graph.settingsViewModelFactory() },
                onBack = mainVm::back,
            )
            wide -> { ...现有... }
        }
```
同时 `when { wide -> ... }` 内 Detail/列表逻辑不变；窄屏下 Settings 同样全屏显示。`import com.myapplication.shared.ui.settings.SettingsScreen`、`import androidx.lifecycle.viewmodel.compose.viewModel`（已有）。

`Sidebar.kt`：底部追加设置入口（现有 Sidebar 结构末尾），调用 `mainVm.openSettings()`，并显示 engine 状态摘要（`graph.engine.status` 经 mainVm 暴露或直接在 Sidebar 收集——MVP：MainViewModel 暴露 `val syncStatus: StateFlow<SyncStatus> = engine.status...`，`MainViewModel` 构造追加 `engine: SyncEngine` 参数）。Sidebar 状态行：
```kotlin
Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
    BasicText(
        when (syncStatus.mode) { SyncMode.Local -> "本地模式" else -> (if (syncStatus.connected) "已同步" else "同步中断") + if (syncStatus.pendingCount > 0) " · 待同步 ${syncStatus.pendingCount}" else "" },
        style = RemType.text12.copy(color = colors.textLow),
    )
}
```
（具体插入位置由实现者按 Sidebar 现有布局放置；入口按钮复用现有 RemButton 或文本点击。）

- [ ] **Step 5: 编译验证**

Run: `./gradlew :shared:compileKotlinDesktop`
Expected: BUILD SUCCESSFUL（`SettingsScreen` 用到 `colors.error`/`colors.brand`——确认 DesignTokens 中存在；`viewModel { graph.settingsViewModelFactory() }` 为工厂 lambda，类型需匹配 `viewModel` 的 `() -> VM` 签名）。

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain
git commit -m "feat(sync): settings screen with sync mode config and status display"
```

---

### Task 7: 测试（夹具扩展 + 同步行为测试 + 存量适配）

**Files:**
- Modify: `shared/src/commonTest/kotlin/com/myapplication/shared/test/FakeTodoRepository.kt`（追加同步方法）
- Create: `shared/src/commonTest/kotlin/com/myapplication/shared/domain/sync/SyncCoordinatorTest.kt`
- Modify: `shared/src/desktopTest/kotlin/com/myapplication/shared/data/TodoRepositoryImplTest.kt`（新增 outbox/双写断言）

- [ ] **Step 1: FakeTodoRepository 追加同步方法**

追加到 fake 类中（返回类型与 port 一致）：

```kotlin
    val outboxState = MutableStateFlow<List<SyncRow>>(emptyList())
    val settingsState = MutableStateFlow<Map<String, String>>(emptyMap())
    var appliedUpserts = mutableListOf<TodoRowDto>()
    var appliedDeletes = mutableListOf<Pair<String, Long>>()

    override suspend fun readOutbox(limit: Int): Either<TodoError, List<SyncRow>> =
        Either.Right(outboxState.value.take(limit))
    override suspend fun clearOutbox(upToSeq: Long): Either<TodoError, Unit> {
        outboxState.value = outboxState.value.filter { it.seq > upToSeq }
        return Either.Right(Unit)
    }
    override fun observeOutboxCount(): Flow<Int> = outboxState.map { it.size }
    override suspend fun applyRemoteUpsert(row: TodoRowDto): Either<TodoError, Unit> {
        appliedUpserts += row
        return Either.Right(Unit)
    }
    override suspend fun applyRemoteUpsertList(row: ListRowDto): Either<TodoError, Unit> = Either.Right(Unit)
    override suspend fun applyRemoteDelete(table: String, rowId: Long, updatedAt: Long): Either<TodoError, Unit> {
        appliedDeletes += table to rowId
        return Either.Right(Unit)
    }
    override suspend fun getSetting(key: String): Either<TodoError, String?> =
        Either.Right(settingsState.value[key])
    override suspend fun setSetting(key: String, value: String): Either<TodoError, Unit> {
        settingsState.value = settingsState.value + (key to value)
        return Either.Right(Unit)
    }
    override suspend fun getSettings(): Either<TodoError, Map<String, String>> =
        Either.Right(settingsState.value)
```

并在 `insertTodo` 成功路径追加 outbox 记录：
```kotlin
        outboxState.value = outboxState.value + SyncRow(
            seq = outboxState.value.size.toLong() + 1,
            table = "todo",
            rowId = item.id,
            action = SyncAction.UPSERT,
            payload = "",
            updatedAt = 0L,
            updatedBy = "",
        )
```

- [ ] **Step 2: 创建 SyncCoordinatorTest.kt**

```kotlin
package com.myapplication.shared.domain.sync

import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import arrow.core.leftOrNull
import arrow.core.rightOrNull
import com.myapplication.shared.test.FakeTodoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FakeSyncClient : SyncClient {
    val pushed = mutableListOf<List<SyncRow>>()
    var failPush = false
    val remote = MutableStateFlow<List<SyncRow>>(emptyList())

    override suspend fun push(rows: List<SyncRow>): Either<SyncError, Unit> {
        if (failPush) return Left(SyncError.Transport("network down"))
        pushed += rows
        return Right(Unit)
    }

    override fun observeRemoteChanges(): Flow<SyncRow> =
        flow { remote.value.forEach { emit(it) } }

    override suspend fun close() = Unit
}

class SyncCoordinatorTest {

    private fun row(
        seq: Long = 1,
        table: String = "todo",
        rowId: Long = 1,
        action: SyncAction = SyncAction.UPSERT,
        payload: String? = null,
        updatedAt: Long = 100,
        updatedBy: String = "device-b",
    ) = SyncRow(seq, table, rowId, action, payload, updatedAt, updatedBy)

    @Test
    fun drainPushesRowsAndClearsOutbox() = runTest {
        val repo = FakeTodoRepository()
        repo.outboxState.value = listOf(row(seq = 1, rowId = 10), row(seq = 2, rowId = 11))
        val client = FakeSyncClient()
        val coordinator = SyncCoordinator(repo, client, deviceId = "device-a")
        val result = coordinator.drainOutbox()
        assertTrue(result.isRight())
        assertEquals(2, result.rightOrNull())
        assertEquals(listOf(10L, 11L), client.pushed.single().map { it.rowId })
        assertTrue(repo.outboxState.value.isEmpty())
    }

    @Test
    fun drainKeepsOutboxWhenPushFails() = runTest {
        val repo = FakeTodoRepository()
        repo.outboxState.value = listOf(row(seq = 1))
        val client = FakeSyncClient().apply { failPush = true }
        val coordinator = SyncCoordinator(repo, client, "device-a")
        val result = coordinator.drainOutbox()
        assertEquals(SyncError.Transport("network down"), result.leftOrNull())
        assertEquals(1, repo.outboxState.value.size)
    }

    @Test
    fun applyRemoteSkipsOwnDevice() = runTest {
        val repo = FakeTodoRepository()
        val coordinator = SyncCoordinator(repo, FakeSyncClient(), "device-a")
        val result = coordinator.applyRemote(row(updatedBy = "device-a"))
        assertTrue(result.isRight())
        assertTrue(repo.appliedUpserts.isEmpty())
    }

    @Test
    fun applyRemoteUpsertTodoDecodesPayload() = runTest {
        val repo = FakeTodoRepository()
        val coordinator = SyncCoordinator(repo, FakeSyncClient(), "device-a")
        val payload = kotlinx.serialization.json.Json.encodeToString(
            TodoRowDto(1, 1, "远程", "", null, false, null, false, null, null, 0.0, false, 0, 200, "device-b"),
        )
        val result = coordinator.applyRemote(row(payload = payload, updatedAt = 200))
        assertTrue(result.isRight())
        assertEquals("远程", repo.appliedUpserts.single().title)
    }

    @Test
    fun applyRemoteDeleteDelegates() = runTest {
        val repo = FakeTodoRepository()
        val coordinator = SyncCoordinator(repo, FakeSyncClient(), "device-a")
        val result = coordinator.applyRemote(row(action = SyncAction.DELETE, rowId = 7, updatedAt = 300))
        assertTrue(result.isRight())
        assertEquals("todo" to 7L, repo.appliedDeletes.single())
    }
}
```

- [ ] **Step 3: TodoRepositoryImplTest 追加双写断言**

新增 3 个测试（追加到现有 15 个之后）：

```kotlin
    @Test
    fun insertTodoWritesOutboxWithPayload() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        assertTrue(repo.insertTodo(inbox, "双写", "", null, null, false).isRight())
        val outbox = repo.readOutbox(10).rightOrNull()!!
        assertEquals(1, outbox.size)
        assertEquals("todo", outbox.single().table)
        assertEquals(SyncAction.UPSERT, outbox.single().action)
        val dto = Json.decodeFromString<TodoRowDto>(outbox.single().payload!!)
        assertEquals("双写", dto.title)
        assertEquals(inbox, dto.listId)
    }

    @Test
    fun deleteForeverWritesDeleteOp() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        repo.insertTodo(inbox, "要删的", "", null, null, false)
        val item = repo.observeAllActive().first().first()
        assertTrue(repo.deleteForever(item.id).isRight())
        val outbox = repo.readOutbox(10).rightOrNull()!!
        assertEquals(SyncAction.DELETE, outbox.last().action)
        assertEquals(item.id, outbox.last().rowId)
    }

    @Test
    fun applyRemoteUpsertObeysLww() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        val fresh = TodoRowDto(1, inbox, "新", "", null, false, null, false, null, null, 0.0, false, 0, 900, "remote")
        assertTrue(repo.applyRemoteUpsert(fresh).isRight())
        val stale = fresh.copy(title = "旧", updatedAt = 800)
        assertTrue(repo.applyRemoteUpsert(stale).isRight())
        assertEquals("新", repo.observeTodo(1).first()?.title)
    }
```

（新增 imports：`arrow.core.rightOrNull`、`com.myapplication.shared.domain.sync.TodoRowDto`、`com.myapplication.shared.domain.sync.SyncAction`、`kotlinx.serialization.json.Json`。）

- [ ] **Step 4: 全量验证**

Run: `./gradlew :shared:desktopTest --rerun-tasks :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL，65 存量 + 5 coordinator + 3 双写 = 73 测试全绿。

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonTest shared/src/desktopTest
git commit -m "test(sync): coordinator LWW orchestration and outbox dual-write tests"
```

---

### Task 8: 文档（Supabase 建表 SQL + ADR）+ 最终验证

**Files:**
- Create: `docs/sync-setup.md`
- Create: `docs/adr/0002-sync-backend.md`

- [ ] **Step 1: 创建 docs/sync-setup.md**

Supabase 项目初始化 SQL（用户在其 Supabase SQL Editor 执行）：

```sql
create table if not exists public.reminder_list (
  id bigint primary key,
  name text not null,
  color_key text not null,
  position bigint not null default 0,
  created_at bigint not null,
  updated_at bigint not null,
  updated_by text not null default ''
);

create table if not exists public.todo (
  id bigint primary key,
  list_id bigint not null references public.reminder_list(id),
  title text not null,
  note text not null default '',
  due_date bigint,
  is_completed boolean not null default false,
  completed_at bigint,
  is_trashed boolean not null default false,
  trashed_at bigint,
  parent_id bigint,
  sort_position double precision not null default 0,
  flag boolean not null default false,
  created_at bigint not null,
  updated_at bigint not null,
  updated_by text not null default ''
);

create index if not exists idx_todo_list_trash on public.todo(list_id, is_trashed);
create index if not exists idx_todo_trash_due on public.todo(is_trashed, due_date);
create index if not exists idx_todo_parent on public.todo(parent_id);

alter table public.reminder_list enable row level security;
alter table public.todo enable row level security;

-- 无登录模型：anon 放开（个人使用；任何持有 anon key 者均可读写）
create policy "anon all reminder_list" on public.reminder_list for all to anon using (true) with check (true);
create policy "anon all todo" on public.todo for all to anon using (true) with check (true);

-- 远端 DELETE 事件需要完整旧行（含 updated_at）做 LWW 守卫；默认 PK-only 会丢删除
alter table public.todo replica identity full;
alter table public.reminder_list replica identity full;

alter publication supabase_realtime add table public.reminder_list;
alter publication supabase_realtime add table public.todo;
```

文档正文（中文）说明：在 Supabase Dashboard 建项目 → SQL Editor 执行上述脚本 → 打开 Realtime 面板确认 `todo`/`reminder_list` 已加入 publication → 应用内设置页填入 URL + anon key → 保存。安全边界说明（anon key 是公开的，RLS 放开意味着任何拿到 key 的人可读写；仅适合个人自用）。

- [ ] **Step 2: 创建 docs/adr/0002-sync-backend.md**

```markdown
# ADR 0002: 多端同步——Supabase 先行，SyncClient 端口预留自建服务器

- 日期：2026-08-11
- 状态：已接受

## 背景

spec 原定"纯本地、无同步"。用户决定增加多端实时同步，并明确未来可能自建 Sundial-Server。

## 决策

1. 同步语义：行级 LWW（`updated_at` 大者胜，物理删除带 `updated_at` 守卫），SQL 层实现，不引入 CRDT。
2. 本地 `outbox` 表记录全部本地变更（payload=完整新行 JSON），`SyncCoordinator` 批量推送、成功后清理。
3. 端口 `SyncClient`（push + 远端变化 Flow + 连接状态）为 seam；当前适配器：`NoopSyncClient`（本地模式）、`SupabaseSyncClient`（PostgREST + Realtime）；工厂 `SyncClientFactory` 已含 `SundialServer` 分支，未来只需新增适配器。
4. 应用内设置页配置同步模式与凭据（settings 表持久化）；远端变化经 `SyncEngine` 应用，自身设备事件过滤（`updated_by`）。
5. 身份：无登录，RLS 放开 anon（个人使用，安全边界见 docs/sync-setup.md）。
6. 冲突处理仅 LWW，不做字段级合并；`deleteForever` 的本地物理删除同步为 DELETE op。

## 不做

- 不做 CRDT / 字段级合并 / 双向删除撤销。
- 不引入认证 UI；Sundial-Server 模式本期不实现（工厂返回 NotConfigured）。
- 不处理多用户数据隔离。

## 后果

- 优点：实时同步代码量最小（约 400 行核心）；同步后端可替换（本地/Supabase/自建）；离线写天然由 outbox 兜底（重连后推送）。
- 代价：LWW 在同时双端编辑同一行时丢更新（个人使用可接受）；Supabase 为外部依赖；RLS 放开的安全风险由部署者自担。
```

- [ ] **Step 3: 最终全量验证**

Run: `./gradlew :shared:desktopTest --rerun-tasks :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL，73 测试全绿。

- [ ] **Step 4: Commit**

```bash
git add docs
git commit -m "docs(sync): supabase setup guide and sync backend ADR"
```

---

## Self-Review 清单

**规格覆盖**：
- 配置页三模式（本地/Supabase/Sundial-Server）→ Task 6 ✓（Sundial 选项可显示但保存无效，工厂返回 NotConfigured）
- Supabase 实时同步（PostgREST upsert/delete + Realtime 订阅）→ Task 4 ✓
- 无登录 RLS 放开 → Task 8 setup SQL ✓
- 同步状态显示（设置页 + 侧边栏）→ Task 6 ✓
- 为 Sundial-Server 留 seam → SyncClient 端口 + 工厂分支 + 配置项 ✓
- 失败重试（outbox 保留 + 2s 轮询循环）→ Task 5 ✓

**类型一致性**：
- `SyncRow` 增加 `seq: Long` 字段（Task 5 Step 1 修正）——Task 2 文件与 Task 7 测试均需同步此字段
- `TodoRepository` 新增 8 个同步方法——Task 7 fake 实现需与 port 完全一致
- `Outbox.toSyncRow()` 的 `seq = id`

**已知风险（编译时验证）**：
- `upsertTodo` 必须含 `id` 列（15 参数）——Task 1 质量审查发现的原稿 bug（缺 id 导致 ON CONFLICT(id) 永不触发），已修正
- `outbox.seq` 用 `selectOutboxMaxSeq`（`COALESCE(MAX(seq),0)+1`）计算，删除后仍单调（count+1 会在 clear 后回退）；`Outbox.toSyncRow().seq = id`（与 seq 单调一致）
- **Android 旧设备系统 SQLite < 3.24 不支持 `ON CONFLICT DO UPDATE`** —— 已改为 UPSERT-free 写法（Task 7 验证时发现 `sqlite-android` 构件不存在）：`upsertTodo`→`updateTodoIfNewer`+`insertTodoIfMissing`、`upsertList`→`updateListIfNewer`+`insertListIfMissing`、`setSetting`→`updateSetting`+`insertSettingIfMissing`（事务内先 update 后 insert，LWW 语义不变：`WHERE id=? AND updated_at <= ?` 保证旧数据不覆盖新数据）。SQLite ≥ 3.8（API 24 起）全部可用。
- supabase-kt 3.7.0 具体 API 名称（`postgresChangeFlow`、`PostgresChangeFilter.Database`、`record` 类型）以编译结果为准，实现者可调整至等价 API
- SQLDelight 生成类型名（`Outbox`/`Setting`）以实际生成为准
- `db.transaction` 内嵌套 select 查询在 SQLDelight 中合法（同一连接）
- `kotlin.uuid.Uuid` 可用性（必要时换 expect/actual）
- 等毫秒级 `updated_at` 平局时后到者胜（无 updated_by 打破平局）——MVP 接受
- **DTO 字段必须加 `@SerialName("snake_case")`**（Task 4 质量审查发现）：realtime record 与 PostgREST 表体均为 snake_case（list_id/updated_at…），不加则远端 upsert 解码必抛 MissingFieldException、PostgREST upsert body 键名不匹配列名。一处序列化器三处复用（outbox 编码、postgrest 表体、realtime 解码）
- **Supabase 需 `replica identity full`**（Task 4 质量审查发现）：默认 PK-only 下 DELETE 的 oldRecord 无 updated_at，LWW 守卫恒假导致远端删除不生效；setup SQL 已加，见 Task 8
