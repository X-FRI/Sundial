# Sundial Pro Value Features Milestone 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users see today's work rhythm from the desktop or home screen without opening the full app.

**Architecture:** Keep widget data generation in shared pure domain code, persist the latest snapshot at the Android platform boundary, and render responsive Glance layouts from one stable view model. macOS WidgetKit is documented as a technical plan in this milestone because the Compose Desktop JVM app cannot directly provide a system widget.

**Tech Stack:** Kotlin Multiplatform, SQLDelight repository flows, Android Glance AppWidget 1.1.1, kotlinx.serialization JSON, existing Compose settings center.

---

## Constraints

- Work directly on `main`. Do not create a branch or worktree.
- Commit after every task.
- Keep Android platform code under `androidApp/src/androidMain/kotlin/com/myapplication/widget`.
- Keep shared pure widget models under `shared/src/commonMain/kotlin/com/myapplication/shared/domain/widget`.
- Do not implement a macOS WidgetKit extension in this milestone; write the technical plan and data contract.
- Preserve existing app behavior when launched normally.

## File Map

- `shared/src/commonMain/kotlin/com/myapplication/shared/domain/widget/TodayWidgetSnapshot.kt`
  - Pure snapshot model, counts, task selection, size policy.
- `shared/src/commonTest/kotlin/com/myapplication/shared/domain/widget/TodayWidgetSnapshotTest.kt`
  - Pure snapshot tests.
- `androidApp/src/androidMain/kotlin/com/myapplication/widget/WidgetSnapshotCache.kt`
  - Android JSON cache in app files directory.
- `androidApp/src/androidTest/kotlin/com/myapplication/widget/WidgetSnapshotCacheTest.kt`
  - Optional Android instrumentation test only if the project already has Android test plumbing; otherwise skip and use compile verification.
- `androidApp/src/androidMain/kotlin/com/myapplication/widget/TodayWidget.kt`
  - Glance size-aware rendering and cache fallback.
- `androidApp/src/androidMain/res/xml/today_widget_info.xml`
  - Widget sizing and resize metadata.
- `androidApp/src/androidMain/kotlin/com/myapplication/MainActivity.kt`
  - Intent extra handling entry point.
- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/main/MainViewModel.kt`
  - Add an explicit launch-scope command for widget intents.
- `shared/src/commonTest/kotlin/com/myapplication/shared/ui/main/MainViewModelTest.kt`
  - Verify widget launch scope behavior.
- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/WidgetSettingsScreen.kt`
  - Product-facing widget status and macOS roadmap surface.
- `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/SettingsHome.kt`
  - Wire the widget settings screen.
- `docs/widgetkit-macos-plan.md`
  - macOS WidgetKit extension plan and shared snapshot JSON contract.
- `androidApp/build.gradle.kts`, `desktopApp/build.gradle.kts`, `shared/src/commonMain/kotlin/com/myapplication/shared/AppInfo.kt`
  - Version bump after final verification.

---

## Task 1: Shared Widget Snapshot Model

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/widget/TodayWidgetSnapshot.kt`
- Create: `shared/src/commonTest/kotlin/com/myapplication/shared/domain/widget/TodayWidgetSnapshotTest.kt`

- [ ] **Step 1: Add failing tests for richer widget snapshot**

Create `shared/src/commonTest/kotlin/com/myapplication/shared/domain/widget/TodayWidgetSnapshotTest.kt` with tests for:

```kotlin
package com.myapplication.shared.domain.widget

import com.myapplication.shared.domain.model.TodoItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

class TodayWidgetSnapshotTest {
    private val zone = TimeZone.UTC
    private val now = Instant.parse("2026-08-13T08:00:00Z")

    @Test
    fun snapshotSeparatesTodayOverdueAndInboxWork() {
        val snapshot = buildTodayWidgetSnapshot(
            todos = listOf(
                todo(1, "今天 9 点", due = "2026-08-13T09:00:00Z", listId = 10),
                todo(2, "逾期", due = "2026-08-12T10:00:00Z", listId = 10),
                todo(3, "待整理", due = null, listId = 99),
                todo(4, "已完成", due = "2026-08-13T07:00:00Z", completedAt = "2026-08-13T07:30:00Z", listId = 10),
            ),
            now = now,
            timeZone = zone,
            inboxListId = 99,
            maxTasks = 5,
        )

        assertEquals(1, snapshot.pendingTodayCount)
        assertEquals(1, snapshot.overdueCount)
        assertEquals(1, snapshot.inboxCount)
        assertEquals(1, snapshot.completedTodayCount)
        assertEquals(listOf("今天 9 点"), snapshot.topTodayTasks.map { it.title })
        assertEquals(listOf("逾期"), snapshot.topOverdueTasks.map { it.title })
    }

    @Test
    fun widgetSizePolicyControlsVisibleTaskCounts() {
        assertEquals(1, WidgetSnapshotSize.Small.maxTodayTasks)
        assertEquals(3, WidgetSnapshotSize.Medium.maxTodayTasks)
        assertEquals(6, WidgetSnapshotSize.Large.maxTodayTasks)
    }

    private fun todo(
        id: Long,
        title: String,
        due: String?,
        listId: Long,
        completedAt: String? = null,
    ): TodoItem = TodoItem(
        id = id,
        listId = listId,
        title = title,
        note = "",
        dueDate = due?.let(Instant::parse),
        isCompleted = completedAt != null,
        completedAt = completedAt?.let(Instant::parse),
        isTrashed = false,
        trashedAt = null,
        parentId = null,
        sortPosition = id.toDouble(),
        flag = id == 1L,
        createdAt = Instant.parse("2026-08-01T00:00:00Z"),
    )
}
```

- [ ] **Step 2: Run test and verify failure**

```bash
./gradlew :shared:desktopTest --tests com.myapplication.shared.domain.widget.TodayWidgetSnapshotTest --no-daemon --console=plain
```

Expected: FAIL because `WidgetSnapshotSize` and `topOverdueTasks` do not exist.

- [ ] **Step 3: Extend the snapshot model**

Modify `TodayWidgetSnapshot.kt`:

```kotlin
@Serializable
data class WidgetTask(...)

@Serializable
data class TodayWidgetSnapshot(
    val dateLabel: String,
    val pendingTodayCount: Int,
    val completedTodayCount: Int,
    val nextTaskTitle: String?,
    val nextTaskDueLabel: String?,
    val topTodayTasks: List<WidgetTask>,
    val topOverdueTasks: List<WidgetTask>,
    val overdueCount: Int,
    val inboxCount: Int,
    val lastUpdatedAt: Instant,
)

enum class WidgetSnapshotSize(val maxTodayTasks: Int, val maxOverdueTasks: Int) {
    Small(maxTodayTasks = 1, maxOverdueTasks = 0),
    Medium(maxTodayTasks = 3, maxOverdueTasks = 1),
    Large(maxTodayTasks = 6, maxOverdueTasks = 3),
}
```

In `buildTodayWidgetSnapshot`, compute:

```kotlin
val overdue = activeParents
    .filter { it.dueDate?.toLocalDateTime(timeZone)?.date?.let { due -> due < today } == true }
    .sortedWith(compareBy<TodoItem> { it.dueDate }.thenBy { it.sortPosition }.thenBy { it.id })
```

Map both `todayPending` and `overdue` through the same private `TodoItem.toWidgetTask(timeZone)` helper. Preserve `maxTasks` as the public parameter and use it for both `topTodayTasks` and `topOverdueTasks` in this task.

- [ ] **Step 4: Run tests**

```bash
./gradlew :shared:desktopTest --tests com.myapplication.shared.domain.widget.TodayWidgetSnapshotTest --no-daemon --console=plain
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/domain/widget/TodayWidgetSnapshot.kt shared/src/commonTest/kotlin/com/myapplication/shared/domain/widget/TodayWidgetSnapshotTest.kt
git commit -F - <<'EOF'
feat(widget): enrich today snapshot model

[Change Nature]
- This commit expands the shared widget snapshot.

[New Capability]
- Widgets can show today, overdue, inbox, and completed summaries.

[Implementation]
- Add serializable widget snapshot models and size policy.
- Include overdue task previews alongside today task previews.

[Impact]
- Android and future macOS widgets can share one stable snapshot contract.
EOF
```

---

## Task 2: Android Snapshot Cache

**Files:**
- Create: `androidApp/src/androidMain/kotlin/com/myapplication/widget/WidgetSnapshotCache.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/myapplication/widget/TodayWidget.kt`

- [ ] **Step 1: Create Android cache helper**

Create `WidgetSnapshotCache.kt`:

```kotlin
package com.myapplication.widget

import android.content.Context
import com.myapplication.shared.domain.widget.TodayWidgetSnapshot
import java.io.File
import kotlinx.serialization.json.Json

internal class WidgetSnapshotCache(
    private val context: Context,
    private val json: Json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
    },
) {
    private val file: File get() = File(context.filesDir, "sundial-widget/today-widget-snapshot.json")

    fun read(): TodayWidgetSnapshot? =
        runCatching {
            if (!file.exists()) return null
            json.decodeFromString<TodayWidgetSnapshot>(file.readText())
        }.getOrNull()

    fun write(snapshot: TodayWidgetSnapshot) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(snapshot))
    }
}
```

- [ ] **Step 2: Use cache in widget loading**

In `TodayWidget.loadSnapshot(context)`, after building a fresh snapshot:

```kotlin
val fresh = buildTodayWidgetSnapshot(...)
WidgetSnapshotCache(context).write(fresh)
return fresh
```

Wrap repository loading with fallback:

```kotlin
return runCatching { loadFreshSnapshot(context) }
    .getOrElse { WidgetSnapshotCache(context).read() ?: TodayWidgetSnapshot.empty(graph.clock.now()) }
```

Add `TodayWidgetSnapshot.empty(now: Instant)` companion or top-level factory in Task 1 model if needed. The empty snapshot must say zero counts and no tasks.

- [ ] **Step 3: Compile Android**

```bash
./gradlew :androidApp:assembleDebug --no-daemon --console=plain
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/myapplication/widget/WidgetSnapshotCache.kt androidApp/src/androidMain/kotlin/com/myapplication/widget/TodayWidget.kt shared/src/commonMain/kotlin/com/myapplication/shared/domain/widget/TodayWidgetSnapshot.kt
git commit -F - <<'EOF'
feat(widget): cache today snapshots on Android

[Change Nature]
- This commit adds a local widget snapshot cache.

[New Capability]
- Android widgets can fall back to the last successful snapshot.

[Implementation]
- Persist the shared serializable snapshot to app-local JSON.
- Read cached data when fresh repository loading fails.

[Impact]
- Home screen widgets are more resilient during startup or transient DB failures.
EOF
```

---

## Task 3: Responsive Android Glance Widget

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/myapplication/widget/TodayWidget.kt`
- Modify: `androidApp/src/androidMain/res/xml/today_widget_info.xml`

- [ ] **Step 1: Add responsive size mode**

In `TodayWidget`, add:

```kotlin
override val sizeMode: SizeMode = SizeMode.Responsive(
    setOf(
        DpSize(160.dp, 96.dp),
        DpSize(260.dp, 140.dp),
        DpSize(320.dp, 220.dp),
    ),
)
```

Use imports:

```kotlin
import androidx.compose.ui.unit.DpSize
import androidx.glance.LocalSize
import androidx.glance.appwidget.SizeMode
```

- [ ] **Step 2: Split small, medium, large rendering**

Inside `provideContent`:

```kotlin
val size = LocalSize.current
val widgetSize = when {
    size.width < 220.dp || size.height < 120.dp -> WidgetSnapshotSize.Small
    size.height < 190.dp -> WidgetSnapshotSize.Medium
    else -> WidgetSnapshotSize.Large
}
TodayWidgetContent(snapshot = snapshot, size = widgetSize)
```

Render:

- Small: title row, next task, compact footer counts.
- Medium: title row, next task, up to 3 today tasks, footer counts.
- Large: summary chips, up to 6 today tasks, overdue preview, footer timestamp.

Use Glance `Text`, `Row`, `Column`, `Spacer`, and color tokens local to the file. Do not use Compose UI components inside Glance.

- [ ] **Step 3: Update provider XML**

In `today_widget_info.xml`, keep resize mode and set:

```xml
android:minWidth="120dp"
android:minHeight="80dp"
android:minResizeWidth="120dp"
android:minResizeHeight="80dp"
android:targetCellWidth="2"
android:targetCellHeight="2"
android:updatePeriodMillis="1800000"
```

- [ ] **Step 4: Run Android build**

```bash
./gradlew :androidApp:assembleDebug --no-daemon --console=plain
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/myapplication/widget/TodayWidget.kt androidApp/src/androidMain/res/xml/today_widget_info.xml
git commit -F - <<'EOF'
feat(widget): add responsive Android layouts

[Change Nature]
- This commit upgrades the Android home screen widget layout.

[New Capability]
- Small, medium, and large widget sizes show progressively richer today summaries.

[Implementation]
- Use Glance responsive SizeMode and LocalSize.
- Render today tasks, overdue preview, and summary counts by size.

[Impact]
- Users can keep Sundial visible on the home screen without opening the app.
EOF
```

---

## Task 4: Widget Launch Routing

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/myapplication/widget/TodayWidget.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/myapplication/MainActivity.kt`
- Modify: `shared/src/commonMain/kotlin/App.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/main/MainViewModel.kt`
- Test: `shared/src/commonTest/kotlin/com/myapplication/shared/ui/main/MainViewModelTest.kt`

- [ ] **Step 1: Add launch target model**

In `MainViewModel.kt`, add:

```kotlin
enum class LaunchTarget {
    Workbench,
    Today,
}

fun applyLaunchTarget(target: LaunchTarget) {
    when (target) {
        LaunchTarget.Workbench -> selectScope(Scope.All)
        LaunchTarget.Today -> selectScope(Scope.Today)
    }
}
```

- [ ] **Step 2: Add test**

Append to `MainViewModelTest.kt`:

```kotlin
@Test
fun widgetLaunchTargetOpensTodayScope() = runTest {
    val vm = mainViewModel()
    vm.selectScope(Scope.All)

    vm.applyLaunchTarget(LaunchTarget.Today)

    assertEquals(Scope.Today, vm.scope.value)
    assertEquals(Route.Main, vm.route.value)
}
```

Adjust helper names to the existing test fixture in `MainViewModelTest.kt`.

- [ ] **Step 3: Thread Android intent into Compose root**

In `MainActivity.kt`, add:

```kotlin
private fun launchTargetFromIntent(): String? = intent?.getStringExtra(EXTRA_SUNDIAL_TARGET)

companion object {
    const val EXTRA_SUNDIAL_TARGET = "com.myapplication.extra.SUNDIAL_TARGET"
    const val TARGET_WORKBENCH = "workbench"
    const val TARGET_TODAY = "today"
}
```

Pass the value to `MainView(launchTarget = launchTargetFromIntent())`. If `MainView` currently has no parameter, add:

```kotlin
@Composable
fun MainView(launchTarget: String? = null)
```

In `App.kt`, after creating `mainVm`, run:

```kotlin
LaunchedEffect(launchTarget) {
    when (launchTarget) {
        "today" -> mainVm.applyLaunchTarget(LaunchTarget.Today)
        "workbench" -> mainVm.applyLaunchTarget(LaunchTarget.Workbench)
    }
}
```

- [ ] **Step 4: Set widget click targets**

In `TodayWidget.kt`, use explicit intents:

```kotlin
private fun launchIntent(context: Context, target: String): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_VIEW)
        .putExtra(MainActivity.EXTRA_SUNDIAL_TARGET, target)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
```

Use:

```kotlin
.clickable(actionStartActivity(launchIntent(context, MainActivity.TARGET_TODAY)))
```

Root/empty space should open workbench or today according to the UI section:

- root card: workbench
- today task rows: today
- overdue summary: workbench

- [ ] **Step 5: Run tests and build**

```bash
./gradlew :shared:desktopTest --tests com.myapplication.shared.ui.main.MainViewModelTest :androidApp:assembleDebug --no-daemon --console=plain
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add androidApp/src/androidMain/kotlin/com/myapplication/widget/TodayWidget.kt androidApp/src/androidMain/kotlin/com/myapplication/MainActivity.kt shared/src/commonMain/kotlin/App.kt shared/src/commonMain/kotlin/com/myapplication/shared/ui/main/MainViewModel.kt shared/src/commonTest/kotlin/com/myapplication/shared/ui/main/MainViewModelTest.kt
git commit -F - <<'EOF'
feat(widget): route widget launches into Sundial

[Change Nature]
- This commit adds explicit app launch targets for widget interactions.

[New Capability]
- Widget taps can open the workbench or today's scope.

[Implementation]
- Add a typed launch target command to MainViewModel.
- Forward Android widget intent extras into the shared app root.

[Impact]
- Home screen widgets become actionable rather than passive summaries.
EOF
```

---

## Task 5: Widget Settings And macOS Plan

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/WidgetSettingsScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/SettingsHome.kt`
- Create: `docs/widgetkit-macos-plan.md`

- [ ] **Step 1: Create widget settings screen**

Create a settings screen that shows:

- Android widget: `已支持 small / medium / large 今日小组件`
- Snapshot cache: `本地缓存最近一次今日摘要`
- Click behavior: `点击小组件打开工作台或今天`
- macOS: `需要 WidgetKit extension，当前为技术方案阶段`

Use existing `SettingsCard`-like visual style if it is private; otherwise build local cards with `colors.surface`, `RemRadii.r4`, and `BasicText`.

- [ ] **Step 2: Wire settings section**

In `SettingsHome.kt`, replace `SettingsSection.Widgets -> PlaceholderSection(...)` with:

```kotlin
SettingsSection.Widgets -> WidgetSettingsScreen()
```

- [ ] **Step 3: Write macOS WidgetKit plan**

Create `docs/widgetkit-macos-plan.md` with these sections:

```markdown
# Sundial macOS WidgetKit Plan

## Goal
Show today's Sundial work from macOS widgets without launching the Compose Desktop app.

## Constraint
Compose Desktop/JVM cannot directly register a native macOS WidgetKit widget. A SwiftUI WidgetKit extension must be added to an Xcode project or native wrapper.

## Shared Snapshot Contract
The extension reads `today-widget-snapshot.json` matching `TodayWidgetSnapshot`.

## Data Channel Options
1. App Group container: preferred for App Store distribution.
2. User application support file: acceptable for direct-distribution prototype.

## Refresh Model
The desktop app writes a fresh snapshot after repository changes and on app foreground. WidgetKit reads the latest JSON during timeline reload.

## Implementation Phases
1. Export desktop snapshot JSON.
2. Add SwiftUI widget target.
3. Add app group entitlement.
4. Implement WidgetKit timeline provider.
5. Add manual reload trigger from desktop app.
```

- [ ] **Step 4: Compile shared and desktop**

```bash
./gradlew :shared:desktopTest :desktopApp:compileKotlinJvm --no-daemon --console=plain
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/WidgetSettingsScreen.kt shared/src/commonMain/kotlin/com/myapplication/shared/ui/settings/SettingsHome.kt docs/widgetkit-macos-plan.md
git commit -F - <<'EOF'
feat(settings): document widget support roadmap

[Change Nature]
- This commit replaces the widget settings placeholder.

[New Capability]
- Users can see Android widget capabilities and the macOS WidgetKit path.

[Implementation]
- Add a dedicated widget settings section.
- Document the macOS WidgetKit data channel and implementation phases.

[Impact]
- Cross-device rhythm work is visible and explainable in product settings.
EOF
```

---

## Task 6: Final Verification And Version Bump

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

If current version is `0.7.0`, bump to `0.8.0`.

In `androidApp/build.gradle.kts`:

```kotlin
versionCode = 9
versionName = "0.8.0"
```

In `desktopApp/build.gradle.kts`:

```kotlin
val displayVersion = "0.8.0"
```

Keep `desktopPackageVersion` unchanged.

In `shared/src/commonMain/kotlin/com/myapplication/shared/AppInfo.kt`:

```kotlin
const val VERSION = "0.8.0"
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
chore(release): bump version to 0.8.0

[Change Nature]
- This commit updates release metadata only.

[Maintenance Work]
- Bump Android, desktop display, and shared app versions to 0.8.0.

[Implementation]
- Keep desktop package version unchanged for jpackage compatibility.
- Re-run shared tests and platform builds after the version update.

[Impact]
- App surfaces report version 0.8.0.
- Runtime behavior is unchanged.
EOF
```

---

## Self-Review

### Spec Coverage

- Android widget today/overdue/inbox/completed summary: Tasks 1, 3.
- Widget small/medium/large sizes: Task 1 size policy and Task 3 responsive Glance UI.
- Click widget opens app to workbench or today: Task 4.
- macOS WidgetKit technical plan and shared data channel: Task 5.
- Widget snapshot local cache: Task 2.
- Final Android/Desktop verification: Task 6.

### Deliberate Non-Goals

- No new branch or worktree.
- No native macOS WidgetKit target implementation in this milestone.
- No background worker scheduler beyond AppWidget provider update period; manual/future proactive updates can be a later milestone.
- No paid features or account system.

### Verification Gates

- Every implementation task commits independently.
- Every task runs the narrow test or compile command listed above.
- Task 6 runs full verification twice around the version bump.
