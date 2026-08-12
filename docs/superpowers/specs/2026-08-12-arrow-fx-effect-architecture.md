# Arrow Fx Effect Architecture Spec

## Goal

Sundial should use Arrow as the default style for command-side functional programming. Domain, data, sync, and UI command boundaries should model failures as typed effects and keep incidental `try/catch`, coroutine launch details, dispatcher switching, and lifecycle cleanup behind explicit handlers.

## Current State

- `arrow-core` is already used for `Either`, `either {}`, `ensure`, `bind`, and `raise`.
- Repository commands already expose `Either<TodoError, A>`, while queries remain `Flow<T>`.
- A first-pass `TodoEffect` helper and `TodoRepositoryImpl.dbCommand` exist in the working tree.
- `SyncCoordinator`, `SyncEngine`, and `SupabaseSyncClient` still contain repeated imperative `try/catch`, `left()/right()`, and null/sentinel error handling.
- ADR 0001 previously described Arrow typed errors but did not make Arrow Fx a project-wide direction. It has been updated in the working tree and must remain aligned with the implementation.

## Product And Engineering Requirements

1. Typed errors remain the public contract:
   - Todo commands return `Either<TodoError, A>`.
   - Sync commands return `Either<SyncError, A>`.
   - Query streams remain `Flow<T>` and are not wrapped in `Either`.

2. Effect handlers must be explicit:
   - `TodoEffect<A>` represents command programs that can raise `TodoError`.
   - `SyncEffect<A>` represents sync programs that can raise `SyncError`.
   - Data-layer DB effects are interpreted by `TodoRepositoryImpl.dbCommand`.
   - UI command effects are interpreted by `launchTodoEffect`.
   - Sync transport/coordination effects are interpreted by `runSyncEffect` and small sync helpers.

3. Coroutine cancellation must stay structural:
   - `CancellationException` must always be rethrown.
   - Unknown exceptions are converted to typed error values only at effect boundaries.

4. Arrow Fx must be real, not decorative:
   - `arrow-fx-coroutines` is a project dependency.
   - Lifecycle cleanup should use Arrow Fx tools such as `guarantee` where a finalizer is required.
   - Future resource-owning seams, especially `SyncClient` and DB dispatcher ownership, should prefer `Resource`, `bracket`, or `guarantee`.

5. Behavior must not change:
   - Outbox coalescing still keeps the latest UPSERT per table/id.
   - Bad remote rows are still isolated during pull.
   - Supabase push still skips malformed payload rows and fails only when every pushed row is malformed.
   - Local-first behavior and sync status semantics remain unchanged.

## Architecture

### Shared Effect Primitives

Create a small `shared.effects` module:

- `TodoEffects.kt`
  - `typealias TodoEffect<A> = suspend Raise<TodoError>.() -> A`
  - `runTodoEffect`
  - `catchPersistence`

- `SyncEffects.kt`
  - `typealias SyncEffect<A> = suspend Raise<SyncError>.() -> A`
  - `runSyncEffect`
  - `catchTransport`
  - `bindLocal` for mapping `Either<TodoError, A>` to `Either<SyncError, A>` inside a sync effect.

The module is intentionally small. It does not introduce a custom IO monad or HKT abstraction.

### Data Layer

`TodoRepositoryImpl` owns SQLDelight execution. It should expose one handler:

```kotlin
private suspend inline fun <A> dbCommand(
    fallbackMessage: String,
    crossinline block: suspend Raise<TodoError>.() -> A,
): Either<TodoError, A>
```

Every database command runs through this handler, so callers no longer know the rules for:

- selecting `dbDispatcher`,
- preserving cancellation,
- mapping unknown exceptions to persistence errors,
- using `Raise<TodoError>` inside transactions.

### Sync Layer

`SyncCoordinator` should express push/pull/apply flows as `runSyncEffect` programs. It should not manually construct `Either` with `either {}` plus local `try/catch` blocks when a shared effect helper can express the same contract.

`SupabaseSyncClient` should use `runSyncEffect` and `catchTransport` for top-level push/pull transport failures, while keeping per-row bad-payload isolation local to the parsing loops.

`SyncEngine` should keep long-running lifecycle orchestration in coroutines, but finalizers must use Arrow Fx where it makes the lifecycle rule explicit. `syncNowJob` cleanup is the first required use of `guarantee`.

### UI Layer

ViewModels should not repeat `viewModelScope.launch { command().onLeft { ... } }`. They should call a single launcher that interprets typed command effects and publishes `lastError`.

## Non-Goals

- Do not replace `Flow<T>` queries with `Either<Error, Flow<T>>`.
- Do not introduce a custom `IO` datatype.
- Do not rewrite Compose lifecycle primitives such as `LaunchedEffect`.
- Do not change Supabase schema, sync semantics, UI behavior, version numbers, or release packaging.

## Acceptance Criteria

- `shared` depends on both `arrow-core` and `arrow-fx-coroutines`.
- `docs/adr/0001-arrow-functional-core.md` states Arrow Fx as the accepted direction.
- `TodoRepositoryImpl` has no scattered DB command `try/catch`; DB command effects go through `dbCommand`.
- `SyncCoordinator.applyRemote` decodes payload through sync effect helpers, not local manual `try/catch`.
- `SupabaseSyncClient.push` and `pull` return typed errors through `runSyncEffect`.
- `SyncEngine.syncNow` uses Arrow Fx `guarantee` for job cleanup.
- Tests cover effect helper success, typed exception mapping, local-to-sync error mapping, and cancellation propagation.
- Verification passes:
  - `./gradlew :shared:desktopTest --no-daemon --console=plain`
  - `./gradlew :shared:desktopTest :desktopApp:compileKotlinJvm :androidApp:assembleDebug --no-daemon --console=plain`
