# Sundial macOS WidgetKit Plan

## Goal

Show today's Sundial work from macOS widgets without launching the Compose Desktop app.

The macOS widget should reuse the same summary surface already used by Android widgets: today's pending count, completed-today count, next task, visible today tasks, overdue count, inbox count, last update time, and a small overdue preview for larger families.

## Constraint

Compose Desktop/JVM cannot directly register a native macOS WidgetKit widget. A native SwiftUI WidgetKit extension must be added through an Xcode project, a native wrapper, or a packaging pipeline that can produce a signed `.appex`.

This milestone only documents the macOS WidgetKit plan and settings roadmap. It does not implement a native WidgetKit extension, Xcode target, app group entitlement, or signed macOS distribution change.

## Shared Snapshot Contract

The extension reads `today-widget-snapshot.json` matching `TodayWidgetSnapshot` from `shared/src/commonMain/kotlin/com/myapplication/shared/domain/widget/TodayWidgetSnapshot.kt`.

JSON encoding should stay compatible with the Android cache:

- `encodeDefaults = true`
- `explicitNulls = true`
- `ignoreUnknownKeys = true`

Current top-level fields:

- `dateLabel: String`
- `pendingTodayCount: Int`
- `completedTodayCount: Int`
- `nextTaskTitle: String?`
- `nextTaskDueLabel: String?`
- `topTodayTasks: List<WidgetTask>`
- `overdueCount: Int`
- `inboxCount: Int`
- `lastUpdatedAt: Instant`
- `topOverdueTasks: List<WidgetTask>`

`WidgetTask` fields:

- `id: Long`
- `title: String`
- `dueLabel: String?`
- `isFlagged: Boolean`

Android currently stores its local snapshot at `sundial-widget/today-widget-snapshot.json` under app files. macOS should keep the same file name and JSON shape, but write it into a macOS-readable shared container chosen by the data channel below.

## Data Channel Options

1. App Group container: preferred for App Store or notarized distribution. The Compose Desktop host writes the JSON into the group container and the WidgetKit extension reads the same file. This needs a native entitlement bridge because plain JVM code cannot claim an app group by itself.
2. User Application Support file: acceptable for a direct-distribution prototype. The desktop app writes `today-widget-snapshot.json` under Sundial's Application Support folder and the extension reads it through a security-compatible native helper or wrapper-owned path.
3. Native helper bridge: useful if the Compose Desktop app cannot reliably access the final container. The JVM app emits the snapshot payload and a tiny native host/helper writes it atomically into the WidgetKit-readable location.

All options should write atomically so WidgetKit never decodes a partially written snapshot. Android uses `AtomicFile`; macOS should use an equivalent write-to-temp-and-rename flow.

## Refresh Model

The desktop app writes a fresh snapshot after repository changes, after app foreground, and before requesting a widget reload. The snapshot builder should call the existing shared `buildTodayWidgetSnapshot(...)` contract with the current clock, timezone, inbox list id, and a max task count large enough for the largest widget family.

WidgetKit reads the latest JSON during timeline reload. The timeline provider should:

- decode the snapshot if it exists and is current for the local date;
- fall back to an empty `TodayWidgetSnapshot` equivalent when the file is missing, stale, or invalid;
- schedule a conservative next reload, plus accept explicit reload requests after the desktop app writes a new snapshot.

Click behavior should mirror Android: the widget body opens Sundial to the workbench, and task or today-focused regions open Sundial to Today.

## Implementation Phases

1. Export desktop snapshot JSON from the Compose Desktop host using `buildTodayWidgetSnapshot(...)` and the current shared JSON contract.
2. Add a native SwiftUI WidgetKit extension target with small, medium, and large widget families.
3. Add the app group entitlement or selected shared-container bridge.
4. Implement a WidgetKit timeline provider that decodes `today-widget-snapshot.json`, validates local-day freshness, and renders fallback content.
5. Add a desktop-to-native reload trigger after snapshot writes.
6. Add packaging, signing, and distribution verification for the extension.
7. Add integration tests or fixture tests that decode the same JSON examples used by shared `TodayWidgetSnapshot` tests.
