# ADR 0001: 采用 Arrow typed errors 与 Arrow Fx 的函数式领域层

- 日期：2026-08-11
- 状态：已接受，2026-08-14 更新

## 背景

原数据层所有命令都是 `Unit`/`Boolean` 返回 + 异常抛出（或静默 `?: return` 吞错），错误路径编译器不可见；编排逻辑（收件箱回退、父任务校验）散落在 Repository 与 ViewModel 中。spec（`docs/superpowers/specs/2026-08-11-reminders-app-design.md`）承诺的 `domain/usecase/` 层未落地。

2026-08-12 更新：项目不再只满足“轻量 typed errors”，而是把 Arrow / Arrow Fx 作为副作用组织方式全面铺开。目标是让领域命令描述为可组合 typed effect，把副作用解释、调度、资源安全、并发组合收敛在明确 handler 中。

## 决策

1. 引入 `io.arrow-kt:arrow-core:2.2.3`、`io.arrow-kt:arrow-fx-coroutines:2.2.3` 与 `io.arrow-kt:arrow-resilience:2.2.3`（KMP：JVM/Android/iOS）。核心 API：`Either`、`either {}` DSL、`ensure`/`bind`/`raise`；Fx API 优先用于 resource safety、typed effect scope、结构化并发；Resilience API 用于 `Schedule`、retry/backoff 等调度/重试策略。
2. 错误建模为 `TodoError` sealed ADT（`EmptyTitle`/`ParentNotFound`/`InboxNotFound`/`Persistence`），UI 层对 ADT 穷尽映射为用户文案（`ui/ErrorUi.kt`）。
3. 所有写命令返回 `Either<TodoError, Unit>`；查询保持 `Flow` 流不包装（数据流非 Effect）。
4. 副作用收敛到明确的 effect handler：Repository 适配器通过统一 DB command handler 解释数据库 effect，把异常映射为 `Persistence` 并重抛 `CancellationException`；UI 通过统一 command launcher 把 typed error 折叠为可观察状态。
5. 编排逻辑（目标列表解析、父任务存在性）落 `domain/usecase/`，ViewModel 消费 `Either` 经 `lastError` StateFlow 呈现。
6. `Clock`/`TimeZone` 通过 `AppGraph` 显式注入，领域层不读全局时钟。

## 不做 / 边界

- 不引入 HKT/高阶抽象（Kotlin 不支持，Arrow 2.x 已放弃模拟）。
- 查询不改 `Option` 包装，保持可空以服务 Compose UI 便利性。
- 不做自定义 IO Monad；Arrow Fx 与 Kotlin Structured Concurrency 是运行时 effect 基础。
- 不把 Compose `LaunchedEffect` 等 UI 生命周期 API 包装成领域 effect；UI 生命周期仍属于 Compose。

2026-08-12 修订：后续资源生命周期（同步 client、DB dispatcher、外部连接）应优先用 Arrow Fx `Resource`/`bracket`/`guarantee` 表达；重试/轮询应优先使用 Arrow Resilience `Schedule` 或同等可组合策略。

## 2026-08-14 架构深化

1. `TodoRepository` 拆分为更窄的领域端口：`TodoQueries`、`TodoCommands`、`ListCommands`、`SyncStore`、`SettingsStore`。`TodoRepository` 仍保留为兼容聚合接口，避免一次性迁移所有调用点。
2. ViewModel 中的写入规则下沉到 use case：完成切换、日期安排、列表保存、设置保存分别由领域用例拥有；ViewModel 负责调用用例、折叠 `Either`、暴露 UI 状态。
3. 命令继续使用 `Either` 表达 typed effects；查询继续保持 `Flow`，不把响应式数据流包装成领域 effect。
4. Compose 生命周期、`viewModelScope`、`LaunchedEffect` 仍是 UI 运行时边界，不包装进领域 effect。领域层不拥有 Compose 生命周期。
5. 同步运行时生命周期隔离到 `Resource`/lease 风格模块；`SyncCoordinator` 依赖窄端口 `SyncStore` 与 `SyncClient`，不再需要完整仓库聚合。

## 后果

- 优点：错误类型即接口文档；编译器强制穷尽处理；use case 可脱离 UI 独立测试（行为层测试面）；静默吞错路径消灭。
- 代价：新增第三方依赖；所有命令调用点需处理 `Either`（ViewModel 模式统一）。
- 后续演进：继续减少直接依赖兼容聚合接口的调用点；`Persistence.message` 可接入日志。
