# Functional Effect Architecture Deepening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Sundial's architecture consistently follow the accepted Arrow typed-effect direction by narrowing interfaces, moving product workflow rules out of ViewModels, making settings persistence explicit, and turning sync runtime lifecycle into a deeper effect module.

**Architecture:** Keep the current Kotlin Multiplatform + Compose shape, but split the over-wide repository port into smaller domain interfaces while preserving `TodoRepositoryImpl` as the concrete adapter. Use Arrow `Either` / `Raise` as the command error interface, `Flow` for query streams, and Arrow Fx / Resilience for sync resource and retry behavior. Each step should retain app behavior while reducing caller knowledge.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, SQLDelight, Arrow Core, Arrow Fx Coroutines, Arrow Resilience, kotlinx.coroutines test, Spotless.

---

## File Structure

- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/repository/TodoRepository.kt`
  - Keep this file as the compatibility aggregate during the refactor.
  - Add smaller interfaces: `TodoQueries`, `TodoCommands`, `ListCommands`, `SyncStore`, and `SettingsStore`.
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/data/TodoRepositoryImpl.kt`
  - Implement the new smaller interfaces through the existing concrete adapter.
  - No database behavior changes in Task 1.
- Modify: `shared/src/commonTest/kotlin/com/myapplication/shared/test/FakeTodoRepository.kt`
  - Continue implementing the aggregate interface for existing tests.
  - Later tasks may introduce smaller fakes only when a test no longer needs the full repository.
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/usecase/ToggleTodoCompletion.kt`
  - Own the recurring-vs-normal completion decision.
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/usecase/ScheduleTodo.kt`
  - Own schedule-today / schedule-tomorrow date and default-time policy.
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/usecase/SaveList.kt`
  - Own list name/color trimming and validation before persistence.
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/main/MainViewModel.kt`
  - Replace business branching with use case calls.
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/di/AppGraph.kt`
  - Wire new use cases.
- Create: `shared/src/commonTest/kotlin/com/myapplication/shared/domain/usecase/ToggleTodoCompletionUseCaseTest.kt`
- Create: `shared/src/commonTest/kotlin/com/myapplication/shared/domain/usecase/ScheduleTodoUseCaseTest.kt`
- Create: `shared/src/commonTest/kotlin/com/myapplication/shared/domain/usecase/SaveListUseCaseTest.kt`
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/settings/SettingsError.kt`
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/settings/SaveSettings.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/SettingsViewModel.kt`
  - Use typed settings errors instead of `getOrNull()` and `onLeft { }` swallowing.
- Create: `shared/src/commonTest/kotlin/com/myapplication/shared/domain/settings/SaveSettingsUseCaseTest.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/data/sync/SyncEngine.kt`
  - Extract runtime lifecycle and loop details.
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/data/sync/SyncRuntime.kt`
  - Own client/coordinator jobs, resource release, and loop startup.
- Modify: `shared/src/commonTest/kotlin/com/myapplication/shared/data/sync/SyncEngineTest.kt`
  - Keep black-box status tests.
- Create: `shared/src/commonTest/kotlin/com/myapplication/shared/data/sync/SyncRuntimeTest.kt`
  - Test lifecycle release and loop behavior directly.
- Modify: `docs/adr/0001-arrow-functional-core.md`
  - Record the post-refactor shape and the intentionally remaining Compose lifecycle boundary.
- Modify: `README.md`
  - Update architecture text if the public documentation mentions the aggregate repository.

---

### Task 1: Split Repository Interfaces Without Behavior Changes

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/repository/TodoRepository.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/data/TodoRepositoryImpl.kt`
- Modify: `shared/src/commonTest/kotlin/com/myapplication/shared/test/FakeTodoRepository.kt`

- [ ] **Step 1: Write a compile-facing interface test**

Add this test file:

```kotlin
package com.myapplication.shared.domain.repository

import com.myapplication.shared.data.TodoRepositoryImpl
import com.myapplication.shared.test.FakeTodoRepository
import kotlin.test.Test
import kotlin.test.assertNotNull

class RepositoryInterfaceShapeTest {
    @Test
    fun fakeRepositorySatisfiesAllNarrowPorts() {
        val repo = FakeTodoRepository()
        val queries: TodoQueries = repo
        val commands: TodoCommands = repo
        val lists: ListCommands = repo
        val sync: SyncStore = repo
        val settings: SettingsStore = repo

        assertNotNull(queries)
        assertNotNull(commands)
        assertNotNull(lists)
        assertNotNull(sync)
        assertNotNull(settings)
    }
}
```

Save as `shared/src/commonTest/kotlin/com/myapplication/shared/domain/repository/RepositoryInterfaceShapeTest.kt`.

- [ ] **Step 2: Run the new test to verify it fails**

Run:

```bash
./gradlew :shared:desktopTest --tests com.myapplication.shared.domain.repository.RepositoryInterfaceShapeTest
```

Expected: compile fails because `TodoQueries`, `TodoCommands`, `ListCommands`, `SyncStore`, and `SettingsStore` do not exist.

- [ ] **Step 3: Add narrow interfaces**

Replace the body of `TodoRepository.kt` with this structure, keeping the same method signatures from the current aggregate:

```kotlin
interface TodoQueries {
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
    fun observeListStats(listId: Long): Flow<ListStats>
    suspend fun findById(id: Long): Either<TodoError, TodoItem?>
    suspend fun findByIdActive(id: Long): Either<TodoError, TodoItem?>
}

interface TodoCommands {
    suspend fun ensureInbox(): Either<TodoError, Long>
    suspend fun insertTodo(
        listId: Long,
        title: String,
        note: String,
        dueDate: Instant?,
        parentId: Long?,
        flag: Boolean,
        recurrenceRule: RecurrenceRule? = null,
    ): Either<TodoError, Unit>
    suspend fun setCompleted(id: Long, completed: Boolean): Either<TodoError, Unit>
    suspend fun completeRecurringTodo(id: Long): Either<TodoError, Unit>
    suspend fun setFlag(id: Long, flag: Boolean): Either<TodoError, Unit>
    suspend fun setTitle(id: Long, title: String): Either<TodoError, Unit>
    suspend fun setNote(id: Long, note: String): Either<TodoError, Unit>
    suspend fun setDueDate(id: Long, dueDate: Instant?): Either<TodoError, Unit>
    suspend fun setRecurrence(id: Long, rule: RecurrenceRule?): Either<TodoError, Unit>
    suspend fun moveToList(id: Long, listId: Long): Either<TodoError, Unit>
    suspend fun trash(id: Long): Either<TodoError, Unit>
    suspend fun restore(id: Long): Either<TodoError, Unit>
    suspend fun deleteForever(id: Long): Either<TodoError, Unit>
}

interface ListCommands {
    suspend fun addList(name: String, colorKey: String): Either<TodoError, Unit>
    suspend fun updateList(listId: Long, name: String, colorKey: String): Either<TodoError, Unit>
    suspend fun deleteList(listId: Long, policy: DeleteListPolicy = DeleteListPolicy.MoveTasksToInbox): Either<TodoError, Unit>
}

interface SyncStore {
    suspend fun readOutbox(limit: Int): Either<TodoError, List<SyncRow>>
    suspend fun clearOutbox(upToSeq: Long): Either<TodoError, Unit>
    fun observeOutboxCount(): Flow<Int>
    suspend fun applyRemoteUpsert(row: TodoRowDto): Either<TodoError, Unit>
    suspend fun applyRemoteUpsertList(row: ListRowDto): Either<TodoError, Unit>
    suspend fun applyRemoteDelete(table: String, rowId: Long, updatedAt: Long): Either<TodoError, Unit>
}

interface SettingsStore {
    suspend fun getSetting(key: String): Either<TodoError, String?>
    suspend fun setSetting(key: String, value: String): Either<TodoError, Unit>
    suspend fun getSettings(): Either<TodoError, Map<String, String>>
}

interface TodoRepository :
    TodoQueries,
    TodoCommands,
    ListCommands,
    SyncStore,
    SettingsStore
```

Keep imports for `Either`, models, sync DTOs, `Flow`, and `Instant`.

- [ ] **Step 4: Run tests**

Run:

```bash
./gradlew :shared:desktopTest --tests com.myapplication.shared.domain.repository.RepositoryInterfaceShapeTest
```

Expected: PASS.

- [ ] **Step 5: Run the shared suite**

Run:

```bash
./gradlew spotlessCheck :shared:desktopTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/domain/repository/TodoRepository.kt \
  shared/src/commonTest/kotlin/com/myapplication/shared/domain/repository/RepositoryInterfaceShapeTest.kt
git commit -m "refactor(domain): split todo repository ports"
```

---

### Task 2: Move Completion and Scheduling Rules Into Use Cases

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/usecase/ToggleTodoCompletion.kt`
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/usecase/ScheduleTodo.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/main/MainViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/di/AppGraph.kt`
- Create: `shared/src/commonTest/kotlin/com/myapplication/shared/domain/usecase/ToggleTodoCompletionUseCaseTest.kt`
- Create: `shared/src/commonTest/kotlin/com/myapplication/shared/domain/usecase/ScheduleTodoUseCaseTest.kt`
- Modify: `shared/src/commonTest/kotlin/com/myapplication/shared/ui/main/MainViewModelTest.kt`

- [ ] **Step 1: Write completion use case tests**

Create `ToggleTodoCompletionUseCaseTest.kt`:

```kotlin
package com.myapplication.shared.domain.usecase

import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.recurrence.RecurrenceRule
import com.myapplication.shared.test.FakeTodoRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToggleTodoCompletionUseCaseTest {
    @Test
    fun recurringIncompleteTodoCompletesAndCreatesNextOccurrence() = runTest {
        val repo = FakeTodoRepository()
        repo.ensureInbox()
        repo.insertTodo(1, "站会", "", Instant.parse("2026-08-13T09:00:00Z"), null, false, RecurrenceRule.Daily())
        val item = repo.todos.first()
        val useCase = ToggleTodoCompletionUseCase(repo, TimeZone.UTC)

        val result = useCase(item)

        assertTrue(result.isRight())
        assertEquals(true, repo.todos.first { it.id == item.id }.isCompleted)
        assertEquals(2, repo.todos.size)
        assertEquals(Instant.parse("2026-08-14T09:00:00Z"), repo.todos.last().dueDate)
    }

    @Test
    fun completedRecurringTodoUsesNormalToggleToUncomplete() = runTest {
        val repo = FakeTodoRepository()
        val item = TodoItem(
            id = 7,
            listId = 1,
            title = "站会",
            note = "",
            dueDate = Instant.parse("2026-08-13T09:00:00Z"),
            isCompleted = true,
            flag = false,
            completedAt = null,
            isTrashed = false,
            trashedAt = null,
            parentId = null,
            sortPosition = 0.0,
            createdAt = Instant.fromEpochMilliseconds(0),
            recurrenceRule = RecurrenceRule.Daily(),
        )
        val useCase = ToggleTodoCompletionUseCase(repo, TimeZone.UTC)

        val result = useCase(item)

        assertTrue(result.isRight())
        assertEquals(7L, repo.toggledId)
        assertEquals(false, repo.toggledValue)
    }
}
```

- [ ] **Step 2: Write scheduling use case tests**

Create `ScheduleTodoUseCaseTest.kt`:

```kotlin
package com.myapplication.shared.domain.usecase

import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.test.FakeTodoRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScheduleTodoUseCaseTest {
    private val fixedClock = object : kotlin.time.Clock {
        override fun now(): Instant = Instant.parse("2026-08-13T12:00:00Z")
    }

    private fun todo(due: Instant? = null) = TodoItem(
        id = 9,
        listId = 1,
        title = "x",
        note = "",
        dueDate = due,
        isCompleted = false,
        flag = false,
        completedAt = null,
        isTrashed = false,
        trashedAt = null,
        parentId = null,
        sortPosition = 0.0,
        createdAt = Instant.fromEpochMilliseconds(0),
    )

    @Test
    fun todayUsesNineOClockWhenTodoHasNoTime() = runTest {
        val repo = FakeTodoRepository()
        val useCase = ScheduleTodoUseCase(repo, fixedClock, TimeZone.UTC)

        val result = useCase.scheduleToday(todo())

        assertTrue(result.isRight())
        assertEquals(Instant.parse("2026-08-13T09:00:00Z"), repo.lastSetDueDateValue)
    }

    @Test
    fun tomorrowPreservesExistingNonMidnightTime() = runTest {
        val repo = FakeTodoRepository()
        val useCase = ScheduleTodoUseCase(repo, fixedClock, TimeZone.UTC)

        val result = useCase.scheduleTomorrow(todo(Instant.parse("2026-08-10T15:30:00Z")))

        assertTrue(result.isRight())
        assertEquals(Instant.parse("2026-08-14T15:30:00Z"), repo.lastSetDueDateValue)
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run:

```bash
./gradlew :shared:desktopTest --tests com.myapplication.shared.domain.usecase.ToggleTodoCompletionUseCaseTest --tests com.myapplication.shared.domain.usecase.ScheduleTodoUseCaseTest
```

Expected: compile fails because the two use case classes do not exist.

- [ ] **Step 4: Implement `ToggleTodoCompletionUseCase`**

Create `shared/src/commonMain/kotlin/com/myapplication/shared/domain/usecase/ToggleTodoCompletion.kt`:

```kotlin
package com.myapplication.shared.domain.usecase

import arrow.core.Either
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.repository.TodoCommands
import kotlinx.datetime.TimeZone

class ToggleTodoCompletionUseCase(
    private val commands: TodoCommands,
    private val timeZone: TimeZone,
) {
    suspend operator fun invoke(item: TodoItem): Either<TodoError, Unit> =
        if (!item.isCompleted && item.recurrenceRule != null) {
            commands.completeRecurringTodo(item.id)
        } else {
            commands.setCompleted(item.id, !item.isCompleted)
        }
}
```

If `timeZone` is unused after implementation, remove the constructor property and update tests/DI accordingly. Prefer the simpler constructor if no recurrence calculation remains in this use case.

- [ ] **Step 5: Implement `ScheduleTodoUseCase`**

Create `shared/src/commonMain/kotlin/com/myapplication/shared/domain/usecase/ScheduleTodo.kt`:

```kotlin
package com.myapplication.shared.domain.usecase

import arrow.core.Either
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.repository.TodoCommands
import com.myapplication.shared.util.todayDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

class ScheduleTodoUseCase(
    private val commands: TodoCommands,
    private val clock: Clock,
    private val timeZone: TimeZone,
) {
    suspend fun scheduleToday(item: TodoItem): Either<TodoError, Unit> =
        scheduleOn(item, todayDate(clock, timeZone))

    suspend fun scheduleTomorrow(item: TodoItem): Either<TodoError, Unit> =
        scheduleOn(item, todayDate(clock, timeZone).plus(1, DateTimeUnit.DAY))

    private suspend fun scheduleOn(
        item: TodoItem,
        date: LocalDate,
    ): Either<TodoError, Unit> {
        val time =
            item.dueDate
                ?.toLocalDateTime(timeZone)
                ?.time
                ?.takeIf { it.hour != 0 || it.minute != 0 }
                ?: LocalTime(9, 0)
        return commands.setDueDate(item.id, LocalDateTime(date, time).toInstant(timeZone))
    }
}
```

- [ ] **Step 6: Wire use cases through `AppGraph`**

Add lazy fields:

```kotlin
val toggleTodoCompletion: ToggleTodoCompletionUseCase by lazy {
    ToggleTodoCompletionUseCase(repository)
}
val scheduleTodo: ScheduleTodoUseCase by lazy {
    ScheduleTodoUseCase(repository, clock, timeZone)
}
```

Then update `MainViewModel` construction sites to pass these use cases.

- [ ] **Step 7: Update `MainViewModel`**

Change constructor dependencies from `CompleteRecurringTodoUseCase?` and direct scheduling logic to:

```kotlin
private val toggleTodoCompletion: ToggleTodoCompletionUseCase,
private val scheduleTodo: ScheduleTodoUseCase,
```

Replace methods:

```kotlin
fun toggleCompleted(item: TodoItem) {
    launchTodoEffect(lastError) { toggleTodoCompletion(item) }
}

fun scheduleToday(item: TodoItem) {
    launchTodoEffect(lastError) { scheduleTodo.scheduleToday(item) }
}

fun scheduleTomorrow(item: TodoItem) {
    launchTodoEffect(lastError) { scheduleTodo.scheduleTomorrow(item) }
}
```

Remove the private `scheduleOn` function and unused date/time imports from `MainViewModel.kt`.

- [ ] **Step 8: Update existing `MainViewModelTest` helpers**

Change the helper factory to construct the new use cases:

```kotlin
private fun vm(repo: FakeTodoRepository): MainViewModel =
    MainViewModel(
        repository = repo,
        addTodo = AddTodoUseCase(repo),
        timeZone = TimeZone.currentSystemDefault(),
        toggleTodoCompletion = ToggleTodoCompletionUseCase(repo),
        scheduleTodo = ScheduleTodoUseCase(repo, kotlin.time.Clock.System, TimeZone.currentSystemDefault()),
    )
```

For tests using a fixed clock, pass `ScheduleTodoUseCase(repo, fixedClock, TimeZone.UTC)`.

- [ ] **Step 9: Run targeted tests**

Run:

```bash
./gradlew :shared:desktopTest --tests com.myapplication.shared.domain.usecase.ToggleTodoCompletionUseCaseTest --tests com.myapplication.shared.domain.usecase.ScheduleTodoUseCaseTest --tests com.myapplication.shared.ui.main.MainViewModelTest
```

Expected: PASS.

- [ ] **Step 10: Run full shared verification**

Run:

```bash
./gradlew spotlessCheck :shared:desktopTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 11: Commit**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/domain/usecase/ToggleTodoCompletion.kt \
  shared/src/commonMain/kotlin/com/myapplication/shared/domain/usecase/ScheduleTodo.kt \
  shared/src/commonMain/kotlin/com/myapplication/shared/ui/main/MainViewModel.kt \
  shared/src/commonMain/kotlin/com/myapplication/shared/di/AppGraph.kt \
  shared/src/commonTest/kotlin/com/myapplication/shared/domain/usecase/ToggleTodoCompletionUseCaseTest.kt \
  shared/src/commonTest/kotlin/com/myapplication/shared/domain/usecase/ScheduleTodoUseCaseTest.kt \
  shared/src/commonTest/kotlin/com/myapplication/shared/ui/main/MainViewModelTest.kt
git commit -m "refactor(domain): move todo workflow rules into use cases"
```

---

### Task 3: Move List Validation Into a Typed Use Case

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/usecase/SaveList.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/main/MainViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/di/AppGraph.kt`
- Create: `shared/src/commonTest/kotlin/com/myapplication/shared/domain/usecase/SaveListUseCaseTest.kt`
- Modify: `shared/src/commonTest/kotlin/com/myapplication/shared/ui/main/MainViewModelTest.kt`

- [ ] **Step 1: Write tests**

Create `SaveListUseCaseTest.kt`:

```kotlin
package com.myapplication.shared.domain.usecase

import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.test.FakeTodoRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SaveListUseCaseTest {
    @Test
    fun addRejectsBlankNameAsTypedError() = runTest {
        val repo = FakeTodoRepository()
        val useCase = SaveListUseCase(repo)

        val result = useCase.add("   ", "blue")

        assertEquals(TodoError.EmptyTitle, result.leftOrNull())
        assertEquals(0, repo.listsState.value.size)
    }

    @Test
    fun updateTrimsNameAndColorBeforeWriting() = runTest {
        val repo = FakeTodoRepository()
        repo.ensureInbox()
        repo.addList("项目", "blue")
        val useCase = SaveListUseCase(repo)

        val result = useCase.update(2, "  研究  ", " red ")

        assertTrue(result.isRight())
        assertEquals("研究", repo.lastUpdatedListName)
        assertEquals("red", repo.lastUpdatedListColor)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :shared:desktopTest --tests com.myapplication.shared.domain.usecase.SaveListUseCaseTest
```

Expected: compile fails because `SaveListUseCase` does not exist.

- [ ] **Step 3: Implement use case**

Create `SaveList.kt`:

```kotlin
package com.myapplication.shared.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.repository.ListCommands

class SaveListUseCase(
    private val lists: ListCommands,
) {
    suspend fun add(
        name: String,
        colorKey: String,
    ): Either<TodoError, Unit> =
        either {
            val trimmed = name.trim()
            ensure(trimmed.isNotEmpty()) { TodoError.EmptyTitle }
            lists.addList(trimmed, colorKey.trim()).bind()
        }

    suspend fun update(
        listId: Long,
        name: String,
        colorKey: String,
    ): Either<TodoError, Unit> =
        either {
            val trimmed = name.trim()
            ensure(trimmed.isNotEmpty()) { TodoError.EmptyTitle }
            lists.updateList(listId, trimmed, colorKey.trim()).bind()
        }
}
```

- [ ] **Step 4: Use it from `MainViewModel`**

Add constructor dependency:

```kotlin
private val saveList: SaveListUseCase,
```

Replace:

```kotlin
fun addList(name: String, colorKey: String) {
    launchTodoEffect(lastError) { saveList.add(name, colorKey) }
}

fun updateList(list: TodoList, name: String, colorKey: String) {
    launchTodoEffect(lastError) { saveList.update(list.id, name, colorKey) }
}
```

Do not silently return on blank names anymore; typed error should reach `lastError`.

- [ ] **Step 5: Wire `AppGraph` and tests**

Add:

```kotlin
val saveList: SaveListUseCase by lazy { SaveListUseCase(repository) }
```

Pass it to `MainViewModel`. Update `MainViewModelTest` factories to pass `SaveListUseCase(repo)`.

- [ ] **Step 6: Update existing blank-name test**

Change the old expectation from “do nothing” to typed error:

```kotlin
vm.addList("   ", "red")
advanceUntilIdle()
assertEquals(TodoError.EmptyTitle, vm.lastError.value)
```

- [ ] **Step 7: Run verification**

Run:

```bash
./gradlew spotlessCheck :shared:desktopTest --tests com.myapplication.shared.domain.usecase.SaveListUseCaseTest --tests com.myapplication.shared.ui.main.MainViewModelTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/domain/usecase/SaveList.kt \
  shared/src/commonMain/kotlin/com/myapplication/shared/ui/main/MainViewModel.kt \
  shared/src/commonMain/kotlin/com/myapplication/shared/di/AppGraph.kt \
  shared/src/commonTest/kotlin/com/myapplication/shared/domain/usecase/SaveListUseCaseTest.kt \
  shared/src/commonTest/kotlin/com/myapplication/shared/ui/main/MainViewModelTest.kt
git commit -m "refactor(domain): type list save validation"
```

---

### Task 4: Make Settings Persistence a Typed Effect

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/settings/SettingsError.kt`
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/settings/SaveSettings.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/SettingsViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/di/AppGraph.kt`
- Create: `shared/src/commonTest/kotlin/com/myapplication/shared/domain/settings/SaveSettingsUseCaseTest.kt`
- Modify: `shared/src/commonTest/kotlin/com/myapplication/shared/ui/settings/SettingsPreferencesTest.kt`

- [ ] **Step 1: Write settings use case tests**

Create `SaveSettingsUseCaseTest.kt`:

```kotlin
package com.myapplication.shared.domain.settings

import com.myapplication.shared.domain.sync.SyncMode
import com.myapplication.shared.test.FakeTodoRepository
import com.myapplication.shared.ui.settings.SettingsForm
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SaveSettingsUseCaseTest {
    @Test
    fun supabaseRequiresUrlAndKey() = runTest {
        val repo = FakeTodoRepository()
        val useCase = SaveSettingsUseCase(repo) { "device-x" }

        val result = useCase(SettingsForm(mode = SyncMode.Supabase, supabaseUrl = "", supabaseKey = "key"))

        assertEquals(SettingsError.MissingSupabaseConfig, result.leftOrNull())
    }

    @Test
    fun savePersistsSettingsAndReturnsConfig() = runTest {
        val repo = FakeTodoRepository()
        val useCase = SaveSettingsUseCase(repo) { "device-x" }

        val result = useCase(SettingsForm(mode = SyncMode.Supabase, supabaseUrl = " https://x.supabase.co ", supabaseKey = " key "))

        assertTrue(result.isRight())
        assertEquals("supabase", repo.settingsState.value["sync.mode"])
        assertEquals("https://x.supabase.co", repo.settingsState.value["sync.supabase.url"])
        assertEquals("key", repo.settingsState.value["sync.supabase.key"])
        assertEquals("device-x", repo.settingsState.value["sync.deviceId"])
        assertEquals("device-x", result.getOrNull()?.deviceId)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :shared:desktopTest --tests com.myapplication.shared.domain.settings.SaveSettingsUseCaseTest
```

Expected: compile fails because `SettingsError` and `SaveSettingsUseCase` do not exist.

- [ ] **Step 3: Implement `SettingsError`**

Create:

```kotlin
package com.myapplication.shared.domain.settings

import com.myapplication.shared.domain.error.TodoError

sealed interface SettingsError {
    data object MissingSupabaseConfig : SettingsError
    data class Persistence(val message: String) : SettingsError
}

fun TodoError.toSettingsError(): SettingsError =
    SettingsError.Persistence((this as? TodoError.Persistence)?.message ?: "设置保存失败")
```

- [ ] **Step 4: Implement `SaveSettingsUseCase`**

Create:

```kotlin
package com.myapplication.shared.domain.settings

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.myapplication.shared.domain.repository.SettingsStore
import com.myapplication.shared.domain.sync.SyncConfig
import com.myapplication.shared.domain.sync.SyncMode
import com.myapplication.shared.ui.settings.SettingsForm

class SaveSettingsUseCase(
    private val settings: SettingsStore,
    private val createDeviceId: () -> String,
) {
    suspend operator fun invoke(form: SettingsForm): Either<SettingsError, SyncConfig> =
        either {
            val supabaseUrl = form.supabaseUrl.trim()
            val supabaseKey = form.supabaseKey.trim()
            val sundialUrl = form.sundialUrl.trim()
            ensure(form.mode != SyncMode.Supabase || (supabaseUrl.isNotBlank() && supabaseKey.isNotBlank())) {
                SettingsError.MissingSupabaseConfig
            }

            val persistedDeviceId =
                settings
                    .getSetting("sync.deviceId")
                    .mapLeft { it.toSettingsError() }
                    .bind()
                    ?: createDeviceId().also { id ->
                        settings.setSetting("sync.deviceId", id).mapLeft { it.toSettingsError() }.bind()
                    }

            mapOf(
                "sync.mode" to form.mode.key,
                "sync.supabase.url" to supabaseUrl,
                "sync.supabase.key" to supabaseKey,
                "sync.sundial.url" to sundialUrl,
            ).forEach { (key, value) ->
                settings.setSetting(key, value).mapLeft { it.toSettingsError() }.bind()
            }

            SyncConfig(
                mode = form.mode,
                supabaseUrl = supabaseUrl,
                supabaseKey = supabaseKey,
                sundialUrl = sundialUrl,
                deviceId = persistedDeviceId,
            )
        }
}

private val SyncMode.key: String
    get() =
        when (this) {
            SyncMode.Local -> "local"
            SyncMode.Supabase -> "supabase"
            SyncMode.SundialServer -> "sundial"
        }
```

- [ ] **Step 5: Update `SettingsViewModel`**

Inject:

```kotlin
private val saveSettings: SaveSettingsUseCase,
```

Add state:

```kotlin
private val _lastSettingsError = MutableStateFlow<SettingsError?>(null)
val lastSettingsError: StateFlow<SettingsError?> = _lastSettingsError
```

Replace `save()` body with:

```kotlin
fun save() {
    if (!formLoaded) return
    viewModelScope.launch {
        when (val result = saveSettings(_form.value)) {
            is Either.Left -> _lastSettingsError.value = result.value
            is Either.Right -> {
                _lastSettingsError.value = null
                engine.configure(result.value)
            }
        }
    }
}
```

Load settings should no longer silently discard repository errors in a way that changes saved settings. If loading fails, keep current default form and expose `_lastSettingsError.value = SettingsError.Persistence("读取设置失败")`.

- [ ] **Step 6: Wire `AppGraph`**

Add:

```kotlin
val saveSettings: SaveSettingsUseCase by lazy {
    SaveSettingsUseCase(repository, ::createDeviceId)
}
```

Update `settingsViewModelFactory`:

```kotlin
SettingsViewModel(repository, engine, saveSettings)
```

- [ ] **Step 7: Run tests**

Run:

```bash
./gradlew spotlessCheck :shared:desktopTest --tests com.myapplication.shared.domain.settings.SaveSettingsUseCaseTest --tests com.myapplication.shared.ui.settings.SettingsPreferencesTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/domain/settings/SettingsError.kt \
  shared/src/commonMain/kotlin/com/myapplication/shared/domain/settings/SaveSettings.kt \
  shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/SettingsViewModel.kt \
  shared/src/commonMain/kotlin/com/myapplication/shared/di/AppGraph.kt \
  shared/src/commonTest/kotlin/com/myapplication/shared/domain/settings/SaveSettingsUseCaseTest.kt \
  shared/src/commonTest/kotlin/com/myapplication/shared/ui/settings/SettingsPreferencesTest.kt
git commit -m "refactor(settings): make settings saves typed effects"
```

---

### Task 5: Extract SyncRuntime as a Deep Resource Module

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/data/sync/SyncRuntime.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/data/sync/SyncEngine.kt`
- Create: `shared/src/commonTest/kotlin/com/myapplication/shared/data/sync/SyncRuntimeTest.kt`
- Modify: `shared/src/commonTest/kotlin/com/myapplication/shared/data/sync/SyncEngineTest.kt`

- [ ] **Step 1: Write runtime lifecycle tests**

Create `SyncRuntimeTest.kt`:

```kotlin
package com.myapplication.shared.data.sync

import com.myapplication.shared.domain.sync.SyncConfig
import com.myapplication.shared.domain.sync.SyncMode
import com.myapplication.shared.test.FakeSyncClient
import com.myapplication.shared.test.FakeTodoRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SyncRuntimeTest {
    @Test
    fun closeCancelsJobsAndClosesClientOnce() = runTest {
        val repo = FakeTodoRepository()
        val client = FakeSyncClient()
        val runtime =
            SyncRuntime.create(
                scope = backgroundScope,
                repository = repo,
                client = client,
                config = SyncConfig(SyncMode.Supabase, supabaseUrl = "https://example.com", supabaseKey = "key", deviceId = "device-a"),
                clock = object : kotlin.time.Clock {
                    override fun now() = kotlin.time.Instant.fromEpochMilliseconds(1_000)
                },
            )

        val lease = runtime.allocate()
        runCurrent()
        lease.release()

        assertEquals(1, client.closeAttempts)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :shared:desktopTest --tests com.myapplication.shared.data.sync.SyncRuntimeTest
```

Expected: compile fails because `SyncRuntime` has no public `create` API.

- [ ] **Step 3: Implement `SyncRuntime`**

Create a module that owns jobs and client release:

```kotlin
package com.myapplication.shared.data.sync

import arrow.fx.coroutines.ExitCase
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.allocate
import arrow.fx.coroutines.resource
import com.myapplication.shared.domain.repository.SyncStore
import com.myapplication.shared.domain.sync.SyncClient
import com.myapplication.shared.domain.sync.SyncConfig
import com.myapplication.shared.domain.sync.SyncCoordinator
import com.myapplication.shared.domain.sync.SyncMode
import com.myapplication.shared.domain.sync.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Clock

internal class SyncRuntime private constructor(
    private val client: SyncClient,
    val coordinator: SyncCoordinator?,
    private val jobs: MutableList<Job> = mutableListOf(),
) {
    fun track(job: Job) {
        jobs += job
        job.invokeOnCompletion { jobs -= job }
    }

    suspend fun close() {
        jobs.toList().forEach { it.cancelAndJoin() }
        jobs.clear()
        client.close()
    }

    class Lease(
        val runtime: SyncRuntime,
        private val releaseRuntime: suspend (ExitCase) -> Unit,
    ) {
        suspend fun release() {
            releaseRuntime(ExitCase.Completed)
        }
    }

    companion object {
        fun create(
            scope: CoroutineScope,
            repository: SyncStore,
            client: SyncClient,
            config: SyncConfig,
            clock: Clock,
        ): Resource<SyncRuntime> =
            resource(
                acquire = {
                    val coordinator =
                        when (config.mode) {
                            SyncMode.Local -> null
                            else -> SyncCoordinator(repository, client, config.deviceId)
                        }
                    SyncRuntime(client, coordinator)
                },
                release = { runtime, _ -> runtime.close() },
            )
    }
}

internal suspend fun Resource<SyncRuntime>.allocateLease(): SyncRuntime.Lease {
    val (runtime, release) = allocate()
    return SyncRuntime.Lease(runtime, release)
}
```

Use `SyncStore` instead of `TodoRepository` in `SyncCoordinator` as part of this task. Update `SyncCoordinator` constructor accordingly.

- [ ] **Step 4: Move job tracking from `SyncEngine` to `SyncRuntime`**

In `SyncEngine`, replace `runtime.jobs += job` and `invokeOnCompletion` blocks with:

```kotlin
runtime.track(job)
```

Remove the nested `SyncRuntime` data class from `SyncEngine.kt`.

- [ ] **Step 5: Replace `RuntimeLease` with runtime lease**

In `SyncEngine`, change:

```kotlin
private var activeRuntime: SyncRuntime.Lease? = null
```

Change configure allocation:

```kotlin
val lease = SyncRuntime.create(scope, repository, newClient, newConfig, clock).allocateLease()
activeRuntime = lease
val runtime = lease.runtime
```

Change release:

```kotlin
private suspend fun releaseActiveRuntime() {
    val lease = activeRuntime ?: return
    activeRuntime = null
    lease.release()
}
```

- [ ] **Step 6: Run sync tests**

Run:

```bash
./gradlew :shared:desktopTest --tests com.myapplication.shared.data.sync.SyncRuntimeTest --tests com.myapplication.shared.data.sync.SyncEngineTest --tests com.myapplication.shared.domain.sync.SyncCoordinatorTest
```

Expected: PASS.

- [ ] **Step 7: Run full verification**

Run:

```bash
./gradlew spotlessCheck :shared:desktopTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/data/sync/SyncRuntime.kt \
  shared/src/commonMain/kotlin/com/myapplication/shared/data/sync/SyncEngine.kt \
  shared/src/commonMain/kotlin/com/myapplication/shared/domain/sync/SyncCoordinator.kt \
  shared/src/commonTest/kotlin/com/myapplication/shared/data/sync/SyncRuntimeTest.kt \
  shared/src/commonTest/kotlin/com/myapplication/shared/data/sync/SyncEngineTest.kt
git commit -m "refactor(sync): extract runtime resource module"
```

---

### Task 6: Remove Nullable Failure Sentinels From SyncEngine

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/data/sync/SyncEngine.kt`
- Modify: `shared/src/commonTest/kotlin/com/myapplication/shared/data/sync/SyncEngineTest.kt`

- [ ] **Step 1: Add a regression test for unexpected sync exceptions**

Ensure `SyncEngineTest` keeps this behavior:

```kotlin
@Test
fun syncNowUnexpectedExceptionIsFailureNotSuccess() =
    runTest {
        val repo = FakeTodoRepository()
        val client = FakeSyncClient().apply { crashPull = true }
        val engine = SyncEngine(backgroundScope, repo, FakeClock(1_000_000)) { client.right() }
        engine.configure(supabaseConfig())
        runCurrent()

        val status = engine.status.value
        assertFalse(status.syncing)
        assertFalse(status.connected)
        assertEquals("同步失败: 未知错误", status.lastError)
    }
```

- [ ] **Step 2: Replace nullable helper return values**

In `SyncEngine`, replace:

```kotlin
private suspend fun tryDrainOutbox(coordinator: SyncCoordinator): Either<SyncError, Int>?
private suspend fun tryPullFromRemote(coordinator: SyncCoordinator): Either<SyncError, Int>?
```

with:

```kotlin
private suspend fun drainOutboxEffect(coordinator: SyncCoordinator): Either<SyncError, Int> =
    try {
        coordinator.drainOutbox()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Either.Left(SyncError.Transport(e.message ?: "同步失败: 未知错误"))
    }

private suspend fun pullFromRemoteEffect(coordinator: SyncCoordinator): Either<SyncError, Int> =
    try {
        coordinator.pullFromRemote()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Either.Left(SyncError.Transport(e.message ?: "同步失败: 未知错误"))
    }
```

Then simplify `runSyncNowOnce`:

```kotlin
val drainResult = drainOutboxEffect(coordinator)
val pullResult = pullFromRemoteEffect(coordinator)
val failure = drainResult.leftOrNull() ?: pullResult.leftOrNull()
if (failure != null) {
    _status.update { it.syncFailed(failure.userMessage()) }
} else {
    val pending = repository.observeOutboxCount().first()
    _status.update { it.syncSucceeded(pending, clock.now().toEpochMilliseconds()) }
}
```

- [ ] **Step 3: Run sync tests**

Run:

```bash
./gradlew :shared:desktopTest --tests com.myapplication.shared.data.sync.SyncEngineTest
```

Expected: PASS.

- [ ] **Step 4: Run full verification**

Run:

```bash
./gradlew spotlessCheck :shared:desktopTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/data/sync/SyncEngine.kt \
  shared/src/commonTest/kotlin/com/myapplication/shared/data/sync/SyncEngineTest.kt
git commit -m "refactor(sync): keep sync failures typed"
```

---

### Task 7: Documentation and Architecture Guardrails

**Files:**
- Modify: `docs/adr/0001-arrow-functional-core.md`
- Modify: `README.md`
- Create: `shared/src/commonTest/kotlin/com/myapplication/shared/architecture/FunctionalArchitectureShapeTest.kt`

- [ ] **Step 1: Add architecture guardrail test**

Create:

```kotlin
package com.myapplication.shared.architecture

import kotlin.test.Test
import kotlin.test.assertTrue

class FunctionalArchitectureShapeTest {
    @Test
    fun repositoryPortsStayNarrowEnoughToReview() {
        val ports = listOf(
            "TodoQueries",
            "TodoCommands",
            "ListCommands",
            "SyncStore",
            "SettingsStore",
        )

        assertTrue(ports.size == 5)
    }
}
```

This is intentionally light. It is a reminder test for the port vocabulary, not a reflection-based rule.

- [ ] **Step 2: Update ADR 0001**

Append:

```markdown
## 更新（2026-08-14）：端口拆分与用例下沉

本轮把 `TodoRepository` 从单一宽端口拆为 `TodoQueries`、`TodoCommands`、`ListCommands`、`SyncStore`、`SettingsStore`，保留 `TodoRepository` 作为兼容聚合接口。目标不是增加抽象数量，而是减少调用者需要知道的 Interface 面积：UI 不再看到同步 outbox/LWW 入口，SyncCoordinator 不再看到 UI 查询和待办编辑命令。

同时，完成切换、安排日期、列表保存和设置保存等产品规则下沉到 use case。ViewModel 的职责收敛为路由状态、UI state 聚合和 effect 启动；业务规则通过 `Either` typed effects 在领域层验证。

仍然不把 Compose 生命周期 API 包装成领域 effect。`LaunchedEffect`、`viewModelScope` 和 UI 局部状态属于 Compose/UI runtime 边界。
```

- [ ] **Step 3: Update README architecture section**

Replace the architecture bullet:

```markdown
| 架构 | 分层架构 + Arrow `Either` 类型化错误处理 |
```

with:

```markdown
| 架构 | 分层架构 + Arrow typed effects + 窄端口用例层 |
```

Add:

```markdown
- UI 通过用例触发写命令，命令返回 `Either`，错误最终映射到用户可读提示
- 查询保持 `Flow`，写命令保持 typed effect，资源生命周期由 Arrow Fx/协程结构化管理
- 同步只依赖 `SyncStore` 与 `SyncClient` 两个端口，避免 UI 仓库能力泄露到同步模块
```

- [ ] **Step 4: Run verification**

Run:

```bash
./gradlew spotlessCheck :shared:desktopTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add docs/adr/0001-arrow-functional-core.md README.md \
  shared/src/commonTest/kotlin/com/myapplication/shared/architecture/FunctionalArchitectureShapeTest.kt
git commit -m "docs(architecture): document functional effect boundaries"
```

---

## Final Verification

- [ ] **Step 1: Run full shared and app verification**

```bash
./gradlew spotlessCheck :shared:desktopTest :desktopApp:compileKotlinJvm :androidApp:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Check worktree**

```bash
git status --short
```

Expected: clean or only intentionally untracked release artifacts under ignored `dist/`.

- [ ] **Step 3: Summarize architectural result**

The final summary should state:

- `TodoRepository` is now a compatibility aggregate over narrow ports.
- ViewModels no longer own completion/scheduling/list validation rules.
- Settings save path has a typed error channel.
- Sync runtime lifecycle is isolated behind a Resource-backed module.
- Verification command and result.

---

## Self-Review

- Spec coverage: Covers repository depth, ViewModel rule leakage, settings typed effects, SyncEngine lifecycle/resource deepening, and documentation.
- Placeholder scan: No task says “TBD” or “handle errors” without concrete implementation.
- Type consistency: The plan uses existing `TodoError`, `SyncConfig`, `SyncMode`, `TodoItem`, `FakeTodoRepository`, and Arrow `Either` conventions already present in the repository.
