# Sundial Pro Value Features Milestone 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first product-value milestone: settings center, list management and analytics, safer list deletion, editable subtasks, recurrence basics, organization suggestions, and mature chart rendering.

**Architecture:** Keep shared business rules pure and testable, interpret database writes through the existing `Either<TodoError, A>` repository boundary, and keep UI commands behind `launchTodoEffect`. Add Vico through a narrow `ui/analytics/charts` adapter so analytics models remain independent from chart library APIs.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, SQLDelight, Arrow typed errors, Vico charts, kotlinx-datetime, existing `Rem*` design system.

---

## Scope Check

The spec covers several product subsystems. This plan implements only Milestone 1 from `docs/superpowers/specs/2026-08-13-sundial-pro-value-features-design.md`.

Milestone 1 is still broad, so execute it as independent vertical slices:

1. Dependency spike for Vico.
2. List management domain and repository semantics.
3. Settings center and list management UI.
4. Editable subtasks.
5. Recurrence data model and completion behavior.
6. Organization suggestions and panel.
7. Analytics model expansion and Vico charts.
8. Sync/schema/docs alignment and full verification.

Do not implement payment, paywall, macOS WidgetKit, LLM providers, advanced RRULE syntax, or infinite-depth subtask UI in this milestone.

## File Structure

### Build and Dependencies

- Modify `shared/build.gradle.kts`: add Vico dependency after a compile spike.

### Database

- Modify `shared/src/commonMain/sqldelight/com/myapplication/shared/data/TodoDb.sq`: add recurrence fields/table queries, list update query, list move query, list stats helper queries if useful.
- Create `shared/src/commonMain/sqldelight/com/myapplication/shared/data/2.sqm`: migrate existing local databases to recurrence fields/table.
- Modify `docs/sync-setup.md`: add remote SQL for recurrence columns/table when sync support lands.

### Domain

- Create `shared/src/commonMain/kotlin/com/myapplication/shared/domain/list/DeleteListPolicy.kt`: list deletion policy ADT.
- Create `shared/src/commonMain/kotlin/com/myapplication/shared/domain/list/ListStats.kt`: pure list stats model and builder.
- Create `shared/src/commonMain/kotlin/com/myapplication/shared/domain/organize/OrganizationReason.kt`: organization reason ADT.
- Create `shared/src/commonMain/kotlin/com/myapplication/shared/domain/organize/OrganizationSuggestion.kt`: suggestion model and actions.
- Create `shared/src/commonMain/kotlin/com/myapplication/shared/domain/organize/OrganizationRules.kt`: pure suggestion builder.
- Create `shared/src/commonMain/kotlin/com/myapplication/shared/domain/recurrence/RecurrenceRule.kt`: recurrence ADT.
- Create `shared/src/commonMain/kotlin/com/myapplication/shared/domain/recurrence/RecurrenceCalculator.kt`: pure next occurrence calculator.
- Create `shared/src/commonMain/kotlin/com/myapplication/shared/domain/recurrence/CompleteRecurringTodoUseCase.kt`: command use case for completing recurring todos.
- Create `shared/src/commonMain/kotlin/com/myapplication/shared/domain/analytics/AnalyticsRange.kt`: week/month range.
- Create `shared/src/commonMain/kotlin/com/myapplication/shared/domain/analytics/ChartSeries.kt`: chart-neutral series models.
- Create `shared/src/commonMain/kotlin/com/myapplication/shared/domain/analytics/ListAnalyticsModel.kt`: pure list-specific analytics builder.

### Repository and Sync

- Modify `shared/src/commonMain/kotlin/com/myapplication/shared/domain/repository/TodoRepository.kt`: add list update/delete policy, list stats, recurrence methods.
- Modify `shared/src/commonMain/kotlin/com/myapplication/shared/data/TodoRepositoryImpl.kt`: implement new commands with outbox snapshots.
- Modify `shared/src/commonMain/kotlin/com/myapplication/shared/domain/sync/SyncModels.kt`: include recurrence fields in `TodoRowDto` and add recurrence DTO later in the sync task.
- Modify `shared/src/commonMain/kotlin/com/myapplication/shared/domain/sync/SyncCoordinator.kt`: route recurrence rows if recurrence table sync is enabled in this milestone.
- Modify `shared/src/commonMain/kotlin/com/myapplication/shared/data/sync/SupabaseSyncClient.kt`: push/pull recurrence table if enabled in this milestone.
- Modify `shared/src/commonTest/kotlin/com/myapplication/shared/test/FakeTodoRepository.kt`: implement new repository contract for tests.

### UI

- Modify `shared/src/commonMain/kotlin/com/myapplication/shared/ui/main/MainViewModel.kt`: add list update/delete policy commands and subtask-aware detail routing.
- Modify `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/SettingsScreen.kt`: split into settings center.
- Create `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/SettingsSection.kt`: settings sections.
- Create `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/SettingsHome.kt`: settings shell with section navigation.
- Create `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/ListSettingsScreen.kt`: list settings content.
- Create `shared/src/commonMain/kotlin/com/myapplication/shared/ui/list/ListEditorDialog.kt`: create/rename/color dialog.
- Create `shared/src/commonMain/kotlin/com/myapplication/shared/ui/list/DeleteListDialog.kt`: safe delete confirmation.
- Create `shared/src/commonMain/kotlin/com/myapplication/shared/ui/list/ListAnalyticsPanel.kt`: single-list stats and chart host.
- Modify `shared/src/commonMain/kotlin/com/myapplication/shared/ui/detail/DetailContent.kt`: make subtask rows editable and navigable, add recurrence picker.
- Modify `shared/src/commonMain/kotlin/com/myapplication/shared/ui/detail/DetailViewModel.kt`: add subtask title editing and recurrence commands.
- Create `shared/src/commonMain/kotlin/com/myapplication/shared/ui/recurrence/RecurrencePicker.kt`: recurrence selector.
- Create `shared/src/commonMain/kotlin/com/myapplication/shared/ui/recurrence/RecurrenceSummary.kt`: compact recurrence label.
- Create `shared/src/commonMain/kotlin/com/myapplication/shared/ui/organize/OrganizePanel.kt`: organization panel.
- Create `shared/src/commonMain/kotlin/com/myapplication/shared/ui/organize/OrganizeSection.kt`: grouped suggestion section.
- Create `shared/src/commonMain/kotlin/com/myapplication/shared/ui/organize/SuggestionRow.kt`: suggestion row with action buttons.
- Create `shared/src/commonMain/kotlin/com/myapplication/shared/ui/analytics/charts/AnalyticsChartTheme.kt`: Vico theme adapter.
- Create `shared/src/commonMain/kotlin/com/myapplication/shared/ui/analytics/charts/CompletionTrendChart.kt`: Vico line chart.
- Create `shared/src/commonMain/kotlin/com/myapplication/shared/ui/analytics/charts/EnergyOutputChart.kt`: Vico column/bar chart.
- Create `shared/src/commonMain/kotlin/com/myapplication/shared/ui/analytics/charts/PressureDistributionChart.kt`: Vico distribution chart.
- Modify `shared/src/commonMain/kotlin/com/myapplication/shared/ui/analytics/AnalyticsModel.kt`: move or bridge to domain analytics models.
- Modify `shared/src/commonMain/kotlin/com/myapplication/shared/ui/analytics/AnalyticsScreen.kt`: consume chart adapters instead of hand-drawn chart code.

### Tests

- Create `shared/src/commonTest/kotlin/com/myapplication/shared/domain/list/ListStatsTest.kt`.
- Create `shared/src/commonTest/kotlin/com/myapplication/shared/domain/organize/OrganizationRulesTest.kt`.
- Create `shared/src/commonTest/kotlin/com/myapplication/shared/domain/recurrence/RecurrenceCalculatorTest.kt`.
- Create `shared/src/commonTest/kotlin/com/myapplication/shared/domain/recurrence/CompleteRecurringTodoUseCaseTest.kt`.
- Create `shared/src/commonTest/kotlin/com/myapplication/shared/domain/analytics/ListAnalyticsModelTest.kt`.
- Modify `shared/src/desktopTest/kotlin/com/myapplication/shared/data/TodoRepositoryImplTest.kt`: repository recurrence/list policy tests.
- Modify `shared/src/commonTest/kotlin/com/myapplication/shared/ui/main/MainViewModelTest.kt`: settings/list/subtask command tests.
- Modify `shared/src/commonTest/kotlin/com/myapplication/shared/ui/analytics/AnalyticsModelTest.kt`: week/month and series tests.

---

## Task 1: Vico Dependency Spike

**Files:**
- Modify: `shared/build.gradle.kts`
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/analytics/charts/AnalyticsChartTheme.kt`
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/analytics/charts/CompletionTrendChart.kt`

- [ ] **Step 1: Add Vico dependency**

Edit `shared/build.gradle.kts` in `commonMain.dependencies`.

Prefer the current stable Vico Compose Multiplatform artifact available from Maven Central. Start with:

```kotlin
val vicoVersion = "2.5.2"
implementation("com.patrykandpatrick.vico:multiplatform:$vicoVersion")
```

If Gradle cannot resolve `multiplatform`, try:

```kotlin
implementation("com.patrykandpatrick.vico:compose-multiplatform:$vicoVersion")
```

If neither artifact resolves, switch this task to Koala Plot with:

```kotlin
implementation("io.github.koalaplot:koalaplot-core:0.12.1")
```

- [ ] **Step 2: Create a minimal chart adapter file**

Create `shared/src/commonMain/kotlin/com/myapplication/shared/ui/analytics/charts/AnalyticsChartTheme.kt`.

```kotlin
package com.myapplication.shared.ui.analytics.charts

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.myapplication.shared.ui.theme.LocalRemColors

internal data class AnalyticsChartColors(
    val primary: Color,
    val secondary: Color,
    val warning: Color,
    val danger: Color,
    val grid: Color,
    val label: Color,
)

@Composable
internal fun rememberAnalyticsChartColors(): AnalyticsChartColors {
    val colors = LocalRemColors.current
    return AnalyticsChartColors(
        primary = colors.brand,
        secondary = colors.info,
        warning = colors.warning,
        danger = colors.error,
        grid = colors.border,
        label = colors.textLow,
    )
}
```

- [ ] **Step 3: Create a compile-only chart host**

Create `shared/src/commonMain/kotlin/com/myapplication/shared/ui/analytics/charts/CompletionTrendChart.kt`.

For the first spike, keep the public component stable and put library-specific calls inside this file. If Vico imports differ, adjust only this file.

```kotlin
package com.myapplication.shared.ui.analytics.charts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.analytics.ChartPoint
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemType

@Composable
internal fun CompletionTrendChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
) {
    val colors = LocalRemColors.current
    Box(
        modifier
            .fillMaxWidth()
            .height(160.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "完成趋势 ${points.sumOf { it.value }}",
            style = RemType.text12.copy(color = colors.textLow),
        )
    }
}
```

This temporary compile host exists only inside the spike task to establish a stable call site. Task 15 replaces its body with Vico rendering before the milestone is complete.

- [ ] **Step 4: Run dependency compile**

Run:

```bash
./gradlew :shared:compileKotlinDesktop :androidApp:assembleDebug --no-daemon --console=plain
```

Expected: build succeeds. If dependency resolution fails, switch artifact as described in Step 1 and rerun.

- [ ] **Step 5: Commit**

```bash
git add shared/build.gradle.kts shared/src/commonMain/kotlin/com/myapplication/shared/ui/analytics/charts
git commit -F - <<'EOF'
chore(analytics): spike chart library dependency

[Change Nature]
- This commit adds chart infrastructure only.

[Maintenance Work]
- Add the Compose Multiplatform chart dependency.
- Create a narrow analytics chart adapter package.

[Implementation]
- Keep the initial chart host minimal to validate dependency compatibility.
- Leave product chart rendering for the analytics implementation task.

[Impact]
- Affects shared build configuration and analytics chart scaffolding.
- Runtime analytics behavior is unchanged until the chart screen is wired.
EOF
```

---

## Task 2: Chart-Neutral Analytics Series Models

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/analytics/AnalyticsRange.kt`
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/analytics/ChartSeries.kt`
- Test: `shared/src/commonTest/kotlin/com/myapplication/shared/domain/analytics/ChartSeriesTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `shared/src/commonTest/kotlin/com/myapplication/shared/domain/analytics/ChartSeriesTest.kt`.

```kotlin
package com.myapplication.shared.domain.analytics

import kotlin.test.Test
import kotlin.test.assertEquals

class ChartSeriesTest {
    @Test
    fun weekRangeUsesSevenDays() {
        assertEquals(7, AnalyticsRange.Week.dayCount)
    }

    @Test
    fun monthRangeUsesThirtyDays() {
        assertEquals(30, AnalyticsRange.Month.dayCount)
    }

    @Test
    fun maxValueFallsBackToOneForEmptySeries() {
        assertEquals(1, ChartSeries("完成趋势", emptyList()).maxValue)
    }

    @Test
    fun maxValueUsesLargestPointValue() {
        val series = ChartSeries(
            title = "精力输出",
            points = listOf(
                ChartPoint("8/11", 2),
                ChartPoint("8/12", 7),
                ChartPoint("8/13", 4),
            ),
        )

        assertEquals(7, series.maxValue)
    }
}
```

- [ ] **Step 2: Run the tests and verify failure**

Run:

```bash
./gradlew :shared:desktopTest --tests com.myapplication.shared.domain.analytics.ChartSeriesTest --no-daemon --console=plain
```

Expected: fails because `AnalyticsRange`, `ChartSeries`, and `ChartPoint` do not exist.

- [ ] **Step 3: Implement the models**

Create `shared/src/commonMain/kotlin/com/myapplication/shared/domain/analytics/AnalyticsRange.kt`.

```kotlin
package com.myapplication.shared.domain.analytics

enum class AnalyticsRange(val dayCount: Int) {
    Week(7),
    Month(30),
}
```

Create `shared/src/commonMain/kotlin/com/myapplication/shared/domain/analytics/ChartSeries.kt`.

```kotlin
package com.myapplication.shared.domain.analytics

data class ChartPoint(
    val label: String,
    val value: Int,
)

data class ChartBucket(
    val label: String,
    val value: Int,
    val tone: ChartTone = ChartTone.Neutral,
)

enum class ChartTone {
    Primary,
    Info,
    Warning,
    Danger,
    Neutral,
}

data class ChartSeries(
    val title: String,
    val points: List<ChartPoint>,
) {
    val maxValue: Int = points.maxOfOrNull { it.value } ?: 1
}

data class BucketSeries(
    val title: String,
    val buckets: List<ChartBucket>,
) {
    val total: Int = buckets.sumOf { it.value }
}
```

- [ ] **Step 4: Run the tests and verify pass**

Run:

```bash
./gradlew :shared:desktopTest --tests com.myapplication.shared.domain.analytics.ChartSeriesTest --no-daemon --console=plain
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/domain/analytics shared/src/commonTest/kotlin/com/myapplication/shared/domain/analytics
git commit -F - <<'EOF'
feat(analytics): add chart-neutral series models

[Change Nature]
- This commit adds domain models for analytics charts.

[New Capability]
- Analytics code can describe week/month ranges and chart data without depending on a rendering library.

[Implementation]
- Add range, point, bucket, tone, and series models.
- Cover empty and populated series behavior with desktop tests.

[Impact]
- Prepares the analytics screen for Vico rendering.
- Existing UI behavior is unchanged.
EOF
```

---

## Task 3: List Stats Domain

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/list/ListStats.kt`
- Test: `shared/src/commonTest/kotlin/com/myapplication/shared/domain/list/ListStatsTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `shared/src/commonTest/kotlin/com/myapplication/shared/domain/list/ListStatsTest.kt`.

Use helper constructors already present in other tests where possible. If no helper exists, create local `TodoItem` values.

```kotlin
package com.myapplication.shared.domain.list

import com.myapplication.shared.domain.model.TodoItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class ListStatsTest {
    private val tz = TimeZone.UTC
    private val today = LocalDate(2026, 8, 13)

    @Test
    fun countsListPressure() {
        val todos = listOf(
            todo(id = 1, listId = 2, dueMillis = Instant.parse("2026-08-12T09:00:00Z").toEpochMilliseconds()),
            todo(id = 2, listId = 2, dueMillis = Instant.parse("2026-08-13T09:00:00Z").toEpochMilliseconds()),
            todo(id = 3, listId = 2, dueMillis = null),
            todo(id = 4, listId = 2, completed = true, completedMillis = Instant.parse("2026-08-13T10:00:00Z").toEpochMilliseconds()),
            todo(id = 5, listId = 3, dueMillis = null),
        )

        val stats = buildListStats(listId = 2, todos = todos, today = today, timeZone = tz)

        assertEquals(3, stats.activeCount)
        assertEquals(1, stats.completedCount)
        assertEquals(1, stats.overdueCount)
        assertEquals(1, stats.todayCount)
        assertEquals(1, stats.noDateCount)
    }

    private fun todo(
        id: Long,
        listId: Long,
        dueMillis: Long?,
        completed: Boolean = false,
        completedMillis: Long? = null,
    ): TodoItem =
        TodoItem(
            id = id,
            listId = listId,
            title = "Task $id",
            note = "",
            dueDate = dueMillis?.let { Instant.fromEpochMilliseconds(it) },
            isCompleted = completed,
            completedAt = completedMillis?.let { Instant.fromEpochMilliseconds(it) },
            isTrashed = false,
            trashedAt = null,
            parentId = null,
            sortPosition = 0.0,
            flag = false,
            createdAt = Instant.fromEpochMilliseconds(0),
            updatedAt = Instant.fromEpochMilliseconds(0),
        )
}
```

- [ ] **Step 2: Run the test and verify failure**

```bash
./gradlew :shared:desktopTest --tests com.myapplication.shared.domain.list.ListStatsTest --no-daemon --console=plain
```

Expected: fails because `ListStats` and `buildListStats` do not exist.

- [ ] **Step 3: Implement list stats**

Create `shared/src/commonMain/kotlin/com/myapplication/shared/domain/list/ListStats.kt`.

```kotlin
package com.myapplication.shared.domain.list

import com.myapplication.shared.domain.model.TodoItem
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class ListStats(
    val listId: Long,
    val activeCount: Int,
    val completedCount: Int,
    val overdueCount: Int,
    val todayCount: Int,
    val noDateCount: Int,
    val trashedCount: Int,
)

fun buildListStats(
    listId: Long,
    todos: List<TodoItem>,
    today: LocalDate,
    timeZone: TimeZone,
): ListStats {
    val inList = todos.filter { it.listId == listId }
    val active = inList.filter { !it.isTrashed && !it.isCompleted }
    val completed = inList.filter { !it.isTrashed && it.isCompleted }
    return ListStats(
        listId = listId,
        activeCount = active.size,
        completedCount = completed.size,
        overdueCount = active.count { it.localDueDate(timeZone)?.let { due -> due < today } == true },
        todayCount = active.count { it.localDueDate(timeZone) == today },
        noDateCount = active.count { it.dueDate == null },
        trashedCount = inList.count { it.isTrashed },
    )
}

private fun TodoItem.localDueDate(timeZone: TimeZone): LocalDate? =
    dueDate?.toLocalDateTime(timeZone)?.date
```

- [ ] **Step 4: Run the test and verify pass**

```bash
./gradlew :shared:desktopTest --tests com.myapplication.shared.domain.list.ListStatsTest --no-daemon --console=plain
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/domain/list/ListStats.kt shared/src/commonTest/kotlin/com/myapplication/shared/domain/list/ListStatsTest.kt
git commit -F - <<'EOF'
feat(list): add list stats model

[Change Nature]
- This commit adds list-level domain statistics.

[New Capability]
- The app can compute active, completed, overdue, today, no-date, and trashed counts for a list.

[Implementation]
- Add a pure list stats builder that takes todos, a date, and a timezone.
- Cover list filtering and pressure counts with desktop tests.

[Impact]
- Prepares list management and list analytics UI.
- Runtime UI behavior is unchanged.
EOF
```

---

## Task 4: Safer List Repository Semantics

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/list/DeleteListPolicy.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/repository/TodoRepository.kt`
- Modify: `shared/src/commonMain/sqldelight/com/myapplication/shared/data/TodoDb.sq`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/data/TodoRepositoryImpl.kt`
- Modify: `shared/src/commonTest/kotlin/com/myapplication/shared/test/FakeTodoRepository.kt`
- Test: `shared/src/desktopTest/kotlin/com/myapplication/shared/data/TodoRepositoryImplTest.kt`

- [ ] **Step 1: Write failing repository tests**

Append tests to `shared/src/desktopTest/kotlin/com/myapplication/shared/data/TodoRepositoryImplTest.kt`.

```kotlin
@Test
fun deleteListMovesTasksToInboxByDefaultPolicy() = runTest {
    val repo = createRepository()
    repo.ensureInbox().getOrNull()
    repo.addList("项目", "red")
    val projectId = repo.observeLists().first().first { it.name == "项目" }.id
    repo.insertTodo(projectId, "A", "", null, null, false)

    repo.deleteList(projectId, DeleteListPolicy.MoveTasksToInbox).getOrNull()

    val inboxId = repo.observeLists().first().first { it.name == "收件箱" }.id
    val active = repo.observeAllActive().first()
    assertEquals(inboxId, active.single { it.title == "A" }.listId)
}

@Test
fun deleteListCanMoveTasksToTrashWithDangerPolicy() = runTest {
    val repo = createRepository()
    repo.ensureInbox().getOrNull()
    repo.addList("项目", "red")
    val projectId = repo.observeLists().first().first { it.name == "项目" }.id
    repo.insertTodo(projectId, "A", "", null, null, false)

    repo.deleteList(projectId, DeleteListPolicy.MoveTasksToTrash).getOrNull()

    assertTrue(repo.observeAllActive().first().none { it.title == "A" })
    assertTrue(repo.observeTrashed().first().any { it.title == "A" })
}

@Test
fun inboxCannotBeDeleted() = runTest {
    val repo = createRepository()
    val inboxId = repo.ensureInbox().getOrNull()!!

    val result = repo.deleteList(inboxId, DeleteListPolicy.MoveTasksToInbox)

    assertTrue(result.isLeft())
}
```

Add imports:

```kotlin
import com.myapplication.shared.domain.list.DeleteListPolicy
import arrow.core.getOrNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
```

- [ ] **Step 2: Run tests and verify failure**

```bash
./gradlew :shared:desktopTest --tests com.myapplication.shared.data.TodoRepositoryImplTest --no-daemon --console=plain
```

Expected: fails because `DeleteListPolicy` and `deleteList(listId, policy)` do not exist.

- [ ] **Step 3: Add delete policy ADT**

Create `shared/src/commonMain/kotlin/com/myapplication/shared/domain/list/DeleteListPolicy.kt`.

```kotlin
package com.myapplication.shared.domain.list

enum class DeleteListPolicy {
    MoveTasksToInbox,
    MoveTasksToTrash,
}
```

- [ ] **Step 4: Update repository interface**

Modify `shared/src/commonMain/kotlin/com/myapplication/shared/domain/repository/TodoRepository.kt`.

```kotlin
import com.myapplication.shared.domain.list.DeleteListPolicy
import com.myapplication.shared.domain.list.ListStats
```

Replace:

```kotlin
suspend fun deleteList(listId: Long): Either<TodoError, Unit>
```

With:

```kotlin
suspend fun updateList(listId: Long, name: String, colorKey: String): Either<TodoError, Unit>
suspend fun deleteList(listId: Long, policy: DeleteListPolicy = DeleteListPolicy.MoveTasksToInbox): Either<TodoError, Unit>
fun observeListStats(listId: Long): Flow<ListStats>
```

- [ ] **Step 5: Add SQL queries**

Modify `shared/src/commonMain/sqldelight/com/myapplication/shared/data/TodoDb.sq`.

Add after `selectByIdForList`:

```sql
updateList:
UPDATE reminder_list
SET name = ?, color_key = ?, updated_at = ?, updated_by = ?
WHERE id = ?;

moveTodosInList:
UPDATE todo
SET list_id = ?, updated_at = ?, updated_by = ?
WHERE list_id = ? AND is_trashed = 0;
```

- [ ] **Step 6: Implement repository methods**

Modify `TodoRepositoryImpl`.

Add imports:

```kotlin
import com.myapplication.shared.domain.list.DeleteListPolicy
import com.myapplication.shared.domain.list.ListStats
import com.myapplication.shared.domain.list.buildListStats
```

Add:

```kotlin
override fun observeListStats(listId: Long): Flow<ListStats> =
    observeAllIncludingTrashed()
        .map { todos ->
            buildListStats(
                listId = listId,
                todos = todos,
                today = clock.now().toLocalDateTime(timeZone).date,
                timeZone = timeZone,
            )
        }
        .flowOn(dbDispatcher)
```

If `observeAllIncludingTrashed()` does not exist, add a private flow:

```kotlin
private fun observeAllIncludingTrashed(): Flow<List<TodoItem>> =
    db.todoDbQueries.selectAllTodos().asFlow()
        .map { it.executeAsList().map { row -> row.toDomain() } }
        .flowOn(dbDispatcher)
```

And add SQL:

```sql
selectAllTodos:
SELECT * FROM todo ORDER BY is_trashed, is_completed, due_date IS NULL, due_date, sort_position, id;
```

Implement `updateList`:

```kotlin
override suspend fun updateList(listId: Long, name: String, colorKey: String): Either<TodoError, Unit> =
    dbCommand("更新列表失败") {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) raise(TodoError.Persistence("列表名称不能为空"))
        db.transaction {
            val existing = db.todoDbQueries.selectByIdForList(listId).executeAsOneOrNull()
                ?: raise(TodoError.Persistence("列表不存在"))
            if (existing.name == "收件箱" && existing.position == 0L) {
                raise(TodoError.Persistence("收件箱不能改名"))
            }
            db.todoDbQueries.updateList(trimmed, colorKey, now, deviceId, listId)
            val row = db.todoDbQueries.selectByIdForList(listId).executeAsOne()
            appendListOutbox(row.toDto())
        }
    }
```

Replace `deleteList` with:

```kotlin
override suspend fun deleteList(listId: Long, policy: DeleteListPolicy): Either<TodoError, Unit> =
    dbCommand("删除列表失败") {
        db.transaction {
            val list = db.todoDbQueries.selectByIdForList(listId).executeAsOneOrNull()
                ?: raise(TodoError.Persistence("列表不存在"))
            if (list.name == "收件箱" && list.position == 0L) {
                raise(TodoError.Persistence("收件箱是系统待整理池，不能删除"))
            }
            val affected = db.todoDbQueries.selectByList(listId).executeAsList()
            when (policy) {
                DeleteListPolicy.MoveTasksToInbox -> {
                    val inboxId = db.todoDbQueries.selectLists().executeAsList()
                        .firstOrNull { it.name == "收件箱" && it.position == 0L }
                        ?.id
                        ?: raise(TodoError.InboxNotFound)
                    db.todoDbQueries.moveTodosInList(inboxId, now, deviceId, listId)
                }
                DeleteListPolicy.MoveTasksToTrash -> {
                    db.todoDbQueries.trashTodosInList(now, now, deviceId, listId)
                }
            }
            affected.forEach { old ->
                db.todoDbQueries.selectById(old.id).executeAsOneOrNull()?.let { updated ->
                    appendTodoOutbox(updated.toDto())
                }
            }
            db.todoDbQueries.deleteList(listId)
            appendListOutbox(list.toDto(), SyncAction.DELETE)
        }
    }
```

- [ ] **Step 7: Update fake repository**

Modify `shared/src/commonTest/kotlin/com/myapplication/shared/test/FakeTodoRepository.kt`.

Add compatible methods:

```kotlin
override suspend fun updateList(listId: Long, name: String, colorKey: String): Either<TodoError, Unit> =
    Either.Right(Unit)

override suspend fun deleteList(listId: Long, policy: DeleteListPolicy): Either<TodoError, Unit> =
    Either.Right(Unit)

override fun observeListStats(listId: Long): Flow<ListStats> =
    todosState.map {
        buildListStats(
            listId = listId,
            todos = it,
            today = LocalDate(2026, 8, 13),
            timeZone = TimeZone.UTC,
        )
    }
```

- [ ] **Step 8: Run repository tests**

```bash
./gradlew :shared:desktopTest --tests com.myapplication.shared.data.TodoRepositoryImplTest --no-daemon --console=plain
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add shared/src/commonMain shared/src/commonTest shared/src/desktopTest
git commit -F - <<'EOF'
feat(list): protect tasks when deleting lists

[Change Nature]
- This commit changes list deletion semantics.

[New Capability]
- Lists can be renamed and recolored.
- Deleting a custom list can move tasks to the inbox or trash based on an explicit policy.

[Implementation]
- Add DeleteListPolicy and list stats repository contract.
- Add SQL queries for list update, list task moves, and full todo observation.
- Keep inbox protected from rename and delete operations.

[Impact]
- Default list deletion now preserves tasks by moving them to the inbox.
- Trash behavior remains available through an explicit dangerous policy.
EOF
```

---

## Task 5: Settings Center Shell

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/SettingsSection.kt`
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/SettingsHome.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Add settings sections**

Create `SettingsSection.kt`.

```kotlin
package com.myapplication.shared.ui.settings

import com.myapplication.shared.ui.components.IconName

enum class SettingsSection(
    val title: String,
    val subtitle: String,
    val icon: IconName,
) {
    Sync("同步", "连接、状态和手动同步", IconName.Sync),
    Lists("列表", "管理列表、颜色和统计", IconName.Inbox),
    Widgets("小组件", "今天摘要和桌面组件", IconName.Today),
    Data("数据", "导出、备份和垃圾箱", IconName.Tray),
    Appearance("外观", "主题和显示密度", IconName.Settings),
    About("关于", "版本和许可证", IconName.Device),
}
```

- [ ] **Step 2: Extract existing sync content**

In `SettingsScreen.kt`, rename the current top-level composable body into:

```kotlin
@Composable
internal fun SyncSettingsContent(vm: SettingsViewModel, onBack: () -> Unit) {
    // Move the current SettingsScreen body here, keeping existing behavior.
}
```

Then change the old public entry point to call the new shell:

```kotlin
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    SettingsHome(
        vm = vm,
        onBack = onBack,
    )
}
```

- [ ] **Step 3: Create settings home shell**

Create `SettingsHome.kt`.

```kotlin
package com.myapplication.shared.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.myapplication.shared.ui.components.RemButton
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemType

@Composable
internal fun SettingsHome(
    vm: SettingsViewModel,
    onBack: () -> Unit,
) {
    val colors = LocalRemColors.current
    var selected by remember { mutableStateOf(SettingsSection.Sync) }
    Row(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(colors.bgBase),
    ) {
        Column(
            Modifier
                .width(260.dp)
                .fillMaxSize()
                .background(colors.surfaceAlt)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    BasicText("设置", style = RemType.title20.copy(color = colors.textHigh))
                    BasicText("配置 Sundial", style = RemType.text12.copy(color = colors.textLow))
                }
                RemButton("返回", onClick = onBack)
            }
            Spacer(Modifier.height(18.dp))
            SettingsSection.entries.forEach { section ->
                SettingsSectionRow(
                    section = section,
                    selected = selected == section,
                    onClick = { selected = section },
                )
            }
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxSize()
                .padding(24.dp),
        ) {
            when (selected) {
                SettingsSection.Sync -> SyncSettingsContent(vm, onBack)
                SettingsSection.Lists -> BasicText("列表管理", style = RemType.title20.copy(color = colors.textHigh))
                SettingsSection.Widgets -> BasicText("小组件", style = RemType.title20.copy(color = colors.textHigh))
                SettingsSection.Data -> BasicText("数据", style = RemType.title20.copy(color = colors.textHigh))
                SettingsSection.Appearance -> BasicText("外观", style = RemType.title20.copy(color = colors.textHigh))
                SettingsSection.About -> BasicText("关于", style = RemType.title20.copy(color = colors.textHigh))
            }
        }
    }
}

@Composable
private fun SettingsSectionRow(
    section: SettingsSection,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalRemColors.current
    val shape = RoundedCornerShape(RemRadii.r4)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) colors.brandSubtle else colors.surfaceAlt)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemIcon(section.icon, if (selected) colors.brand else colors.textLow, Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            BasicText(section.title, style = RemType.text14.copy(color = colors.textHigh))
            BasicText(section.subtitle, style = RemType.text12.copy(color = colors.textLow))
        }
    }
}
```

- [ ] **Step 4: Run compile**

```bash
./gradlew :desktopApp:compileKotlinJvm :androidApp:assembleDebug --no-daemon --console=plain
```

Expected: PASS. The Lists/Widgets/Data/Appearance/About sections show temporary section titles until their concrete screens are wired.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings
git commit -F - <<'EOF'
feat(settings): add settings center shell

[Change Nature]
- This commit adds a new settings information architecture.

[New Capability]
- Settings now has sections for sync, lists, widgets, data, appearance, and about.

[Implementation]
- Preserve the existing sync settings content.
- Add a section rail and route section selection inside the settings screen.

[Impact]
- Settings is ready for list management and future product configuration areas.
- Existing sync configuration remains available.
EOF
```

---

## Task 6: List Management UI

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/ListSettingsScreen.kt`
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/list/ListEditorDialog.kt`
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/list/DeleteListDialog.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/SettingsHome.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/main/MainViewModel.kt`
- Test: `shared/src/commonTest/kotlin/com/myapplication/shared/ui/main/MainViewModelTest.kt`

- [ ] **Step 1: Add MainViewModel list commands**

Modify `MainViewModel.kt`.

Add import:

```kotlin
import com.myapplication.shared.domain.list.DeleteListPolicy
```

Add:

```kotlin
fun updateList(list: TodoList, name: String, colorKey: String) {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return
    launchTodoEffect(lastError) { repository.updateList(list.id, trimmed, colorKey) }
}

fun deleteList(list: TodoList, policy: DeleteListPolicy) {
    launchTodoEffect(lastError) { repository.deleteList(list.id, policy) }
    if (scope.value == Scope.List(list.id)) scope.value = Scope.All
}
```

Replace existing `deleteList(list: TodoList)` with:

```kotlin
fun deleteList(list: TodoList) {
    deleteList(list, DeleteListPolicy.MoveTasksToInbox)
}
```

- [ ] **Step 2: Add ViewModel tests**

Append to `MainViewModelTest.kt`:

```kotlin
@Test
fun deleteListDefaultsToMoveTasksToInbox() = runTest {
    val repo = FakeTodoRepository()
    val vm = MainViewModel(repo, AddTodoUseCase(repo))
    val list = TodoList(7, "项目", "blue", 1, Instant.fromEpochMilliseconds(0))

    vm.deleteList(list)
    runCurrent()

    assertEquals(DeleteListPolicy.MoveTasksToInbox, repo.lastDeleteListPolicy)
}

@Test
fun updateListTrimsNameBeforeWriting() = runTest {
    val repo = FakeTodoRepository()
    val vm = MainViewModel(repo, AddTodoUseCase(repo))
    val list = TodoList(7, "项目", "blue", 1, Instant.fromEpochMilliseconds(0))

    vm.updateList(list, "  研究  ", "red")
    runCurrent()

    assertEquals("研究", repo.lastUpdatedListName)
    assertEquals("red", repo.lastUpdatedListColor)
}
```

Update `FakeTodoRepository` with observable fields:

```kotlin
var lastDeleteListPolicy: DeleteListPolicy? = null
var lastUpdatedListName: String? = null
var lastUpdatedListColor: String? = null
```

Update fake methods:

```kotlin
override suspend fun updateList(listId: Long, name: String, colorKey: String): Either<TodoError, Unit> {
    lastUpdatedListName = name
    lastUpdatedListColor = colorKey
    return Either.Right(Unit)
}

override suspend fun deleteList(listId: Long, policy: DeleteListPolicy): Either<TodoError, Unit> {
    lastDeleteListPolicy = policy
    return Either.Right(Unit)
}
```

- [ ] **Step 3: Run ViewModel tests**

```bash
./gradlew :shared:desktopTest --tests com.myapplication.shared.ui.main.MainViewModelTest --no-daemon --console=plain
```

Expected: PASS.

- [ ] **Step 4: Create list editor dialog**

Create `ListEditorDialog.kt`.

```kotlin
package com.myapplication.shared.ui.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.ui.components.RemDialog
import com.myapplication.shared.ui.components.RemTextField

@Composable
internal fun ListEditorDialog(
    list: TodoList?,
    onDismiss: () -> Unit,
    onSave: (name: String, colorKey: String) -> Unit,
) {
    var name by remember(list?.id) { mutableStateOf(list?.name ?: "") }
    val colorKey = list?.colorKey ?: "blue"
    RemDialog(
        title = if (list == null) "新建列表" else "编辑列表",
        confirmText = "保存",
        onDismiss = onDismiss,
        onConfirm = {
            onSave(name, colorKey)
            onDismiss()
        },
        content = {
            Column {
                RemTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "列表名称",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
            }
        },
    )
}
```

Add color swatches in a follow-up inside this task after confirming the project’s `ListColorOf` API. The minimum milestone behavior is rename/create.

- [ ] **Step 5: Create delete list dialog**

Create `DeleteListDialog.kt`.

```kotlin
package com.myapplication.shared.ui.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.list.DeleteListPolicy
import com.myapplication.shared.domain.list.ListStats
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.ui.components.RemButton
import com.myapplication.shared.ui.components.RemButtonVariant
import com.myapplication.shared.ui.components.RemDialog
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemType

@Composable
internal fun DeleteListDialog(
    list: TodoList,
    stats: ListStats?,
    onDismiss: () -> Unit,
    onDelete: (DeleteListPolicy) -> Unit,
) {
    val colors = LocalRemColors.current
    RemDialog(
        title = "删除 ${list.name}",
        onDismiss = onDismiss,
        showButtons = false,
        content = {
            Column {
                BasicText(
                    text = "默认会删除列表，并把其中任务移到收件箱。",
                    style = RemType.text14.copy(color = colors.textNormal),
                )
                Spacer(Modifier.height(8.dp))
                BasicText(
                    text = stats?.let { "未完成 ${it.activeCount} · 已完成 ${it.completedCount} · 逾期 ${it.overdueCount}" } ?: "正在读取列表统计…",
                    style = RemType.text12.copy(color = colors.textLow),
                )
                Spacer(Modifier.height(14.dp))
                RemButton(
                    text = "删除列表，任务移到收件箱",
                    onClick = {
                        onDelete(DeleteListPolicy.MoveTasksToInbox)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                RemButton(
                    text = "删除列表，并将任务移到垃圾箱",
                    onClick = {
                        onDelete(DeleteListPolicy.MoveTasksToTrash)
                        onDismiss()
                    },
                    variant = RemButtonVariant.Danger,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}
```

- [ ] **Step 6: Create list settings screen**

Create `ListSettingsScreen.kt`.

```kotlin
package com.myapplication.shared.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.ui.components.RemButton
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.list.DeleteListDialog
import com.myapplication.shared.ui.list.ListEditorDialog
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemType

@Composable
internal fun ListSettingsScreen(mainVm: MainViewModel) {
    val colors = LocalRemColors.current
    val lists by mainVm.lists.collectAsState()
    val counts by mainVm.listCounts.collectAsState()
    var editing by remember { mutableStateOf<TodoList?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TodoList?>(null) }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText("列表", style = RemType.title20.copy(color = colors.textHigh))
                BasicText("管理收件箱和自定义列表", style = RemType.text12.copy(color = colors.textLow))
            }
            RemButton("新建列表", onClick = { creating = true })
        }
        Spacer(Modifier.height(16.dp))
        lists.forEach { list ->
            val isInbox = list.name == "收件箱" && list.position == 0
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isInbox) { editing = list }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RemIcon(com.myapplication.shared.ui.components.IconName.Inbox, colors.textLow)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    BasicText(list.name, style = RemType.text14.copy(color = colors.textHigh))
                    BasicText("${counts[list.id] ?: 0} 项未完成", style = RemType.text12.copy(color = colors.textLow))
                }
                if (!isInbox) {
                    RemButton("删除", onClick = { deleting = list })
                }
            }
        }
    }

    if (creating) {
        ListEditorDialog(
            list = null,
            onDismiss = { creating = false },
            onSave = { name, color -> mainVm.addList(name, color) },
        )
    }
    editing?.let { list ->
        ListEditorDialog(
            list = list,
            onDismiss = { editing = null },
            onSave = { name, color -> mainVm.updateList(list, name, color) },
        )
    }
    deleting?.let { list ->
        DeleteListDialog(
            list = list,
            stats = null,
            onDismiss = { deleting = null },
            onDelete = { policy -> mainVm.deleteList(list, policy) },
        )
    }
}
```

- [ ] **Step 7: Wire settings home to list screen**

Modify `SettingsHome.kt` signature:

```kotlin
internal fun SettingsHome(
    vm: SettingsViewModel,
    mainVm: MainViewModel,
    onBack: () -> Unit,
)
```

Modify public `SettingsScreen` signature if needed and update call sites in `DesktopShell.kt` / `MobileShell.kt` to pass `mainVm`.

In `SettingsHome`, replace the temporary Lists section body:

```kotlin
SettingsSection.Lists -> ListSettingsScreen(mainVm)
```

- [ ] **Step 8: Run compile**

```bash
./gradlew :shared:desktopTest :desktopApp:compileKotlinJvm :androidApp:assembleDebug --no-daemon --console=plain
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/ui shared/src/commonTest/kotlin/com/myapplication/shared/ui shared/src/commonTest/kotlin/com/myapplication/shared/test
git commit -F - <<'EOF'
feat(settings): add list management screen

[Change Nature]
- This commit adds user-visible list management.

[New Capability]
- Users can create, rename, and delete custom lists from settings.
- Deleting a list now presents safe and dangerous policy choices.

[Implementation]
- Add list editor and delete confirmation dialogs.
- Route the settings center list section to list management content.
- Extend MainViewModel with list update and policy-based delete commands.

[Impact]
- Settings becomes a real management surface for lists.
- Inbox remains protected as a system list.
EOF
```

---

## Task 7: Editable Subtasks and Detail Navigation

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/main/MainViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/detail/DetailContent.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/detail/DetailViewModel.kt`
- Test: `shared/src/commonTest/kotlin/com/myapplication/shared/ui/main/MainViewModelTest.kt`

- [ ] **Step 1: Add parent-aware detail route**

Modify `Route.Detail`:

```kotlin
data class Detail(val todoId: Long, val parentTodoId: Long? = null) : Route
```

Modify `openDetail`:

```kotlin
fun openDetail(id: Long, parentTodoId: Long? = null) {
    route.value = Route.Detail(todoId = id, parentTodoId = parentTodoId)
}
```

Modify `back`:

```kotlin
fun back() {
    val current = route.value
    route.value = when (current) {
        is Route.Detail -> current.parentTodoId?.let { Route.Detail(it) } ?: Route.Main
        Route.Settings,
        Route.Main -> Route.Main
    }
}
```

- [ ] **Step 2: Add route test**

Append to `MainViewModelTest.kt`:

```kotlin
@Test
fun backFromSubtaskDetailReturnsToParentDetail() {
    val repo = FakeTodoRepository()
    val vm = MainViewModel(repo, AddTodoUseCase(repo))

    vm.openDetail(id = 22, parentTodoId = 11)
    vm.back()

    assertEquals(Route.Detail(11), vm.route.value)
}
```

- [ ] **Step 3: Run ViewModel test**

```bash
./gradlew :shared:desktopTest --tests com.myapplication.shared.ui.main.MainViewModelTest --no-daemon --console=plain
```

Expected: PASS after Step 1.

- [ ] **Step 4: Add subtask title command**

In `DetailViewModel.kt`, add:

```kotlin
fun setSubTaskTitle(item: TodoItem, title: String) {
    launchTodoEffect(lastError) { repository.setTitle(item.id, title) }
}
```

- [ ] **Step 5: Update subtask rows**

In `DetailContent.kt`, replace the static subtask text block with an editable row composable.

Add local composable near `DetailTitleField`:

```kotlin
@Composable
private fun SubTaskEditableRow(
    sub: TodoItem,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onTitleChange: (String) -> Unit,
    onTrash: () -> Unit,
) {
    val colors = LocalRemColors.current
    var title by remember(sub.id) { mutableStateOf(sub.title) }
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        RemCheckbox(sub.isCompleted, onToggle, size = 12.dp)
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = title,
            onValueChange = {
                title = it
                onTitleChange(it)
            },
            textStyle = RemType.text14.copy(
                color = if (sub.isCompleted) colors.textLow else colors.textHigh,
                textDecoration = if (sub.isCompleted) TextDecoration.LineThrough else null,
            ),
            cursorBrush = SolidColor(colors.brand),
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpen),
        )
        RemIconButton(IconName.Trash, "删除子任务", onClick = onTrash, size = 14.dp)
    }
}
```

Use it in the loop:

```kotlin
subtasks.forEach { sub ->
    SubTaskEditableRow(
        sub = sub,
        onToggle = { detailVm.toggleSubTask(sub) },
        onOpen = { mainVm.openDetail(sub.id, parentTodoId = current.id) },
        onTitleChange = { detailVm.setSubTaskTitle(sub, it) },
        onTrash = { detailVm.trashSubTask(sub) },
    )
}
```

- [ ] **Step 6: Run compile**

```bash
./gradlew :shared:desktopTest :desktopApp:compileKotlinJvm :androidApp:assembleDebug --no-daemon --console=plain
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/ui/main/MainViewModel.kt shared/src/commonMain/kotlin/com/myapplication/shared/ui/detail shared/src/commonTest/kotlin/com/myapplication/shared/ui/main/MainViewModelTest.kt
git commit -F - <<'EOF'
feat(detail): allow editing subtasks

[Change Nature]
- This commit adds subtask editing behavior.

[New Capability]
- Subtasks can be edited inline and opened as normal todo details.
- Returning from a subtask detail restores the parent detail.

[Implementation]
- Extend detail routing with an optional parent todo id.
- Add subtask title updates through DetailViewModel.
- Replace static subtask text rows with editable rows.

[Impact]
- Subtasks behave more like regular todos while keeping one-level UI scope.
EOF
```

---

## Task 8: Recurrence Domain

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/recurrence/RecurrenceRule.kt`
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/recurrence/RecurrenceCalculator.kt`
- Test: `shared/src/commonTest/kotlin/com/myapplication/shared/domain/recurrence/RecurrenceCalculatorTest.kt`

- [ ] **Step 1: Write failing recurrence tests**

Create `RecurrenceCalculatorTest.kt`.

```kotlin
package com.myapplication.shared.domain.recurrence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

class RecurrenceCalculatorTest {
    @Test
    fun dailyRuleMovesByIntervalDays() {
        val next = nextOccurrence(
            baseDate = LocalDate(2026, 8, 13),
            rule = RecurrenceRule.Daily(interval = 2),
        )

        assertEquals(LocalDate(2026, 8, 15), next)
    }

    @Test
    fun weeklyRuleMovesBySevenDayIntervals() {
        val next = nextOccurrence(
            baseDate = LocalDate(2026, 8, 13),
            rule = RecurrenceRule.Weekly(interval = 1),
        )

        assertEquals(LocalDate(2026, 8, 20), next)
    }

    @Test
    fun monthlyRuleKeepsDayWhenPossible() {
        val next = nextOccurrence(
            baseDate = LocalDate(2026, 8, 13),
            rule = RecurrenceRule.Monthly(interval = 1),
        )

        assertEquals(LocalDate(2026, 9, 13), next)
    }
}
```

- [ ] **Step 2: Run test and verify failure**

```bash
./gradlew :shared:desktopTest --tests com.myapplication.shared.domain.recurrence.RecurrenceCalculatorTest --no-daemon --console=plain
```

Expected: fails because recurrence types do not exist.

- [ ] **Step 3: Implement recurrence rule**

Create `RecurrenceRule.kt`.

```kotlin
package com.myapplication.shared.domain.recurrence

sealed interface RecurrenceRule {
    val interval: Int

    data class Daily(override val interval: Int = 1) : RecurrenceRule
    data class Weekly(override val interval: Int = 1) : RecurrenceRule
    data class Monthly(override val interval: Int = 1) : RecurrenceRule
}

fun RecurrenceRule.label(): String = when (this) {
    is RecurrenceRule.Daily -> if (interval == 1) "每天" else "每 $interval 天"
    is RecurrenceRule.Weekly -> if (interval == 1) "每周" else "每 $interval 周"
    is RecurrenceRule.Monthly -> if (interval == 1) "每月" else "每 $interval 月"
}
```

- [ ] **Step 4: Implement recurrence calculator**

Create `RecurrenceCalculator.kt`.

```kotlin
package com.myapplication.shared.domain.recurrence

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

fun nextOccurrence(
    baseDate: LocalDate,
    rule: RecurrenceRule,
): LocalDate =
    when (rule) {
        is RecurrenceRule.Daily -> baseDate.plus(rule.interval, DateTimeUnit.DAY)
        is RecurrenceRule.Weekly -> baseDate.plus(rule.interval * 7, DateTimeUnit.DAY)
        is RecurrenceRule.Monthly -> baseDate.plus(rule.interval, DateTimeUnit.MONTH)
    }
```

- [ ] **Step 5: Run tests**

```bash
./gradlew :shared:desktopTest --tests com.myapplication.shared.domain.recurrence.RecurrenceCalculatorTest --no-daemon --console=plain
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/domain/recurrence shared/src/commonTest/kotlin/com/myapplication/shared/domain/recurrence
git commit -F - <<'EOF'
feat(recurrence): add recurrence rule calculator

[Change Nature]
- This commit adds pure recurrence domain logic.

[New Capability]
- The app can calculate the next daily, weekly, or monthly occurrence.

[Implementation]
- Add a small recurrence ADT and label helper.
- Add nextOccurrence as a pure date function with tests.

[Impact]
- Prepares repository and detail UI recurrence support.
- No database or UI behavior changes yet.
EOF
```

---

## Task 9: Recurrence Schema and Repository

**Files:**
- Modify: `shared/src/commonMain/sqldelight/com/myapplication/shared/data/TodoDb.sq`
- Create: `shared/src/commonMain/sqldelight/com/myapplication/shared/data/2.sqm`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/model/TodoItem.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/repository/TodoRepository.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/data/TodoRepositoryImpl.kt`
- Modify: `shared/src/commonTest/kotlin/com/myapplication/shared/test/FakeTodoRepository.kt`
- Test: `shared/src/desktopTest/kotlin/com/myapplication/shared/data/TodoRepositoryImplTest.kt`

- [ ] **Step 1: Add failing repository recurrence test**

Append to `TodoRepositoryImplTest.kt`:

```kotlin
@Test
fun setRecurrencePersistsRuleOnTodo() = runTest {
    val repo = createRepository()
    val inboxId = repo.ensureInbox().getOrNull()!!
    repo.insertTodo(inboxId, "喝水", "", Instant.parse("2026-08-13T09:00:00Z"), null, false)
    val todo = repo.observeAllActive().first().single()

    repo.setRecurrence(todo.id, RecurrenceRule.Daily()).getOrNull()

    val updated = repo.findByIdActive(todo.id).getOrNull()!!
    assertEquals(RecurrenceRule.Daily(), updated.recurrenceRule)
}
```

Add imports:

```kotlin
import com.myapplication.shared.domain.recurrence.RecurrenceRule
```

- [ ] **Step 2: Run test and verify failure**

```bash
./gradlew :shared:desktopTest --tests com.myapplication.shared.data.TodoRepositoryImplTest --no-daemon --console=plain
```

Expected: fails because `TodoItem.recurrenceRule` and repository methods do not exist.

- [ ] **Step 3: Extend TodoItem**

Modify `TodoItem.kt` constructor with:

```kotlin
val recurrenceRule: RecurrenceRule? = null,
```

Add import:

```kotlin
import com.myapplication.shared.domain.recurrence.RecurrenceRule
```

- [ ] **Step 4: Add schema fields and queries**

Modify `TodoDb.sq`.

Add columns to `todo`:

```sql
recurrence_frequency TEXT,
recurrence_interval INTEGER,
```

Add query after `updateDueDate`:

```sql
updateRecurrence:
UPDATE todo
SET recurrence_frequency = ?, recurrence_interval = ?, updated_at = ?, updated_by = ?
WHERE id = ?;
```

Update `insertTodo`, `updateTodoIfNewer`, and `insertTodoIfMissing` to include the recurrence fields. Existing inserts should write `NULL, NULL`.

Create `2.sqm`:

```sql
ALTER TABLE todo ADD COLUMN recurrence_frequency TEXT;
ALTER TABLE todo ADD COLUMN recurrence_interval INTEGER;
```

- [ ] **Step 5: Map recurrence fields**

In `TodoRepositoryImpl.kt`, add mapping helpers:

```kotlin
private fun encodeFrequency(rule: RecurrenceRule?): String? = when (rule) {
    is RecurrenceRule.Daily -> "daily"
    is RecurrenceRule.Weekly -> "weekly"
    is RecurrenceRule.Monthly -> "monthly"
    null -> null
}

private fun decodeRecurrence(frequency: String?, interval: Long?): RecurrenceRule? =
    when (frequency) {
        "daily" -> RecurrenceRule.Daily(interval = (interval ?: 1L).toInt())
        "weekly" -> RecurrenceRule.Weekly(interval = (interval ?: 1L).toInt())
        "monthly" -> RecurrenceRule.Monthly(interval = (interval ?: 1L).toInt())
        else -> null
    }
```

Update row-to-domain mapping:

```kotlin
recurrenceRule = decodeRecurrence(recurrence_frequency, recurrence_interval),
```

- [ ] **Step 6: Add repository methods**

In `TodoRepository.kt`:

```kotlin
suspend fun setRecurrence(id: Long, rule: RecurrenceRule?): Either<TodoError, Unit>
```

In `TodoRepositoryImpl.kt`:

```kotlin
override suspend fun setRecurrence(id: Long, rule: RecurrenceRule?): Either<TodoError, Unit> =
    dbCommand("更新重复规则失败") {
        db.transaction {
            db.todoDbQueries.updateRecurrence(
                recurrence_frequency = encodeFrequency(rule),
                recurrence_interval = rule?.interval?.toLong(),
                updated_at = now,
                updated_by = deviceId,
                id = id,
            )
            val row = db.todoDbQueries.selectById(id).executeAsOne()
            appendTodoOutbox(row.toDto())
        }
    }
```

Update fake repository with:

```kotlin
override suspend fun setRecurrence(id: Long, rule: RecurrenceRule?): Either<TodoError, Unit> =
    Either.Right(Unit)
```

- [ ] **Step 7: Run repository tests**

```bash
./gradlew :shared:desktopTest --tests com.myapplication.shared.data.TodoRepositoryImplTest --no-daemon --console=plain
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add shared/src/commonMain shared/src/commonTest shared/src/desktopTest
git commit -F - <<'EOF'
feat(recurrence): persist todo recurrence rules

[Change Nature]
- This commit adds recurrence persistence.

[New Capability]
- Todos can store daily, weekly, and monthly recurrence rules.

[Implementation]
- Add recurrence columns and migration.
- Map recurrence fields between SQL rows and TodoItem.
- Add repository command support with outbox snapshots.

[Impact]
- Existing todos default to no recurrence.
- Sync payloads need recurrence field alignment in the sync task.
EOF
```

---

## Task 10: Complete Recurring Todo Use Case

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/recurrence/CompleteRecurringTodoUseCase.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/main/MainViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/di/AppGraph.kt`
- Test: `shared/src/commonTest/kotlin/com/myapplication/shared/domain/recurrence/CompleteRecurringTodoUseCaseTest.kt`

- [ ] **Step 1: Write failing use case test**

Create `CompleteRecurringTodoUseCaseTest.kt`.

```kotlin
package com.myapplication.shared.domain.recurrence

import arrow.core.getOrNull
import com.myapplication.shared.test.FakeTodoRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

class CompleteRecurringTodoUseCaseTest {
    @Test
    fun completingRecurringTodoCreatesNextOccurrence() = runTest {
        val repo = FakeTodoRepository()
        repo.addList("收件箱", "blue")
        repo.insertTodo(1, "喝水", "", Instant.parse("2026-08-13T09:00:00Z"), null, false)
        val todo = repo.todos.first().copy(recurrenceRule = RecurrenceRule.Daily())
        repo.replaceTodo(todo)

        val useCase = CompleteRecurringTodoUseCase(repo, TimeZone.UTC)
        useCase(todo).getOrNull()

        assertEquals(true, repo.todos.first { it.id == todo.id }.isCompleted)
        assertEquals(2, repo.todos.size)
        assertEquals("喝水", repo.todos.last().title)
        assertEquals(Instant.parse("2026-08-14T09:00:00Z"), repo.todos.last().dueDate)
    }
}
```

Add test helpers to `FakeTodoRepository`:

```kotlin
val todos: List<TodoItem> get() = todosState.value

fun replaceTodo(item: TodoItem) {
    todosState.value = todosState.value.map { if (it.id == item.id) item else it }
}
```

- [ ] **Step 2: Run test and verify failure**

```bash
./gradlew :shared:desktopTest --tests com.myapplication.shared.domain.recurrence.CompleteRecurringTodoUseCaseTest --no-daemon --console=plain
```

Expected: fails because `CompleteRecurringTodoUseCase` does not exist.

- [ ] **Step 3: Implement use case**

Create `CompleteRecurringTodoUseCase.kt`.

```kotlin
package com.myapplication.shared.domain.recurrence

import arrow.core.Either
import arrow.core.raise.either
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.repository.TodoRepository
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

class CompleteRecurringTodoUseCase(
    private val repository: TodoRepository,
    private val timeZone: TimeZone,
) {
    suspend operator fun invoke(todo: TodoItem): Either<TodoError, Unit> = either {
        repository.setCompleted(todo.id, true).bind()
        val rule = todo.recurrenceRule ?: return@either
        val due = todo.dueDate ?: return@either
        val local = due.toLocalDateTime(timeZone)
        val nextDate = nextOccurrence(local.date, rule)
        val nextDue = LocalDateTime(nextDate, local.time).toInstant(timeZone)
        repository.insertTodo(
            listId = todo.listId,
            title = todo.title,
            note = todo.note,
            dueDate = nextDue,
            parentId = todo.parentId,
            flag = todo.flag,
        ).bind()
    }
}
```

- [ ] **Step 4: Wire AppGraph and MainViewModel**

In `AppGraph.kt`, add:

```kotlin
val completeRecurringTodo: CompleteRecurringTodoUseCase by lazy {
    CompleteRecurringTodoUseCase(repository, timeZone)
}
```

Update `MainViewModel` constructor:

```kotlin
private val completeRecurringTodo: CompleteRecurringTodoUseCase? = null,
```

Update `toggleCompleted`:

```kotlin
fun toggleCompleted(item: TodoItem) {
    if (!item.isCompleted && item.recurrenceRule != null && completeRecurringTodo != null) {
        launchTodoEffect(lastError) { completeRecurringTodo(item) }
    } else {
        launchTodoEffect(lastError) { repository.setCompleted(item.id, !item.isCompleted) }
    }
}
```

Update `DesktopShell.kt` and `MobileShell.kt` ViewModel creation to pass `graph.completeRecurringTodo`.

- [ ] **Step 5: Run tests**

```bash
./gradlew :shared:desktopTest --tests com.myapplication.shared.domain.recurrence.CompleteRecurringTodoUseCaseTest --no-daemon --console=plain
```

Expected: PASS.

- [ ] **Step 6: Run full compile**

```bash
./gradlew :shared:desktopTest :desktopApp:compileKotlinJvm :androidApp:assembleDebug --no-daemon --console=plain
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain shared/src/commonTest
git commit -F - <<'EOF'
feat(recurrence): create next todo on completion

[Change Nature]
- This commit adds recurring completion behavior.

[New Capability]
- Completing a recurring todo also creates the next occurrence.

[Implementation]
- Add CompleteRecurringTodoUseCase.
- Calculate the next due date from the current due date and recurrence rule.
- Wire MainViewModel completion through the recurrence use case when needed.

[Impact]
- Non-recurring todo completion remains unchanged.
- Recurring todos now continue their schedule automatically.
EOF
```

---

## Task 11: Recurrence Picker UI

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/recurrence/RecurrenceSummary.kt`
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/recurrence/RecurrencePicker.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/detail/DetailViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/detail/DetailContent.kt`

- [ ] **Step 1: Add recurrence detail command**

In `DetailViewModel.kt`, add:

```kotlin
fun setRecurrence(rule: RecurrenceRule?) {
    launchTodoEffect(lastError) { repository.setRecurrence(todoId, rule) }
}
```

Add import:

```kotlin
import com.myapplication.shared.domain.recurrence.RecurrenceRule
```

- [ ] **Step 2: Create summary**

Create `RecurrenceSummary.kt`.

```kotlin
package com.myapplication.shared.ui.recurrence

import com.myapplication.shared.domain.recurrence.RecurrenceRule
import com.myapplication.shared.domain.recurrence.label

fun recurrenceSummary(rule: RecurrenceRule?): String =
    rule?.label() ?: "不重复"
```

- [ ] **Step 3: Create picker**

Create `RecurrencePicker.kt`.

```kotlin
package com.myapplication.shared.ui.recurrence

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.recurrence.RecurrenceRule
import com.myapplication.shared.domain.recurrence.label
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemDialog
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemType

@Composable
fun RecurrencePicker(
    selected: RecurrenceRule?,
    onSelect: (RecurrenceRule?) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf<RecurrenceRule?>(null, RecurrenceRule.Daily(), RecurrenceRule.Weekly(), RecurrenceRule.Monthly())
    RemDialog(
        title = "重复",
        onDismiss = onDismiss,
        showButtons = false,
        content = {
            Column {
                options.forEach { option ->
                    val checked = option == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(option)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val colors = LocalRemColors.current
                        BasicText(recurrenceSummary(option), style = RemType.text14.copy(color = colors.textHigh), modifier = Modifier.weight(1f))
                        if (checked) {
                            Spacer(Modifier.width(8.dp))
                            RemIcon(IconName.CheckCircle, colors.brand)
                        }
                    }
                }
            }
        },
    )
}
```

- [ ] **Step 4: Add recurrence row to details**

In `DetailContent.kt`, add local state:

```kotlin
var showRecurrencePicker by remember { mutableStateOf(false) }
```

After the date row, add:

```kotlin
Row(
    Modifier
        .fillMaxWidth()
        .clickable { showRecurrencePicker = true }
        .padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    RemIcon(IconName.Clock, colors.textLow, Modifier.size(14.dp))
    Spacer(Modifier.width(8.dp))
    androidx.compose.foundation.text.BasicText("重复", style = RemType.text12.copy(color = colors.textNormal))
    Spacer(Modifier.weight(1f))
    androidx.compose.foundation.text.BasicText(
        recurrenceSummary(current.recurrenceRule),
        style = RemType.text12.copy(color = colors.textLow),
    )
}
```

At the bottom:

```kotlin
if (showRecurrencePicker) {
    RecurrencePicker(
        selected = current?.recurrenceRule,
        onSelect = detailVm::setRecurrence,
        onDismiss = { showRecurrencePicker = false },
    )
}
```

Add imports:

```kotlin
import com.myapplication.shared.ui.recurrence.RecurrencePicker
import com.myapplication.shared.ui.recurrence.recurrenceSummary
```

- [ ] **Step 5: Run compile**

```bash
./gradlew :desktopApp:compileKotlinJvm :androidApp:assembleDebug --no-daemon --console=plain
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/ui/recurrence shared/src/commonMain/kotlin/com/myapplication/shared/ui/detail
git commit -F - <<'EOF'
feat(detail): add recurrence picker

[Change Nature]
- This commit adds recurrence editing to todo details.

[New Capability]
- Users can set a todo to repeat daily, weekly, monthly, or not repeat.

[Implementation]
- Add recurrence summary and picker components.
- Wire detail view model recurrence updates through the repository.

[Impact]
- Recurrence settings become visible and editable in the detail panel.
EOF
```

---

## Task 12: Organization Suggestions

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/organize/OrganizationReason.kt`
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/organize/OrganizationSuggestion.kt`
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/organize/OrganizationRules.kt`
- Test: `shared/src/commonTest/kotlin/com/myapplication/shared/domain/organize/OrganizationRulesTest.kt`

- [ ] **Step 1: Write failing tests**

Create `OrganizationRulesTest.kt`.

```kotlin
package com.myapplication.shared.domain.organize

import com.myapplication.shared.domain.model.TodoItem
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class OrganizationRulesTest {
    private val today = LocalDate(2026, 8, 13)

    @Test
    fun inboxTaskWithoutDateGetsInboxAndNoDateReasons() {
        val suggestions = buildOrganizationSuggestions(
            todos = listOf(todo(id = 1, listId = 10, dueMillis = null)),
            inboxListId = 10,
            today = today,
            timeZone = TimeZone.UTC,
        )

        val reasons = suggestions.single().reasons
        assertTrue(OrganizationReason.Inbox in reasons)
        assertTrue(OrganizationReason.NoDate in reasons)
    }

    @Test
    fun overdueTaskGetsOverdueReason() {
        val suggestions = buildOrganizationSuggestions(
            todos = listOf(todo(id = 1, listId = 20, dueMillis = Instant.parse("2026-08-12T09:00:00Z").toEpochMilliseconds())),
            inboxListId = 10,
            today = today,
            timeZone = TimeZone.UTC,
        )

        assertTrue(OrganizationReason.Overdue in suggestions.single().reasons)
    }

    private fun todo(id: Long, listId: Long, dueMillis: Long?): TodoItem =
        TodoItem(
            id = id,
            listId = listId,
            title = "Task $id",
            note = "",
            dueDate = dueMillis?.let { Instant.fromEpochMilliseconds(it) },
            isCompleted = false,
            completedAt = null,
            isTrashed = false,
            trashedAt = null,
            parentId = null,
            sortPosition = 0.0,
            flag = false,
            createdAt = Instant.fromEpochMilliseconds(0),
            updatedAt = Instant.fromEpochMilliseconds(0),
        )
}
```

- [ ] **Step 2: Run tests and verify failure**

```bash
./gradlew :shared:desktopTest --tests com.myapplication.shared.domain.organize.OrganizationRulesTest --no-daemon --console=plain
```

Expected: fails because organization domain does not exist.

- [ ] **Step 3: Implement organization models**

Create `OrganizationReason.kt`.

```kotlin
package com.myapplication.shared.domain.organize

enum class OrganizationReason {
    Inbox,
    NoDate,
    Overdue,
    LongTitle,
    MissingNextStep,
}
```

Create `OrganizationSuggestion.kt`.

```kotlin
package com.myapplication.shared.domain.organize

import com.myapplication.shared.domain.model.TodoItem

data class OrganizationSuggestion(
    val todo: TodoItem,
    val reasons: Set<OrganizationReason>,
    val actions: List<OrganizationAction>,
)

enum class OrganizationAction {
    ScheduleToday,
    ScheduleTomorrow,
    MoveToList,
    CreateSubtask,
    Trash,
}
```

- [ ] **Step 4: Implement rules**

Create `OrganizationRules.kt`.

```kotlin
package com.myapplication.shared.domain.organize

import com.myapplication.shared.domain.model.TodoItem
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun buildOrganizationSuggestions(
    todos: List<TodoItem>,
    inboxListId: Long?,
    today: LocalDate,
    timeZone: TimeZone,
): List<OrganizationSuggestion> =
    todos
        .filter { !it.isTrashed && !it.isCompleted && it.parentId == null }
        .mapNotNull { todo ->
            val reasons = buildSet {
                if (inboxListId != null && todo.listId == inboxListId) add(OrganizationReason.Inbox)
                if (todo.dueDate == null) add(OrganizationReason.NoDate)
                if (todo.localDueDate(timeZone)?.let { it < today } == true) add(OrganizationReason.Overdue)
                if (todo.title.length > 36) add(OrganizationReason.LongTitle)
                if (todo.title.length > 20 && todo.note.isBlank()) add(OrganizationReason.MissingNextStep)
            }
            if (reasons.isEmpty()) {
                null
            } else {
                OrganizationSuggestion(
                    todo = todo,
                    reasons = reasons,
                    actions = actionsFor(reasons),
                )
            }
        }

private fun actionsFor(reasons: Set<OrganizationReason>): List<OrganizationAction> =
    buildList {
        if (OrganizationReason.Overdue in reasons || OrganizationReason.NoDate in reasons) add(OrganizationAction.ScheduleToday)
        add(OrganizationAction.ScheduleTomorrow)
        if (OrganizationReason.Inbox in reasons) add(OrganizationAction.MoveToList)
        if (OrganizationReason.LongTitle in reasons || OrganizationReason.MissingNextStep in reasons) add(OrganizationAction.CreateSubtask)
        add(OrganizationAction.Trash)
    }.distinct().take(3)

private fun TodoItem.localDueDate(timeZone: TimeZone): LocalDate? =
    dueDate?.toLocalDateTime(timeZone)?.date
```

- [ ] **Step 5: Run tests**

```bash
./gradlew :shared:desktopTest --tests com.myapplication.shared.domain.organize.OrganizationRulesTest --no-daemon --console=plain
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/domain/organize shared/src/commonTest/kotlin/com/myapplication/shared/domain/organize
git commit -F - <<'EOF'
feat(organize): add organization suggestion rules

[Change Nature]
- This commit adds pure organization rules.

[New Capability]
- The app can identify inbox, no-date, overdue, long-title, and missing-next-step tasks.

[Implementation]
- Add organization reasons, actions, suggestions, and rule builder.
- Keep rule evaluation pure and covered by tests.

[Impact]
- Prepares the workbench organization panel.
- No UI behavior changes yet.
EOF
```

---

## Task 13: Organization Panel UI

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/organize/OrganizePanel.kt`
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/organize/OrganizeSection.kt`
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/organize/SuggestionRow.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/ledger/MainLedger.kt`

- [ ] **Step 1: Create suggestion row**

Create `SuggestionRow.kt`.

```kotlin
package com.myapplication.shared.ui.organize

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.organize.OrganizationAction
import com.myapplication.shared.domain.organize.OrganizationSuggestion
import com.myapplication.shared.ui.components.RemButton
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemType

@Composable
internal fun SuggestionRow(
    suggestion: OrganizationSuggestion,
    onAction: (OrganizationAction) -> Unit,
) {
    val colors = LocalRemColors.current
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        BasicText(suggestion.todo.title, style = RemType.text14.copy(color = colors.textHigh))
        BasicText(
            suggestion.reasons.joinToString(" · ") { it.name },
            style = RemType.text12.copy(color = colors.textLow),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            suggestion.actions.forEach { action ->
                RemButton(action.label, onClick = { onAction(action) })
                Spacer(Modifier.width(6.dp))
            }
        }
    }
}

private val OrganizationAction.label: String
    get() = when (this) {
        OrganizationAction.ScheduleToday -> "今天"
        OrganizationAction.ScheduleTomorrow -> "明天"
        OrganizationAction.MoveToList -> "移动"
        OrganizationAction.CreateSubtask -> "拆分"
        OrganizationAction.Trash -> "删除"
    }
```

- [ ] **Step 2: Create organize section and panel**

Create `OrganizeSection.kt`.

```kotlin
package com.myapplication.shared.ui.organize

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import com.myapplication.shared.domain.organize.OrganizationAction
import com.myapplication.shared.domain.organize.OrganizationSuggestion
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemType

@Composable
internal fun OrganizeSection(
    title: String,
    suggestions: List<OrganizationSuggestion>,
    onAction: (OrganizationSuggestion, OrganizationAction) -> Unit,
) {
    val colors = LocalRemColors.current
    Column {
        BasicText("$title ${suggestions.size}", style = RemType.label12.copy(color = colors.textLow))
        suggestions.forEach { suggestion ->
            SuggestionRow(suggestion) { action -> onAction(suggestion, action) }
        }
    }
}
```

Create `OrganizePanel.kt`.

```kotlin
package com.myapplication.shared.ui.organize

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.myapplication.shared.domain.organize.OrganizationAction
import com.myapplication.shared.domain.organize.OrganizationSuggestion
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemType

@Composable
internal fun OrganizePanel(
    suggestions: List<OrganizationSuggestion>,
    onAction: (OrganizationSuggestion, OrganizationAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalRemColors.current
    if (suggestions.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        BasicText("待整理", style = RemType.title18.copy(color = colors.textHigh))
        BasicText("把捕获的任务变成可执行计划", style = RemType.text12.copy(color = colors.textLow))
        OrganizeSection("建议", suggestions, onAction)
    }
}
```

- [ ] **Step 3: Wire panel into workbench**

In `MainLedger.kt`, compute suggestions near existing active/list context:

```kotlin
val organizeSuggestions = remember(active, lists, today, timeZone) {
    val inboxId = lists.firstOrNull { it.name == "收件箱" && it.position == 0L }?.id
    buildOrganizationSuggestions(
        todos = active.map { it.item },
        inboxListId = inboxId,
        today = today,
        timeZone = timeZone,
    )
}
```

Render above task sections for `Scope.All`:

```kotlin
if (scope == Scope.All) {
    OrganizePanel(
        suggestions = organizeSuggestions,
        onAction = { suggestion, action ->
            when (action) {
                OrganizationAction.ScheduleToday -> mainVm.openDetail(suggestion.todo.id)
                OrganizationAction.ScheduleTomorrow -> mainVm.openDetail(suggestion.todo.id)
                OrganizationAction.MoveToList -> mainVm.openDetail(suggestion.todo.id)
                OrganizationAction.CreateSubtask -> mainVm.openDetail(suggestion.todo.id)
                OrganizationAction.Trash -> mainVm.trash(suggestion.todo)
            }
        },
    )
}
```

This first UI pass routes planning actions to detail when they require extra user choice. Later tasks can add direct date-setting buttons.

- [ ] **Step 4: Run compile**

```bash
./gradlew :desktopApp:compileKotlinJvm :androidApp:assembleDebug --no-daemon --console=plain
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/ui/organize shared/src/commonMain/kotlin/com/myapplication/shared/ui/ledger/MainLedger.kt
git commit -F - <<'EOF'
feat(organize): add workbench organization panel

[Change Nature]
- This commit adds a visible organization surface.

[New Capability]
- The workbench can show tasks that need organizing and expose suggested actions.

[Implementation]
- Add organize panel, section, and row components.
- Wire pure organization suggestions into the workbench.

[Impact]
- Users get a focused entry point for inbox, no-date, and overdue cleanup.
EOF
```

---

## Task 14: List Analytics Model

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/analytics/ListAnalyticsModel.kt`
- Test: `shared/src/commonTest/kotlin/com/myapplication/shared/domain/analytics/ListAnalyticsModelTest.kt`

- [ ] **Step 1: Write failing tests**

Create `ListAnalyticsModelTest.kt`.

```kotlin
package com.myapplication.shared.domain.analytics

import com.myapplication.shared.domain.model.TodoItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class ListAnalyticsModelTest {
    @Test
    fun buildsListCompletionAndEnergySeries() {
        val model = buildListAnalyticsModel(
            listId = 2,
            todos = listOf(
                todo(1, 2, completedAt = "2026-08-12T10:00:00Z"),
                todo(2, 2, completedAt = "2026-08-13T10:00:00Z", note = "deep", flag = true),
                todo(3, 3, completedAt = "2026-08-13T10:00:00Z"),
            ),
            today = LocalDate(2026, 8, 13),
            range = AnalyticsRange.Week,
            timeZone = TimeZone.UTC,
        )

        assertEquals(2, model.completedTotal)
        assertEquals(7, model.completion.points.size)
        assertEquals(7, model.energy.points.size)
    }

    private fun todo(
        id: Long,
        listId: Long,
        completedAt: String,
        note: String = "",
        flag: Boolean = false,
    ): TodoItem =
        TodoItem(
            id = id,
            listId = listId,
            title = "Task $id",
            note = note,
            dueDate = null,
            isCompleted = true,
            completedAt = Instant.parse(completedAt),
            isTrashed = false,
            trashedAt = null,
            parentId = null,
            sortPosition = 0.0,
            flag = flag,
            createdAt = Instant.fromEpochMilliseconds(0),
            updatedAt = Instant.fromEpochMilliseconds(0),
        )
}
```

- [ ] **Step 2: Run test and verify failure**

```bash
./gradlew :shared:desktopTest --tests com.myapplication.shared.domain.analytics.ListAnalyticsModelTest --no-daemon --console=plain
```

Expected: fails because `buildListAnalyticsModel` does not exist.

- [ ] **Step 3: Implement model**

Create `ListAnalyticsModel.kt`.

```kotlin
package com.myapplication.shared.domain.analytics

import com.myapplication.shared.domain.model.TodoItem
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

data class ListAnalyticsModel(
    val listId: Long,
    val completedTotal: Int,
    val completion: ChartSeries,
    val energy: ChartSeries,
)

fun buildListAnalyticsModel(
    listId: Long,
    todos: List<TodoItem>,
    today: LocalDate,
    range: AnalyticsRange,
    timeZone: TimeZone,
): ListAnalyticsModel {
    val days = ((range.dayCount - 1) downTo 0).map { offset -> today.plus(-offset, DateTimeUnit.DAY) }
    val completed = todos.filter { it.listId == listId && !it.isTrashed && it.isCompleted && it.completedAt != null }
    val completedByDate = completed.groupBy { it.completedAt!!.toLocalDateTime(timeZone).date }
    val completionPoints = days.map { date ->
        ChartPoint(date.shortLabel(today), completedByDate[date].orEmpty().size)
    }
    val energyPoints = days.map { date ->
        val done = completedByDate[date].orEmpty()
        ChartPoint(date.shortLabel(today), done.sumOf { energyScore(it) })
    }
    return ListAnalyticsModel(
        listId = listId,
        completedTotal = completed.size,
        completion = ChartSeries("完成趋势", completionPoints),
        energy = ChartSeries("精力输出", energyPoints),
    )
}

private fun energyScore(todo: TodoItem): Int {
    var score = 1
    if (todo.note.isNotBlank()) score += 1
    if (todo.flag) score += 1
    return score
}

private fun LocalDate.shortLabel(today: LocalDate): String =
    if (this == today) "今天" else "${month.ordinal + 1}/${day}"
```

- [ ] **Step 4: Run test**

```bash
./gradlew :shared:desktopTest --tests com.myapplication.shared.domain.analytics.ListAnalyticsModelTest --no-daemon --console=plain
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/domain/analytics shared/src/commonTest/kotlin/com/myapplication/shared/domain/analytics
git commit -F - <<'EOF'
feat(analytics): add list analytics model

[Change Nature]
- This commit adds list-specific analytics data.

[New Capability]
- The app can build completion and energy series for a single list.

[Implementation]
- Add pure list analytics model builder.
- Support week and month range lengths through AnalyticsRange.

[Impact]
- Prepares list analytics panels and Vico chart rendering.
EOF
```

---

## Task 15: Vico Analytics Charts

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/analytics/charts/CompletionTrendChart.kt`
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/analytics/charts/EnergyOutputChart.kt`
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/analytics/charts/PressureDistributionChart.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/analytics/AnalyticsScreen.kt`

- [ ] **Step 1: Replace spike chart body with Vico rendering**

Use the Vico API from the dependency version selected in Task 1. Keep the function signature:

```kotlin
@Composable
internal fun CompletionTrendChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
)
```

Map `points.map { it.value.toFloat() }` into the Vico model producer. Keep all Vico imports isolated to `ui/analytics/charts`.

If using Vico 2.x, the implementation shape should be:

```kotlin
val modelProducer = remember { CartesianChartModelProducer() }
LaunchedEffect(points) {
    modelProducer.runTransaction {
        lineSeries { series(points.map { it.value }) }
    }
}
CartesianChartHost(
    chart = rememberCartesianChart(rememberLineCartesianLayer()),
    modelProducer = modelProducer,
    modifier = modifier.height(180.dp),
)
```

Adjust import names to the resolved Vico version. Do not expose Vico types outside this file.

- [ ] **Step 2: Add energy chart**

Create `EnergyOutputChart.kt` with the same shape:

```kotlin
@Composable
internal fun EnergyOutputChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
) {
    // Use Vico column/bar layer with values from points.
}
```

Use the selected Vico bar/column layer. Keep fallback text out of the final body.

- [ ] **Step 3: Add pressure distribution chart**

Create `PressureDistributionChart.kt`:

```kotlin
@Composable
internal fun PressureDistributionChart(
    buckets: List<ChartBucket>,
    modifier: Modifier = Modifier,
) {
    // Use Vico column/bar layer with bucket values.
}
```

- [ ] **Step 4: Wire AnalyticsScreen**

Modify `AnalyticsScreen.kt`:

- Remove hand-drawn chart composables for completion trend and energy output.
- Convert current `AnalyticsModel.days` to `ChartPoint`.
- Convert current `AnalyticsModel.pressure` to `ChartBucket`.
- Render:

```kotlin
CompletionTrendChart(points = model.days.map { ChartPoint(it.label, it.completedCount) })
EnergyOutputChart(points = model.days.map { ChartPoint(it.label, it.energy) })
PressureDistributionChart(buckets = model.pressure.map { ChartBucket(it.label, it.count, it.tone.toChartTone()) })
```

Add mapper:

```kotlin
private fun AnalyticsTone.toChartTone(): ChartTone = when (this) {
    AnalyticsTone.Danger -> ChartTone.Danger
    AnalyticsTone.Brand -> ChartTone.Primary
    AnalyticsTone.Info -> ChartTone.Info
    AnalyticsTone.Neutral -> ChartTone.Neutral
}
```

- [ ] **Step 5: Run full build**

```bash
./gradlew :shared:desktopTest :desktopApp:compileKotlinJvm :androidApp:assembleDebug --no-daemon --console=plain
```

Expected: PASS. Analytics screen must compile for desktop and Android.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/ui/analytics shared/src/commonMain/kotlin/com/myapplication/shared/ui/analytics/charts
git commit -F - <<'EOF'
feat(analytics): render dashboard charts with Vico

[Change Nature]
- This commit replaces hand-drawn analytics chart rendering.

[New Capability]
- Analytics charts use a mature Compose Multiplatform chart library.

[Implementation]
- Keep Vico APIs inside chart adapter components.
- Feed chart-neutral domain series into line and bar chart components.

[Impact]
- Analytics visuals become more maintainable and product-grade.
- Domain analytics models remain independent of the chart library.
EOF
```

---

## Task 16: Single-List Analytics Panel

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/list/ListAnalyticsPanel.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/ListSettingsScreen.kt`

- [ ] **Step 1: Create panel**

Create `ListAnalyticsPanel.kt`.

```kotlin
package com.myapplication.shared.ui.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.myapplication.shared.domain.analytics.ListAnalyticsModel
import com.myapplication.shared.ui.analytics.charts.CompletionTrendChart
import com.myapplication.shared.ui.analytics.charts.EnergyOutputChart
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemType

@Composable
internal fun ListAnalyticsPanel(
    model: ListAnalyticsModel,
    modifier: Modifier = Modifier,
) {
    val colors = LocalRemColors.current
    Column(modifier) {
        BasicText("列表分析", style = RemType.title18.copy(color = colors.textHigh))
        BasicText("完成 ${model.completedTotal} 项", style = RemType.text12.copy(color = colors.textLow))
        CompletionTrendChart(points = model.completion.points)
        EnergyOutputChart(points = model.energy.points)
    }
}
```

- [ ] **Step 2: Wire a selected list in ListSettingsScreen**

In `ListSettingsScreen.kt`, add:

```kotlin
var selectedList by remember { mutableStateOf<TodoList?>(null) }
```

Set row click:

```kotlin
.clickable { selectedList = list }
```

If `selectedList` is not null, compute analytics using `buildListAnalyticsModel` from `mainVm.analyticsTodos` and render `ListAnalyticsPanel`.

```kotlin
val analyticsTodos by mainVm.analyticsTodos.collectAsState()
selectedList?.let { list ->
    val model = buildListAnalyticsModel(
        listId = list.id,
        todos = analyticsTodos,
        today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
        range = AnalyticsRange.Week,
        timeZone = TimeZone.currentSystemDefault(),
    )
    ListAnalyticsPanel(model)
}
```

- [ ] **Step 3: Run compile**

```bash
./gradlew :desktopApp:compileKotlinJvm :androidApp:assembleDebug --no-daemon --console=plain
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/ui/list shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/ListSettingsScreen.kt
git commit -F - <<'EOF'
feat(list): show single-list analytics

[Change Nature]
- This commit adds list-level analytics UI.

[New Capability]
- Users can inspect completion and energy charts for a selected list.

[Implementation]
- Add a list analytics panel backed by pure list analytics models.
- Reuse the analytics chart adapter components.

[Impact]
- List management now includes statistics and visual feedback.
EOF
```

---

## Task 17: Sync DTO Alignment for Recurrence

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/sync/SyncModels.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/data/TodoRepositoryImpl.kt`
- Modify: `docs/sync-setup.md`
- Test: `shared/src/commonTest/kotlin/com/myapplication/shared/domain/sync/SyncCoordinatorTest.kt`

- [ ] **Step 1: Extend TodoRowDto**

Add fields to `TodoRowDto`:

```kotlin
val recurrence_frequency: String? = null,
val recurrence_interval: Long? = null,
```

Keep the new property names in snake case to match the existing DTO fields:

```kotlin
val recurrence_frequency: String? = null,
val recurrence_interval: Long? = null,
```

- [ ] **Step 2: Update DTO conversion**

In `TodoRepositoryImpl.toDto()`, include recurrence fields from SQL rows.

```kotlin
recurrence_frequency = recurrence_frequency,
recurrence_interval = recurrence_interval,
```

In remote upsert application, pass recurrence fields into `updateTodoIfNewer` and `insertTodoIfMissing`.

- [ ] **Step 3: Update sync setup docs**

In `docs/sync-setup.md`, add remote migration statements:

```sql
ALTER TABLE public.todo ADD COLUMN IF NOT EXISTS recurrence_frequency text;
ALTER TABLE public.todo ADD COLUMN IF NOT EXISTS recurrence_interval bigint;
```

- [ ] **Step 4: Add sync test**

Append to `SyncCoordinatorTest.kt` a test that applies a remote todo with recurrence fields and verifies the decoded DTO reaches the repository.

```kotlin
@Test
fun appliesRemoteTodoRecurrenceFields() = runTest {
    val repo = FakeTodoRepository()
    val coordinator = SyncCoordinator(repo, FakeSyncClient(), "device-a")
    val payload = Json.encodeToString(
        TodoRowDto(
            id = 1,
            list_id = 1,
            title = "远程重复",
            note = "",
            due_date = 1786602000000,
            is_completed = false,
            completed_at = null,
            is_trashed = false,
            trashed_at = null,
            parent_id = null,
            sort_position = 0.0,
            flag = false,
            created_at = 0,
            updated_at = 200,
            updated_by = "device-b",
            recurrence_frequency = "daily",
            recurrence_interval = 1,
        ),
    )

    val result = coordinator.applyRemote(row(payload = payload, updatedAt = 200))

    assertTrue(result.isRight())
    assertEquals("daily", repo.appliedUpserts.single().recurrence_frequency)
    assertEquals(1L, repo.appliedUpserts.single().recurrence_interval)
}
```

- [ ] **Step 5: Run sync tests**

```bash
./gradlew :shared:desktopTest --tests com.myapplication.shared.domain.sync.SyncCoordinatorTest --no-daemon --console=plain
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/domain/sync shared/src/commonMain/kotlin/com/myapplication/shared/data/TodoRepositoryImpl.kt shared/src/commonTest/kotlin/com/myapplication/shared/domain/sync docs/sync-setup.md
git commit -F - <<'EOF'
feat(sync): include recurrence fields in todo sync

[Change Nature]
- This commit extends sync payloads for recurrence.

[New Capability]
- Recurrence metadata can move through todo sync rows.

[Implementation]
- Add recurrence fields to TodoRowDto and row conversion.
- Update local remote-apply paths and sync setup documentation.

[Impact]
- Older todos without recurrence continue to sync with null recurrence fields.
EOF
```

---

## Task 18: Final Verification and Version Bump

**Files:**
- Modify: `androidApp/build.gradle.kts`
- Modify: `desktopApp/build.gradle.kts`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/AppInfo.kt`

- [ ] **Step 1: Run full verification**

```bash
./gradlew :shared:desktopTest :desktopApp:compileKotlinJvm :androidApp:assembleDebug --no-daemon --console=plain
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run diff check**

```bash
git diff --check
```

Expected: no output.

- [ ] **Step 3: Bump version**

If the current version is `0.6.0`, bump to `0.7.0`.

In `androidApp/build.gradle.kts`:

```kotlin
versionCode = 8
versionName = "0.7.0"
```

In `desktopApp/build.gradle.kts`:

```kotlin
val displayVersion = "0.7.0"
```

Keep `desktopPackageVersion` unchanged unless jpackage requirements change.

In `shared/src/commonMain/kotlin/com/myapplication/shared/AppInfo.kt`:

```kotlin
const val VERSION = "0.7.0"
```

- [ ] **Step 4: Run full verification again**

```bash
./gradlew :shared:desktopTest :desktopApp:compileKotlinJvm :androidApp:assembleDebug --no-daemon --console=plain
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add androidApp/build.gradle.kts desktopApp/build.gradle.kts shared/src/commonMain/kotlin/com/myapplication/shared/AppInfo.kt
git commit -F - <<'EOF'
chore(release): bump version to 0.7.0

[Change Nature]
- This commit updates release metadata only.

[Maintenance Work]
- Bump Android, desktop display, and shared app versions to 0.7.0.

[Implementation]
- Keep desktop package version unchanged for jpackage compatibility.
- Re-run shared tests and platform builds after the version update.

[Impact]
- App surfaces report version 0.7.0.
- Runtime behavior is unchanged.
EOF
```

---

## Self-Review

### Spec Coverage

- Mature chart library: Task 1 and Task 15.
- Settings center: Task 5.
- List CRUD and management: Task 4 and Task 6.
- List delete semantics: Task 4 and Task 6.
- Single-list analytics: Task 14 and Task 16.
- Editable subtasks: Task 7.
- Recurrence basics: Task 8, Task 9, Task 10, and Task 11.
- Organization suggestions: Task 12 and Task 13.
- Sync/schema alignment: Task 17.
- Verification and versioning: Task 18.

### Execution Notes

- Execute tasks in order.
- Commit after each task.
- If Vico dependency resolution fails in Task 1, use Koala Plot and update the chart adapter tasks to import Koala Plot APIs. Keep the public chart composable signatures unchanged.
- If Task 9 schema changes require SQLDelight generated API name adjustments, fix call sites in `TodoRepositoryImpl.kt` and rerun `:shared:compileKotlinDesktop` before proceeding.
- If a task uncovers unrelated dirty files, do not stage them into that task commit.
