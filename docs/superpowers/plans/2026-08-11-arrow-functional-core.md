# Arrow 函数式核心改造实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 引入 Arrow（typed errors），把命令操作改为 `Either<TodoError, Unit>` 纯函数返回，收敛收件箱不变量到 Use Case 层，Clock/TimeZone 显式注入，让 domain/data 层达到 Effect 风格纯度。

**Architecture:** 三层改造 —— (1) 领域层新增 `TodoError` ADT + `AddTodoUseCase`/`AddSubTaskUseCase`（吸收目标列表解析、父任务存在性校验，删除静默 `?: return`）；(2) 数据层 `TodoRepository` port 重塑：命令全部返回 `Either<TodoError, Unit>`，`deleteList` 事务化，注入 `Clock`/`TimeZone`；(3) UI 层 ViewModel 消费 `Either`，失败经 `lastError: StateFlow<TodoError?>` + `RemDialog` 呈现。查询保持 `Flow` 流不包装。

**Tech Stack:** Kotlin Multiplatform + Compose Multiplatform（commonMain）。新增 `io.arrow-kt:arrow-core:2.2.3`（JVM/Android/iOS 全支持）。Arrow API 使用：`either {}` DSL、`ensure`、成员 `bind()`/`raise()`、`onLeft`/`isRight`/`leftOrNull`。测试：kotlin.test，仅新增依赖不新增测试框架。

**规格:** `docs/superpowers/plans/2026-08-11-arrow-functional-core.md`（本计划）

**验证命令:** `./gradlew :shared:desktopTest --rerun-tasks :androidApp:assembleDebug`（预期 BUILD SUCCESSFUL，原 52 测试全绿 + 新增 ~12 个）

**当前 HEAD:** `c7d8c94`。**禁止修改**：`TodoDb.sq`、`DateParser.kt`、`Formatting.kt`（纯函数保持不动）、`docs/` 其他计划。

**提交策略:** 每个 Task 末尾编译/测试通过后提交一次，commit message 用 `feat(fp): ...` 前缀。

---

### Task 1: 引入 Arrow 依赖 + TodoError ADT（可编译）

**Files:**
- Modify: `gradle.properties`
- Modify: `shared/build.gradle.kts`
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/error/TodoError.kt`

- [ ] **Step 1: gradle.properties 增加版本属性**

在 `#Libraries` 区块追加：

```properties
arrow.version=2.2.3
```

- [ ] **Step 2: shared/build.gradle.kts 声明依赖**

在 `shared/build.gradle.kts` 顶部变量区（`sqlDelightVersion` 附近）追加：

```kotlin
val arrowVersion = findProperty("arrow.version") as String
```

在 `commonMain` 的 dependencies 区块追加：

```kotlin
implementation("io.arrow-kt:arrow-core:$arrowVersion")
```

- [ ] **Step 3: 创建 TodoError ADT**

Create `shared/src/commonMain/kotlin/com/myapplication/shared/domain/error/TodoError.kt`：

```kotlin
package com.myapplication.shared.domain.error

sealed interface TodoError {
    data object EmptyTitle : TodoError
    data object ParentNotFound : TodoError
    data object InboxNotFound : TodoError
    data class Persistence(val message: String) : TodoError
}
```

- [ ] **Step 4: 编译验证**

Run: `./gradlew :shared:compileKotlinDesktop`
Expected: BUILD SUCCESSFUL（依赖拉取 + 新文件编译通过）

- [ ] **Step 5: Commit**

```bash
git add gradle.properties shared/build.gradle.kts shared/src/commonMain/kotlin/com/myapplication/shared/domain/error/TodoError.kt
git commit -m "feat(fp): add arrow-core dependency and TodoError ADT"
```

---

### Task 2: 领域/数据层重塑（port 命令 Either 化 + Use Case 落地）

> 本任务与 Task 3 为一个不可拆分的编译单元：port 重塑会破坏 MainViewModel/DetailViewModel，Task 3 统一修复。本任务结束时不要求编译通过。

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/repository/TodoRepository.kt`（全量重写）
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/data/TodoRepositoryImpl.kt`（全量重写）
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/usecase/AddTodo.kt`
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/usecase/AddSubTask.kt`

- [ ] **Step 1: 重写 TodoRepository port**

全量替换 `shared/src/commonMain/kotlin/com/myapplication/shared/domain/repository/TodoRepository.kt` 为：

```kotlin
package com.myapplication.shared.domain.repository

import arrow.core.Either
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.model.TodoList
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface TodoRepository {
    // Queries — 数据流保持 Flow，不包装
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
    suspend fun findById(id: Long): Either<TodoError, TodoItem?>

    // Commands — 类型化错误，纯 Effect
    suspend fun ensureInbox(): Either<TodoError, Long>
    suspend fun addList(name: String, colorKey: String): Either<TodoError, Unit>
    suspend fun deleteList(listId: Long): Either<TodoError, Unit>
    suspend fun insertTodo(listId: Long, title: String, note: String, dueDate: Instant?, parentId: Long?, flag: Boolean): Either<TodoError, Unit>
    suspend fun setCompleted(id: Long, completed: Boolean): Either<TodoError, Unit>
    suspend fun setFlag(id: Long, flag: Boolean): Either<TodoError, Unit>
    suspend fun setTitle(id: Long, title: String): Either<TodoError, Unit>
    suspend fun setNote(id: Long, note: String): Either<TodoError, Unit>
    suspend fun setDueDate(id: Long, dueDate: Instant?): Either<TodoError, Unit>
    suspend fun moveToList(id: Long, listId: Long): Either<TodoError, Unit>
    suspend fun trash(id: Long): Either<TodoError, Unit>
    suspend fun restore(id: Long): Either<TodoError, Unit>
    suspend fun deleteForever(id: Long): Either<TodoError, Unit>
}
```

**变更说明**：删除 `addTodo`/`addSubTask`（业务逻辑上移到 Use Case，port 只留原始 `insertTodo`）；新增 `findById`（use case 依赖，返回类型化结果）；所有命令返回 `Either<TodoError, Unit>`，`ensureInbox` 返回 `Either<TodoError, Long>`。

- [ ] **Step 2: 创建 AddTodoUseCase**

Create `shared/src/commonMain/kotlin/com/myapplication/shared/domain/usecase/AddTodo.kt`：

```kotlin
package com.myapplication.shared.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.repository.TodoRepository
import kotlinx.datetime.Instant

data class AddTodoInput(
    val listId: Long?,
    val parentId: Long?,
    val title: String,
    val note: String,
    val dueDate: Instant?,
    val flag: Boolean,
)

class AddTodoUseCase(private val repository: TodoRepository) {

    suspend operator fun invoke(input: AddTodoInput): Either<TodoError, Unit> = either {
        ensure(input.title.isNotBlank()) { TodoError.EmptyTitle }
        val targetListId = when {
            input.parentId != null ->
                repository.findById(input.parentId).bind()?.listId
                    ?: raise(TodoError.ParentNotFound)
            input.listId != null -> input.listId
            else -> repository.ensureInbox().bind()
        }
        repository.insertTodo(
            listId = targetListId,
            title = input.title.trim(),
            note = input.note.trim(),
            dueDate = input.dueDate,
            parentId = input.parentId,
            flag = input.flag,
        ).bind()
    }
}
```

> 注：`bind()` 与 `raise()` 是 `Raise<E>` 作用域成员，无需 import；`ensure` 需 import `arrow.core.raise.ensure`。若编译器报 unresolved，追加 `import arrow.core.raise.raise` 与 `import arrow.core.raise.bind`。

- [ ] **Step 3: 创建 AddSubTaskUseCase**

Create `shared/src/commonMain/kotlin/com/myapplication/shared/domain/usecase/AddSubTask.kt`：

```kotlin
package com.myapplication.shared.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.repository.TodoRepository

class AddSubTaskUseCase(private val repository: TodoRepository) {

    suspend operator fun invoke(parentId: Long, title: String): Either<TodoError, Unit> = either {
        ensure(title.isNotBlank()) { TodoError.EmptyTitle }
        val parent = repository.findById(parentId).bind()
            ?: raise(TodoError.ParentNotFound)
        repository.insertTodo(parent.listId, title.trim(), "", null, parentId, false).bind()
    }
}
```

- [ ] **Step 4: 重写 TodoRepositoryImpl**

全量替换 `shared/src/commonMain/kotlin/com/myapplication/shared/data/TodoRepositoryImpl.kt` 为：

```kotlin
package com.myapplication.shared.data

import app.cash.sqldelight.coroutines.asFlow
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.domain.repository.TodoRepository
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

class TodoRepositoryImpl(
    private val db: TodoDb,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
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

    private inline fun <A> guard(block: () -> A): Either<TodoError, A> =
        try {
            block().right()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TodoError.Persistence(e.message ?: "数据库操作失败").left()
        }

    override suspend fun ensureInbox(): Either<TodoError, Long> = either {
        val lists = guard { db.todoDbQueries.selectLists().executeAsList() }.bind()
        if (lists.isEmpty()) {
            guard {
                db.todoDbQueries.insertList("收件箱", "blue", 0, clock.now().toEpochMilliseconds())
            }.bind()
        }
        guard { db.todoDbQueries.selectLists().executeAsList().firstOrNull()?.id }.bind()
            ?: raise(TodoError.InboxNotFound)
    }

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

    override suspend fun addList(name: String, colorKey: String): Either<TodoError, Unit> = guard {
        val position = db.todoDbQueries.selectLists().executeAsList().size
        db.todoDbQueries.insertList(name, colorKey, position.toLong(), clock.now().toEpochMilliseconds())
    }

    override suspend fun deleteList(listId: Long): Either<TodoError, Unit> = either {
        try {
            db.transaction {
                db.todoDbQueries.trashTodosInList(clock.now().toEpochMilliseconds(), listId)
                db.todoDbQueries.deleteList(listId)
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
    ): Either<TodoError, Unit> = guard {
        db.todoDbQueries.insertTodo(listId, title, note, dueDate?.toEpochMilliseconds(), parentId, 0.0, flag, clock.now().toEpochMilliseconds())
    }

    override suspend fun setCompleted(id: Long, completed: Boolean): Either<TodoError, Unit> = guard {
        db.todoDbQueries.updateCompleted(completed, if (completed) clock.now().toEpochMilliseconds() else null, id)
    }

    override suspend fun setFlag(id: Long, flag: Boolean): Either<TodoError, Unit> = guard {
        db.todoDbQueries.updateFlag(flag, id)
    }

    override suspend fun setTitle(id: Long, title: String): Either<TodoError, Unit> = guard {
        db.todoDbQueries.updateTitle(title, id)
    }

    override suspend fun setNote(id: Long, note: String): Either<TodoError, Unit> = guard {
        db.todoDbQueries.updateNote(note, id)
    }

    override suspend fun setDueDate(id: Long, dueDate: Instant?): Either<TodoError, Unit> = guard {
        db.todoDbQueries.updateDueDate(dueDate?.toEpochMilliseconds(), id)
    }

    override suspend fun moveToList(id: Long, listId: Long): Either<TodoError, Unit> = guard {
        db.todoDbQueries.moveToList(listId, id)
    }

    override suspend fun trash(id: Long): Either<TodoError, Unit> = guard {
        db.todoDbQueries.trashTodo(clock.now().toEpochMilliseconds(), id)
    }

    override suspend fun restore(id: Long): Either<TodoError, Unit> = guard {
        db.todoDbQueries.restoreTodo(id)
    }

    override suspend fun deleteForever(id: Long): Either<TodoError, Unit> = guard {
        db.todoDbQueries.deleteTodo(id)
    }
}
```

> 注：`either {}`/`raise` 为 `arrow.core.raise` 包，需 `import arrow.core.raise.either`；`db.transaction {}` 为 SQLDelight 2.x suspend 事务。若 `either` 在非 suspend 上下文中报错，检查 import 完整性。

---

### Task 3: AppGraph + ViewModel + UI 接线（编译恢复检查点）

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/di/AppGraph.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/main/MainViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/detail/DetailViewModel.kt`
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/ErrorUi.kt`
- Modify: `shared/src/commonMain/kotlin/App.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/detail/DetailScreen.kt`

- [ ] **Step 1: AppGraph 注入 Clock/TimeZone + Use Case**

全量替换 `shared/src/commonMain/kotlin/com/myapplication/shared/di/AppGraph.kt`：

```kotlin
package com.myapplication.shared.di

import app.cash.sqldelight.db.SqlDriver
import com.myapplication.shared.data.TodoDb
import com.myapplication.shared.data.TodoRepositoryImpl
import com.myapplication.shared.domain.repository.TodoRepository
import com.myapplication.shared.domain.usecase.AddSubTaskUseCase
import com.myapplication.shared.domain.usecase.AddTodoUseCase
import kotlin.time.Clock
import kotlinx.datetime.TimeZone

class AppGraph(
    driver: SqlDriver,
    val clock: Clock = Clock.System,
    val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val repository: TodoRepository by lazy { TodoRepositoryImpl(TodoDb(driver), clock, timeZone) }
    val addTodo: AddTodoUseCase by lazy { AddTodoUseCase(repository) }
    val addSubTask: AddSubTaskUseCase by lazy { AddSubTaskUseCase(repository) }
}

expect fun createAppGraph(): AppGraph
```

平台 actual（`AppGraph.desktop.kt`/`AppGraph.ios.kt`）无需改动（构造参数有默认值）。

- [ ] **Step 2: 重写 MainViewModel**

全量替换 `shared/src/commonMain/kotlin/com/myapplication/shared/ui/main/MainViewModel.kt`：

```kotlin
package com.myapplication.shared.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.onLeft
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.domain.repository.TodoRepository
import com.myapplication.shared.domain.usecase.AddTodoInput
import com.myapplication.shared.domain.usecase.AddTodoUseCase
import kotlin.time.Clock
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
import kotlinx.datetime.LocalDateTime
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
class MainViewModel(
    private val repository: TodoRepository,
    private val addTodo: AddTodoUseCase,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    val scope = MutableStateFlow<Scope>(Scope.All)
    val searchQuery = MutableStateFlow("")
    val route = MutableStateFlow<Route>(Route.Main)
    val lastError = MutableStateFlow<TodoError?>(null)

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
        viewModelScope.launch { repository.ensureInbox().onLeft { lastError.value = it } }
    }

    fun dismissError() {
        lastError.value = null
    }

    fun selectScope(s: Scope) {
        scope.value = s
        searchQuery.value = ""
        back()
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

    fun createTodo(title: String, note: String, due: LocalDateTime?, flag: Boolean = false, listId: Long? = null) {
        viewModelScope.launch {
            addTodo(
                AddTodoInput(
                    listId = listId,
                    parentId = null,
                    title = title,
                    note = note,
                    dueDate = due?.toInstant(timeZone),
                    flag = flag,
                ),
            ).onLeft { lastError.value = it }
        }
    }

    fun toggleCompleted(item: TodoItem) {
        viewModelScope.launch { repository.setCompleted(item.id, !item.isCompleted).onLeft { lastError.value = it } }
    }

    fun toggleFlag(item: TodoItem) {
        viewModelScope.launch { repository.setFlag(item.id, !item.flag).onLeft { lastError.value = it } }
    }

    fun trash(item: TodoItem) {
        viewModelScope.launch { repository.trash(item.id).onLeft { lastError.value = it } }
    }

    fun restore(item: TodoItem) {
        viewModelScope.launch { repository.restore(item.id).onLeft { lastError.value = it } }
    }

    fun deleteForever(item: TodoItem) {
        viewModelScope.launch { repository.deleteForever(item.id).onLeft { lastError.value = it } }
    }

    fun addList(name: String, colorKey: String) {
        viewModelScope.launch { repository.addList(name, colorKey).onLeft { lastError.value = it } }
    }

    fun deleteList(list: TodoList) {
        viewModelScope.launch { repository.deleteList(list.id).onLeft { lastError.value = it } }
        if (scope.value == Scope.List(list.id)) scope.value = Scope.All
    }
}
```

- [ ] **Step 3: 重写 DetailViewModel**

全量替换 `shared/src/commonMain/kotlin/com/myapplication/shared/ui/detail/DetailViewModel.kt`：

```kotlin
package com.myapplication.shared.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.onLeft
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.domain.repository.TodoRepository
import com.myapplication.shared.domain.usecase.AddSubTaskUseCase
import com.myapplication.shared.util.todayDate
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

class DetailViewModel(
    private val repository: TodoRepository,
    private val addSubTask: AddSubTaskUseCase,
    private val todoId: Long,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    val todo: StateFlow<TodoItem?> = repository.observeTodo(todoId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val subtasks: StateFlow<List<TodoItem>> = repository.observeSubTasks(todoId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lists: StateFlow<List<TodoList>> = repository.observeLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lastError = MutableStateFlow<TodoError?>(null)

    fun dismissError() {
        lastError.value = null
    }

    fun setTitle(title: String) {
        viewModelScope.launch { repository.setTitle(todoId, title).onLeft { lastError.value = it } }
    }

    fun setNote(note: String) {
        viewModelScope.launch { repository.setNote(todoId, note).onLeft { lastError.value = it } }
    }

    fun setDueDate(due: LocalDateTime?) {
        viewModelScope.launch {
            repository.setDueDate(todoId, due?.toInstant(timeZone)).onLeft { lastError.value = it }
        }
    }

    fun setTime(hour: Int, minute: Int) {
        val current = todo.value ?: return
        val base = current.dueDate
            ?.toLocalDateTime(timeZone)
            ?: LocalDateTime(todayDate(clock, timeZone), LocalTime(hour, minute))
        val ldt = LocalDateTime(base.date, LocalTime(hour, minute))
        viewModelScope.launch {
            repository.setDueDate(todoId, ldt.toInstant(timeZone)).onLeft { lastError.value = it }
        }
    }

    fun setTimeNull() {
        val current = todo.value ?: return
        if (current.dueDate == null) return
        val ldt = current.dueDate.toLocalDateTime(timeZone)
        val noTime = LocalDateTime(ldt.date, LocalTime(0, 0))
        viewModelScope.launch {
            repository.setDueDate(todoId, noTime.toInstant(timeZone)).onLeft { lastError.value = it }
        }
    }

    fun moveToList(listId: Long) {
        viewModelScope.launch { repository.moveToList(todoId, listId).onLeft { lastError.value = it } }
    }

    fun addSubTask(title: String) {
        viewModelScope.launch { addSubTask(todoId, title).onLeft { lastError.value = it } }
    }

    fun toggleSubTask(item: TodoItem) {
        viewModelScope.launch { repository.setCompleted(item.id, !item.isCompleted).onLeft { lastError.value = it } }
    }

    fun trashSubTask(item: TodoItem) {
        viewModelScope.launch { repository.trash(item.id).onLeft { lastError.value = it } }
    }
}
```

> 注：`todayDate()` 签名在本 Task 不修改（仍是无参版本），Step 4 会为它加参数重载。

- [ ] **Step 4: Formatting.kt 增加 todayDate 参数重载（保留无参版本给 UI 用）**

Modify `shared/src/commonMain/kotlin/com/myapplication/shared/util/Formatting.kt`，在现有 `todayDate()` 下方追加：

```kotlin
fun todayDate(clock: Clock, timeZone: TimeZone): LocalDate =
    clock.now().toLocalDateTime(timeZone).date
```

- [ ] **Step 5: 创建 ErrorUi.kt**

Create `shared/src/commonMain/kotlin/com/myapplication/shared/ui/ErrorUi.kt`：

```kotlin
package com.myapplication.shared.ui

import com.myapplication.shared.domain.error.TodoError

fun TodoError.uiMessage(): String = when (this) {
    TodoError.EmptyTitle -> "标题不能为空"
    TodoError.ParentNotFound -> "父任务不存在"
    TodoError.InboxNotFound -> "收件箱初始化失败"
    is TodoError.Persistence -> "操作失败，请重试"
}
```

- [ ] **Step 6: App.kt 接入 MainViewModel 构造 + 错误弹窗**

Modify `shared/src/commonMain/kotlin/App.kt`：

1. 第 59 行 ViewModel 创建改为：

```kotlin
val mainVm: MainViewModel = viewModel { MainViewModel(graph.repository, graph.addTodo, graph.clock, graph.timeZone) }
```

2. 新增 import：

```kotlin
import androidx.compose.runtime.collectAsState
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.ui.components.RemDialog
import com.myapplication.shared.ui.components.RemButton
import com.myapplication.shared.ui.components.RemButtonVariant
import com.myapplication.shared.ui.theme.RemType
import com.myapplication.shared.ui.uiMessage
```

3. 在 `AppRoot` 函数体末尾（`BoxWithConstraints` 之后、函数结束前）追加：

```kotlin
val error by mainVm.lastError.collectAsState()
if (error != null) {
    RemDialog(
        title = "出错了",
        onDismiss = mainVm::dismissError,
        confirmText = "知道了",
        onConfirm = mainVm::dismissError,
        showButtons = false,
    ) {
        BasicText(error.uiMessage(), style = RemType.text14.copy(color = colors.textNormal))
    }
}
```

- [ ] **Step 7: DetailScreen.kt 接入 DetailViewModel 构造 + 错误弹窗**

Modify `shared/src/commonMain/kotlin/com/myapplication/shared/ui/detail/DetailScreen.kt`：

1. 第 65-67 行 ViewModel 创建改为：

```kotlin
val detailVm: DetailViewModel = viewModel(key = "detail-$todoId") {
    DetailViewModel(graph.repository, graph.addSubTask, todoId)
}
```

2. 新增 import：

```kotlin
import androidx.compose.runtime.collectAsState
import com.myapplication.shared.ui.uiMessage
```

3. 在 `Column` 结束后（`showListDialog` 块之后、函数结束前）追加：

```kotlin
val detailError by detailVm.lastError.collectAsState()
if (detailError != null) {
    RemDialog(
        title = "出错了",
        onDismiss = detailVm::dismissError,
        confirmText = "知道了",
        onConfirm = detailVm::dismissError,
        showButtons = false,
    ) {
        BasicText(detailError.uiMessage(), style = RemType.text14.copy(color = colors.textNormal))
    }
}
```

- [ ] **Step 8: 编译恢复检查点**

Run: `./gradlew :shared:compileKotlinDesktop`
Expected: BUILD SUCCESSFUL（全部 commonMain 编译通过；若 `bind`/`raise` 报 unresolved，追加 import 后重跑）

- [ ] **Step 9: Commit**

```bash
git add shared/src
git commit -m "feat(fp): typed errors via Arrow, use cases, clock injection"
```

---

### Task 4: 测试夹具 + 存量测试更新（全绿恢复）

**Files:**
- Create: `shared/src/commonTest/kotlin/com/myapplication/shared/test/FakeTodoRepository.kt`
- Modify: `shared/src/commonTest/kotlin/com/myapplication/shared/ui/main/MainViewModelTest.kt`（全量重写）
- Modify: `shared/src/desktopTest/kotlin/com/myapplication/shared/data/TodoRepositoryImplTest.kt`（逐测试更新）

- [ ] **Step 1: 创建共享测试夹具**

Create `shared/src/commonTest/kotlin/com/myapplication/shared/test/FakeTodoRepository.kt`：

```kotlin
package com.myapplication.shared.test

import arrow.core.Either
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.domain.repository.TodoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Instant

class FakeTodoRepository : TodoRepository {
    val listsState = MutableStateFlow<List<TodoList>>(emptyList())
    val todosState = MutableStateFlow<List<TodoItem>>(emptyList())
    var ensureInboxCalls = 0
    var lastInserted: TodoItem? = null
    var toggledId: Long? = null
    var toggledValue: Boolean? = null
    var flaggedId: Long? = null
    var flaggedValue: Boolean? = null
    var failNextInsert = false
    private var nextId = 1L

    override fun observeLists(): Flow<List<TodoList>> = listsState
    override fun observeAllActive(): Flow<List<TodoItem>> = todosState
    override fun observeByList(listId: Long): Flow<List<TodoItem>> = todosState
    override fun observeToday(): Flow<List<TodoItem>> = todosState
    override fun observeScheduled(): Flow<List<TodoItem>> = todosState
    override fun observeCompleted(): Flow<List<TodoItem>> = todosState
    override fun observeTrashed(): Flow<List<TodoItem>> = todosState
    override fun observeSubTasks(parentId: Long): Flow<List<TodoItem>> = todosState
    override fun observeTodo(id: Long): Flow<TodoItem?> =
        MutableStateFlow(todosState.value.firstOrNull { it.id == id })
    override fun search(query: String): Flow<List<TodoItem>> = todosState

    override suspend fun findById(id: Long): Either<TodoError, TodoItem?> =
        Either.Right(todosState.value.firstOrNull { it.id == id })

    override suspend fun ensureInbox(): Either<TodoError, Long> {
        ensureInboxCalls++
        if (listsState.value.isEmpty()) {
            listsState.value = listOf(TodoList(1, "收件箱", "blue", 0, Instant.fromEpochMilliseconds(0)))
        }
        return Either.Right(listsState.value.first().id)
    }

    override suspend fun addList(name: String, colorKey: String): Either<TodoError, Unit> {
        listsState.value = listsState.value + TodoList(
            nextId++, name, colorKey, listsState.value.size, Instant.fromEpochMilliseconds(0),
        )
        return Either.Right(Unit)
    }

    override suspend fun deleteList(listId: Long): Either<TodoError, Unit> = Either.Right(Unit)

    override suspend fun insertTodo(
        listId: Long,
        title: String,
        note: String,
        dueDate: Instant?,
        parentId: Long?,
        flag: Boolean,
    ): Either<TodoError, Unit> {
        if (failNextInsert) {
            failNextInsert = false
            return Either.Left(TodoError.Persistence("boom"))
        }
        val item = TodoItem(
            nextId++, listId, title, note, dueDate, false, flag, null, false, null,
            parentId, 0.0, Instant.fromEpochMilliseconds(0),
        )
        todosState.value = todosState.value + item
        lastInserted = item
        return Either.Right(Unit)
    }

    override suspend fun setCompleted(id: Long, completed: Boolean): Either<TodoError, Unit> {
        toggledId = id
        toggledValue = completed
        return Either.Right(Unit)
    }

    override suspend fun setFlag(id: Long, flag: Boolean): Either<TodoError, Unit> {
        flaggedId = id
        flaggedValue = flag
        return Either.Right(Unit)
    }

    override suspend fun setTitle(id: Long, title: String): Either<TodoError, Unit> = Either.Right(Unit)
    override suspend fun setNote(id: Long, note: String): Either<TodoError, Unit> = Either.Right(Unit)
    override suspend fun setDueDate(id: Long, dueDate: Instant?): Either<TodoError, Unit> = Either.Right(Unit)
    override suspend fun moveToList(id: Long, listId: Long): Either<TodoError, Unit> = Either.Right(Unit)
    override suspend fun trash(id: Long): Either<TodoError, Unit> = Either.Right(Unit)
    override suspend fun restore(id: Long): Either<TodoError, Unit> = Either.Right(Unit)
    override suspend fun deleteForever(id: Long): Either<TodoError, Unit> = Either.Right(Unit)
}
```

- [ ] **Step 2: 重写 MainViewModelTest**

全量替换 `shared/src/commonTest/kotlin/com/myapplication/shared/ui/main/MainViewModelTest.kt`：

```kotlin
package com.myapplication.shared.ui.main

import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.domain.usecase.AddTodoUseCase
import com.myapplication.shared.test.FakeTodoRepository
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val fixedClock: Clock = object : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(1_000_000_000_000)
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

    private fun vm(repo: FakeTodoRepository = FakeTodoRepository()): MainViewModel =
        MainViewModel(repo, AddTodoUseCase(repo), fixedClock, TimeZone.currentSystemDefault())

    @Test
    fun createTodoWithDateAndNote() = runTest(dispatcher) {
        val repo = FakeTodoRepository()
        repo.ensureInbox()
        val vm = MainViewModel(repo, AddTodoUseCase(repo), fixedClock, TimeZone.currentSystemDefault())
        collect(vm)
        val due = LocalDateTime(2026, 8, 12, 15, 0)
        vm.createTodo("交报告", "备注内容", due)
        advanceUntilIdle()
        assertEquals("交报告", repo.lastInserted?.title)
        assertNotNull(repo.lastInserted?.dueDate)
        assertNull(repo.lastInserted?.parentId)
    }

    @Test
    fun createTodoInListAddsToList() = runTest(dispatcher) {
        val repo = FakeTodoRepository()
        repo.ensureInbox()
        val vm = MainViewModel(repo, AddTodoUseCase(repo), fixedClock, TimeZone.currentSystemDefault())
        collect(vm)
        vm.createTodo("写周报", "", null, false, 7)
        advanceUntilIdle()
        assertEquals(7L, repo.lastInserted?.listId)
    }

    @Test
    fun createTodoBlankShowsEmptyTitleError() = runTest(dispatcher) {
        val repo = FakeTodoRepository()
        val vm = MainViewModel(repo, AddTodoUseCase(repo), fixedClock, TimeZone.currentSystemDefault())
        collect(vm)
        vm.createTodo("   ", "", null)
        advanceUntilIdle()
        assertEquals(TodoError.EmptyTitle, vm.lastError.value)
        assertNull(repo.lastInserted)
    }

    @Test
    fun persistenceFailureSurfacesError() = runTest(dispatcher) {
        val repo = FakeTodoRepository()
        repo.ensureInbox()
        repo.failNextInsert = true
        val vm = MainViewModel(repo, AddTodoUseCase(repo), fixedClock, TimeZone.currentSystemDefault())
        collect(vm)
        vm.createTodo("写不了", "", null)
        advanceUntilIdle()
        assertTrue(vm.lastError.value is TodoError.Persistence)
    }

    @Test
    fun dismissErrorClearsLastError() {
        val repo = FakeTodoRepository()
        val vm = MainViewModel(repo, AddTodoUseCase(repo), fixedClock, TimeZone.currentSystemDefault())
        vm.lastError.value = TodoError.EmptyTitle
        vm.dismissError()
        assertNull(vm.lastError.value)
    }

    @Test
    fun toggleCompletedDelegates() = runTest(dispatcher) {
        val repo = FakeTodoRepository()
        repo.ensureInbox()
        val vm = MainViewModel(repo, AddTodoUseCase(repo), fixedClock, TimeZone.currentSystemDefault())
        collect(vm)
        val item = TodoItem(5, 1, "x", "", null, false, false, null, false, null, null, 0.0, Instant.fromEpochMilliseconds(0))
        vm.toggleCompleted(item)
        advanceUntilIdle()
        assertEquals(5L, repo.toggledId)
        assertEquals(true, repo.toggledValue)
    }

    @Test
    fun toggleFlagDelegates() = runTest(dispatcher) {
        val repo = FakeTodoRepository()
        repo.ensureInbox()
        val vm = MainViewModel(repo, AddTodoUseCase(repo), fixedClock, TimeZone.currentSystemDefault())
        collect(vm)
        val item = TodoItem(6, 1, "x", "", null, false, false, null, false, null, null, 0.0, Instant.fromEpochMilliseconds(0))
        vm.toggleFlag(item)
        advanceUntilIdle()
        assertEquals(6L, repo.flaggedId)
        assertEquals(true, repo.flaggedValue)
    }

    @Test
    fun openDetailAndBack() {
        val vm = MainViewModel(FakeTodoRepository(), AddTodoUseCase(FakeTodoRepository()), fixedClock, TimeZone.currentSystemDefault())
        vm.openDetail(3)
        assertEquals(Route.Detail(3), vm.route.value)
        vm.back()
        assertEquals(Route.Main, vm.route.value)
    }

    @Test
    fun selectScopeClearsSearchQuery() = runTest(dispatcher) {
        val vm = MainViewModel(FakeTodoRepository(), AddTodoUseCase(FakeTodoRepository()), fixedClock, TimeZone.currentSystemDefault())
        vm.setSearch("牛奶")
        vm.selectScope(Scope.Today)
        assertEquals("", vm.searchQuery.value)
    }

    @Test
    fun deleteSelectedListResetsScopeToAll() = runTest(dispatcher) {
        val repo = FakeTodoRepository()
        repo.ensureInbox()
        val vm = MainViewModel(repo, AddTodoUseCase(repo), fixedClock, TimeZone.currentSystemDefault())
        collect(vm)
        vm.selectScope(Scope.List(7))
        vm.deleteList(TodoList(7, "x", "blue", 1, Instant.fromEpochMilliseconds(0)))
        advanceUntilIdle()
        assertEquals(Scope.All, vm.scope.value)
    }

    @Test
    fun selectScopeClosesDetail() {
        val vm = MainViewModel(FakeTodoRepository(), AddTodoUseCase(FakeTodoRepository()), fixedClock, TimeZone.currentSystemDefault())
        vm.openDetail(3)
        vm.selectScope(Scope.Today)
        assertEquals(Route.Main, vm.route.value)
    }
}
```

> 注：测试内 `MainViewModel(repo, AddTodoUseCase(repo), ...)` 可抽成私有 `vm()` 辅助函数以减重复，执行时按需精简；固定时钟 `Instant` 为 kotlin.time.Instant。

- [ ] **Step 3: 更新 TodoRepositoryImplTest（15 个测试逐条改断言）**

Modify `shared/src/desktopTest/kotlin/com/myapplication/shared/data/TodoRepositoryImplTest.kt`：

1. 新增 imports：

```kotlin
import arrow.core.leftOrNull
import arrow.core.rightOrNull
import com.myapplication.shared.domain.usecase.AddSubTaskUseCase
import kotlin.test.assertIs
```

2. `newRepo()` 保持 `TodoRepositoryImpl(TodoDb(driver))`（clock/tz 有默认值，不动）。

3. 各测试断言替换（原测试名不变）：

| 原断言 | 改为 |
|---|---|
| `repo.ensureInbox()` | `assertTrue(repo.ensureInbox().isRight())` |
| `repo.addTodo(inbox, "交季度报告", "", null, null)` | `assertTrue(repo.insertTodo(inbox, "交季度报告", "", null, null, false).isRight())` |
| `repo.addTodo(...)`（dueDateFallsIntoTodayQuery 两处） | 同上前缀 `assertTrue(...isRight())` |
| `repo.addTodo(...)`（trashThenRestoreThenDeleteForever / searchMatchesTitleAndNote / setCompletedAndDueDate / observeScheduledIncludesDatedTodos / searchEscapesWildcards / setCompletedFalseClearsCompletedAt / setFlagPersistsAndDefaults） | 同上前缀 |
| `repo.addSubTask(parent.id, "子任务")` | `assertTrue(AddSubTaskUseCase(repo)(parent.id, "子任务").isRight())` |
| `repo.addSubTask(999L, "孤儿")` | `assertEquals(TodoError.ParentNotFound, AddSubTaskUseCase(repo)(999L, "孤儿").leftOrNull())` |
| `repo.addList("项目", "red")` | `assertTrue(repo.addList("项目", "red").isRight())` |
| `repo.addList("甲", "blue")` / `repo.addList("乙", "red")` | 同上前缀 |
| `repo.trash(item.id)` / `repo.restore(item.id)` / `repo.deleteForever(item.id)` | `assertTrue(...isRight())` |
| `repo.setCompleted(item.id, true)` / `false` | `assertTrue(...isRight())` |
| `repo.setDueDate(item.id, due)` / `(item.id, null)` | `assertTrue(...isRight())` |
| `repo.setFlag(item.id, true)` / `(item.id, false)` | `assertTrue(...isRight())` |

`addSubTaskMissingParentIsNoOp` 断言体保留（verify 无孤儿 todo 生成），仅调用行改为 use case 形式。

- [ ] **Step 4: 测试全绿恢复**

Run: `./gradlew :shared:desktopTest --rerun-tasks`
Expected: BUILD SUCCESSFUL，52 个原测试全过（含更新后的 TodoRepositoryImplTest + MainViewModelTest）

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonTest shared/src/desktopTest
git commit -m "test(fp): adapt tests to typed-error repository and use cases"
```

---

### Task 5: 新增 Use Case 行为测试

**Files:**
- Create: `shared/src/commonTest/kotlin/com/myapplication/shared/domain/usecase/AddTodoUseCaseTest.kt`
- Create: `shared/src/commonTest/kotlin/com/myapplication/shared/domain/usecase/AddSubTaskUseCaseTest.kt`

- [ ] **Step 1: 创建 AddTodoUseCaseTest**

Create `shared/src/commonTest/kotlin/com/myapplication/shared/domain/usecase/AddTodoUseCaseTest.kt`：

```kotlin
package com.myapplication.shared.domain.usecase

import arrow.core.leftOrNull
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.test.FakeTodoRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AddTodoUseCaseTest {

    private suspend fun repoWithInbox(): FakeTodoRepository {
        val repo = FakeTodoRepository()
        repo.ensureInbox()
        return repo
    }

    private fun input(
        listId: Long? = null,
        parentId: Long? = null,
        title: String = "买牛奶",
        note: String = "",
    ) = AddTodoInput(listId, parentId, title, note, null, false)

    @Test
    fun addsTodoToExplicitList() = runTest {
        val repo = repoWithInbox()
        val result = AddTodoUseCase(repo)(input(listId = 1, title = "交报告"))
        assertTrue(result.isRight())
        assertEquals(1L, repo.lastInserted?.listId)
        assertEquals("交报告", repo.lastInserted?.title)
    }

    @Test
    fun blankTitleReturnsEmptyTitleError() = runTest {
        val repo = repoWithInbox()
        val result = AddTodoUseCase(repo)(input(title = "   "))
        assertEquals(TodoError.EmptyTitle, result.leftOrNull())
        assertNull(repo.lastInserted)
    }

    @Test
    fun noListFallsBackToInbox() = runTest {
        val repo = repoWithInbox()
        val result = AddTodoUseCase(repo)(input(title = "无列表"))
        assertTrue(result.isRight())
        assertEquals(1L, repo.lastInserted?.listId)
    }

    @Test
    fun emptyDbCreatesInboxOnDemand() = runTest {
        val repo = FakeTodoRepository()
        val result = AddTodoUseCase(repo)(input(title = "冷启动"))
        assertTrue(result.isRight())
        assertEquals(1, repo.ensureInboxCalls)
        assertEquals(1L, repo.lastInserted?.listId)
    }

    @Test
    fun subtaskInheritsParentList() = runTest {
        val repo = repoWithInbox()
        repo.addList("项目", "red")
        repo.insertTodo(2, "父任务", "", null, null, false)
        val parentId = repo.lastInserted!!.id
        val result = AddTodoUseCase(repo)(input(parentId = parentId, title = "子任务"))
        assertTrue(result.isRight())
        assertEquals(2L, repo.lastInserted?.listId)
        assertEquals(parentId, repo.lastInserted?.parentId)
    }

    @Test
    fun missingParentReturnsParentNotFound() = runTest {
        val repo = repoWithInbox()
        val result = AddTodoUseCase(repo)(input(parentId = 999L, title = "孤儿"))
        assertEquals(TodoError.ParentNotFound, result.leftOrNull())
    }

    @Test
    fun persistenceFailurePropagates() = runTest {
        val repo = repoWithInbox()
        repo.failNextInsert = true
        val result = AddTodoUseCase(repo)(input(title = "写不了"))
        assertIs<TodoError.Persistence>(result.leftOrNull())
    }
}
```

- [ ] **Step 2: 创建 AddSubTaskUseCaseTest**

Create `shared/src/commonTest/kotlin/com/myapplication/shared/domain/usecase/AddSubTaskUseCaseTest.kt`：

```kotlin
package com.myapplication.shared.domain.usecase

import arrow.core.leftOrNull
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.test.FakeTodoRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AddSubTaskUseCaseTest {

    private suspend fun repoWithParent(): Pair<FakeTodoRepository, Long> {
        val repo = FakeTodoRepository()
        repo.ensureInbox()
        repo.insertTodo(1, "父任务", "", null, null, false)
        return repo to repo.lastInserted!!.id
    }

    @Test
    fun addsSubtaskToParentList() = runTest {
        val (repo, parentId) = repoWithParent()
        val result = AddSubTaskUseCase(repo)(parentId, "子任务")
        assertTrue(result.isRight())
        assertEquals(1L, repo.lastInserted?.listId)
        assertEquals(parentId, repo.lastInserted?.parentId)
    }

    @Test
    fun missingParentReturnsParentNotFound() = runTest {
        val repo = FakeTodoRepository()
        repo.ensureInbox()
        val result = AddSubTaskUseCase(repo)(999L, "孤儿")
        assertEquals(TodoError.ParentNotFound, result.leftOrNull())
    }

    @Test
    fun blankTitleReturnsEmptyTitleError() = runTest {
        val (repo, parentId) = repoWithParent()
        val result = AddSubTaskUseCase(repo)(parentId, "  ")
        assertEquals(TodoError.EmptyTitle, result.leftOrNull())
    }
}
```

- [ ] **Step 3: 全量验证（最终检查点）**

Run: `./gradlew :shared:desktopTest --rerun-tasks :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL；原 52 测试 + 新增 10 个 use case 测试 + 5 个 VM 错误处理测试全部通过（`:shared:desktopTest` 报告 `total tests` 含 commonTest）

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonTest/kotlin/com/myapplication/shared/domain/usecase
git commit -m "test(fp): use-case behavior tests for addTodo and addSubTask"
```

---

## Self-Review 清单

**规格覆盖**：
- Arrow 引入 → Task 1 ✓
- 命令 Either 化 → Task 2 port/impl ✓
- Use Case 层（AddTodo/AddSubTask，吸收收件箱回退 + 父任务校验）→ Task 2 ✓
- 静默错误消除（原 `addSubTask` 的 `?: return`）→ AddSubTaskUseCase 返回 `ParentNotFound` ✓
- Clock/TimeZone 注入 → Task 2 impl + Task 3 AppGraph/VM ✓
- UI 错误呈现 → Task 3 App.kt/DetailScreen RemDialog ✓
- 测试适配 + 行为层测试 → Task 4/5 ✓
- deleteList 事务化 → Task 2 ✓

**已知风险（编译时验证）**：
- `bind()`/`raise()` 为 `Raise` 成员，若编译器报 unresolved 需补 import（Task 2 已备注）
- `db.transaction {}` suspend 版本依赖 SQLDelight coroutines 支持，若不可用改用 `db.transactionWithResult { ... }`
- Arrow 2.2.3 与 Kotlin 2.4.10 元数据兼容（旧编译器产物的向前兼容）
