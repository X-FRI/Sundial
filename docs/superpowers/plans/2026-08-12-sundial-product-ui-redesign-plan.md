# Sundial Product UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the product-level Sundial UI system defined in `docs/superpowers/specs/2026-08-12-sundial-product-ui-redesign-design.md`: compact native navigation, time-aware main ledger, desktop detail inspector, and mobile single-hand execution flow.

**Architecture:** Keep the existing Kotlin Compose Multiplatform app, repository, sync engine, and ViewModel shape. Add focused UI modules under `shared/src/commonMain/kotlin/com/myapplication/shared/ui/ledger/`, split shell layout from task-list rendering, and reuse the existing `MainViewModel`, `DetailViewModel`, and `AppGraph` instead of introducing a new state framework.

**Tech Stack:** Kotlin 2.4.10, Compose Multiplatform 1.11.1, SQLDelight, kotlinx-datetime, existing custom `Rem*` components, existing hand-drawn `RemIcons`, commonTest with `kotlin.test` and `kotlinx-coroutines-test`.

---

## Scope Check

The spec covers one coherent subsystem: product-level UI/UX for the existing Sundial app. It touches desktop and mobile surfaces, but both share one interaction model and one component system. This works as one implementation plan because each task produces a working slice while preserving the current app behavior.

This plan does not implement new data capabilities such as priority, notifications, full calendar scheduling, drag sorting, accounts, or collaboration. It also does not rewrite sync, repository, SQL schema, or date parsing.

## File Structure

### New Files

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/ledger/LedgerUiModels.kt`  
  Pure UI models and helper functions for today rhythm, task grouping, and count summaries.

- `shared/src/commonTest/kotlin/com/myapplication/shared/ui/ledger/LedgerUiModelsTest.kt`  
  Deterministic tests for rhythm and grouping helpers.

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/ledger/TaskRow.kt`  
  Product-level task row, due badge, section header, and task section rendering.

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/ledger/MainLedger.kt`  
  Central desktop/mobile reusable ledger: page header, rhythm, overview, quick-add, task sections.

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/ledger/TodayRhythm.kt`  
  Desktop rhythm strip and mobile compact rhythm summary.

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/shell/DesktopShell.kt`  
  Desktop three-column shell: sidebar, ledger, inspector.

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/shell/MobileShell.kt`  
  Mobile top bar, rhythm summary, list, bottom nav, and bottom-sheet detail container.

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/sidebar/SidebarNav.kt`  
  Compact product navigation replacing the current large smart-list grid.

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/detail/DetailContent.kt`  
  Shared detail body used by desktop inspector and mobile bottom sheet.

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/detail/DetailInspector.kt`  
  Desktop right-side inspector wrapper around `DetailContent`.

### Modified Files

- `shared/src/commonMain/kotlin/App.kt`  
  Replace current wide/narrow branching with `DesktopShell` and `MobileShell`.

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/theme/DesignTokens.kt`  
  Extend color, type, spacing, and control-size tokens.

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/RemBadge.kt`  
  Add compact due/status variants.

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/RemButton.kt`  
  Add stable icon button size variants and a compact primary button style.

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/RemCheckbox.kt`  
  Support desktop row size and mobile row size without layout shift.

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/RemIcons.kt`  
  Add any missing simple line icons used by the new UI, such as `Clock`, `Inbox`, `Layers`, and `Send`.

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/detail/DetailScreen.kt`  
  Reduce to compatibility wrapper around `DetailContent` where possible.

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/narrow/NarrowShell.kt`  
  Migrate or delete after `MobileShell` owns narrow layout.

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/todolist/TodoListScreen.kt`  
  Migrate row/section responsibilities to `MainLedger` and keep only compatibility helpers if needed.

- `shared/src/commonTest/kotlin/com/myapplication/shared/ui/main/MainViewModelTest.kt`  
  Add route/selection regression tests if AppRoot semantics change.

## Task 1: Add Ledger UI Models And Rhythm Tests

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/ledger/LedgerUiModels.kt`
- Create: `shared/src/commonTest/kotlin/com/myapplication/shared/ui/ledger/LedgerUiModelsTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `shared/src/commonTest/kotlin/com/myapplication/shared/ui/ledger/LedgerUiModelsTest.kt`:

```kotlin
package com.myapplication.shared.ui.ledger

import com.myapplication.shared.domain.model.TodoItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

class LedgerUiModelsTest {
    private val tz = TimeZone.UTC

    @Test
    fun rhythmPicksNextUncompletedDueItemToday() {
        val todos = listOf(
            item(id = 1, title = "已完成", due = "2026-08-12T08:00:00Z", completed = true),
            item(id = 2, title = "下一件", due = "2026-08-12T09:00:00Z", completed = false),
            item(id = 3, title = "明天", due = "2026-08-13T09:00:00Z", completed = false),
        )

        val state = buildTodayRhythmState(
            todos = todos,
            now = Instant.parse("2026-08-12T08:42:00Z"),
            timeZone = tz,
        )

        assertEquals("08:42", state.nowLabel)
        assertEquals("09:00", state.nextDueLabel)
        assertEquals("下一件", state.nextTitle)
        assertEquals(1, state.completedTodayCount)
        assertEquals(1, state.pendingTodayCount)
    }

    @Test
    fun rhythmHasNoNextWhenAllTodayItemsAreCompleted() {
        val todos = listOf(
            item(id = 1, title = "done", due = "2026-08-12T08:00:00Z", completed = true),
        )

        val state = buildTodayRhythmState(
            todos = todos,
            now = Instant.parse("2026-08-12T12:00:00Z"),
            timeZone = tz,
        )

        assertNull(state.nextDueLabel)
        assertNull(state.nextTitle)
        assertEquals(1, state.completedTodayCount)
        assertEquals(0, state.pendingTodayCount)
    }

    @Test
    fun groupingSeparatesActiveAndCompletedParentTasks() {
        val todos = listOf(
            item(id = 1, title = "active", due = "2026-08-12T09:00:00Z", completed = false),
            item(id = 2, title = "done", due = "2026-08-12T10:00:00Z", completed = true),
            item(id = 3, title = "child", due = "2026-08-12T11:00:00Z", completed = false, parentId = 1),
        )

        val groups = buildTaskGroups(todos)

        assertEquals(listOf(1L), groups.active.map { it.item.id })
        assertEquals(listOf(3L), groups.active.first().subtasks.map { it.id })
        assertEquals(listOf(2L), groups.completed.map { it.item.id })
    }

    private fun item(
        id: Long,
        title: String,
        due: String,
        completed: Boolean,
        parentId: Long? = null,
    ): TodoItem = TodoItem(
        id = id,
        listId = 1,
        title = title,
        note = "",
        dueDate = Instant.parse(due),
        isCompleted = completed,
        flag = false,
        completedAt = if (completed) Instant.parse(due) else null,
        isTrashed = false,
        trashedAt = null,
        parentId = parentId,
        sortPosition = id.toDouble(),
        createdAt = Instant.parse("2026-08-12T00:00:00Z"),
    )
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
./gradlew :shared:desktopTest --tests "com.myapplication.shared.ui.ledger.LedgerUiModelsTest" --rerun-tasks
```

Expected: compile fails because `buildTodayRhythmState`, `buildTaskGroups`, and related models do not exist.

- [ ] **Step 3: Add the pure UI models and helpers**

Create `shared/src/commonMain/kotlin/com/myapplication/shared/ui/ledger/LedgerUiModels.kt`:

```kotlin
package com.myapplication.shared.ui.ledger

import com.myapplication.shared.domain.model.TodoItem
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class TodayRhythmState(
    val nowLabel: String,
    val nextDueLabel: String?,
    val nextTitle: String?,
    val pendingTodayCount: Int,
    val completedTodayCount: Int,
)

data class TaskRowModel(
    val item: TodoItem,
    val subtasks: List<TodoItem>,
)

data class TaskGroups(
    val active: List<TaskRowModel>,
    val completed: List<TaskRowModel>,
)

fun buildTodayRhythmState(
    todos: List<TodoItem>,
    now: Instant,
    timeZone: TimeZone,
): TodayRhythmState {
    val today = now.toLocalDateTime(timeZone).date
    val todayItems = todos.filter { todo ->
        todo.dueDate?.toLocalDateTime(timeZone)?.date == today
    }
    val pending = todayItems.filter { !it.isCompleted }
        .sortedWith(compareBy<TodoItem> { it.dueDate }.thenBy { it.sortPosition }.thenBy { it.id })
    val completed = todayItems.count { it.isCompleted }
    val next = pending.firstOrNull { item -> item.dueDate?.let { it >= now } ?: false } ?: pending.firstOrNull()
    return TodayRhythmState(
        nowLabel = formatLedgerTime(now, timeZone),
        nextDueLabel = next?.dueDate?.let { formatLedgerTime(it, timeZone) },
        nextTitle = next?.title,
        pendingTodayCount = pending.size,
        completedTodayCount = completed,
    )
}

fun buildTaskGroups(todos: List<TodoItem>): TaskGroups {
    val visible = todos.filter { !it.isTrashed }
    val subtasks = visible.filter { it.parentId != null }
        .groupBy { it.parentId!! }
    val parents = visible.filter { it.parentId == null }
        .sortedWith(compareBy<TodoItem> { it.isCompleted }.thenBy { it.dueDate }.thenBy { it.sortPosition }.thenBy { it.id })
    val active = parents.filter { !it.isCompleted }.map { parent ->
        TaskRowModel(parent, subtasks[parent.id].orEmpty().sortedBy { it.sortPosition })
    }
    val completed = parents.filter { it.isCompleted }.map { parent ->
        TaskRowModel(parent, subtasks[parent.id].orEmpty().sortedBy { it.sortPosition })
    }
    return TaskGroups(active = active, completed = completed)
}

fun formatLedgerTime(instant: Instant, timeZone: TimeZone): String {
    val time = instant.toLocalDateTime(timeZone).time
    return "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
}
```

- [ ] **Step 4: Run model tests**

Run:

```bash
./gradlew :shared:desktopTest --tests "com.myapplication.shared.ui.ledger.LedgerUiModelsTest" --rerun-tasks
```

Expected: tests pass.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/ui/ledger/LedgerUiModels.kt \
  shared/src/commonTest/kotlin/com/myapplication/shared/ui/ledger/LedgerUiModelsTest.kt
git commit -m "test(ui): add ledger rhythm model coverage"
```

## Task 2: Extend Design Tokens And Core Control Variants

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/theme/DesignTokens.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/RemButton.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/RemCheckbox.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/RemBadge.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/RemIcons.kt`

- [ ] **Step 1: Extend token contracts**

Modify `DesignTokens.kt`:

```kotlin
data class RemColors(
    val bgPrimary: Color,
    val bgSecondary: Color,
    val bgPanel: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val brandSubtle: Color,
    val borderSubtle: Color,
    val textHigh: Color,
    val textNormal: Color,
    val textLow: Color,
    val border: Color,
    val inputBg: Color,
    val brand: Color,
    val brandHover: Color,
    val brandSecondary: Color,
    val error: Color,
    val success: Color,
    val warning: Color,
    val info: Color,
    val focusRing: Color,
)
```

Set light additions:

```kotlin
surface = Color(0xFFFFFFFF),
surfaceAlt = Color(0xFFF7F7F5),
brandSubtle = Color(0xFFFFF2E8),
borderSubtle = Color(0xFFEAE7E2),
```

Set dark additions:

```kotlin
surface = Color(0xFF242424),
surfaceAlt = Color(0xFF1B1B1B),
brandSubtle = Color(0xFF3A2416),
borderSubtle = Color(0xFF363331),
```

Extend type and dimensions:

```kotlin
object RemType {
    val text10 = TextStyle(fontFamily = FontFamily.Default, fontSize = 10.sp)
    val text12 = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp)
    val text14 = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp)
    val text16 = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp)
    val title18 = TextStyle(fontFamily = FontFamily.Default, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    val title20 = TextStyle(fontFamily = FontFamily.Default, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    val title24 = TextStyle(fontFamily = FontFamily.Default, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
    val label10 = TextStyle(fontFamily = FontFamily.Default, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    val label12 = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp, fontWeight = FontWeight.Medium)
}

object RemControlSize {
    val iconSmall = 32.dp
    val iconMedium = 36.dp
    val touch = 44.dp
    val rowDesktop = 42.dp
    val rowMobile = 48.dp
}
```

- [ ] **Step 2: Add icon names**

Modify `IconName` in `RemIcons.kt` to include:

```kotlin
Clock, Inbox, Layers, Send
```

Add simple 24-grid drawing branches:

```kotlin
IconName.Clock -> {
    circle(12f, 12f, 8f)
    line(12f, 7.5f, 12f, 12f)
    line(12f, 12f, 15.5f, 14.2f)
}
IconName.Inbox -> {
    box(4f, 6f, 16f, 12f, r = 2.5f)
    line(4f, 12f, 8.5f, 12f)
    line(15.5f, 12f, 20f, 12f)
    line(8.5f, 12f, 10f, 15f)
    line(14f, 15f, 15.5f, 12f)
    line(10f, 15f, 14f, 15f)
}
IconName.Layers -> {
    poly(12f, 4.5f, 20f, 9f, 12f, 13.5f, 4f, 9f, 12f, 4.5f)
    poly(5.5f, 13f, 12f, 16.7f, 18.5f, 13f)
    poly(5.5f, 16.5f, 12f, 20.2f, 18.5f, 16.5f)
}
IconName.Send -> {
    poly(4f, 12f, 20f, 5f, 15f, 20f, 12f, 14f, 4f, 12f)
}
```

- [ ] **Step 3: Add stable icon button sizing**

In `RemButton.kt`, keep existing `RemIconButton` API and add optional touch target:

```kotlin
fun RemIconButton(
    icon: IconName,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    containerSize: Dp = RemControlSize.iconSmall,
)
```

Use `containerSize` for the outer `Modifier.size(containerSize)` and `size` for the inner icon. Update current call sites only if compilation requires the renamed/added parameter.

- [ ] **Step 4: Add badge variants**

In `RemBadge.kt`, add:

```kotlin
enum class RemBadgeTone { Neutral, Brand, Success, Warning, Error }
```

Map tones to existing colors:

```kotlin
private fun badgeToneColor(tone: RemBadgeTone, colors: RemColors): Color? = when (tone) {
    RemBadgeTone.Neutral -> null
    RemBadgeTone.Brand -> colors.brand
    RemBadgeTone.Success -> colors.success
    RemBadgeTone.Warning -> colors.warning
    RemBadgeTone.Error -> colors.error
}
```

Keep the existing `color: Color?` parameter for compatibility. Add a `tone: RemBadgeTone = RemBadgeTone.Neutral` overload path that resolves to `color ?: badgeToneColor(tone, colors)`.

- [ ] **Step 5: Compile**

Run:

```bash
./gradlew :shared:compileKotlinDesktop
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/ui/theme/DesignTokens.kt \
  shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/RemButton.kt \
  shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/RemCheckbox.kt \
  shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/RemBadge.kt \
  shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/RemIcons.kt
git commit -m "feat(ui): extend product design tokens"
```

## Task 3: Build Product Task Rows And Sections

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/ledger/TaskRow.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/todolist/TodoListScreen.kt` only if helpers need to be moved or made internal-public.

- [ ] **Step 1: Create TaskRow and TaskSection**

Create `TaskRow.kt`:

```kotlin
package com.myapplication.shared.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemBadge
import com.myapplication.shared.ui.components.RemBadgeTone
import com.myapplication.shared.ui.components.RemCheckbox
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.components.RemIconButton
import com.myapplication.shared.ui.components.rememberHoverBackground
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemControlSize
import com.myapplication.shared.ui.theme.RemType
import com.myapplication.shared.util.DueBucket
import com.myapplication.shared.util.bucketOf
import com.myapplication.shared.util.formatDueDate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun TaskSection(
    title: String,
    rows: List<TaskRowModel>,
    today: LocalDate,
    selectedId: Long?,
    completed: Boolean,
    onOpen: (Long) -> Unit,
    onToggleCompleted: (TodoItem) -> Unit,
    onToggleFlag: (TodoItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalRemColors.current
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.text.BasicText(
                title,
                style = RemType.label12.copy(color = if (completed) colors.textLow else colors.textHigh),
            )
            Spacer(Modifier.width(6.dp))
            androidx.compose.foundation.text.BasicText(
                rows.size.toString(),
                style = RemType.text12.copy(color = colors.textLow),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surface),
        ) {
            rows.forEach { row ->
                TaskRow(
                    model = row,
                    today = today,
                    selected = selectedId == row.item.id,
                    onOpen = { onOpen(row.item.id) },
                    onToggleCompleted = { onToggleCompleted(row.item) },
                    onToggleFlag = { onToggleFlag(row.item) },
                )
            }
        }
    }
}

@Composable
fun TaskRow(
    model: TaskRowModel,
    today: LocalDate,
    selected: Boolean,
    onOpen: () -> Unit,
    onToggleCompleted: () -> Unit,
    onToggleFlag: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalRemColors.current
    val item = model.item
    val interaction = remember { MutableInteractionSource() }
    val hover = rememberHoverBackground(interaction)
    val rowBg = when {
        selected -> colors.brandSubtle
        else -> hover
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = RemControlSize.rowDesktop)
            .background(rowBg)
            .clickable(interactionSource = interaction, indication = null, onClick = onOpen)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemCheckbox(item.isCompleted, onToggleCompleted, size = 16.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            androidx.compose.foundation.text.BasicText(
                item.title,
                style = RemType.text14.copy(
                    color = if (item.isCompleted) colors.textLow else colors.textHigh,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val support = when {
                item.note.isNotBlank() -> item.note
                model.subtasks.isNotEmpty() -> "${model.subtasks.size} 个子任务"
                item.isCompleted && item.completedAt != null -> "已完成 ${formatDueDate(item.completedAt)}"
                else -> null
            }
            if (support != null) {
                androidx.compose.foundation.text.BasicText(
                    support,
                    style = RemType.text12.copy(color = colors.textLow),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DueBadge(item, today)
        Spacer(Modifier.width(8.dp))
        RemIconButton(
            IconName.Flag,
            if (item.flag) "取消旗标" else "添加旗标",
            onClick = onToggleFlag,
            size = 14.dp,
            containerSize = 32.dp,
        )
    }
}

@Composable
private fun DueBadge(item: TodoItem, today: LocalDate) {
    val due = item.dueDate ?: return
    val tz = TimeZone.currentSystemDefault()
    val bucket = bucketOf(due.toLocalDateTime(tz).date, today)
    val tone = when (bucket) {
        DueBucket.OVERDUE -> RemBadgeTone.Error
        DueBucket.TODAY -> RemBadgeTone.Brand
        else -> RemBadgeTone.Neutral
    }
    RemBadge(
        label = formatDueDate(due, tz, today),
        tone = tone,
        monospace = true,
        icon = { RemIcon(IconName.Calendar, LocalRemColors.current.brand, Modifier.size(10.dp)) },
    )
}
```

- [ ] **Step 2: Compile**

Run:

```bash
./gradlew :shared:compileKotlinDesktop
```

Expected: compile errors only for `RemBadgeTone` or `containerSize` if Task 2 did not finish. If Task 2 is complete, expected `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/ui/ledger/TaskRow.kt
git commit -m "feat(ui): add product task rows"
```

## Task 4: Build Today Rhythm And Main Ledger

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/ledger/TodayRhythm.kt`
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/ledger/MainLedger.kt`

- [ ] **Step 1: Create TodayRhythm components**

Create `TodayRhythm.kt`:

```kotlin
package com.myapplication.shared.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemBadge
import com.myapplication.shared.ui.components.RemBadgeTone
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemType

@Composable
fun TodayRhythm(
    state: TodayRhythmState,
    modifier: Modifier = Modifier,
) {
    val colors = LocalRemColors.current
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(RemRadii.r4))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.text.BasicText("今日节奏", style = RemType.label12.copy(color = colors.textNormal))
            Spacer(Modifier.width(8.dp))
            RemIcon(IconName.Clock, colors.textLow)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RhythmMetric("现在", state.nowLabel, RemBadgeTone.Neutral)
            RhythmMetric("下一件", state.nextDueLabel ?: "无", RemBadgeTone.Brand)
            RhythmMetric("待办", state.pendingTodayCount.toString(), RemBadgeTone.Warning)
            RhythmMetric("已完成", state.completedTodayCount.toString(), RemBadgeTone.Success)
        }
        if (state.nextTitle != null) {
            androidx.compose.foundation.text.BasicText(
                state.nextTitle,
                style = RemType.text12.copy(color = colors.textLow),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
fun TodayRhythmCompact(
    state: TodayRhythmState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RemBadge("下一件 ${state.nextDueLabel ?: "无"}", tone = RemBadgeTone.Brand)
        RemBadge("今日 ${state.pendingTodayCount}", tone = RemBadgeTone.Warning)
        RemBadge("已完成 ${state.completedTodayCount}", tone = RemBadgeTone.Success)
    }
}

@Composable
private fun RhythmMetric(label: String, value: String, tone: RemBadgeTone) {
    Column(horizontalAlignment = Alignment.Start) {
        androidx.compose.foundation.text.BasicText(label, style = RemType.text12.copy(color = LocalRemColors.current.textLow))
        RemBadge(value, tone = tone, monospace = true)
    }
}
```

- [ ] **Step 2: Create MainLedger**

Create `MainLedger.kt`:

```kotlin
package com.myapplication.shared.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemButton
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.components.RemTextField
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.main.Scope
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemType
import com.myapplication.shared.ui.todolist.TodoFormDialog
import com.myapplication.shared.ui.todolist.scopeTitle
import com.myapplication.shared.util.todayDate
import kotlin.time.Clock
import kotlinx.datetime.TimeZone

@Composable
fun MainLedger(
    mainVm: MainViewModel,
    selectedId: Long?,
    modifier: Modifier = Modifier,
    clock: Clock = Clock.System,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val colors = LocalRemColors.current
    val todos by mainVm.todos.collectAsState()
    val scope by mainVm.scope.collectAsState()
    val query by mainVm.searchQuery.collectAsState()
    val lists by mainVm.lists.collectAsState()
    val todayCount by mainVm.todayCount.collectAsState()
    val scheduledCount by mainVm.scheduledCount.collectAsState()
    val completedCount by mainVm.completedCount.collectAsState()
    val listCounts by mainVm.listCounts.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    val groups = remember(todos) { buildTaskGroups(todos) }
    val rhythm = remember(todos, clock, timeZone) { buildTodayRhythmState(todos, clock.now(), timeZone) }
    val today = todayDate()

    Column(modifier.fillMaxSize().background(colors.bgSecondary).padding(horizontal = 24.dp, vertical = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                androidx.compose.foundation.text.BasicText(
                    scopeTitle(scope, query),
                    style = RemType.title24.copy(color = colors.textHigh),
                )
                androidx.compose.foundation.text.BasicText(
                    "${today.year}-${today.monthNumber.toString().padStart(2, '0')}-${today.dayOfMonth.toString().padStart(2, '0')}",
                    style = RemType.text12.copy(color = colors.textLow),
                )
            }
            RemButton("添加待办", onClick = { showCreate = true })
        }
        Spacer(Modifier.height(16.dp))
        TodayRhythm(rhythm)
        Spacer(Modifier.height(12.dp))
        CompactOverview(
            todayCount = todayCount,
            scheduledCount = scheduledCount,
            completedCount = completedCount,
            inboxCount = listCounts.values.firstOrNull() ?: 0,
            onScope = mainVm::selectScope,
        )
        Spacer(Modifier.height(12.dp))
        QuickAddBar(onClick = { showCreate = true })
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                TaskSection(
                    title = "待办",
                    rows = groups.active,
                    today = today,
                    selectedId = selectedId,
                    completed = false,
                    onOpen = mainVm::openDetail,
                    onToggleCompleted = mainVm::toggleCompleted,
                    onToggleFlag = mainVm::toggleFlag,
                )
            }
            item { Spacer(Modifier.height(12.dp)) }
            item {
                TaskSection(
                    title = "已完成",
                    rows = groups.completed,
                    today = today,
                    selectedId = selectedId,
                    completed = true,
                    onOpen = mainVm::openDetail,
                    onToggleCompleted = mainVm::toggleCompleted,
                    onToggleFlag = mainVm::toggleFlag,
                )
            }
        }
    }

    if (showCreate) {
        TodoFormDialog(
            lists = lists,
            defaultListId = (scope as? Scope.List)?.listId,
            onDismiss = { showCreate = false },
            onConfirm = { title, note, due, flag, listId ->
                mainVm.createTodo(title, note, due, flag, listId)
                showCreate = false
            },
        )
    }
}

@Composable
private fun CompactOverview(
    todayCount: Int,
    scheduledCount: Int,
    completedCount: Int,
    inboxCount: Int,
    onScope: (Scope) -> Unit,
) {
    Row(Modifier.fillMaxWidth()) {
        OverviewCell("待办", todayCount, IconName.Today) { onScope(Scope.Today) }
        OverviewCell("计划", scheduledCount, IconName.Scheduled) { onScope(Scope.Scheduled) }
        OverviewCell("已完成", completedCount, IconName.CheckCircle) { onScope(Scope.Completed) }
        OverviewCell("收件箱", inboxCount, IconName.Inbox) { onScope(Scope.All) }
    }
}

@Composable
private fun OverviewCell(label: String, count: Int, icon: IconName, onClick: () -> Unit) {
    Row(
        Modifier.weight(1f).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemIcon(icon, LocalRemColors.current.brand)
        androidx.compose.foundation.text.BasicText(" $label $count", style = RemType.text12.copy(color = LocalRemColors.current.textNormal))
    }
}

@Composable
private fun QuickAddBar(onClick: () -> Unit) {
    RemTextField(
        value = "",
        onValueChange = {},
        placeholder = "添加待办…",
        leadingIcon = IconName.Plus,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}
```

- [ ] **Step 3: Compile**

Run:

```bash
./gradlew :shared:compileKotlinDesktop
```

Expected: `BUILD SUCCESSFUL`. If `Modifier.weight` is unresolved in `OverviewCell`, add `import androidx.compose.foundation.layout.weight` is incorrect; the correct fix is to keep `OverviewCell` inside a `RowScope` receiver:

```kotlin
@Composable
private fun RowScope.OverviewCell(label: String, count: Int, icon: IconName, onClick: () -> Unit)
```

and import `androidx.compose.foundation.layout.RowScope`.

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/ui/ledger/TodayRhythm.kt \
  shared/src/commonMain/kotlin/com/myapplication/shared/ui/ledger/MainLedger.kt
git commit -m "feat(ui): add time-aware main ledger"
```

## Task 5: Replace Sidebar SmartGrid With Compact Product Navigation

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/sidebar/SidebarNav.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/sidebar/Sidebar.kt`

- [ ] **Step 1: Create compact sidebar**

Create `SidebarNav.kt`:

```kotlin
package com.myapplication.shared.ui.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.sync.SyncMode
import com.myapplication.shared.domain.sync.SyncStatus
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.components.RemIconButton
import com.myapplication.shared.ui.components.RemSyncIndicator
import com.myapplication.shared.ui.components.RemTextField
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.main.Scope
import com.myapplication.shared.ui.sync.phase
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemType

@Composable
fun SidebarNav(
    mainVm: MainViewModel,
    syncStatus: SyncStatus,
    onSyncNow: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalRemColors.current
    val scope by mainVm.scope.collectAsState()
    val query by mainVm.searchQuery.collectAsState()
    val lists by mainVm.lists.collectAsState()
    val todayCount by mainVm.todayCount.collectAsState()
    val scheduledCount by mainVm.scheduledCount.collectAsState()
    val allCount by mainVm.allCount.collectAsState()
    val completedCount by mainVm.completedCount.collectAsState()
    val trashCount by mainVm.trashCount.collectAsState()
    val listCounts by mainVm.listCounts.collectAsState()

    Column(
        modifier
            .fillMaxHeight()
            .width(260.dp)
            .background(colors.surfaceAlt)
            .statusBarsPadding()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RemIcon(IconName.Today, colors.brand, Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            androidx.compose.foundation.text.BasicText("Sundial", style = RemType.title20.copy(color = colors.textHigh))
        }
        Spacer(Modifier.height(18.dp))
        RemTextField(value = query, onValueChange = mainVm::setSearch, placeholder = "搜索", leadingIcon = IconName.Search)
        Spacer(Modifier.height(18.dp))
        NavRow(IconName.Today, "今天", todayCount, scope == Scope.Today) { mainVm.selectScope(Scope.Today) }
        NavRow(IconName.Scheduled, "计划", scheduledCount, scope == Scope.Scheduled) { mainVm.selectScope(Scope.Scheduled) }
        NavRow(IconName.Layers, "全部", allCount, scope == Scope.All) { mainVm.selectScope(Scope.All) }
        NavRow(IconName.CheckCircle, "已完成", completedCount, scope == Scope.Completed) { mainVm.selectScope(Scope.Completed) }
        NavRow(IconName.Trash, "垃圾箱", trashCount, scope == Scope.Trash) { mainVm.selectScope(Scope.Trash) }
        Spacer(Modifier.height(18.dp))
        androidx.compose.foundation.text.BasicText("我的列表", style = RemType.label12.copy(color = colors.textLow))
        Spacer(Modifier.height(6.dp))
        lists.forEach { list ->
            NavRow(IconName.Inbox, list.name, listCounts[list.id] ?: 0, scope == Scope.List(list.id)) {
                mainVm.selectScope(Scope.List(list.id))
            }
        }
        Spacer(Modifier.weight(1f))
        SyncFooter(syncStatus, onSyncNow)
    }
}

@Composable
private fun NavRow(icon: IconName, label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalRemColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RemRadii.r4))
            .background(if (selected) colors.brandSubtle else colors.surfaceAlt)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemIcon(icon, if (selected) colors.brand else colors.textLow, Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        androidx.compose.foundation.text.BasicText(label, style = RemType.text14.copy(color = colors.textHigh), modifier = Modifier.weight(1f))
        androidx.compose.foundation.text.BasicText(count.toString(), style = RemType.text12.copy(color = colors.textLow))
    }
}

@Composable
private fun SyncFooter(syncStatus: SyncStatus, onSyncNow: (() -> Unit)?) {
    val colors = LocalRemColors.current
    val label = when {
        syncStatus.mode == SyncMode.Local -> "本地模式"
        syncStatus.syncing -> "同步中…"
        syncStatus.connected -> "已同步"
        else -> "同步中断"
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RemSyncIndicator(syncStatus.phase(), size = 12.dp)
        Spacer(Modifier.width(8.dp))
        androidx.compose.foundation.text.BasicText(label, style = RemType.text12.copy(color = colors.textLow), modifier = Modifier.weight(1f))
        RemIconButton(IconName.Settings, "设置", onClick = {}, size = 16.dp)
        if (onSyncNow != null && syncStatus.mode != SyncMode.Local) {
            RemIconButton(IconName.Sync, "立即同步", onClick = onSyncNow, size = 14.dp)
        }
    }
}
```

- [ ] **Step 2: Replace old Sidebar body**

Modify `Sidebar.kt` so the public `Sidebar(mainVm, syncStatus, onSyncNow)` function delegates to `SidebarNav(mainVm, syncStatus, onSyncNow)` while preserving the old function signature:

```kotlin
@Composable
fun Sidebar(mainVm: MainViewModel, syncStatus: SyncStatus = SyncStatus.initial, onSyncNow: (() -> Unit)? = null) {
    SidebarNav(mainVm = mainVm, syncStatus = syncStatus, onSyncNow = onSyncNow)
}
```

Keep the old helper code only if other files still reference it; otherwise delete private SmartGrid/ListRow helpers in the same commit.

- [ ] **Step 3: Fix settings click**

The `SyncFooter` in Step 1 lacks access to `mainVm.openSettings()`. Change its signature:

```kotlin
private fun SyncFooter(syncStatus: SyncStatus, onSettings: () -> Unit, onSyncNow: (() -> Unit)?)
```

Call:

```kotlin
SyncFooter(syncStatus, mainVm::openSettings, onSyncNow)
```

and use:

```kotlin
RemIconButton(IconName.Settings, "设置", onClick = onSettings, size = 16.dp)
```

- [ ] **Step 4: Compile**

Run:

```bash
./gradlew :shared:compileKotlinDesktop
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/ui/sidebar/SidebarNav.kt \
  shared/src/commonMain/kotlin/com/myapplication/shared/ui/sidebar/Sidebar.kt
git commit -m "feat(ui): replace smart grid sidebar with product navigation"
```

## Task 6: Split Detail Content And Add Desktop Inspector

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/detail/DetailContent.kt`
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/detail/DetailInspector.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/detail/DetailScreen.kt`

- [ ] **Step 1: Extract shared detail content**

Create `DetailContent.kt` by moving the body from current `DetailScreen` into this new composable:

```kotlin
@Composable
fun DetailContent(
    mainVm: MainViewModel,
    graph: AppGraph,
    todoId: Long,
    modifier: Modifier = Modifier,
    showCloseButton: Boolean,
) {
    // Move the current DetailScreen implementation body here.
    // Keep DetailViewModel creation keyed by "detail-$todoId".
    // Render the close button only when showCloseButton is true.
    // Keep date picker, list dialog, subtask input, trash action, and local title/note state behavior.
}
```

Use the exact imports and logic from `DetailScreen.kt`. The only behavioral change is replacing the unconditional close button with:

```kotlin
if (showCloseButton) {
    RemIconButton(IconName.Close, "关闭详情", onClick = mainVm::back, size = 16.dp)
}
```

- [ ] **Step 2: Keep DetailScreen as compatibility wrapper**

Modify `DetailScreen.kt`:

```kotlin
@Composable
fun DetailScreen(
    mainVm: MainViewModel,
    graph: AppGraph,
    todoId: Long,
    modifier: Modifier = Modifier,
) {
    DetailContent(
        mainVm = mainVm,
        graph = graph,
        todoId = todoId,
        modifier = modifier,
        showCloseButton = true,
    )
}
```

- [ ] **Step 3: Add desktop inspector**

Create `DetailInspector.kt`:

```kotlin
package com.myapplication.shared.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.myapplication.shared.di.AppGraph
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.theme.LocalRemColors

@Composable
fun DetailInspector(
    mainVm: MainViewModel,
    graph: AppGraph,
    todoId: Long?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalRemColors.current
    Box(
        modifier
            .width(348.dp)
            .fillMaxHeight()
            .background(colors.surface)
            .drawBehind {
                drawLine(colors.borderSubtle, Offset(0f, 0f), Offset(0f, size.height), 1f)
            },
    ) {
        if (todoId != null) {
            DetailContent(
                mainVm = mainVm,
                graph = graph,
                todoId = todoId,
                showCloseButton = true,
            )
        }
    }
}
```

- [ ] **Step 4: Compile**

Run:

```bash
./gradlew :shared:compileKotlinDesktop
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/ui/detail/DetailContent.kt \
  shared/src/commonMain/kotlin/com/myapplication/shared/ui/detail/DetailInspector.kt \
  shared/src/commonMain/kotlin/com/myapplication/shared/ui/detail/DetailScreen.kt
git commit -m "refactor(ui): split shared detail content"
```

## Task 7: Add DesktopShell And Wire Wide Layout

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/shell/DesktopShell.kt`
- Modify: `shared/src/commonMain/kotlin/App.kt`
- Modify: `shared/src/commonTest/kotlin/com/myapplication/shared/ui/main/MainViewModelTest.kt` if route behavior changes.

- [ ] **Step 1: Create DesktopShell**

Create `DesktopShell.kt`:

```kotlin
package com.myapplication.shared.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.myapplication.shared.di.AppGraph
import com.myapplication.shared.ui.detail.DetailInspector
import com.myapplication.shared.ui.ledger.MainLedger
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.main.Route
import com.myapplication.shared.ui.sidebar.Sidebar
import com.myapplication.shared.ui.theme.LocalRemColors

@Composable
fun DesktopShell(
    graph: AppGraph,
    mainVm: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val route by mainVm.route.collectAsState()
    val syncStatus by graph.engine.status.collectAsState()
    val selectedId = (route as? Route.Detail)?.todoId
    Row(modifier.fillMaxSize().background(LocalRemColors.current.bgSecondary)) {
        Sidebar(mainVm, syncStatus, onSyncNow = { graph.engine.syncNow() })
        MainLedger(
            mainVm = mainVm,
            selectedId = selectedId,
            modifier = Modifier.weight(1f),
            clock = graph.clock,
            timeZone = graph.timeZone,
        )
        DetailInspector(
            mainVm = mainVm,
            graph = graph,
            todoId = selectedId,
        )
    }
}
```

- [ ] **Step 2: Wire AppRoot wide branch**

Modify `App.kt`:

- Keep `SettingsScreen` as the highest-priority route branch.
- Replace the current wide `Row { Sidebar + TodoListScreen + AnimatedVisibility DetailScreen }` branch with:

```kotlin
wide -> DesktopShell(graph = graph, mainVm = mainVm)
```

- Keep the current narrow branch for now; Task 8 replaces it.
- Add import:

```kotlin
import com.myapplication.shared.ui.shell.DesktopShell
```

- [ ] **Step 3: Run existing ViewModel tests**

Run:

```bash
./gradlew :shared:desktopTest --tests "com.myapplication.shared.ui.main.MainViewModelTest" --rerun-tasks
```

Expected: all existing tests pass, including `selectScopeClosesDetail`.

- [ ] **Step 4: Compile desktop**

Run:

```bash
./gradlew :shared:compileKotlinDesktop
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/ui/shell/DesktopShell.kt \
  shared/src/commonMain/kotlin/App.kt \
  shared/src/commonTest/kotlin/com/myapplication/shared/ui/main/MainViewModelTest.kt
git commit -m "feat(ui): wire desktop product shell"
```

## Task 8: Add MobileShell And Wire Narrow Layout

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/shell/MobileShell.kt`
- Modify: `shared/src/commonMain/kotlin/App.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/narrow/NarrowShell.kt` after migration if no longer used.

- [ ] **Step 1: Create MobileShell**

Create `MobileShell.kt`:

```kotlin
package com.myapplication.shared.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myapplication.shared.di.AppGraph
import com.myapplication.shared.ui.detail.DetailContent
import com.myapplication.shared.ui.ledger.MainLedger
import com.myapplication.shared.ui.ledger.TodayRhythmCompact
import com.myapplication.shared.ui.ledger.buildTodayRhythmState
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.main.Route
import com.myapplication.shared.ui.narrow.NarrowBottomNav
import com.myapplication.shared.ui.narrow.NarrowTopBar
import com.myapplication.shared.ui.theme.LocalRemColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileShell(
    graph: AppGraph,
    mainVm: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val colors = LocalRemColors.current
    val route by mainVm.route.collectAsState()
    val todos by mainVm.todos.collectAsState()
    val syncStatus by graph.engine.status.collectAsState()
    val selectedId = (route as? Route.Detail)?.todoId
    val rhythm = buildTodayRhythmState(todos, graph.clock.now(), graph.timeZone)
    Box(modifier.fillMaxSize().background(colors.bgPrimary)) {
        Column(Modifier.fillMaxSize()) {
            NarrowTopBar(mainVm, syncStatus = syncStatus, onSyncNow = { graph.engine.syncNow() })
            TodayRhythmCompact(rhythm)
            PullToRefreshBox(
                isRefreshing = syncStatus.syncing,
                onRefresh = { graph.engine.syncNow() },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                MainLedger(
                    mainVm = mainVm,
                    selectedId = selectedId,
                    modifier = Modifier.fillMaxSize().padding(bottom = 72.dp),
                    clock = graph.clock,
                    timeZone = graph.timeZone,
                )
            }
            NarrowBottomNav(mainVm, Modifier.navigationBarsPadding())
        }
        if (selectedId != null) {
            ModalBottomSheet(
                onDismissRequest = mainVm::back,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                dragHandle = null,
                contentWindowInsets = { androidx.compose.foundation.layout.WindowInsets(0.dp) },
            ) {
                DetailContent(
                    mainVm = mainVm,
                    graph = graph,
                    todoId = selectedId,
                    showCloseButton = true,
                    modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
                )
            }
        }
    }
}
```

- [ ] **Step 2: Wire AppRoot narrow branch**

Modify `App.kt`:

```kotlin
else -> MobileShell(graph = graph, mainVm = mainVm)
```

Add import:

```kotlin
import com.myapplication.shared.ui.shell.MobileShell
```

Remove no-longer-used imports from the old narrow branch.

- [ ] **Step 3: Rework mobile shell if MainLedger is too desktop-heavy**

If mobile compile passes but visual inspection shows duplicated headers or too much vertical content, split `MainLedger` parameters before proceeding:

```kotlin
fun MainLedger(
    mainVm: MainViewModel,
    selectedId: String?,
    showHeader: Boolean = true,
    showRhythm: Boolean = true,
    showOverview: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
)
```

Use mobile call:

```kotlin
MainLedger(
    mainVm = mainVm,
    selectedId = selectedId,
    showHeader = false,
    showRhythm = false,
    showOverview = false,
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
)
```

- [ ] **Step 4: Compile Android and desktop**

Run:

```bash
./gradlew :shared:compileKotlinDesktop :androidApp:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/ui/shell/MobileShell.kt \
  shared/src/commonMain/kotlin/App.kt \
  shared/src/commonMain/kotlin/com/myapplication/shared/ui/narrow/NarrowShell.kt \
  shared/src/commonMain/kotlin/com/myapplication/shared/ui/ledger/MainLedger.kt
git commit -m "feat(ui): wire mobile product shell"
```

## Task 9: Polish Accessibility, Keyboard Behavior, And State Boundaries

**Files:**
- Modify: `shared/src/commonMain/kotlin/App.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/ledger/MainLedger.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/ledger/TaskRow.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/sidebar/SidebarNav.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/shell/DesktopShell.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/shell/MobileShell.kt`

- [ ] **Step 1: Add keyboard shortcuts in AppRoot**

In `App.kt`, extend the existing `onPreviewKeyEvent` logic:

```kotlin
if (event.type == KeyEventType.KeyUp && event.key == Key.Escape) {
    mainVm.back()
    true
} else {
    false
}
```

Keep `Escape` only in this task. Do not add `Cmd+F` or `Cmd+N` until search/add focus behavior has a tested owner.

- [ ] **Step 2: Add semantic labels**

Ensure these icon-only controls have meaningful labels:

```kotlin
RemIconButton(IconName.Settings, "设置", onClick = mainVm::openSettings, size = 16.dp)
RemIconButton(IconName.Sync, "立即同步", onClick = { graph.engine.syncNow() }, size = 14.dp)
RemIconButton(IconName.Flag, "添加旗标", onClick = onToggleFlag, size = 14.dp)
RemIconButton(IconName.Close, "关闭详情", onClick = mainVm::back, size = 16.dp)
```

For clickable task rows, add:

```kotlin
.semantics { contentDescription = "打开待办详情：${item.title}" }
```

For checkbox clicks, verify `RemCheckbox` exposes stateful semantics. If it does not, update `RemCheckbox` with:

```kotlin
.semantics {
    contentDescription = if (checked) "标记为未完成" else "标记为已完成"
}
```

- [ ] **Step 3: Separate checkbox and row click behavior**

In `TaskRow.kt`, confirm:

- `RemCheckbox` only calls `onToggleCompleted`.
- Row background/click only calls `onOpen`.
- Flag icon only calls `onToggleFlag`.

Do not call `onOpen` from checkbox or flag handlers.

- [ ] **Step 4: Compile**

Run:

```bash
./gradlew :shared:compileKotlinDesktop
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/App.kt \
  shared/src/commonMain/kotlin/com/myapplication/shared/ui/ledger/MainLedger.kt \
  shared/src/commonMain/kotlin/com/myapplication/shared/ui/ledger/TaskRow.kt \
  shared/src/commonMain/kotlin/com/myapplication/shared/ui/sidebar/SidebarNav.kt \
  shared/src/commonMain/kotlin/com/myapplication/shared/ui/shell/DesktopShell.kt \
  shared/src/commonMain/kotlin/com/myapplication/shared/ui/shell/MobileShell.kt \
  shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/RemCheckbox.kt
git commit -m "fix(ui): harden task interaction semantics"
```

## Task 10: Full Verification And Visual QA

**Files:**
- Modify: `README.md` or the project roadmap document only if implementation discovers a user-facing limitation that must be documented.

- [ ] **Step 1: Run focused tests**

Run:

```bash
./gradlew :shared:desktopTest --tests "com.myapplication.shared.ui.ledger.LedgerUiModelsTest" --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`, all ledger model tests pass.

- [ ] **Step 2: Run full shared tests**

Run:

```bash
./gradlew :shared:desktopTest --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Build Android**

Run:

```bash
./gradlew :androidApp:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Launch desktop and capture screenshots**

Run:

```bash
./gradlew :desktopApp:run
```

Capture desktop screenshot at 1000x680 and, if possible, resize the window below 900dp to capture the mobile shell branch. Inspect the screenshots for:

- no large smart-list tiles
- no graph-paper background
- no cards inside cards
- no clipped Chinese text
- active task rows visually stronger than completed rows
- visible add-task entry
- visible sync status
- desktop selected task opens right inspector
- narrow selected task opens bottom sheet

- [ ] **Step 5: Stop desktop app**

Terminate the Gradle run with `Ctrl+C` in the running terminal session. Confirm no `:desktopApp:run` process remains in Codex background processes.

- [ ] **Step 6: Commit docs if needed**

If `README.md` or a roadmap document changed because a limitation was discovered, commit:

```bash
git add README.md
git commit -m "docs(ui): record product UI verification notes"
```

If the roadmap document changed instead of `README.md`, stage the exact roadmap file shown by `git status --short`. If no docs changed, skip this commit.

- [ ] **Step 7: Final status**

Run:

```bash
git status --short --branch
```

Expected: clean working tree on the implementation branch.

## Self-Review Checklist

- Spec coverage:
  - Desktop compact sidebar: Task 5.
  - Desktop main ledger: Task 4 and Task 7.
  - Today rhythm: Task 1 and Task 4.
  - Lightweight state overview: Task 4.
  - Desktop detail inspector: Task 6 and Task 7.
  - Mobile top/rhythm/list/nav/sheet: Task 8.
  - Accessibility and ergonomics: Task 9.
  - Visual and build verification: Task 10.

- Scope guard:
  - No database schema changes.
  - No sync core changes.
  - No priority, reminder scheduling, full calendar, drag sorting, accounts, or collaboration.
  - No new UI framework or icon dependency.

- Type consistency:
  - `TodayRhythmState`, `TaskRowModel`, and `TaskGroups` are defined in Task 1 before usage.
  - `RemBadgeTone`, extra `IconName` values, and `RemControlSize` are defined in Task 2 before usage.
  - `DetailContent` is defined in Task 6 before `DesktopShell` and `MobileShell` call it.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-08-12-sundial-product-ui-redesign-plan.md`. Two execution options:

1. Subagent-Driven (recommended) - dispatch a fresh subagent per task, review between tasks, fast iteration.

2. Inline Execution - execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
