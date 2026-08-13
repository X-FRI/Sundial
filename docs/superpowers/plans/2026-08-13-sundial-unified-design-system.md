# Sundial Unified Design System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first shadcn-inspired Sundial design-system pass: unified product navigation semantics, cleaner desktop/mobile shells, consistent timeline summary, and standard back affordance.

**Architecture:** Keep the existing Kotlin Multiplatform + Compose stack. Add a small pure UI design-language module for tested navigation semantics, then map desktop/mobile shells onto it. Do not migrate to React/shadcn; adopt shadcn-like principles in Compose-native primitives.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Compose Material 3 behavior primitives, Kotlin test.

---

### Task 1: Product Navigation Semantics

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/design/SundialDesignLanguage.kt`
- Create: `shared/src/commonTest/kotlin/com/myapplication/shared/ui/design/SundialDesignLanguageTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.myapplication.shared.ui.design

import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.main.Scope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.Instant

class SundialDesignLanguageTest {
    @Test
    fun topLevelDestinationMapsSmartScopesToWorkbench() {
        assertEquals(SundialDestination.Workbench, destinationForScope(Scope.All))
        assertEquals(SundialDestination.Workbench, destinationForScope(Scope.Today))
        assertEquals(SundialDestination.Workbench, destinationForScope(Scope.Scheduled))
        assertEquals(SundialDestination.Workbench, destinationForScope(Scope.Completed))
        assertEquals(SundialDestination.Workbench, destinationForScope(Scope.Trash))
    }

    @Test
    fun topLevelDestinationMapsListAndAnalytics() {
        assertEquals(SundialDestination.Lists, destinationForScope(Scope.List(7)))
        assertEquals(SundialDestination.Analytics, destinationForScope(Scope.Analytics))
    }

    @Test
    fun selectingListsUsesFirstListOrWorkbenchFallback() {
        val lists = listOf(TodoList(9, "收件箱", "blue", 0, Instant.fromEpochMilliseconds(0)))

        assertEquals(Scope.List(9), scopeForDestination(SundialDestination.Lists, lists))
        assertEquals(Scope.All, scopeForDestination(SundialDestination.Lists, emptyList()))
    }

    @Test
    fun primaryDestinationsUseStableLabelsAndIcons() {
        assertEquals(listOf("工作台", "列表", "分析"), sundialPrimaryDestinations().map { it.label })
        assertEquals(listOf(IconName.Layers, IconName.Tray, IconName.Chart), sundialPrimaryDestinations().map { it.icon })
    }
}
```

- [ ] **Step 2: Run test to verify RED**

Run: `./gradlew :shared:desktopTest --tests "com.myapplication.shared.ui.design.SundialDesignLanguageTest"`

Expected: FAIL because `SundialDesignLanguage.kt` does not exist.

- [ ] **Step 3: Implement minimal semantics**

Create `SundialDestination`, `SundialNavItem`, `destinationForScope`, `scopeForDestination`, and `sundialPrimaryDestinations`.

- [ ] **Step 4: Run test to verify GREEN**

Run: `./gradlew :shared:desktopTest --tests "com.myapplication.shared.ui.design.SundialDesignLanguageTest"`

Expected: PASS.

### Task 2: Shared Back Action

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/SundialBackAction.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/SettingsHome.kt`

- [ ] **Step 1: Add component**

Create a compact chevron action that pairs `IconName.ChevronBack` with optional label text.

- [ ] **Step 2: Replace settings text return button**

Replace `RemButton("返回", ...)` in `SettingsHomeHeader` with `SundialBackAction("返回", onBack)`.

### Task 3: Desktop Sidebar Navigation Cleanup

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/sidebar/SidebarNav.kt`

- [ ] **Step 1: Use primary destinations**

Render top-level rows from `sundialPrimaryDestinations()` and `scopeForDestination`.

- [ ] **Step 2: Make secondary Workbench lenses quieter**

Keep smart lenses under `工作台视图`, but use a secondary row variant with smaller type and subdued selected state.

### Task 4: Mobile Shell Cleanup

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/shell/MobileShell.kt`

- [ ] **Step 1: Remove duplicate Workbench filter strip**

When not in a list scope, do not render `MobileWorkbenchFilters`; the ledger Accordion is the Workbench lens UI.

- [ ] **Step 2: Render primary bottom navigation from design semantics**

Use `sundialPrimaryDestinations()`, `destinationForScope`, and `scopeForDestination` to avoid hard-coded duplicate logic.

### Task 5: Timeline Summary Refinement

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/ledger/TodayRhythm.kt`

- [ ] **Step 1: Give summary a rail + legend grammar**

Render distribution rail with the segment legend below it: colored dot, label, count. Use the same pattern in compact and wide summaries.

- [ ] **Step 2: Preserve Today rail**

Keep `showDayRail` behavior for Today scope, but style the compact mobile Today rail consistently.

### Task 6: Verification

**Files:**
- No new files.

- [ ] **Step 1: Run focused tests**

Run: `./gradlew :shared:desktopTest --tests "com.myapplication.shared.ui.design.SundialDesignLanguageTest"`

- [ ] **Step 2: Run existing UI model tests**

Run: `./gradlew :shared:desktopTest --tests "com.myapplication.shared.ui.ledger.LedgerUiModelsTest"`

- [ ] **Step 3: Compile desktop shared code**

Run: `./gradlew :desktopApp:compileKotlinDesktop`
