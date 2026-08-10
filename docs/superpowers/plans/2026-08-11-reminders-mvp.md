# Reminders MVP 实现计划（macOS + Android）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 Compose Multiplatform 工程上实现 Reminders 风格待办应用 MVP：多列表、快速输入+自然语言日期解析、完成/子任务、今日视图、搜索、深色模式、回收站，macOS 桌面端 + Android 双端可用。

**Architecture:** 方案 A 共享 MVVM 三层。`shared` 模块内 `data`（SQLDelight + RepositoryImpl）/ `domain`（模型 + 接口 + 用例）/ `ui`（Compose + ViewModel）；`desktopApp`/`androidApp` 仅做薄壳接线。响应式布局：宽窗口三栏（侧边栏|列表|详情），窄窗口两屏（主屏+详情）。**对 spec 的一处细化**：导航采用 sealed class 路由状态（`Route`）而非 navigation-compose 库——两屏应用无需引入导航依赖，降低版本兼容风险，后续需要可平滑替换。

**Tech Stack:** Kotlin 2.4.10 / CMP 1.11.1 / AGP 8.13.2 / Gradle 8.14.3、SQLDelight 2.3.2、kotlinx-datetime 0.8.0、org.jetbrains.androidx.lifecycle 2.11.0（ViewModel+StateFlow）、Material3、kotlinx-coroutines-test 1.11.0。

**环境要点（本机已配好）**：JDK 21（Homebrew，`org.gradle.java.home` 已指向）；Android SDK 在 `~/Library/Android/sdk`；`local.properties` 已有 `sdk.dir` 与 release 签名。所有 Gradle 命令在 `/Users/somhairle/Workspace/ComposeDemo` 下运行。

---

### Task 0: 基线提交（清理未提交的模板改动）

**Files:**
- （无新文件）

- [ ] **Step 1: 提交当前所有未提交改动**

```bash
git add -A && git commit -m "chore: baseline from CMP template setup (signing, versions, ios removal)"
```

Expected: 提交成功，`git status` 干净（`local.properties`、`keystore/` 已被 gitignore）。

---

### Task 1: 构建配置——接入 SQLDelight 与依赖

**Files:**
- Modify: `gradle.properties`
- Modify: `settings.gradle.kts`
- Modify: `shared/build.gradle.kts`

- [ ] **Step 1: gradle.properties 追加版本号**

在 `gradle.properties` 末尾追加：

```properties
#Libraries
sqlDelight.version=2.3.2
kotlinxDatetime.version=0.8.0
lifecycle.version=2.11.0
coroutinesTest.version=1.11.0
```

- [ ] **Step 2: settings.gradle.kts 注册 SQLDelight 插件**

在 `pluginManagement { plugins { ... } }` 块的 `id("org.jetbrains.compose").version(composeVersion)` 之后追加：

```kotlin
        val sqldelightVersion = extra["sqlDelight.version"] as String

        id("app.cash.sqldelight").version(sqldelightVersion)
```

- [ ] **Step 3: shared/build.gradle.kts 应用插件并加依赖**

`plugins` 块追加 `id("app.cash.sqldelight")`（版本从 settings 注入，无需 version）：

```kotlin
plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("app.cash.sqldelight")
}
```

`commonMain` dependencies 块改为：

```kotlin
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.components.resources)
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.11.0")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
                implementation("app.cash.sqldelight:runtime:2.3.2")
                implementation("app.cash.sqldelight:coroutines-extensions:2.3.2")
            }
        }
```

`androidMain` 追加：

```kotlin
                api("androidx.core:core-ktx:1.16.0")
                implementation("app.cash.sqldelight:android-driver:2.3.2")
```

`desktopMain` 追加：

```kotlin
                implementation(compose.desktop.common)
                implementation("app.cash.sqldelight:sqlite-driver:2.3.2")
```

`kotlin {}` 块末尾（`sourceSets { ... }` 之后）追加测试源集与 SQLDelight 配置：

```kotlin
    sourceSets {
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation("app.cash.sqldelight:sqlite-driver:2.3.2")
            }
        }
    }
```

并在 `android {}` 块之后、文件末尾追加：

```kotlin
sqldelight {
    databases {
        create("TodoDb") {
            packageName.set("com.myapplication.shared.data")
        }
    }
}
```

- [ ] **Step 4: 验证编译**

Run: `./gradlew :shared:compileKotlinDesktop`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 提交**

```bash
git add -A && git commit -m "build: add sqldelight, datetime, lifecycle, material3 deps"
```

---

### Task 2: SQLDelight schema（TodoDb.sq）

**Files:**
- Create: `shared/src/commonMain/sqldelight/com/myapplication/shared/data/TodoDb.sq`

- [ ] **Step 1: 写 schema**

```sql
CREATE TABLE reminder_list (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  color_key TEXT NOT NULL,
  position INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL
);

CREATE TABLE todo (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  list_id INTEGER NOT NULL REFERENCES reminder_list(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  note TEXT NOT NULL DEFAULT '',
  due_date INTEGER,
  is_completed INTEGER NOT NULL DEFAULT 0,
  completed_at INTEGER,
  is_trashed INTEGER NOT NULL DEFAULT 0,
  trashed_at INTEGER,
  parent_id INTEGER,
  sort_position REAL NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL
);

CREATE INDEX idx_todo_list_trash ON todo(list_id, is_trashed);
CREATE INDEX idx_todo_trash_due ON todo(is_trashed, due_date);
CREATE INDEX idx_todo_parent ON todo(parent_id);

insertList:
INSERT INTO reminder_list(name, color_key, position, created_at)
VALUES (?, ?, ?, ?);

selectLists:
SELECT * FROM reminder_list ORDER BY position, id;

deleteList:
DELETE FROM reminder_list WHERE id = ?;

trashTodosInList:
UPDATE todo SET is_trashed = 1, trashed_at = ? WHERE list_id = ? AND is_trashed = 0;

insertTodo:
INSERT INTO todo(list_id, title, note, due_date, is_completed, is_trashed, parent_id, sort_position, created_at)
VALUES (?, ?, ?, ?, 0, 0, ?, ?, ?);

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

searchTodos:
SELECT * FROM todo WHERE is_trashed = 0 AND (title LIKE ? ESCAPE '\' OR note LIKE ? ESCAPE '\')
ORDER BY is_completed, due_date IS NULL, due_date;

updateCompleted:
UPDATE todo SET is_completed = ?, completed_at = ? WHERE id = ?;

updateTitle:
UPDATE todo SET title = ? WHERE id = ?;

updateNote:
UPDATE todo SET note = ? WHERE id = ?;

updateDueDate:
UPDATE todo SET due_date = ? WHERE id = ?;

moveToList:
UPDATE todo SET list_id = ? WHERE id = ?;

trashTodo:
UPDATE todo SET is_trashed = 1, trashed_at = ? WHERE id = ?;

restoreTodo:
UPDATE todo SET is_trashed = 0, trashed_at = NULL WHERE id = ?;

deleteTodo:
DELETE FROM todo WHERE id = ?;
```

- [ ] **Step 2: 验证代码生成**

Run: `./gradlew :shared:compileKotlinDesktop`
Expected: `BUILD SUCCESSFUL`；生成 `TodoDb`、`Todo`、`ReminderList` 类（包 `com.myapplication.shared.data`）

- [ ] **Step 3: 提交**

```bash
git add -A && git commit -m "feat(data): add sqldelight schema for lists and todos"
```

---

### Task 3: domain 模型与仓库接口

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/model/TodoList.kt`
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/model/TodoItem.kt`
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/repository/TodoRepository.kt`

- [ ] **Step 1: TodoList.kt**

```kotlin
package com.myapplication.shared.domain.model

import kotlinx.datetime.Instant

data class TodoList(
    val id: Long,
    val name: String,
    val colorKey: String,
    val position: Int,
    val createdAt: Instant,
)
```

- [ ] **Step 2: TodoItem.kt**

```kotlin
package com.myapplication.shared.domain.model

import kotlinx.datetime.Instant

data class TodoItem(
    val id: Long,
    val listId: Long,
    val title: String,
    val note: String,
    val dueDate: Instant?,
    val isCompleted: Boolean,
    val completedAt: Instant?,
    val isTrashed: Boolean,
    val trashedAt: Instant?,
    val parentId: Long?,
    val sortPosition: Double,
    val createdAt: Instant,
)
```

- [ ] **Step 3: TodoRepository.kt**

```kotlin
package com.myapplication.shared.domain.repository

import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.model.TodoList
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface TodoRepository {
    suspend fun ensureInbox()
    fun observeLists(): Flow<List<TodoList>>
    fun observeAllActive(): Flow<List<TodoItem>>
    fun observeByList(listId: Long): Flow<List<TodoItem>>
    fun observeToday(): Flow<List<TodoItem>>
    fun observeScheduled(): Flow<List<TodoItem>>
    fun observeCompleted(): Flow<List<TodoItem>>
    fun observeTrashed(): Flow<List<TodoItem>>
    fun observeSubTasks(parentId: Long): Flow<List<TodoItem>>
    fun observeTodo(id: Long): Flow<TodoItem?>
    fun search(query: String): Flow<List<TodoItem>>

    suspend fun addList(name: String, colorKey: String)
    suspend fun deleteList(listId: Long)
    suspend fun addTodo(listId: Long?, title: String, note: String, dueDate: Instant?, parentId: Long? = null)
    suspend fun addSubTask(parentId: Long, title: String)
    suspend fun setCompleted(id: Long, completed: Boolean)
    suspend fun setTitle(id: Long, title: String)
    suspend fun setNote(id: Long, note: String)
    suspend fun setDueDate(id: Long, dueDate: Instant?)
    suspend fun moveToList(id: Long, listId: Long)
    suspend fun trash(id: Long)
    suspend fun restore(id: Long)
    suspend fun deleteForever(id: Long)
}
```

- [ ] **Step 4: 验证编译**

Run: `./gradlew :shared:compileKotlinDesktop`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 提交**

```bash
git add -A && git commit -m "feat(domain): add models and repository interface"
```

---

### Task 4: SQL driver（expect/actual）+ TodoRepositoryImpl + 仓库测试

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/data/SqlDriver.kt`
- Create: `shared/src/desktopMain/kotlin/com/myapplication/shared/data/SqlDriver.desktop.kt`
- Create: `shared/src/androidMain/kotlin/com/myapplication/shared/data/SqlDriver.android.kt`
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/data/TodoRepositoryImpl.kt`
- Create: `shared/src/desktopTest/kotlin/com/myapplication/shared/data/TodoRepositoryImplTest.kt`

- [ ] **Step 1: 写失败的仓库测试**

`TodoRepositoryImplTest.kt`：

```kotlin
package com.myapplication.shared.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TodoRepositoryImplTest {

    private fun newRepo(): TodoRepositoryImpl {
        val driver = JdbcSqliteDriver("jdbc:sqlite::memory:")
        TodoDb.Schema.create(driver)
        return TodoRepositoryImpl(TodoDb(driver))
    }

    private suspend fun TodoRepositoryImpl.inbox(): Long {
        ensureInbox()
        return observeLists().first().first().id
    }

    @Test
    fun ensureInboxCreatesDefaultList() = runTest {
        val repo = newRepo()
        assertTrue(repo.observeLists().first().isEmpty())
        repo.ensureInbox()
        val lists = repo.observeLists().first()
        assertEquals(1, lists.size)
        assertEquals("收件箱", lists.first().name)
    }

    @Test
    fun addTodoStoresTitleAndList() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        repo.addTodo(inbox, "交季度报告", "", null, null)
        val items = repo.observeAllActive().first()
        assertEquals(1, items.size)
        assertEquals("交季度报告", items.first().title)
        assertEquals(inbox, items.first().listId)
        assertFalse(items.first().isCompleted)
    }

    @Test
    fun dueDateFallsIntoTodayQuery() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        repo.addTodo(inbox, "今天的事", "", now.atStartOfDayIn(TimeZone.currentSystemDefault()).plus(1, DateTimeUnit.HOUR), null)
        repo.addTodo(inbox, "没日期的事", "", null, null)
        val today = repo.observeToday().first()
        assertEquals(1, today.size)
        assertEquals("今天的事", today.first().title)
    }

    @Test
    fun trashThenRestoreThenDeleteForever() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        repo.addTodo(inbox, "要扔的", "", null, null)
        val item = repo.observeAllActive().first().first()
        repo.trash(item.id)
        assertTrue(repo.observeAllActive().first().isEmpty())
        assertEquals(1, repo.observeTrashed().first().size)
        repo.restore(item.id)
        assertEquals(1, repo.observeAllActive().first().size)
        repo.trash(item.id)
        repo.deleteForever(item.id)
        assertTrue(repo.observeTrashed().first().isEmpty())
    }

    @Test
    fun subtaskLinksToParentList() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        repo.addTodo(inbox, "父任务", "", null, null)
        val parent = repo.observeAllActive().first().first()
        repo.addSubTask(parent.id, "子任务")
        val children = repo.observeSubTasks(parent.id).first()
        assertEquals(1, children.size)
        assertEquals("子任务", children.first().title)
        assertEquals(parent.listId, children.first().listId)
        assertEquals(parent.id, children.first().parentId)
    }

    @Test
    fun searchMatchesTitleAndNote() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        repo.addTodo(inbox, "买牛奶", "", "全脂的", null)
        repo.addTodo(inbox, "写周报", "", "", null)
        val byTitle = repo.search("牛奶").first()
        assertEquals(1, byTitle.size)
        val byNote = repo.search("全脂").first()
        assertEquals(1, byNote.size)
        assertTrue(repo.search("不存在").first().isEmpty())
    }

    @Test
    fun setCompletedAndDueDate() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        repo.addTodo(inbox, "改状态", "", null, null)
        val item = repo.observeAllActive().first().first()
        repo.setCompleted(item.id, true)
        assertTrue(repo.observeAllActive().first().first().isCompleted)
        val due = Clock.System.now()
        repo.setDueDate(item.id, due)
        val after = repo.observeTodo(item.id).first()
        assertNotNull(after?.dueDate)
        repo.setDueDate(item.id, null)
        assertNull(repo.observeTodo(item.id).first()?.dueDate)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :shared:desktopTest`
Expected: FAIL（`TodoRepositoryImpl` 不存在，编译错误）

- [ ] **Step 3: 实现 driver expect/actual**

`SqlDriver.kt`（commonMain）：

```kotlin
package com.myapplication.shared.data

import app.cash.sqldelight.db.SqlDriver

expect fun createSqlDriver(): SqlDriver
```

`SqlDriver.desktop.kt`（desktopMain）：

```kotlin
package com.myapplication.shared.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.nio.file.Files
import java.nio.file.Paths

actual fun createSqlDriver(): SqlDriver {
    val home = System.getProperty("user.home") ?: "."
    val dir = Paths.get(home, ".reminders")
    Files.createDirectories(dir)
    val driver = JdbcSqliteDriver("jdbc:sqlite:${dir.resolve("reminders.db")}")
    TodoDb.Schema.create(driver)
    return driver
}
```

`SqlDriver.android.kt`（androidMain）：

```kotlin
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
```

- [ ] **Step 4: 实现 TodoRepositoryImpl**

`TodoRepositoryImpl.kt`：

```kotlin
package com.myapplication.shared.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.domain.repository.TodoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toEpochMilliseconds
import kotlinx.datetime.toLocalDateTime

class TodoRepositoryImpl(private val db: TodoDb) : TodoRepository {

    private fun Todo.toDomain() = TodoItem(
        id = id,
        listId = listId,
        title = title,
        note = note,
        dueDate = dueDate?.let { Instant.fromEpochMilliseconds(it) },
        isCompleted = isCompleted,
        completedAt = completedAt?.let { Instant.fromEpochMilliseconds(it) },
        isTrashed = isTrashed,
        trashedAt = trashedAt?.let { Instant.fromEpochMilliseconds(it) },
        parentId = parentId,
        sortPosition = sortPosition,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
    )

    private fun ReminderList.toDomain() = TodoList(
        id = id,
        name = name,
        colorKey = color_key,
        position = position.toInt(),
        createdAt = Instant.fromEpochMilliseconds(created_at),
    )

    override suspend fun ensureInbox() {
        if (db.selectListsQuery().executeAsList().isEmpty()) {
            db.insertList("收件箱", "blue", 0, Clock.System.now().toEpochMilliseconds())
        }
    }

    override fun observeLists(): Flow<List<TodoList>> =
        db.selectListsQuery().asFlow().mapToList().map { lists -> lists.map { it.toDomain() } }

    override fun observeAllActive(): Flow<List<TodoItem>> =
        db.selectAllActiveQuery().asFlow().mapToList().map { todos -> todos.map { it.toDomain() } }

    override fun observeByList(listId: Long): Flow<List<TodoItem>> =
        db.selectByListQuery(listId).asFlow().mapToList().map { todos -> todos.map { it.toDomain() } }

    override fun observeToday(): Flow<List<TodoItem>> {
        val tz = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(tz).date
        val start = today.atStartOfDayIn(tz).toEpochMilliseconds()
        val end = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz).toEpochMilliseconds()
        return db.selectTodayQuery(start, end).asFlow().mapToList().map { todos -> todos.map { it.toDomain() } }
    }

    override fun observeScheduled(): Flow<List<TodoItem>> =
        db.selectWithDueDateQuery().asFlow().mapToList().map { todos -> todos.map { it.toDomain() } }

    override fun observeCompleted(): Flow<List<TodoItem>> =
        db.selectCompletedQuery().asFlow().mapToList().map { todos -> todos.map { it.toDomain() } }

    override fun observeTrashed(): Flow<List<TodoItem>> =
        db.selectTrashedQuery().asFlow().mapToList().map { todos -> todos.map { it.toDomain() } }

    override fun observeSubTasks(parentId: Long): Flow<List<TodoItem>> =
        db.selectSubTasksQuery(parentId).asFlow().mapToList().map { todos -> todos.map { it.toDomain() } }

    override fun observeTodo(id: Long): Flow<TodoItem?> =
        db.selectByIdQuery(id).asFlow().mapToOneOrNull().map { it?.toDomain() }

    override fun search(query: String): Flow<List<TodoItem>> {
        val escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val pattern = "%$escaped%"
        return db.searchTodosQuery(pattern, pattern).asFlow().mapToList()
            .map { todos -> todos.map { it.toDomain() } }
    }

    override suspend fun addList(name: String, colorKey: String) {
        val position = db.selectListsQuery().executeAsList().size
        db.insertList(name, colorKey, position.toLong(), Clock.System.now().toEpochMilliseconds())
    }

    override suspend fun deleteList(listId: Long) {
        db.trashTodosInList(Clock.System.now().toEpochMilliseconds(), listId)
        db.deleteList(listId)
    }

    override suspend fun addTodo(listId: Long?, title: String, note: String, dueDate: Instant?, parentId: Long?) {
        val now = Clock.System.now().toEpochMilliseconds()
        val targetList = parentId
            ?.let { pid -> db.selectByIdQuery(pid).executeAsOneOrNull()?.listId }
            ?: listId ?: 1L
        db.insertTodo(targetList, title, note, dueDate?.toEpochMilliseconds(), parentId, 0.0, now)
    }

    override suspend fun addSubTask(parentId: Long, title: String) {
        val parent = db.selectByIdQuery(parentId).executeAsOneOrNull()
            ?: return
        db.insertTodo(parent.listId, title, "", null, parentId, 0.0, Clock.System.now().toEpochMilliseconds())
    }

    override suspend fun setCompleted(id: Long, completed: Boolean) {
        db.updateCompleted(completed, if (completed) Clock.System.now().toEpochMilliseconds() else null, id)
    }

    override suspend fun setTitle(id: Long, title: String) {
        db.updateTitle(title, id)
    }

    override suspend fun setNote(id: Long, note: String) {
        db.updateNote(note, id)
    }

    override suspend fun setDueDate(id: Long, dueDate: Instant?) {
        db.updateDueDate(dueDate?.toEpochMilliseconds(), id)
    }

    override suspend fun moveToList(id: Long, listId: Long) {
        db.moveToList(listId, id)
    }

    override suspend fun trash(id: Long) {
        db.trashTodo(Clock.System.now().toEpochMilliseconds(), id)
    }

    override suspend fun restore(id: Long) {
        db.restoreTodo(id)
    }

    override suspend fun deleteForever(id: Long) {
        db.deleteTodo(id)
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `./gradlew :shared:desktopTest`
Expected: `BUILD SUCCESSFUL`，7 个测试全绿

- [ ] **Step 6: 提交**

```bash
git add -A && git commit -m "feat(data): implement sql driver and todo repository with tests"
```

---

### Task 5: 自然语言日期解析 + 格式化工具 + 测试

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/util/DateParser.kt`
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/util/Formatting.kt`
- Create: `shared/src/commonTest/kotlin/com/myapplication/shared/util/DateParserTest.kt`

- [ ] **Step 1: 写失败的解析器测试**

`DateParserTest.kt`：

```kotlin
package com.myapplication.shared.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DateParserTest {

    private val today: LocalDate = LocalDate(2026, Month.AUGUST, 11) // 周二

    @Test
    fun noDateToken() {
        val r = DateParser.parse("买牛奶", today)
        assertEquals("买牛奶", r.title)
        assertNull(r.dueDate)
    }

    @Test
    fun tomorrow() {
        val r = DateParser.parse("明天 交报告", today)
        assertEquals("交报告", r.title)
        assertEquals(LocalDate(2026, 8, 12), r.dueDate?.date)
    }

    @Test
    fun todayWithTime() {
        val r = DateParser.parse("买牛奶 今天", today)
        assertEquals("买牛奶", r.title)
        assertEquals(LocalDate(2026, 8, 11), r.dueDate?.date)
    }

    @Test
    fun tomorrowWithTime() {
        val r = DateParser.parse("交报告 明天15:00", today)
        assertEquals("交报告", r.title)
        assertEquals(LocalDate(2026, 8, 12), r.dueDate?.date)
        assertEquals(LocalTime(15, 0), r.dueDate?.time)
    }

    @Test
    fun dayAfterTomorrowWithTime() {
        val r = DateParser.parse("后天 10:30 开会", today)
        assertEquals("开会", r.title)
        assertEquals(LocalDate(2026, 8, 13), r.dueDate?.date)
        assertEquals(LocalTime(10, 30), r.dueDate?.time)
    }

    @Test
    fun weekdayLaterThisWeek() {
        // 今天是周二，周五 = 本周五
        val r = DateParser.parse("周五开会", today)
        assertEquals("开会", r.title)
        assertEquals(LocalDate(2026, 8, 14), r.dueDate?.date)
    }

    @Test
    fun weekdayEarlierThisWeekRollsToNextWeek() {
        // 今天是周二，周一 = 下周一
        val r = DateParser.parse("周一见客户", today)
        assertEquals("见客户", r.title)
        assertEquals(LocalDate(2026, 8, 17), r.dueDate?.date)
    }

    @Test
    fun sameWeekdayMeansToday() {
        val r = DateParser.parse("周二晨会", today)
        assertEquals("晨会", r.title)
        assertEquals(LocalDate(2026, 8, 11), r.dueDate?.date)
    }

    @Test
    fun nextWeek() {
        val r = DateParser.parse("下周一看医生", today)
        assertEquals("看医生", r.title)
        assertEquals(LocalDate(2026, 8, 17), r.dueDate?.date)
    }

    @Test
    fun monthDayThisYear() {
        val r = DateParser.parse("12月25日 圣诞采购", today)
        assertEquals("圣诞采购", r.title)
        assertEquals(LocalDate(2026, 12, 25), r.dueDate?.date)
    }

    @Test
    fun monthDayNextYearWhenPassed() {
        val r = DateParser.parse("1月1日 新年快乐", today)
        assertEquals("新年快乐", r.title)
        assertEquals(LocalDate(2027, 1, 1), r.dueDate?.date)
    }

    @Test
    fun afternoonTime() {
        val r = DateParser.parse("下午3点 开会", today)
        assertEquals("开会", r.title)
        assertEquals(LocalTime(15, 0), r.dueDate?.time)
        assertEquals(LocalDate(2026, 8, 11), r.dueDate?.date)
    }

    @Test
    fun englishRelative() {
        val r = DateParser.parse("buy milk tomorrow", today)
        assertEquals("buy milk", r.title)
        assertEquals(LocalDate(2026, 8, 12), r.dueDate?.date)
    }

    @Test
    fun titleOnlyKeptWhenBlank() {
        val r = DateParser.parse("明天", today)
        assertEquals("明天", r.title)
        assertEquals(LocalDate(2026, 8, 12), r.dueDate?.date)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :shared:desktopTest --tests "com.myapplication.shared.util.DateParserTest"`
Expected: FAIL（`DateParser` 不存在）

- [ ] **Step 3: 实现 DateParser**

`DateParser.kt`：

```kotlin
package com.myapplication.shared.util

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

data class ParsedInput(val title: String, val dueDate: LocalDateTime?)

object DateParser {

    private val weekdayZh = mapOf(
        "一" to 1, "二" to 2, "三" to 3, "四" to 4,
        "五" to 5, "六" to 6, "日" to 7, "天" to 7,
    )
    private val weekdayEn = mapOf(
        "monday" to 1, "tuesday" to 2, "wednesday" to 3, "thursday" to 4,
        "friday" to 5, "saturday" to 6, "sunday" to 7,
    )
    private val timeRegex = Regex("""(上午|中午|下午|晚上|早上)?\s*(\d{1,2})\s*[:：点时]\s*(\d{1,2})?\s*分?""")

    fun parse(input: String, today: LocalDate): ParsedInput {
        var title = input.trim()
        var date: LocalDate? = null

        for ((kw, offset) in listOf("今天" to 0L, "明天" to 1L, "后天" to 2L)) {
            if (title.contains(kw)) {
                date = today.plus(offset, DateTimeUnit.DAY)
                title = removeToken(title, kw)
                break
            }
        }

        if (date == null) {
            Regex("下周([一二三四五六日天])").find(title)?.let { m ->
                val target = weekdayZh.getValue(m.groupValues[1])
                date = today.plus(
                    (7 - today.dayOfWeek.isoDayNumber + target).toLong(),
                    DateTimeUnit.DAY,
                )
                title = removeToken(title, m.value)
            }
        }

        if (date == null) {
            Regex("周([一二三四五六日天])").find(title)?.let { m ->
                val target = weekdayZh.getValue(m.groupValues[1])
                val todayDow = today.dayOfWeek.isoDayNumber
                date = when {
                    target == todayDow -> today
                    target > todayDow -> today.plus((target - todayDow).toLong(), DateTimeUnit.DAY)
                    else -> today.plus((7 - todayDow + target).toLong(), DateTimeUnit.DAY)
                }
                title = removeToken(title, m.value)
            }
        }

        if (date == null) {
            Regex("""(\d{1,2})月(\d{1,2})[日号]""").find(title)?.let { m ->
                runCatching {
                    val month = m.groupValues[1].toInt()
                    val day = m.groupValues[2].toInt()
                    val candidate = LocalDate(today.year, month, day)
                    if (candidate < today) LocalDate(today.year + 1, month, day) else candidate
                }.onSuccess {
                    date = it
                    title = removeToken(title, m.value)
                }
            }
        }

        if (date == null) {
            val lower = title.lowercase()
            when {
                lower.contains("day after tomorrow") -> {
                    date = today.plus(2, DateTimeUnit.DAY)
                    title = removeToken(title, "day after tomorrow")
                }
                lower.contains("tomorrow") -> {
                    date = today.plus(1, DateTimeUnit.DAY)
                    title = removeToken(title, "tomorrow")
                }
                lower.contains("today") -> {
                    date = today
                    title = removeToken(title, "today")
                }
                else -> {
                    Regex("next\\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)")
                        .find(lower)?.let { m ->
                            val target = weekdayEn.getValue(m.groupValues[1])
                            date = today.plus(
                                (7 - today.dayOfWeek.isoDayNumber + target).toLong(),
                                DateTimeUnit.DAY,
                            )
                            title = removeToken(title, m.value)
                        }
                }
            }
        }

        val time = extractTime(title)
        if (time != null) {
            title = timeRegex.replace(title, " ")
        }

        val due = date?.let { LocalDateTime(it, time ?: LocalTime(0, 0)) }
        val cleanTitle = title.replace(Regex("\\s+"), " ").trim()
        return ParsedInput(cleanTitle.ifBlank { input.trim() }, due)
    }

    private fun extractTime(text: String): LocalTime? {
        val m = timeRegex.find(text) ?: return null
        val h = m.groupValues[2].toInt()
        val min = m.groupValues[3]?.takeIf { it.isNotEmpty() }?.toInt() ?: 0
        val marker = m.groupValues[1]
        val hour = when (marker) {
            "下午", "晚上" -> if (h < 12) h + 12 else h
            "中午" -> 12
            else -> h
        }
        return runCatching { LocalTime(hour, min) }.getOrNull()
    }

    private fun removeToken(text: String, token: String): String =
        text.replace(token, " ").replace(Regex("\\s+"), " ").trim()
}
```

- [ ] **Step 4: 实现 Formatting（今日日期/分桶/显示格式化）**

`Formatting.kt`：

```kotlin
package com.myapplication.shared.util

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime

enum class DueBucket { OVERDUE, TODAY, TOMORROW, THIS_WEEK, LATER }

fun todayDate(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

fun bucketOf(due: LocalDate, today: LocalDate): DueBucket {
    val diff = today.daysUntil(due)
    return when {
        diff < 0 -> DueBucket.OVERDUE
        diff == 0 -> DueBucket.TODAY
        diff == 1 -> DueBucket.TOMORROW
        diff < 7 - today.dayOfWeek.isoDayNumber -> DueBucket.THIS_WEEK
        else -> DueBucket.LATER
    }
}

fun bucketLabel(bucket: DueBucket): String = when (bucket) {
    DueBucket.OVERDUE -> "过期"
    DueBucket.TODAY -> "今天"
    DueBucket.TOMORROW -> "明天"
    DueBucket.THIS_WEEK -> "本周"
    DueBucket.LATER -> "计划"
}

private val weekdaysZh = arrayOf("", "一", "二", "三", "四", "五", "六", "日")

fun formatDueDate(due: Instant?, tz: TimeZone = TimeZone.currentSystemDefault()): String {
    if (due == null) return ""
    val ldt = due.toLocalDateTime(tz)
    val date = ldt.date
    val days = todayDate().daysUntil(date)
    val dateLabel = when (days) {
        0 -> "今天"
        -1 -> "昨天"
        1 -> "明天"
        else -> {
            val todayDow = todayDate().dayOfWeek.isoDayNumber
            if (date.dayOfWeek.isoDayNumber > todayDow && days in 2..6) "周${weekdaysZh[date.dayOfWeek.isoDayNumber]}"
            else "${date.monthNumber}月${date.dayOfMonth}日"
        }
    }
    val time = ldt.time
    val timeLabel = if (time.hour == 0 && time.minute == 0) {
        ""
    } else {
        " ${time.hour}:${time.minute.toString().padStart(2, '0')}"
    }
    return dateLabel + timeLabel
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `./gradlew :shared:desktopTest --tests "com.myapplication.shared.util.DateParserTest"`
Expected: `BUILD SUCCESSFUL`，13 个测试全绿

- [ ] **Step 6: 提交**

```bash
git add -A && git commit -m "feat(util): natural language date parser and formatting helpers"
```

---

### Task 6: AppGraph 依赖根 + createAppGraph

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/di/AppGraph.kt`
- Create: `shared/src/desktopMain/kotlin/com/myapplication/shared/di/AppGraph.desktop.kt`
- Create: `shared/src/androidMain/kotlin/com/myapplication/shared/di/AppGraph.android.kt`

- [ ] **Step 1: commonMain AppGraph.kt**

```kotlin
package com.myapplication.shared.di

import app.cash.sqldelight.db.SqlDriver
import com.myapplication.shared.data.TodoDb
import com.myapplication.shared.data.TodoRepositoryImpl
import com.myapplication.shared.domain.repository.TodoRepository

class AppGraph(driver: SqlDriver) {
    val repository: TodoRepository by lazy { TodoRepositoryImpl(TodoDb(driver)) }
}

expect fun createAppGraph(): AppGraph
```

- [ ] **Step 2: desktopMain AppGraph.desktop.kt**

```kotlin
package com.myapplication.shared.di

import com.myapplication.shared.data.createSqlDriver

actual fun createAppGraph(): AppGraph = AppGraph(createSqlDriver())
```

- [ ] **Step 3: androidMain AppGraph.android.kt**

```kotlin
package com.myapplication.shared.di

import com.myapplication.shared.data.createSqlDriver

actual fun createAppGraph(): AppGraph = AppGraph(createSqlDriver())
```

- [ ] **Step 4: 验证编译**

Run: `./gradlew :shared:compileKotlinDesktop`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 提交**

```bash
git add -A && git commit -m "feat(di): add app graph and platform factories"
```

---

### Task 7: Reminders 主题（浅色/深色）

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/theme/Theme.kt`

- [ ] **Step 1: 实现主题**

```kotlin
package com.myapplication.shared.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ListColorKeys = listOf("blue", "red", "orange", "yellow", "green", "teal", "purple")

val ListColorOf = mapOf(
    "blue" to Color(0xFF007AFF),
    "red" to Color(0xFFFF3B30),
    "orange" to Color(0xFFFF9500),
    "yellow" to Color(0xFFFFCC00),
    "green" to Color(0xFF34C759),
    "teal" to Color(0xFF5AC8FA),
    "purple" to Color(0xFFAF52DE),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF007AFF),
    onPrimary = Color.White,
    background = Color(0xFFF5F5F4),
    onBackground = Color(0xFF1C1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFECECEB),
    onSurfaceVariant = Color(0xFF6E6E73),
    outline = Color(0xFFC7C7CC),
    secondaryContainer = Color(0xFFE4E4E2),
    onSecondaryContainer = Color(0xFF3A3A3C),
    error = Color(0xFFFF3B30),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    background = Color(0xFF1E1E1E),
    onBackground = Color(0xFFF5F5F4),
    surface = Color(0xFF2C2C2E),
    onSurface = Color(0xFFF5F5F4),
    surfaceVariant = Color(0xFF3A3A3C),
    onSurfaceVariant = Color(0xFFAEAEB2),
    outline = Color(0xFF48484A),
    secondaryContainer = Color(0xFF3A3A3C),
    onSecondaryContainer = Color(0xFFE5E5EA),
    error = Color(0xFFFF453A),
)

@Composable
fun RemindersTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
```

- [ ] **Step 2: 验证编译**

Run: `./gradlew :shared:compileKotlinDesktop`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 提交**

```bash
git add -A && git commit -m "feat(ui): add reminders-style light/dark theme"
```

---

### Task 8: ViewModels（Main + Detail）+ 测试

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/main/MainViewModel.kt`
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/detail/DetailViewModel.kt`
- Create: `shared/src/commonTest/kotlin/com/myapplication/shared/ui/main/MainViewModelTest.kt`

- [ ] **Step 1: 写失败的 ViewModel 测试（含 FakeRepository）**

`MainViewModelTest.kt`：

```kotlin
package com.myapplication.shared.ui.main

import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.domain.repository.TodoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private class FakeRepository : TodoRepository {
    val listsState = MutableStateFlow<List<TodoList>>(emptyList())
    val todosState = MutableStateFlow<List<TodoItem>>(emptyList())
    var addedTitle: String? = null
    var addedListId: Long? = null
    var addedDue: Instant? = null
    var addedParent: Long? = null
    var toggledId: Long? = null
    var toggledValue: Boolean? = null

    override suspend fun ensureInbox() {
        listsState.value = listOf(TodoList(1, "收件箱", "blue", 0, Instant.fromEpochMilliseconds(0)))
    }

    override fun observeLists(): Flow<List<TodoList>> = listsState
    override fun observeAllActive(): Flow<List<TodoItem>> = todosState
    override fun observeByList(listId: Long): Flow<List<TodoItem>> = todosState
    override fun observeToday(): Flow<List<TodoItem>> = todosState
    override fun observeScheduled(): Flow<List<TodoItem>> = todosState
    override fun observeCompleted(): Flow<List<TodoItem>> = todosState
    override fun observeTrashed(): Flow<List<TodoItem>> = todosState
    override fun observeSubTasks(parentId: Long): Flow<List<TodoItem>> = todosState
    override fun observeTodo(id: Long): Flow<TodoItem?> = MutableStateFlow(null)
    override fun search(query: String): Flow<List<TodoItem>> = todosState

    override suspend fun addList(name: String, colorKey: String) = Unit
    override suspend fun deleteList(listId: Long) = Unit
    override suspend fun addTodo(listId: Long?, title: String, note: String, dueDate: Instant?, parentId: Long?) {
        addedTitle = title
        addedListId = listId
        addedDue = dueDate
        addedParent = parentId
    }

    override suspend fun addSubTask(parentId: Long, title: String) = Unit
    override suspend fun setCompleted(id: Long, completed: Boolean) {
        toggledId = id
        toggledValue = completed
    }

    override suspend fun setTitle(id: Long, title: String) = Unit
    override suspend fun setNote(id: Long, note: String) = Unit
    override suspend fun setDueDate(id: Long, dueDate: Instant?) = Unit
    override suspend fun moveToList(id: Long, listId: Long) = Unit
    override suspend fun trash(id: Long) = Unit
    override suspend fun restore(id: Long) = Unit
    override suspend fun deleteForever(id: Long) = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.collect(vm: MainViewModel) {
        backgroundScope.launch { vm.todos.collect {} }
        backgroundScope.launch { vm.lists.collect {} }
    }

    @Test
    fun quickAddParsesDateAndTitle() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = MainViewModel(repo)
        collect(vm)
        vm.addQuick("明天 交报告")
        advanceUntilIdle()
        assertEquals("交报告", repo.addedTitle)
        assertNotNull(repo.addedDue)
        assertNull(repo.addedParent)
    }

    @Test
    fun quickAddInListScopeAddsToList() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = MainViewModel(repo)
        collect(vm)
        vm.selectScope(Scope.List(7))
        vm.addQuick("写周报")
        advanceUntilIdle()
        assertEquals(7L, repo.addedListId)
    }

    @Test
    fun quickAddBlankIgnored() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = MainViewModel(repo)
        collect(vm)
        vm.addQuick("   ")
        advanceUntilIdle()
        assertNull(repo.addedTitle)
    }

    @Test
    fun toggleCompletedDelegates() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = MainViewModel(repo)
        collect(vm)
        val item = TodoItem(5, 1, "x", "", null, false, null, false, null, null, 0.0, Instant.fromEpochMilliseconds(0))
        vm.toggleCompleted(item)
        advanceUntilIdle()
        assertEquals(5L, repo.toggledId)
        assertEquals(true, repo.toggledValue)
    }

    @Test
    fun openDetailAndBack() {
        val vm = MainViewModel(FakeRepository())
        vm.openDetail(3)
        assertEquals(Route.Detail(3), vm.route.value)
        vm.back()
        assertEquals(Route.Main, vm.route.value)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :shared:desktopTest --tests "com.myapplication.shared.ui.main.MainViewModelTest"`
Expected: FAIL（`MainViewModel` 不存在）

- [ ] **Step 3: 实现 MainViewModel**

`MainViewModel.kt`：

```kotlin
package com.myapplication.shared.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.domain.repository.TodoRepository
import com.myapplication.shared.util.DateParser
import com.myapplication.shared.util.todayDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

sealed interface Route {
    data object Main : Route
    data class Detail(val todoId: Long) : Route
}

sealed interface Scope {
    data object Today : Scope
    data object Scheduled : Scope
    data object All : Scope
    data object Completed : Scope
    data object Trash : Scope
    data class List(val listId: Long) : Scope
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(private val repository: TodoRepository) : ViewModel() {

    val scope = MutableStateFlow<Scope>(Scope.All)
    val searchQuery = MutableStateFlow("")
    val route = MutableStateFlow<Route>(Route.Main)

    val lists: StateFlow<List<TodoList>> = repository.observeLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todos: StateFlow<List<TodoItem>> =
        combine(scope, searchQuery) { s, q -> s to q }
            .flatMapLatest { (s, q) ->
                if (q.isNotBlank()) repository.search(q.trim())
                else when (s) {
                    Scope.Today -> repository.observeToday()
                    Scope.Scheduled -> repository.observeScheduled()
                    Scope.All -> repository.observeAllActive()
                    Scope.Completed -> repository.observeCompleted()
                    Scope.Trash -> repository.observeTrashed()
                    is Scope.List -> repository.observeByList(s.listId)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun count(flow: Flow<List<TodoItem>>): StateFlow<Int> =
        flow.map { it.size }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val todayCount: StateFlow<Int> = count(repository.observeToday())
    val scheduledCount: StateFlow<Int> = count(repository.observeScheduled())
    val allCount: StateFlow<Int> = count(repository.observeAllActive())
    val completedCount: StateFlow<Int> = count(repository.observeCompleted())
    val trashCount: StateFlow<Int> = count(repository.observeTrashed())
    val listCounts: StateFlow<Map<Long, Int>> = repository.observeAllActive()
        .map { todos -> todos.groupingBy { it.listId }.eachCount() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        viewModelScope.launch { repository.ensureInbox() }
    }

    fun selectScope(s: Scope) {
        scope.value = s
    }

    fun setSearch(q: String) {
        searchQuery.value = q
    }

    fun openDetail(id: Long) {
        route.value = Route.Detail(id)
    }

    fun back() {
        route.value = Route.Main
    }

    fun addQuick(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return
        val parsed = DateParser.parse(trimmed, todayDate())
        val listId = (scope.value as? Scope.List)?.listId
        val due = parsed.dueDate?.toInstant(TimeZone.currentSystemDefault())
        viewModelScope.launch {
            repository.addTodo(listId, parsed.title, "", due, null)
        }
    }

    fun toggleCompleted(item: TodoItem) {
        viewModelScope.launch { repository.setCompleted(item.id, !item.isCompleted) }
    }

    fun trash(item: TodoItem) {
        viewModelScope.launch { repository.trash(item.id) }
    }

    fun restore(item: TodoItem) {
        viewModelScope.launch { repository.restore(item.id) }
    }

    fun deleteForever(item: TodoItem) {
        viewModelScope.launch { repository.deleteForever(item.id) }
    }

    fun addList(name: String, colorKey: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.addList(trimmed, colorKey) }
    }

    fun deleteList(list: TodoList) {
        viewModelScope.launch { repository.deleteList(list.id) }
    }
}
```

- [ ] **Step 4: 实现 DetailViewModel**

`DetailViewModel.kt`：

```kotlin
package com.myapplication.shared.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.domain.repository.TodoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

class DetailViewModel(
    private val repository: TodoRepository,
    private val todoId: Long,
) : ViewModel() {

    val todo: StateFlow<TodoItem?> = repository.observeTodo(todoId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val subtasks: StateFlow<List<TodoItem>> = repository.observeSubTasks(todoId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lists: StateFlow<List<TodoList>> = repository.observeLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setTitle(title: String) {
        viewModelScope.launch { repository.setTitle(todoId, title) }
    }

    fun setNote(note: String) {
        viewModelScope.launch { repository.setNote(todoId, note) }
    }

    fun setDueDate(due: LocalDateTime?) {
        viewModelScope.launch {
            repository.setDueDate(todoId, due?.toInstant(TimeZone.currentSystemDefault()))
        }
    }

    fun moveToList(listId: Long) {
        viewModelScope.launch { repository.moveToList(todoId, listId) }
    }

    fun addSubTask(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.addSubTask(todoId, trimmed) }
    }

    fun toggleSubTask(item: TodoItem) {
        viewModelScope.launch { repository.setCompleted(item.id, !item.isCompleted) }
    }

    fun trashSubTask(item: TodoItem) {
        viewModelScope.launch { repository.trash(item.id) }
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `./gradlew :shared:desktopTest --tests "com.myapplication.shared.ui.main.MainViewModelTest"`
Expected: `BUILD SUCCESSFUL`，5 个测试全绿

- [ ] **Step 6: 提交**

```bash
git add -A && git commit -m "feat(ui): add main and detail viewmodels with tests"
```

---

### Task 9: App 根组件——响应式路由与布局骨架

**Files:**
- Modify: `shared/src/commonMain/kotlin/App.kt`（整体替换）
- Modify: `shared/src/desktopMain/kotlin/main.desktop.kt`
- Modify: `shared/src/androidMain/kotlin/main.android.kt`

- [ ] **Step 1: 替换 App.kt**

```kotlin
package com.myapplication.shared.ui.app

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myapplication.shared.di.AppGraph
import com.myapplication.shared.di.createAppGraph
import com.myapplication.shared.ui.detail.DetailScreen
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.main.Route
import com.myapplication.shared.ui.sidebar.Sidebar
import com.myapplication.shared.ui.theme.RemindersTheme
import com.myapplication.shared.ui.todolist.TodoListScreen

@Composable
fun App() {
    RemindersTheme {
        val graph = remember { createAppGraph() }
        AppRoot(graph)
    }
}

@Composable
fun AppRoot(graph: AppGraph) {
    val mainVm: MainViewModel = viewModel { MainViewModel(graph.repository) }
    val route by mainVm.route.collectAsState()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 900.dp
        val selectedId = (route as? Route.Detail)?.todoId
        when {
            wide -> {
                Row(Modifier.fillMaxSize()) {
                    Sidebar(mainVm)
                    TodoListScreen(mainVm, Modifier.weight(1f))
                    if (selectedId != null) {
                        DetailScreen(mainVm, graph, selectedId)
                    }
                }
            }
            selectedId != null -> {
                DetailScreen(mainVm, graph, selectedId)
            }
            else -> {
                Column(Modifier.fillMaxSize()) {
                    NarrowTopBar(mainVm)
                    TodoListScreen(mainVm, Modifier.weight(1f))
                    NarrowBottomNav(mainVm)
                }
            }
        }
    }
}
```

注意：`Sidebar`、`TodoListScreen`、`DetailScreen`、`NarrowTopBar`、`NarrowBottomNav` 在后续任务中实现；先建空壳保证编译。

- [ ] **Step 2: 空壳组件（临时，Task 10-12 替换）**

在同文件追加：

```kotlin
@Composable
fun NarrowTopBar(mainVm: MainViewModel) {
    Text("提醒事项", style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxSize().let { it })
}

@Composable
fun NarrowBottomNav(mainVm: MainViewModel) {
    Text("")
}
```

`Sidebar`、`TodoListScreen`、`DetailScreen` 用最小签名在各自任务中创建（Task 10/11/12）。

- [ ] **Step 3: 更新两个平台壳文件的 import**

`main.desktop.kt` 改为：

```kotlin
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import com.myapplication.shared.ui.app.App

actual fun getPlatformName(): String = "Desktop"

@Composable fun MainView() = App()

@Preview
@Composable
fun AppPreview() {
    App()
}
```

`main.android.kt` 改为：

```kotlin
import androidx.compose.runtime.Composable
import com.myapplication.shared.ui.app.App

actual fun getPlatformName(): String = "Android"

@Composable fun MainView() = App()
```

- [ ] **Step 4: 验证编译**

Run: `./gradlew :shared:compileKotlinDesktop`
Expected: FAIL——因为 `Sidebar`/`TodoListScreen`/`DetailScreen` 尚不存在。这是预期的失败：立即在 Task 10-12 补齐。若想在中间状态编译通过，可将上一步的空壳改为：

```kotlin
@Composable fun Sidebar(mainVm: MainViewModel) = Text("")
@Composable fun TodoListScreen(mainVm: MainViewModel, modifier: Modifier = Modifier) = Text("")
@Composable fun DetailScreen(mainVm: MainViewModel, graph: AppGraph, todoId: Long) = Text("")
```

- [ ] **Step 5: 提交**

```bash
git add -A && git commit -m "refactor(ui): replace template app with responsive root"
```

---

### Task 10: 侧边栏 + 列表管理对话框

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/sidebar/Sidebar.kt`

- [ ] **Step 1: 实现 Sidebar**

```kotlin
package com.myapplication.shared.ui.sidebar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.main.Scope
import com.myapplication.shared.ui.theme.ListColorKeys
import com.myapplication.shared.ui.theme.ListColorOf

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Sidebar(mainVm: MainViewModel) {
    val lists by mainVm.lists.collectAsState()
    val scope by mainVm.scope.collectAsState()
    val query by mainVm.searchQuery.collectAsState()
    val todayCount by mainVm.todayCount.collectAsState()
    val scheduledCount by mainVm.scheduledCount.collectAsState()
    val allCount by mainVm.allCount.collectAsState()
    val completedCount by mainVm.completedCount.collectAsState()
    val trashCount by mainVm.trashCount.collectAsState()
    val listCounts by mainVm.listCounts.collectAsState()

    var showAddList by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxHeight()
            .width(220.dp)
            .padding(12.dp),
    ) {
        Text("提醒事项", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        TextField(
            value = query,
            onValueChange = mainVm::setSearch,
            placeholder = { Text("搜索") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        ScopeRow("📅 今天", todayCount, scope == Scope.Today) { mainVm.selectScope(Scope.Today) }
        ScopeRow("🗓 计划", scheduledCount, scope == Scope.Scheduled) { mainVm.selectScope(Scope.Scheduled) }
        ScopeRow("🗂 全部待办", allCount, scope == Scope.All) { mainVm.selectScope(Scope.All) }
        ScopeRow("✓ 已完成", completedCount, scope == Scope.Completed) { mainVm.selectScope(Scope.Completed) }
        ScopeRow("🗑 垃圾箱", trashCount, scope == Scope.Trash) { mainVm.selectScope(Scope.Trash) }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("我的列表", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        lists.forEach { list ->
            ListRow(
                list = list,
                count = listCounts[list.id] ?: 0,
                selected = scope == Scope.List(list.id),
                onSelect = { mainVm.selectScope(Scope.List(list.id)) },
                onDelete = { mainVm.deleteList(list) },
            )
        }
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = { showAddList = true }, modifier = Modifier.fillMaxWidth()) {
            Text("＋ 添加列表")
        }
    }

    if (showAddList) {
        AddListDialog(onDismiss = { showAddList = false }) { name, color ->
            mainVm.addList(name, color)
            showAddList = false
        }
    }
}

@Composable
private fun ScopeRow(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .combinedClickable(onClick = onClick, onLongClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (selected) color else MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.weight(1f))
        if (count > 0) {
            Text(
                count.toString(),
                color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListRow(
    list: TodoList,
    count: Int,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .combinedClickable(onClick = onSelect, onLongClick = { menuOpen = true }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(ListColorOf[list.colorKey] ?: Color.Gray, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Text(list.name)
            Spacer(Modifier.weight(1f))
            if (count > 0) {
                Text(count.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("删除列表") }, onClick = {
                menuOpen = false
                onDelete()
            })
        }
    }
}

@Composable
private fun AddListDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var colorKey by remember { mutableStateOf(ListColorKeys.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建列表") },
        text = {
            Column {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("列表名称") },
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                Row {
                    ListColorKeys.forEach { key ->
                        Box(
                            Modifier
                                .padding(end = 8.dp)
                                .size(24.dp)
                                .background(
                                    ListColorOf[key] ?: Color.Gray,
                                    CircleShape,
                                )
                                .combinedClickable(onClick = { colorKey = key }, onLongClick = {}),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) onConfirm(name, colorKey)
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
```

- [ ] **Step 2: 验证编译**

Run: `./gradlew :shared:compileKotlinDesktop`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 提交**

```bash
git add -A && git commit -m "feat(ui): sidebar with scopes, lists and add/delete dialogs"
```

---

### Task 11: 待办列表 + 快速输入 + 行组件

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/todolist/TodoListScreen.kt`

- [ ] **Step 1: 实现 TodoListScreen**

```kotlin
package com.myapplication.shared.ui.todolist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.main.Scope
import com.myapplication.shared.util.bucketLabel
import com.myapplication.shared.util.bucketOf
import com.myapplication.shared.util.formatDueDate
import com.myapplication.shared.util.todayDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun TodoListScreen(mainVm: MainViewModel, modifier: Modifier = Modifier) {
    val todos by mainVm.todos.collectAsState()
    val scope by mainVm.scope.collectAsState()
    val query by mainVm.searchQuery.collectAsState()

    Column(modifier) {
        Text(
            scopeTitle(scope, query),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        if (scope != Scope.Trash) {
            QuickAddRow(mainVm)
        }
        val today = todayDate()
        when (scope) {
            Scope.Scheduled -> ScheduledGrouped(todos, today, mainVm)
            Scope.Trash -> TrashList(todos, mainVm)
            else -> PlainList(todos, today, mainVm)
        }
    }
}

fun scopeTitle(scope: Scope, query: String): String = when {
    query.isNotBlank() -> "搜索"
    scope == Scope.Today -> "今天"
    scope == Scope.Scheduled -> "计划"
    scope == Scope.All -> "全部待办"
    scope == Scope.Completed -> "已完成"
    scope == Scope.Trash -> "垃圾箱"
    is Scope.List -> "列表"
    else -> "待办"
}

@Composable
private fun QuickAddRow(mainVm: MainViewModel) {
    var text by remember { mutableStateOf("") }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text("＋ 添加待办…（支持“明天 15:00”等日期）") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            mainVm.addQuick(text)
            text = ""
        }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    )
}

@Composable
private fun PlainList(todos: List<TodoItem>, today: kotlinx.datetime.LocalDate, mainVm: MainViewModel) {
    val active = todos.filter { !it.isCompleted && it.parentId == null }
    val childrenByParent = todos.filter { it.parentId != null }.groupBy { it.parentId!! }
    val completed = todos.filter { it.isCompleted && it.parentId == null }
    var expanded by remember { mutableStateOf(true) }

    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        items(active) { parent ->
            TodoRow(parent, mainVm, today, showChevron = childrenByParent[parent.id] != null, expanded = expanded, onToggleExpand = { expanded = !expanded })
            if (expanded) {
                childrenByParent[parent.id]?.forEach { child ->
                    TodoRow(child, mainVm, today, indent = true)
                }
            }
        }
        if (completed.isNotEmpty()) {
            item {
                Text(
                    "已完成",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                )
            }
            items(completed) { item ->
                TodoRow(item, mainVm, today)
            }
        }
    }
}

@Composable
private fun ScheduledGrouped(todos: List<TodoItem>, today: kotlinx.datetime.LocalDate, mainVm: MainViewModel) {
    val tz = TimeZone.currentSystemDefault()
    val grouped = todos
        .filter { it.dueDate != null }
        .groupBy { bucketOf(it.dueDate!!.toLocalDateTime(tz).date, today) }
    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        listOf(
            com.myapplication.shared.util.DueBucket.OVERDUE,
            com.myapplication.shared.util.DueBucket.TODAY,
            com.myapplication.shared.util.DueBucket.TOMORROW,
            com.myapplication.shared.util.DueBucket.THIS_WEEK,
            com.myapplication.shared.util.DueBucket.LATER,
        ).forEach { bucket ->
            val items = grouped[bucket].orEmpty()
            if (items.isNotEmpty()) {
                item {
                    Text(
                        bucketLabel(bucket),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                    )
                }
                items(items) { item -> TodoRow(item, mainVm, today) }
            }
        }
    }
}

@Composable
private fun TrashList(todos: List<TodoItem>, mainVm: MainViewModel) {
    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        items(todos) { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(item.title, Modifier.weight(1f))
                TextButton(onClick = { mainVm.restore(item) }) { Text("恢复") }
                TextButton(onClick = { mainVm.deleteForever(item) }) { Text("彻底删除") }
            }
        }
    }
}

@Composable
fun TodoRow(
    item: TodoItem,
    mainVm: MainViewModel,
    today: kotlinx.datetime.LocalDate,
    indent: Boolean = false,
    showChevron: Boolean = false,
    expanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
) {
    val isOverdue = item.dueDate?.let {
        it.toLocalDateTime(TimeZone.currentSystemDefault()).date < today
    } == true
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { if (showChevron) onToggleExpand() else mainVm.openDetail(item.id) }
            .padding(start = if (indent) 28.dp else 0.dp)
            .padding(vertical = 6.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(18.dp)
                .background(
                    if (item.isCompleted) MaterialTheme.colorScheme.primary else Color.Transparent,
                    CircleShape,
                )
                .clickable { mainVm.toggleCompleted(item) },
        ) {
            if (item.isCompleted) {
                Text(
                    "✓",
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(start = 3.dp, top = 1.dp),
                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                )
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(3.dp)
                        .background(MaterialTheme.colorScheme.outline, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
                color = if (item.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            if (item.note.isNotBlank()) {
                Text(
                    item.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        if (showChevron) {
            Text(if (expanded) "⌄" else "›", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item.dueDate?.let {
            val label = formatDueDate(it)
            val overdueColor = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = overdueColor,
                modifier = Modifier
                    .background(
                        if (isOverdue) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        CircleShape,
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `./gradlew :shared:compileKotlinDesktop`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 提交**

```bash
git add -A && git commit -m "feat(ui): todo list, quick add and scheduled grouping"
```

---

### Task 12: 详情页（标题/备注/日期/列表/子任务/移入回收站）

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/detail/DetailScreen.kt`

- [ ] **Step 1: 实现 DetailScreen**

```kotlin
package com.myapplication.shared.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myapplication.shared.di.AppGraph
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.theme.ListColorOf
import com.myapplication.shared.util.formatDueDate
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun DetailScreen(mainVm: MainViewModel, graph: AppGraph, todoId: Long) {
    val detailVm: DetailViewModel = viewModel(key = "detail-$todoId") {
        DetailViewModel(graph.repository, todoId)
    }
    val todo by detailVm.todo.collectAsState()
    val subtasks by detailVm.subtasks.collectAsState()
    val lists by detailVm.lists.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var showListMenu by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .widthIn(max = 420.dp)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = mainVm::back) { Text("‹ 返回") }
            Spacer(Modifier.weight(1f))
        }
        val current = todo
        if (current == null) {
            Text("待办不存在或已删除", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        OutlinedTextField(
            value = current.title,
            onValueChange = detailVm::setTitle,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = current.note,
            onValueChange = detailVm::setNote,
            placeholder = { Text("备注…（阶段二将支持 Markdown 富文本与图片）") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        // 日期
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { showDatePicker = true }
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("📅 日期", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(if (current.dueDate != null) formatDueDate(current.dueDate) else "无")
            if (current.dueDate != null) {
                TextButton(onClick = { detailVm.setDueDate(null) }) { Text("清除") }
            }
        }
        Spacer(Modifier.height(8.dp))

        // 列表
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { showListMenu = true }
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🗂 列表", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                val currentList = lists.firstOrNull { it.id == current.listId }
                Box(
                    Modifier
                        .size(10.dp)
                        .background(ListColorOf[currentList?.colorKey] ?: Color.Gray, CircleShape),
                )
                Spacer(Modifier.width(6.dp))
                Text(currentList?.name ?: "未知列表")
            }
            DropdownMenu(expanded = showListMenu, onDismissRequest = { showListMenu = false }) {
                lists.forEach { list ->
                    DropdownMenuItem(
                        text = { Text(list.name) },
                        onClick = {
                            showListMenu = false
                            detailVm.moveToList(list.id)
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // 子任务
        Text("子任务", style = MaterialTheme.typography.titleMedium)
        subtasks.forEach { sub ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(16.dp)
                        .background(
                            if (sub.isCompleted) MaterialTheme.colorScheme.primary else Color.Transparent,
                            CircleShape,
                        )
                        .clickable { detailVm.toggleSubTask(sub) },
                ) {
                    if (sub.isCompleted) Text("✓", color = MaterialTheme.colorScheme.onPrimary, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                }
                Spacer(Modifier.width(8.dp))
                Text(sub.title, Modifier.weight(1f))
                TextButton(onClick = { detailVm.trashSubTask(sub) }) { Text("🗑") }
            }
        }
        var newSub by remember { mutableStateOf("") }
        OutlinedTextField(
            value = newSub,
            onValueChange = { newSub = it },
            placeholder = { Text("＋ 添加子任务…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        ) { TextButton(onClick = {
            detailVm.addSubTask(newSub)
            newSub = ""
        }) { Text("添加") } }
        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = {
                mainVm.trash(current)
                mainVm.back()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("移到垃圾箱", color = MaterialTheme.colorScheme.error)
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    stateDatePickerMillis?.let { ms ->
                        val date = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.UTC).date
                        val time = current.dueDate
                            ?.toLocalDateTime(TimeZone.currentSystemDefault())?.time
                            ?: LocalTime(9, 0)
                        detailVm.setDueDate(LocalDateTime(date, time))
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            val state = rememberDatePickerState()
            stateDatePickerMillis = state.selectedDateMillis
            DatePicker(state = state)
        }
    }
}
```

注意：`stateDatePickerMillis` 需要状态持有。将上面 `DetailScreen` 开头的 `var showDatePicker by remember { mutableStateOf(false) }` 改为同时声明：

```kotlin
    var showDatePicker by remember { mutableStateOf(false) }
    var stateDatePickerMillis by remember { mutableStateOf<Long?>(null) }
```

并把 `rememberDatePickerState()` 的调用移到 `DetailScreen` 顶部（对话框内每次重组会重建）：

```kotlin
    val datePickerState = if (showDatePicker) rememberDatePickerState() else null
```

对话框内使用 `datePickerState`，确认回调读取 `datePickerState?.selectedDateMillis`。请按此最终版本实现，保证日期选择状态稳定。

- [ ] **Step 2: 编译并修复**

Run: `./gradlew :shared:compileKotlinDesktop`
Expected: `BUILD SUCCESSFUL`（若 DatePicker 需要 opt-in，顶部加 `@OptIn(ExperimentalMaterial3Api::class)`）

- [ ] **Step 3: 提交**

```bash
git add -A && git commit -m "feat(ui): detail screen with date, list, subtasks and trash"
```

---

### Task 13: 平台接线（窗口/上下文/应用名）

**Files:**
- Modify: `desktopApp/src/jvmMain/kotlin/main.kt`
- Modify: `desktopApp/build.gradle.kts`
- Modify: `androidApp/src/androidMain/kotlin/com/myapplication/MainActivity.kt`
- Modify: `androidApp/src/androidMain/res/values/strings.xml`

- [ ] **Step 1: desktopApp main.kt——窗口配置**

```kotlin
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "提醒事项",
        state = rememberWindowState(width = 1000.dp, height = 680.dp),
    ) {
        window.minimumSize = Dimension(720, 500)
        MainView()
    }
}
```

- [ ] **Step 2: desktopApp/build.gradle.kts——应用名**

`packageName = "KotlinMultiplatformComposeDesktopApplication"` 改为 `packageName = "Reminders"`。

- [ ] **Step 3: MainActivity——注入 Android Context**

```kotlin
package com.myapplication

import MainView
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.myapplication.shared.data.setAndroidAppContext

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAndroidAppContext(applicationContext)
        setContent {
            MainView()
        }
    }
}
```

- [ ] **Step 4: strings.xml——应用名**

将 `app_name` 的值改为 `提醒事项`（`androidApp/src/androidMain/res/values/strings.xml`）。

- [ ] **Step 5: 双端构建验证**

Run: `./gradlew :desktopApp:packageDistributionForCurrentOS :androidApp:assembleDebug`
Expected: `BUILD SUCCESSFUL`；产出 DMG 与 `androidApp-release` 签名配置不变（debug APK 用 debug key）

- [ ] **Step 6: 提交**

```bash
git add -A && git commit -m "feat(app): wire platform entries, window config and app name"
```

---

### Task 14: 端到端验证 + 手工验收清单

**Files:**
- （无）

- [ ] **Step 1: 全量测试**

Run: `./gradlew :shared:desktopTest`
Expected: 全部测试通过

- [ ] **Step 2: 桌面冒烟测试**

Run: `./gradlew :desktopApp:run`
Expected: 应用窗口打开（标题"提醒事项"），验证以下清单后关闭。可用 `timeout 60 ./gradlew :desktopApp:run` 自动退出。

- [ ] **Step 3: 手工验收清单（macOS 桌面）**

1. 首次启动自动创建"收件箱"列表
2. 快速输入"明天 15:00 交季度报告"→ 回车 → 列表出现"交季度报告"带"明天 15:00"徽章
3. 输入"买牛奶"→ 无日期徽章
4. 点击圆圈完成 → 移到"已完成"分组，标题划线
5. 侧边栏"今天"→ 只显示今天的待办；"计划"→ 按过期/今天/明天/本周/计划分组
6. 搜索"牛奶"→ 只显示匹配项
7. 长按（或右键）列表 → 删除列表 → 该列表待办全部进垃圾箱
8. 添加列表（选颜色）→ 出现在侧边栏；快速输入会加进当前选中列表
9. 点击待办 → 详情：改标题/备注、设日期（选择器）、换列表、添加子任务（缩进显示）、子任务完成
10. 垃圾箱：恢复 / 彻底删除
11. 深色模式：系统切换深色 → 应用跟随

- [ ] **Step 4: Android 验证（可选，需模拟器）**

```bash
~/Library/Android/sdk/cmdline-tools/latest/bin/sdkmanager "system-images;android-36;google_apis;arm64-v8a"
~/Library/Android/sdk/cmdline-tools/latest/bin/avdmanager create avd -n reminders -k "system-images;android-36;google_apis;arm64-v8a" -d pixel_7
~/Library/Android/sdk/emulator/emulator -avd reminders &
~/Library/Android/sdk/platform-tools/adb wait-for-device
~/Library/Android/sdk/platform-tools/adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

Expected: 模拟器打开应用，重复 Step 3 清单（窄屏为底部导航形态）。确认后 `adb emu kill` 关闭。

- [ ] **Step 5: 最终提交**

```bash
git add -A && git commit -m "chore: finalize reminders mvp verification"
```

---

## 自检记录（planning 时已执行）

- **Spec 覆盖**：多列表（T3/T10）、快速输入+日期解析（T5/T8/T11）、完成/未完成（T2/T8/T11）、子任务（T2/T4/T12）、今日视图（T4/T11）、搜索（T2/T4/T11）、深色模式（T7）、回收站（T2/T4/T11/T12）、响应式布局（T9）、平台接线（T13）、测试（T4/T5/T8/T14）。Spec 第 9 节范围外项未纳入。
- **占位符扫描**：全部步骤含完整代码与命令；无 TBD/TODO。
- **类型一致性**：`observeTodo`/`addSubTask`/`Scope.List`/`Route.Detail` 等在实现与测试中签名一致；`insertTodo` 参数序与 .sq 定义一致（list_id, title, note, due_date, parent_id, sort_position, created_at）。
- **已知取舍**：今日视图边界在跨零点时不会自动刷新（MVP 接受）；`addTodo` 无列表时默认 inbox id=1（`ensureInbox` 保证存在）。
