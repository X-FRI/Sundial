# Arrow Fx Effect Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Refactor Sundial command-side architecture so Todo, DB, UI, and Sync side effects are represented and interpreted through Arrow typed effect handlers, with Arrow Fx used for coroutine lifecycle cleanup.

**Architecture:** Add a small `shared.effects` module for typed `Raise` programs. Route DB commands through `TodoRepositoryImpl.dbCommand`, UI commands through `launchTodoEffect`, and sync commands through `runSyncEffect`/`catchTransport`/`bindLocal`. Keep query streams as `Flow<T>` and keep Compose lifecycle APIs at the UI layer.

**Tech Stack:** Kotlin Multiplatform, Arrow Core 2.2.3, Arrow Fx Coroutines 2.2.3, Kotlin coroutines, SQLDelight, kotlin.test.

---

## Files

- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/effects/SyncEffects.kt`
- Create: `shared/src/commonTest/kotlin/com/myapplication/shared/effects/EffectHandlersTest.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/effects/TodoEffects.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/effects/TodoEffectRunner.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/data/TodoRepositoryImpl.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/domain/sync/SyncCoordinator.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/data/sync/SupabaseSyncClient.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/data/sync/SyncEngine.kt`
- Modify: `shared/build.gradle.kts`
- Modify: `docs/adr/0001-arrow-functional-core.md`

## Task 1: Lock The Effect Handler Contract With Tests

- [x] **Step 1: Write failing tests**

Create `shared/src/commonTest/kotlin/com/myapplication/shared/effects/EffectHandlersTest.kt`:

```kotlin
package com.myapplication.shared.effects

import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.sync.SyncError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EffectHandlersTest {
    @Test
    fun todoEffectMapsUnexpectedExceptionToPersistenceError() = runTest {
        val result = runTodoEffect {
            catchPersistence("fallback") { error("boom") }
        }

        assertEquals(TodoError.Persistence("boom"), result.leftOrNull())
    }

    @Test
    fun todoEffectRethrowsCancellation() = runTest {
        assertFailsWith<CancellationException> {
            runTodoEffect {
                catchPersistence("fallback") { throw CancellationException("cancel") }
            }
        }
    }

    @Test
    fun syncEffectMapsUnexpectedExceptionToTransportError() = runTest {
        val result = runSyncEffect {
            catchTransport("fallback") { error("network boom") }
        }

        assertEquals(SyncError.Transport("network boom"), result.leftOrNull())
    }

    @Test
    fun syncEffectMapsLocalTodoErrorToTransportError() = runTest {
        val result = runSyncEffect {
            bindLocal(arrow.core.Either.Left(TodoError.Persistence("db down")))
        }

        assertEquals(SyncError.Transport("db down"), result.leftOrNull())
    }

    @Test
    fun syncEffectRethrowsCancellation() = runTest {
        assertFailsWith<CancellationException> {
            runSyncEffect {
                catchTransport("fallback") { throw CancellationException("cancel") }
            }
        }
    }

    @Test
    fun syncEffectCanRaiseTypedErrorDirectly() = runTest {
        val result = runSyncEffect<String> {
            raise(SyncError.Transport("typed"))
        }

        assertTrue(result.isLeft())
        assertEquals(SyncError.Transport("typed"), result.leftOrNull())
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :shared:desktopTest --tests com.myapplication.shared.effects.EffectHandlersTest --no-daemon --console=plain
```

Expected: fails because `runSyncEffect`, `catchTransport`, and `bindLocal` do not exist.

- [x] **Step 3: Implement sync effect helpers**

Create `shared/src/commonMain/kotlin/com/myapplication/shared/effects/SyncEffects.kt`:

```kotlin
package com.myapplication.shared.effects

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.sync.SyncError
import kotlinx.coroutines.CancellationException

typealias SyncEffect<A> = suspend Raise<SyncError>.() -> A

suspend fun <A> runSyncEffect(effect: SyncEffect<A>): Either<SyncError, A> =
    either { effect() }

suspend fun <A> Raise<SyncError>.catchTransport(
    fallbackMessage: String,
    block: suspend () -> A,
): A =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        raise(SyncError.Transport(e.message ?: fallbackMessage))
    }

fun TodoError.toSyncError(): SyncError =
    SyncError.Transport((this as? TodoError.Persistence)?.message ?: "本地读取失败")

fun <A> Raise<SyncError>.bindLocal(effect: Either<TodoError, A>): A =
    effect.mapLeft { it.toSyncError() }.bind()
```

- [x] **Step 4: Run test to verify it passes**

Run:

```bash
./gradlew :shared:desktopTest --tests com.myapplication.shared.effects.EffectHandlersTest --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL`.

## Task 2: Refactor SyncCoordinator To SyncEffect

- [x] **Step 1: Verify current sync tests pass before refactor**

Run:

```bash
./gradlew :shared:desktopTest --tests com.myapplication.shared.domain.sync.SyncCoordinatorTest --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 2: Replace local `either {}` and JSON `try/catch` with sync effects**

Modify `shared/src/commonMain/kotlin/com/myapplication/shared/domain/sync/SyncCoordinator.kt`:

```kotlin
import com.myapplication.shared.effects.bindLocal
import com.myapplication.shared.effects.catchTransport
import com.myapplication.shared.effects.runSyncEffect
```

Use `runSyncEffect` for `drainOutbox`, `pullFromRemote`, and `applyRemote`. Decode payloads with `catchTransport("解析远端 todo 行失败")` and `catchTransport("解析远端列表行失败")`.

- [x] **Step 3: Run sync coordinator tests**

Run:

```bash
./gradlew :shared:desktopTest --tests com.myapplication.shared.domain.sync.SyncCoordinatorTest --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL`.

## Task 3: Refactor SupabaseSyncClient Transport Boundary

- [x] **Step 1: Refactor push/pull to `runSyncEffect`**

Modify `shared/src/commonMain/kotlin/com/myapplication/shared/data/sync/SupabaseSyncClient.kt`:

```kotlin
import com.myapplication.shared.effects.catchTransport
import com.myapplication.shared.effects.runSyncEffect
```

Remove top-level `try/catch` and `left()/right()` from `push` and `pull`. Keep per-row `runCatching` inside bad-payload isolation loops.

- [x] **Step 2: Run compile and sync tests**

Run:

```bash
./gradlew :shared:desktopTest --tests com.myapplication.shared.domain.sync.SyncCoordinatorTest --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL`.

## Task 4: Verify DB/UI Effect Boundaries

- [x] **Step 1: Check command boundaries are centralized**

Run:

```bash
rg -n "guard|try \\{|catch \\(|left\\(|right\\(|either \\{" shared/src/commonMain/kotlin/com/myapplication/shared/data/TodoRepositoryImpl.kt shared/src/commonMain/kotlin/com/myapplication/shared/ui/effects/TodoEffectRunner.kt
```

Expected: no scattered DB command `try/catch`; only shared effect helpers contain generic exception mapping.

- [x] **Step 2: Run repository and ViewModel tests**

Run:

```bash
./gradlew :shared:desktopTest --tests com.myapplication.shared.data.TodoRepositoryImplTest --tests com.myapplication.shared.ui.main.MainViewModelTest --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL`.

## Task 5: Full Verification

- [x] **Step 1: Run complete verification**

Run:

```bash
./gradlew :shared:desktopTest :desktopApp:compileKotlinJvm :androidApp:assembleDebug --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 2: Check git diff hygiene**

Run:

```bash
git diff --check
git status --short
```

Expected: no whitespace errors; only Arrow Fx architecture files and expected refactor files changed.

## Task 6: Extract Pure SyncStatus Transitions

- [x] **Step 1: Write failing reducer tests**

Create `shared/src/commonTest/kotlin/com/myapplication/shared/domain/sync/SyncStatusTransitionsTest.kt` to lock the pure status transitions for configuration, sync success/failure, outbox observation, remote apply, and remote subscription failures.

- [x] **Step 2: Implement pure status transition helpers**

Create `shared/src/commonMain/kotlin/com/myapplication/shared/domain/sync/SyncStatusTransitions.kt` with extension functions such as `configuring`, `configurationFailed`, `syncStarted`, `syncFailed`, `syncSucceeded`, `outboxObserved`, `remoteApplied`, and `remoteSubscriptionLost`.

- [x] **Step 3: Refactor SyncEngine to use reducer helpers**

Replace scattered `_status.copy(...)` calls in `SyncEngine` with pure transition helpers so the engine remains a runtime shell for effects, resources, jobs, and schedules.

- [x] **Step 4: Verify reducer and runtime behavior**

Run:

```bash
./gradlew :shared:desktopTest --tests com.myapplication.shared.data.sync.SyncEngineTest --tests com.myapplication.shared.domain.sync.SyncStatusTransitionsTest --no-daemon --console=plain
./gradlew :shared:desktopTest :desktopApp:compileKotlinJvm :androidApp:assembleDebug --no-daemon --console=plain
git diff --check
```

Expected: all commands pass and `SyncEngine` no longer owns user-facing status transition rules directly.

## Task 7: Deepen Analytics And Supabase Lifecycle Modules

- [x] **Step 1: Write failing analytics model tests**

Create `shared/src/commonTest/kotlin/com/myapplication/shared/ui/analytics/AnalyticsModelTest.kt` to cover completion counts, streaks, energy scoring, pressure buckets, and empty-state encouragement.

- [x] **Step 2: Extract analytics model builder from Compose screen**

Create `shared/src/commonMain/kotlin/com/myapplication/shared/ui/analytics/AnalyticsModel.kt` and move `AnalyticsModel`, chart point/bucket models, energy scoring, streak calculation, pressure distribution, and encouragement copy into pure functions. Keep `AnalyticsScreen` focused on rendering.

- [x] **Step 3: Resource-manage Supabase Realtime subscriptions**

Refactor `SupabaseSyncClient.observeRemoteChanges()` so Realtime channels are acquired and released through Arrow Fx `Resource.use` instead of local `try/finally`.

- [x] **Step 4: Update ADR dependency record**

Update ADR 0001 to mention `arrow-resilience` explicitly and clarify that `Schedule` comes from Arrow Resilience while `Resource`/`bracket`/`guarantee` come from Arrow Fx Coroutines.

## Self-Review

- Spec coverage: typed Todo effects, typed Sync effects, DB handler, UI launcher, Arrow Fx dependency, lifecycle `guarantee`, sync behavior preservation, and verification commands are covered.
- Placeholder scan: no `TBD`, no open-ended "add appropriate handling", no undefined task outputs.
- Type consistency: `TodoEffect`, `SyncEffect`, `runTodoEffect`, `runSyncEffect`, `catchPersistence`, `catchTransport`, and `bindLocal` are used consistently.
