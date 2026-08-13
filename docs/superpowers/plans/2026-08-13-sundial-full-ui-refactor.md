# Sundial Full UI Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor Sundial into a coherent shadcn-inspired, Compose-native product with one design language, unified navigation semantics, consistent desktop/mobile layouts, and screenshot-verifiable UI quality.

**Architecture:** Keep Kotlin Multiplatform, Compose Multiplatform, current data/sync/effect architecture, and current charting stack. Convert the UI layer from local `Rem*` patches into a structured `Sundial UI` system: pure product semantics, tokens, primitives, composed patterns, screen scaffolds, and visual QA. Refactor incrementally so each milestone compiles and preserves existing task data behavior.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Compose Foundation/Material3 behavior primitives, Vico charts, Kotlin test, Gradle desktop/android verification.

---

## File Structure

Create or formalize these design-system files:

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/design/SundialDesignLanguage.kt`
  Product navigation semantics: top-level destinations, workbench lenses, detail presentation modes.

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/design/SundialDensity.kt`
  Density, layout breakpoints, control heights, row heights, panel widths.

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/design/SundialMotion.kt`
  Durations, easing labels, reduced-motion switches for shared UI transitions.

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/theme/DesignTokens.kt`
  Expand current token contract with shadcn-like surfaces, overlays, accent roles, focus and state roles.

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/SundialBackAction.kt`
  Unified chevron back action.

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/SundialSurface.kt`
  Shared border/background containers: page, panel, inset, row, selected row.

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/SundialToolbar.kt`
  Shared toolbar and icon-action slots for desktop and mobile.

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/SundialAccordion.kt`
  Shared accordion section contract used by ledgers, settings groups, list management, and trash.

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/SundialNavigation.kt`
  Shared nav row and bottom-nav item rendering driven by `SundialDesignLanguage`.

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/shell/DesktopShell.kt`
  Three-pane desktop app shell: sidebar, content, inspector.

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/shell/MobileShell.kt`
  Mobile shell: top bar, content scroll area, bottom nav, detail sheet.

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/ledger/MainLedger.kt`
  Workbench/List/Archive ledger layout, no duplicate filter chips.

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/ledger/TaskRow.kt`
  Canonical task row and task section visual behavior.

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/detail/DetailContent.kt`
  Full task editor layout with readable title, editable subtasks, stable footer actions.

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/SettingsHome.kt`
  Settings scaffold using shared navigation and section patterns. It must not render placeholder pages.

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/ListSettingsScreen.kt`
  List CRUD as a proper settings surface.

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/SettingsPreferences.kt`
  Pure settings preference model: theme mode, display density, font family, and resolved font family behavior.

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/AppearanceSettingsScreen.kt`
  Real appearance settings page: theme mode, density, and font family input with live preview.

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/DataSettingsScreen.kt`
  Real data settings page: local-first storage facts, export/backup placeholders only where backed by visible disabled states, trash/data maintenance entry points.

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/AboutSettingsScreen.kt`
  Real about page: version, license, sync/widget capability facts, and platform support notes.

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/WidgetSettingsScreen.kt`
  Remove after widget information has moved into About/Data. There should be no standalone Settings "小组件" destination.

- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/analytics/AnalyticsScreen.kt`
  Analytics screen aligned to new surface/card/chart rhythm.

Test files:

- `shared/src/commonTest/kotlin/com/myapplication/shared/ui/design/SundialDesignLanguageTest.kt`
- `shared/src/commonTest/kotlin/com/myapplication/shared/ui/design/SundialDensityTest.kt`
- `shared/src/commonTest/kotlin/com/myapplication/shared/ui/ledger/LedgerUiModelsTest.kt`
- `shared/src/commonTest/kotlin/com/myapplication/shared/ui/main/MainViewModelTest.kt`
- `shared/src/commonTest/kotlin/com/myapplication/shared/ui/settings/ListSettingsSelectionTest.kt`
- `shared/src/commonTest/kotlin/com/myapplication/shared/ui/settings/SettingsPreferencesTest.kt`
- `shared/src/commonTest/kotlin/com/myapplication/shared/ui/detail/DetailViewModelTest.kt`

---

## Milestone 0: Stabilize Current Work

### Task 0.1: Verify current first-pass design-system work

**Files:**
- Read: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/design/SundialDesignLanguage.kt`
- Read: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/SundialBackAction.kt`
- Read: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/shell/MobileShell.kt`
- Read: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/sidebar/SidebarNav.kt`

- [ ] **Step 1: Inspect local diff**

Run:

```bash
git status --short
git diff -- shared/src/commonMain/kotlin/com/myapplication/shared/ui/design/SundialDesignLanguage.kt \
  shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/SundialBackAction.kt \
  shared/src/commonMain/kotlin/com/myapplication/shared/ui/shell/MobileShell.kt \
  shared/src/commonMain/kotlin/com/myapplication/shared/ui/sidebar/SidebarNav.kt
```

Expected: only UI design-system changes plus docs are present; unrelated `tools/` remains untracked and untouched.

- [ ] **Step 2: Run current verification**

Run:

```bash
./gradlew :shared:desktopTest --tests "com.myapplication.shared.ui.design.SundialDesignLanguageTest"
./gradlew :shared:desktopTest --tests "com.myapplication.shared.ui.ledger.LedgerUiModelsTest"
./gradlew :shared:compileKotlinDesktop
./gradlew :desktopApp:compileKotlinJvm
```

Expected: all commands pass. Existing Gradle/Kotlin deprecation warnings may remain.

---

## Milestone 1: Product Semantics Contract

### Task 1.1: Extend design-language tests for destination and lens separation

**Files:**
- Modify: `shared/src/commonTest/kotlin/com/myapplication/shared/ui/design/SundialDesignLanguageTest.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/design/SundialDesignLanguage.kt`

- [ ] **Step 1: Write failing tests**

Append these tests:

```kotlin
@Test
fun workbenchLensesUseStableOrderLabelsAndScopes() {
    assertEquals(
        listOf("全部", "今天", "计划", "已完成", "垃圾箱"),
        sundialWorkbenchLenses().map { it.label },
    )
    assertEquals(
        listOf(Scope.All, Scope.Today, Scope.Scheduled, Scope.Completed, Scope.Trash),
        sundialWorkbenchLenses().map { it.scope },
    )
}

@Test
fun topLevelDestinationDoesNotTreatSmartLensAsPrimaryNavigation() {
    val primaryScopes = sundialPrimaryDestinations().map { scopeForDestination(it.destination, emptyList()) }
    assertEquals(listOf(Scope.All, Scope.All, Scope.Analytics), primaryScopes)
}
```

- [ ] **Step 2: Run RED**

Run:

```bash
./gradlew :shared:desktopTest --tests "com.myapplication.shared.ui.design.SundialDesignLanguageTest"
```

Expected: FAIL with unresolved `sundialWorkbenchLenses`.

- [ ] **Step 3: Implement minimal model**

Add:

```kotlin
data class SundialLensItem(
    val scope: Scope,
    val label: String,
    val icon: IconName,
)

fun sundialWorkbenchLenses(): List<SundialLensItem> = listOf(
    SundialLensItem(Scope.All, "全部", IconName.Layers),
    SundialLensItem(Scope.Today, "今天", IconName.Today),
    SundialLensItem(Scope.Scheduled, "计划", IconName.Scheduled),
    SundialLensItem(Scope.Completed, "已完成", IconName.CheckCircle),
    SundialLensItem(Scope.Trash, "垃圾箱", IconName.Trash),
)
```

- [ ] **Step 4: Run GREEN**

Run:

```bash
./gradlew :shared:desktopTest --tests "com.myapplication.shared.ui.design.SundialDesignLanguageTest"
```

Expected: PASS.

### Task 1.2: Add layout and density contract

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/design/SundialDensity.kt`
- Create: `shared/src/commonTest/kotlin/com/myapplication/shared/ui/design/SundialDensityTest.kt`

- [ ] **Step 1: Write failing tests**

Create:

```kotlin
package com.myapplication.shared.ui.design

import kotlin.test.Test
import kotlin.test.assertEquals

class SundialDensityTest {
    @Test
    fun desktopPanelWidthsAreStable() {
        assertEquals(272, SundialLayout.sidebarWidthDp)
        assertEquals(420, SundialLayout.inspectorWidthDp)
        assertEquals(720, SundialLayout.compactBreakpointDp)
    }

    @Test
    fun rowHeightsSeparateDesktopAndTouch() {
        assertEquals(40, SundialDensity.compactTaskRowDp)
        assertEquals(52, SundialDensity.touchTaskRowDp)
        assertEquals(36, SundialDensity.toolbarControlDp)
    }
}
```

- [ ] **Step 2: Run RED**

Run:

```bash
./gradlew :shared:desktopTest --tests "com.myapplication.shared.ui.design.SundialDensityTest"
```

Expected: FAIL with unresolved `SundialLayout` and `SundialDensity`.

- [ ] **Step 3: Implement minimal constants**

Create:

```kotlin
package com.myapplication.shared.ui.design

object SundialLayout {
    const val compactBreakpointDp = 720
    const val sidebarWidthDp = 272
    const val inspectorWidthDp = 420
    const val contentMaxWidthDp = 920
}

object SundialDensity {
    const val compactTaskRowDp = 40
    const val touchTaskRowDp = 52
    const val toolbarControlDp = 36
    const val bottomNavHeightDp = 58
}
```

- [ ] **Step 4: Run GREEN**

Run:

```bash
./gradlew :shared:desktopTest --tests "com.myapplication.shared.ui.design.SundialDensityTest"
```

Expected: PASS.

---

## Milestone 2: Token and Primitive System

### Task 2.1: Expand tokens without changing call sites

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/theme/DesignTokens.kt`
- Test: `shared/src/commonTest/kotlin/com/myapplication/shared/ui/design/SundialDensityTest.kt`

- [ ] **Step 1: Add token names**

Extend `RemColors` with:

```kotlin
val surfaceRaised: Color,
val surfaceInset: Color,
val overlay: Color,
val rowHover: Color,
val rowSelected: Color,
val destructiveSubtle: Color,
val successSubtle: Color,
val infoSubtle: Color,
val warningSubtle: Color,
```

Add matching values to `LightRemColors` and `DarkRemColors`:

```kotlin
surfaceRaised = Color(0xFFFFFFFF)
surfaceInset = Color(0xFFF6F6F4)
overlay = Color(0x66000000)
rowHover = Color(0xFFF4F4F2)
rowSelected = Color(0xFFFFF2E8)
destructiveSubtle = Color(0xFFFFEEEE)
successSubtle = Color(0xFFEFF8EF)
infoSubtle = Color(0xFFEFF5FF)
warningSubtle = Color(0xFFFFF4E3)
```

For dark theme:

```kotlin
surfaceRaised = Color(0xFF282828)
surfaceInset = Color(0xFF1B1B1B)
overlay = Color(0x99000000)
rowHover = Color(0xFF2C2C2C)
rowSelected = Color(0xFF3A2416)
destructiveSubtle = Color(0xFF3A2020)
successSubtle = Color(0xFF1E3320)
infoSubtle = Color(0xFF1B2A44)
warningSubtle = Color(0xFF352717)
```

- [ ] **Step 2: Compile**

Run:

```bash
./gradlew :shared:compileKotlinDesktop
```

Expected: PASS.

### Task 2.2: Add shared surface primitive

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/SundialSurface.kt`

- [ ] **Step 1: Add component**

Create:

```kotlin
package com.myapplication.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemRadii

enum class SundialSurfaceTone { Page, Panel, Raised, Inset, Transparent }

@Composable
fun SundialSurface(
    tone: SundialSurfaceTone,
    modifier: Modifier = Modifier,
    border: Boolean = false,
    radius: Dp = RemRadii.r4,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalRemColors.current
    val bg = when (tone) {
        SundialSurfaceTone.Page -> colors.bgPrimary
        SundialSurfaceTone.Panel -> colors.surface
        SundialSurfaceTone.Raised -> colors.surfaceRaised
        SundialSurfaceTone.Inset -> colors.surfaceInset
        SundialSurfaceTone.Transparent -> Color.Transparent
    }
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .clip(shape)
            .background(bg)
            .border(if (border) 1.dp else 0.dp, colors.borderSubtle, shape),
        content = content,
    )
}
```

- [ ] **Step 2: Compile**

Run:

```bash
./gradlew :shared:compileKotlinDesktop
```

Expected: PASS.

### Task 2.3: Add shared navigation primitive

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/SundialNavigation.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/sidebar/SidebarNav.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/shell/MobileShell.kt`

- [ ] **Step 1: Create shared nav components**

Create `SundialNavRow` and `SundialBottomNavItem` with parameters:

```kotlin
@Composable
fun SundialNavRow(
    icon: IconName,
    label: String,
    count: Int?,
    selected: Boolean,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
)

@Composable
fun SundialBottomNavItem(
    icon: IconName,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
)
```

Use the exact visual behavior currently inside `SidebarNav.NavRow` and `MobileShell.MobileNavItem`, with shared selected colors.

- [ ] **Step 2: Replace duplicate local components**

In `SidebarNav.kt`, remove local `NavRow` and call `SundialNavRow`.

In `MobileShell.kt`, remove local `MobileNavItem` and call `SundialBottomNavItem`.

- [ ] **Step 3: Compile**

Run:

```bash
./gradlew :shared:compileKotlinDesktop
./gradlew :desktopApp:compileKotlinJvm
```

Expected: PASS.

---

## Milestone 3: Accordion and Ledger Refactor

### Task 3.1: Extract section model visibility tests

**Files:**
- Modify: `shared/src/commonTest/kotlin/com/myapplication/shared/ui/ledger/LedgerUiModelsTest.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/ledger/MainLedger.kt`

- [ ] **Step 1: Add test for workbench section names**

Append:

```kotlin
@Test
fun workbenchSectionsAlwaysUseStableAccordionBuckets() {
    val today = kotlinx.datetime.LocalDate(2026, 8, 12)
    val active = listOf(
        TaskRowModel(item(1, "overdue", "2026-08-11T09:00:00Z", false), emptyList()),
        TaskRowModel(item(2, "today", "2026-08-12T09:00:00Z", false), emptyList()),
        TaskRowModel(item(3, "future", "2026-08-15T09:00:00Z", false), emptyList()),
        TaskRowModel(item(4, "loose", null, false), emptyList()),
    )

    val sections = buildWorkbenchLedgerSectionsForTest(active, today, TimeZone.UTC, inboxListId = null)

    assertEquals(listOf("逾期", "今天", "未来 7 天", "无日期", "待整理"), sections.map { it.title })
}
```

- [ ] **Step 2: Run RED**

Run:

```bash
./gradlew :shared:desktopTest --tests "com.myapplication.shared.ui.ledger.LedgerUiModelsTest"
```

Expected: FAIL with unresolved `buildWorkbenchLedgerSectionsForTest`.

- [ ] **Step 3: Expose internal pure builder**

Rename private `workbenchSections` to internal:

```kotlin
internal fun buildWorkbenchLedgerSectionsForTest(
    active: List<TaskRowModel>,
    today: LocalDate,
    timeZone: TimeZone,
    inboxListId: Long?,
): List<LedgerTaskSection> = listOf(...)
```

Keep production call through a small private wrapper if needed.

- [ ] **Step 4: Run GREEN**

Run:

```bash
./gradlew :shared:desktopTest --tests "com.myapplication.shared.ui.ledger.LedgerUiModelsTest"
```

Expected: PASS.

### Task 3.2: Extract shared accordion component

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/SundialAccordion.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/ledger/TaskRow.kt`

- [ ] **Step 1: Add `SundialAccordionSection`**

Create a reusable section component:

```kotlin
@Composable
fun SundialAccordionSection(
    title: String,
    count: Int,
    tone: Color,
    emptyText: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = true,
    content: @Composable () -> Unit,
)
```

Header behavior:

- chevron left
- title + count
- title color is tone
- default expanded
- `remember(title)` preserves per-section expansion state

- [ ] **Step 2: Replace section headers**

In `TaskSection` and `TrashSection`, replace duplicate header code with `SundialAccordionSection`.

- [ ] **Step 3: Compile**

Run:

```bash
./gradlew :shared:compileKotlinDesktop
```

Expected: PASS.

### Task 3.3: Make ledger full-area scroll surface

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/ledger/MainLedger.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/shell/MobileShell.kt`

- [ ] **Step 1: Adjust mobile ledger padding**

For mobile `MainLedger` call, keep:

```kotlin
showHeader = false
showRhythm = false
compactRows = true
edgeToEdgeRows = true
contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
```

- [ ] **Step 2: Make `LazyColumn` own scroll area**

In `MainLedger`, set `LazyColumn` content padding to row padding instead of wrapping the whole ledger in large padding on mobile.

- [ ] **Step 3: Compile**

Run:

```bash
./gradlew :shared:compileKotlinDesktop
```

Expected: PASS.

---

## Milestone 4: Shell and Navigation Refactor

### Task 4.1: Desktop shell width and inspector contract

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/shell/DesktopShell.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/detail/DetailInspector.kt`
- Test: `shared/src/commonTest/kotlin/com/myapplication/shared/ui/design/SundialDensityTest.kt`

- [ ] **Step 1: Use `SundialLayout` constants**

Replace hard-coded sidebar and inspector widths with `SundialLayout.sidebarWidthDp.dp` and `SundialLayout.inspectorWidthDp.dp`.

- [ ] **Step 2: Add border and page surfaces**

Desktop shell should read as:

```text
Sidebar | Main content scroll area | Inspector
```

The sidebar and inspector each have a subtle border. The main content uses page background.

- [ ] **Step 3: Compile**

Run:

```bash
./gradlew :desktopApp:compileKotlinJvm
```

Expected: PASS.

### Task 4.2: Mobile shell safe-area and top-bar cleanup

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/shell/MobileShell.kt`

- [ ] **Step 1: Keep safe-area structure**

Ensure `MobileTopBar` includes `statusBarsPadding()` and `MobileBottomNav` includes `navigationBarsPadding()`.

- [ ] **Step 2: Keep only primary bottom nav**

Bottom nav uses exactly:

```kotlin
sundialPrimaryDestinations()
```

No smart scope chips are rendered outside `Scope.List`.

- [ ] **Step 3: Compile**

Run:

```bash
./gradlew :shared:compileKotlinDesktop
```

Expected: PASS.

---

## Milestone 5: Task Detail Refactor

### Task 5.1: Lock detail navigation behavior

**Files:**
- Modify: `shared/src/commonTest/kotlin/com/myapplication/shared/ui/main/MainViewModelTest.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/main/MainViewModel.kt`

- [ ] **Step 1: Add route intent tests**

Append:

```kotlin
@Test
fun settingsBackReturnsToMainWithoutChangingScope() {
    val vm = vm(FakeTodoRepository())
    vm.selectScope(Scope.Scheduled)
    vm.openSettings()

    vm.back()

    assertEquals(Scope.Scheduled, vm.scope.value)
    assertEquals(Route.Main, vm.route.value)
}

@Test
fun detailCloseDoesNotNavigateParentChain() {
    val vm = vm(FakeTodoRepository())
    vm.openDetail(4)
    vm.openDetail(9, parentTodoId = 4)

    vm.closeDetail()

    assertEquals(Route.Main, vm.route.value)
}
```

- [ ] **Step 2: Run test**

Run:

```bash
./gradlew :shared:desktopTest --tests "com.myapplication.shared.ui.main.MainViewModelTest"
```

Expected: PASS if current behavior already satisfies this contract.

### Task 5.2: Rebuild detail layout around readable title

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/detail/DetailContent.kt`

- [ ] **Step 1: Split detail into regions**

Inside `DetailContent`, arrange regions in this order:

```text
Header: checkbox, close, due badge
Title editor: full-width multi-line title
Note editor: full-width note field
Properties: date, flag, list
Subtasks: editable subtask rows
Metadata/actions: created date, move, trash
```

- [ ] **Step 2: Ensure title is never constrained to a tiny header field**

Use a full-width title text field area with at least 2 lines and no horizontal clipping.

- [ ] **Step 3: Compile**

Run:

```bash
./gradlew :shared:compileKotlinDesktop
```

Expected: PASS.

### Task 5.3: Make subtasks editable

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/detail/DetailViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/detail/DetailContent.kt`
- Test: `shared/src/commonTest/kotlin/com/myapplication/shared/ui/detail/DetailViewModelTest.kt`

- [ ] **Step 1: Write failing test**

Add:

```kotlin
@Test
fun updateSubtaskTitleDelegatesToRepository() = runTest(dispatcher) {
    val repo = FakeTodoRepository()
    repo.ensureInbox()
    repo.insertTodo(1, "Parent", "", null, null, false)
    repo.insertTodo(1, "Child", "", null, parentId = 1, false)
    val child = repo.todos.first { it.parentId == 1L }
    val vm = DetailViewModel(repo, todoId = 1)

    vm.updateSubtaskTitle(child, "  New child  ")
    advanceUntilIdle()

    assertEquals("New child", repo.todos.first { it.id == child.id }.title)
}
```

- [ ] **Step 2: Run RED**

Run:

```bash
./gradlew :shared:desktopTest --tests "com.myapplication.shared.ui.detail.DetailViewModelTest"
```

Expected: FAIL with unresolved `updateSubtaskTitle`.

- [ ] **Step 3: Implement view-model command**

Add:

```kotlin
fun updateSubtaskTitle(item: TodoItem, title: String) {
    val trimmed = title.trim()
    if (trimmed.isEmpty() || trimmed == item.title) return
    launchTodoEffect(lastError) { repository.updateTodo(item.copy(title = trimmed)) }
}
```

Use the repository update method that exists in the current codebase; if the method name differs, use the existing update method used by parent task title editing.

- [ ] **Step 4: Wire UI**

Render each subtask title with an editable text field and call `updateSubtaskTitle` on commit.

- [ ] **Step 5: Run GREEN**

Run:

```bash
./gradlew :shared:desktopTest --tests "com.myapplication.shared.ui.detail.DetailViewModelTest"
./gradlew :shared:compileKotlinDesktop
```

Expected: PASS.

---

## Milestone 6: Settings and List Management Refactor

### Task 6.1: Settings sections become real destinations

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/SettingsSection.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/SettingsHome.kt`
- Delete: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/WidgetSettingsScreen.kt`
- Test: `shared/src/commonTest/kotlin/com/myapplication/shared/ui/settings/SettingsPreferencesTest.kt`

- [ ] **Step 1: Write failing section test**

Create `shared/src/commonTest/kotlin/com/myapplication/shared/ui/settings/SettingsPreferencesTest.kt` with:

```kotlin
package com.myapplication.shared.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SettingsPreferencesTest {
    @Test
    fun settingsSectionsDoNotExposeStandaloneWidgetPage() {
        assertFalse(SettingsSection.entries.any { it.title == "小组件" })
        assertEquals(
            listOf("同步", "列表", "数据", "外观", "关于"),
            SettingsSection.entries.map { it.title },
        )
    }
}
```

- [ ] **Step 2: Run RED**

Run:

```bash
./gradlew :shared:desktopTest --tests "com.myapplication.shared.ui.settings.SettingsPreferencesTest"
```

Expected: FAIL because `SettingsSection.Widgets` still exists.

- [ ] **Step 3: Remove widget section**

Update `SettingsSection.kt` to:

```kotlin
internal enum class SettingsSection(
    val title: String,
    val subtitle: String,
    val icon: IconName,
) {
    Sync("同步", "连接、状态和手动同步", IconName.Sync),
    Lists("列表", "管理列表、颜色和统计", IconName.Inbox),
    Data("数据", "导出、备份和本地维护", IconName.Tray),
    Appearance("外观", "主题、密度和字体", IconName.Settings),
    About("关于", "版本、许可证和平台能力", IconName.Device),
}
```

Remove the `SettingsSection.Widgets -> WidgetSettingsScreen()` branch from `SettingsHome.kt`.

Delete `WidgetSettingsScreen.kt` after the about page has absorbed its useful capability facts in Task 6.6.

- [ ] **Step 4: Run GREEN**

Run:

```bash
./gradlew :shared:desktopTest --tests "com.myapplication.shared.ui.settings.SettingsPreferencesTest"
```

Expected: PASS.

### Task 6.2: Settings scaffold uses shared navigation rows

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/SettingsHome.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/SundialNavigation.kt`

- [ ] **Step 1: Replace local settings row visuals**

Use `SundialNavRow` for wide settings navigation. Use a compact pill variant only for narrow settings tabs.

- [ ] **Step 2: Confirm back action remains chevron**

Search:

```bash
rg -n 'RemButton\\("返回"|SundialBackAction' shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings
```

Expected: `SundialBackAction` exists and `RemButton("返回"` does not.

### Task 6.3: Settings preferences model supports theme, density, and live font family

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/SettingsPreferences.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/SettingsViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/theme/DesignTokens.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/theme/Theme.kt`
- Modify: `shared/src/commonMain/kotlin/App.kt`
- Test: `shared/src/commonTest/kotlin/com/myapplication/shared/ui/settings/SettingsPreferencesTest.kt`

- [ ] **Step 1: Add failing preference tests**

Append:

```kotlin
@Test
fun appearanceSettingsParsePersistedValuesWithDefaults() {
    val prefs = SettingsPreferences.fromSettings(
        mapOf(
            "appearance.theme" to "dark",
            "appearance.density" to "compact",
            "appearance.fontFamily" to "Avenir Next",
        ),
    )

    assertEquals(ThemePreference.Dark, prefs.theme)
    assertEquals(DisplayDensity.Compact, prefs.density)
    assertEquals("Avenir Next", prefs.fontFamily)
}

@Test
fun blankFontFamilyFallsBackToSystemFont() {
    val prefs = SettingsPreferences(fontFamily = "   ")

    assertEquals(null, prefs.normalizedFontFamilyOrNull())
}

@Test
fun fontFamilyIsTrimmedBeforePersisting() {
    val prefs = SettingsPreferences(fontFamily = "  SF Pro Text  ")

    assertEquals("SF Pro Text", prefs.toSettingsMap()["appearance.fontFamily"])
}
```

- [ ] **Step 2: Run RED**

Run:

```bash
./gradlew :shared:desktopTest --tests "com.myapplication.shared.ui.settings.SettingsPreferencesTest"
```

Expected: FAIL with unresolved `SettingsPreferences`, `ThemePreference`, and `DisplayDensity`.

- [ ] **Step 3: Implement pure preference model**

Create:

```kotlin
package com.myapplication.shared.ui.settings

enum class ThemePreference(val key: String) {
    System("system"),
    Light("light"),
    Dark("dark");

    companion object {
        fun fromKey(key: String?): ThemePreference = entries.firstOrNull { it.key == key } ?: System
    }
}

enum class DisplayDensity(val key: String) {
    Comfortable("comfortable"),
    Compact("compact");

    companion object {
        fun fromKey(key: String?): DisplayDensity = entries.firstOrNull { it.key == key } ?: Comfortable
    }
}

data class SettingsPreferences(
    val theme: ThemePreference = ThemePreference.System,
    val density: DisplayDensity = DisplayDensity.Comfortable,
    val fontFamily: String = "",
) {
    fun normalizedFontFamilyOrNull(): String? = fontFamily.trim().ifBlank { null }

    fun toSettingsMap(): Map<String, String> = mapOf(
        "appearance.theme" to theme.key,
        "appearance.density" to density.key,
        "appearance.fontFamily" to fontFamily.trim(),
    )

    companion object {
        fun fromSettings(settings: Map<String, String>): SettingsPreferences = SettingsPreferences(
            theme = ThemePreference.fromKey(settings["appearance.theme"]),
            density = DisplayDensity.fromKey(settings["appearance.density"]),
            fontFamily = settings["appearance.fontFamily"].orEmpty(),
        )
    }
}
```

- [ ] **Step 4: Extend `SettingsViewModel`**

Add:

```kotlin
private val _preferences = MutableStateFlow(SettingsPreferences())
val preferences: StateFlow<SettingsPreferences> = _preferences

fun setThemePreference(theme: ThemePreference) {
    _preferences.value = _preferences.value.copy(theme = theme)
    saveAppearancePreferences()
}

fun setDisplayDensity(density: DisplayDensity) {
    _preferences.value = _preferences.value.copy(density = density)
    saveAppearancePreferences()
}

fun setFontFamily(value: String) {
    _preferences.value = _preferences.value.copy(fontFamily = value)
    saveAppearancePreferences()
}

private fun saveAppearancePreferences() {
    if (!formLoaded) return
    val prefs = _preferences.value
    viewModelScope.launch {
        prefs.toSettingsMap().forEach { (key, value) ->
            repository.setSetting(key, value).onLeft { }
        }
    }
}
```

In `init`, after `val settings = ...`, set:

```kotlin
_preferences.value = SettingsPreferences.fromSettings(settings)
```

This makes font family input live at the ViewModel level and persistent through the existing settings table.

- [ ] **Step 5: Make theme consume preferences**

Change `RemindersTheme` signature:

```kotlin
@Composable
fun RemindersTheme(
    preferences: SettingsPreferences = SettingsPreferences(),
    content: @Composable () -> Unit,
)
```

Resolve theme:

```kotlin
val dark = when (preferences.theme) {
    ThemePreference.System -> isSystemInDarkTheme()
    ThemePreference.Light -> false
    ThemePreference.Dark -> true
}
val colors = if (dark) DarkRemColors else LightRemColors
```

Add `LocalRemFontFamily`:

```kotlin
val LocalRemFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Default }
```

Resolve the user-entered font family:

```kotlin
val family = preferences.normalizedFontFamilyOrNull()?.let { FontFamily(it) } ?: FontFamily.Default
```

Then provide:

```kotlin
CompositionLocalProvider(
    LocalRemColors provides colors,
    LocalRemFontFamily provides family,
) {
    content()
}
```

Update `RemType` styles in `DesignTokens.kt` to use `LocalRemFontFamily.current` through a composable helper:

```kotlin
@Composable
fun remTextStyle(base: TextStyle): TextStyle =
    base.copy(fontFamily = LocalRemFontFamily.current)
```

Use this helper only in UI call sites introduced by this milestone, then migrate all typography in Milestone 8 cleanup. This keeps the first pass small and compilable.

- [ ] **Step 6: Wire App root**

In `App.kt`, collect `settingsViewModel.preferences` before `RemindersTheme` and call:

```kotlin
RemindersTheme(preferences = preferences) {
    ...
}
```

Expected behavior: typing a font family in settings updates the state flow and recomposes the app theme immediately.

- [ ] **Step 7: Run GREEN and compile**

Run:

```bash
./gradlew :shared:desktopTest --tests "com.myapplication.shared.ui.settings.SettingsPreferencesTest"
./gradlew :shared:compileKotlinDesktop
./gradlew :desktopApp:compileKotlinJvm
```

Expected: PASS.

### Task 6.4: Appearance page becomes a real settings screen

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/AppearanceSettingsScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/SettingsHome.kt`

- [ ] **Step 1: Create appearance screen**

Create a scrollable screen with:

```text
Header: 外观 + "调整 Sundial 的显示方式"
Theme section: System / Light / Dark segmented rows
Density section: Comfortable / Compact rows
Font section: Font family text input + live preview
Preview section: sample task row title, note, badge using current font family
```

The font field:

```kotlin
RemTextField(
    value = preferences.fontFamily,
    onValueChange = vm::setFontFamily,
    placeholder = "系统默认，或输入 Avenir Next / SF Pro Text / Inter",
    leadingIcon = IconName.Settings,
    modifier = Modifier.fillMaxWidth(),
)
```

The preview text must use the theme-provided font:

```kotlin
BasicText(
    "今天 9:00 · 完成一件重要待办",
    style = remTextStyle(RemType.text14).copy(color = colors.textHigh),
)
```

- [ ] **Step 2: Wire settings branch**

In `SettingsHome.kt`, replace:

```kotlin
SettingsSection.Appearance -> PlaceholderSection(...)
```

with:

```kotlin
SettingsSection.Appearance -> AppearanceSettingsScreen(vm)
```

- [ ] **Step 3: Search for placeholder branch**

Run:

```bash
rg -n "SettingsSection.Appearance -> PlaceholderSection|外观会在这里|主题、显示密度" shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings
```

Expected: no matches for placeholder appearance copy.

- [ ] **Step 4: Compile**

Run:

```bash
./gradlew :shared:compileKotlinDesktop
```

Expected: PASS.

### Task 6.5: Data page becomes a real settings screen

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/DataSettingsScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/SettingsHome.kt`

- [ ] **Step 1: Create data settings screen**

Create a scrollable screen with:

```text
Header: 数据 + "本地优先、备份和维护"
Storage section: local database location facts if available, local-first explanation
Maintenance section: trash count, completed count, pending sync count
Export section: disabled export JSON button with clear "即将推出" badge if export is not implemented
Backup section: disabled backup/restore buttons with disabled state, not placeholder prose
```

Use real counts already available through `MainViewModel`:

```kotlin
val completedCount by mainVm.completedCount.collectAsState()
val trashCount by mainVm.trashCount.collectAsState()
```

Use sync status from `SettingsViewModel`:

```kotlin
val syncStatus by vm.syncStatus.collectAsState()
```

- [ ] **Step 2: Wire settings branch**

Replace:

```kotlin
SettingsSection.Data -> PlaceholderSection(...)
```

with:

```kotlin
SettingsSection.Data -> DataSettingsScreen(vm, mainVm)
```

- [ ] **Step 3: Compile**

Run:

```bash
./gradlew :shared:compileKotlinDesktop
```

Expected: PASS.

### Task 6.6: About page absorbs useful widget information

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/AboutSettingsScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/SettingsHome.kt`
- Delete: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/WidgetSettingsScreen.kt`

- [ ] **Step 1: Create about screen**

Create a scrollable screen with:

```text
Header: 关于 Sundial
Version card: AppInfo.VERSION, AppInfo.LICENSE, AppInfo.DESCRIPTION
Capability card: local-first, Supabase sync, Android widget, macOS WidgetKit planned
Widget facts: Android widget supported, snapshot cache supported, launch routing supported, macOS WidgetKit planned
Open source card: Apache-2.0
```

Move useful static facts from `WidgetSettingsScreen.kt` into this screen as `AboutCapabilityFact`.

- [ ] **Step 2: Wire settings branch**

Replace:

```kotlin
SettingsSection.About -> PlaceholderSection(...)
```

with:

```kotlin
SettingsSection.About -> AboutSettingsScreen()
```

- [ ] **Step 3: Delete widget file**

Run:

```bash
git rm shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/WidgetSettingsScreen.kt
```

- [ ] **Step 4: Search for removed widget settings references**

Run:

```bash
rg -n "SettingsSection.Widgets|WidgetSettingsScreen|小组件" shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings
```

Expected: no `SettingsSection.Widgets` and no `WidgetSettingsScreen`. The word `小组件` may appear only inside About capability facts.

- [ ] **Step 5: Compile**

Run:

```bash
./gradlew :shared:compileKotlinDesktop
```

Expected: PASS.

### Task 6.7: Remove settings placeholders completely

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/SettingsHome.kt`
- Inspect: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/*.kt`

- [ ] **Step 1: Delete `PlaceholderSection`**

Remove the `PlaceholderSection` composable from `SettingsHome.kt`.

- [ ] **Step 2: Search for placeholder code**

Run:

```bash
rg -n "PlaceholderSection|会在这里|逐步开放|占位|即将开放" shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings
```

Expected: no placeholder settings page copy remains. Disabled future actions are allowed only when they are explicit controls with disabled state and an `即将推出` badge.

- [ ] **Step 3: Compile**

Run:

```bash
./gradlew :shared:compileKotlinDesktop
```

Expected: PASS.

### Task 6.8: List CRUD becomes a complete settings surface

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/ListSettingsScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/list/ListEditorDialog.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/list/DeleteListDialog.kt`
- Test: `shared/src/commonTest/kotlin/com/myapplication/shared/ui/settings/ListSettingsSelectionTest.kt`

- [ ] **Step 1: Verify list behavior tests**

Run:

```bash
./gradlew :shared:desktopTest --tests "com.myapplication.shared.ui.settings.ListSettingsSelectionTest"
```

Expected: PASS before visual refactor.

- [ ] **Step 2: Refactor visual layout**

List settings screen has:

```text
Header: Lists title + create button
Left/Top list index: list rows with color dot, count, selected state
Main panel: selected list details, analytics mini-panel, edit/delete actions
Delete dialog: explicit policy choice
```

- [ ] **Step 3: Compile**

Run:

```bash
./gradlew :shared:compileKotlinDesktop
```

Expected: PASS.

---

## Milestone 7: Analytics Screen Refactor

### Task 7.1: Normalize analytics chart panels

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/analytics/AnalyticsScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/analytics/charts/AnalyticsChartTheme.kt`
- Test: `shared/src/commonTest/kotlin/com/myapplication/shared/ui/analytics/AnalyticsChartToneTest.kt`

- [ ] **Step 1: Run existing chart tests**

Run:

```bash
./gradlew :shared:desktopTest --tests "com.myapplication.shared.ui.analytics.AnalyticsChartToneTest"
```

Expected: PASS before visual refactor.

- [ ] **Step 2: Apply shared surfaces**

Each chart panel uses `SundialSurface(tone = SundialSurfaceTone.Panel, border = true)`.

Chart page uses:

```text
Header: 分析 + date range/context
KPI strip: completion, overdue, output, streak
Charts: completion trend, pressure distribution, energy output
List breakdown: compact table or ranked rows
```

- [ ] **Step 3: Compile**

Run:

```bash
./gradlew :shared:compileKotlinDesktop
```

Expected: PASS.

---

## Milestone 8: Legacy Cleanup and Visual QA

### Task 8.1: Remove legacy narrow shell from app path

**Files:**
- Inspect: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/narrow/NarrowShell.kt`
- Inspect: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/**/*.kt`

- [ ] **Step 1: Confirm references**

Run:

```bash
rg -n "NarrowShell|NarrowBottomNav|MobileWorkbenchFilters" shared/src/commonMain/kotlin
```

Expected: `MobileWorkbenchFilters` has no references. `NarrowShell` is either unreferenced or clearly legacy.

- [ ] **Step 2: Delete or mark legacy**

If `NarrowShell.kt` is unreferenced, delete it. If previews or platform entry points still reference it, replace those references with `MobileShell`.

- [ ] **Step 3: Compile**

Run:

```bash
./gradlew :shared:compileKotlinDesktop
./gradlew :desktopApp:compileKotlinJvm
```

Expected: PASS.

### Task 8.2: Run desktop app and capture visual states

**Files:**
- No source changes unless visual bugs are found.

- [ ] **Step 1: Run desktop app**

Run:

```bash
./gradlew :desktopApp:run
```

Expected: app opens.

- [ ] **Step 2: Manually inspect states**

Inspect:

- Workbench desktop
- Workbench with detail inspector
- Lists desktop
- Analytics desktop
- Settings desktop
- Settings appearance page with live font-family input
- Settings data page
- Settings about page with widget capabilities
- Dark theme if available

Check:

- no overlapping text
- no clipped task detail title
- sidebar hierarchy clear
- inspector width stable
- timeline summary rail and legend visible
- list rows use full scroll area
- settings has no standalone widget destination
- settings has no placeholder pages
- entering a font family in Appearance changes the preview immediately and persists after app restart

### Task 8.3: Run Android compile checks

**Files:**
- No source changes unless compile fails.

- [ ] **Step 1: Compile Android debug**

Run:

```bash
./gradlew :androidApp:compileDebugKotlinAndroid
```

Expected: PASS.

- [ ] **Step 2: Build Android debug APK**

Run:

```bash
./gradlew :androidApp:assembleDebug
```

Expected: PASS.

### Task 8.4: Final verification suite

**Files:**
- No source changes unless verification fails.

- [ ] **Step 1: Run core tests**

Run:

```bash
./gradlew :shared:desktopTest
```

Expected: PASS.

- [ ] **Step 2: Run app compiles**

Run:

```bash
./gradlew :shared:compileKotlinDesktop
./gradlew :desktopApp:compileKotlinJvm
./gradlew :androidApp:compileDebugKotlinAndroid
```

Expected: PASS.

- [ ] **Step 3: Search for banned duplicate UI**

Run:

```bash
rg -n 'MobileWorkbenchFilters|RemButton\\("返回"|SettingsSection.Widgets|WidgetSettingsScreen|PlaceholderSection|会在这里|逐步开放|占位|工作台时间线|今天时间线|计划时间线|完成回顾|垃圾箱摘要' shared/src/commonMain/kotlin/com/myapplication/shared/ui
```

Expected:

- No `MobileWorkbenchFilters`.
- No `RemButton("返回"`.
- No `SettingsSection.Widgets`.
- No `WidgetSettingsScreen`.
- No `PlaceholderSection` in settings.
- Timeline titles may remain only if they appear in data model or final copy intentionally; visible duplicate badge strips must not return.

---

## Rollout Strategy

Implement in this order:

1. Milestone 0 and 1: pure semantics and tests.
2. Milestone 2: tokens and primitives.
3. Milestone 3: ledger and accordion.
4. Milestone 4: desktop/mobile shell.
5. Milestone 5: task detail.
6. Milestone 6: settings preferences, appearance, data, about, and list management.
7. Milestone 7: analytics.
8. Milestone 8: cleanup and QA.

Commit after each milestone with messages:

```bash
git add docs/superpowers/plans/2026-08-13-sundial-full-ui-refactor.md
git commit -m "docs: plan full sundial ui refactor"

git add shared/src/commonMain/kotlin/com/myapplication/shared/ui shared/src/commonTest/kotlin/com/myapplication/shared/ui
git commit -m "refactor: define sundial design semantics"

git add shared/src/commonMain/kotlin/com/myapplication/shared/ui shared/src/commonTest/kotlin/com/myapplication/shared/ui
git commit -m "refactor: introduce sundial ui primitives"

git add shared/src/commonMain/kotlin/com/myapplication/shared/ui shared/src/commonTest/kotlin/com/myapplication/shared/ui
git commit -m "refactor: unify ledger accordion layout"

git add shared/src/commonMain/kotlin/com/myapplication/shared/ui
git commit -m "refactor: unify desktop and mobile shells"

git add shared/src/commonMain/kotlin/com/myapplication/shared/ui/detail shared/src/commonTest/kotlin/com/myapplication/shared/ui/detail
git commit -m "refactor: rebuild task detail layout"

git add shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings shared/src/commonMain/kotlin/com/myapplication/shared/ui/list shared/src/commonTest/kotlin/com/myapplication/shared/ui/settings shared/src/commonMain/kotlin/com/myapplication/shared/ui/theme shared/src/commonMain/kotlin/App.kt
git commit -m "refactor: rebuild settings and appearance preferences"

git add shared/src/commonMain/kotlin/com/myapplication/shared/ui/analytics shared/src/commonTest/kotlin/com/myapplication/shared/ui/analytics
git commit -m "refactor: align analytics with sundial design system"
```

## Self-Review

Spec coverage:

- Unified shadcn-inspired design system: covered by Milestones 1 and 2.
- Navigation/IA cleanup: covered by Milestones 1 and 4.
- Accordion-ledger workbench logic: covered by Milestone 3.
- Desktop and mobile coherent shells: covered by Milestone 4.
- Detail title readability and subtask editing: covered by Milestone 5.
- Settings/list CRUD and list analysis surface: covered by Milestone 6.
- Settings removes standalone widget destination and absorbs useful widget facts into About: covered by Milestone 6.
- Settings removes placeholder pages by replacing Data, Appearance, and About with real screens: covered by Milestone 6.
- Appearance settings support theme, density, and user-entered font family with live preview/persistence: covered by Milestone 6.
- Analytics visual consistency: covered by Milestone 7.
- Visual QA and legacy cleanup: covered by Milestone 8.

Placeholder scan:

- No `TBD`.
- No `TODO`.
- No vague “add appropriate handling” steps.

Type consistency:

- `SundialDestination`, `SundialNavItem`, `SundialLensItem`, `SundialLayout`, and `SundialDensity` are defined before use.
- Verification commands match existing Gradle task names observed in the project.
