# ADR 0001: 采用 Arrow typed errors 的函数式领域层

- 日期：2026-08-11
- 状态：已接受

## 背景

原数据层所有命令都是 `Unit`/`Boolean` 返回 + 异常抛出（或静默 `?: return` 吞错），错误路径编译器不可见；编排逻辑（收件箱回退、父任务校验）散落在 Repository 与 ViewModel 中。spec（`docs/superpowers/specs/2026-08-11-reminders-app-design.md`）承诺的 `domain/usecase/` 层未落地。

## 决策

1. 引入 `io.arrow-kt:arrow-core:2.2.3`（KMP：JVM/Android/iOS），仅用其轻量 Effect 部分：`Either`、`either {}` DSL、`ensure`/`bind`/`raise`。
2. 错误建模为 `TodoError` sealed ADT（`EmptyTitle`/`ParentNotFound`/`InboxNotFound`/`Persistence`），UI 层对 ADT 穷尽映射为用户文案（`ui/ErrorUi.kt`）。
3. 所有写命令返回 `Either<TodoError, Unit>`；查询保持 `Flow` 流不包装（数据流非 Effect）。
4. 副作用收敛到 `TodoRepositoryImpl` 单个文件，`guard` 包装器把异常映射为 `Persistence` 并重抛 `CancellationException`。
5. 编排逻辑（目标列表解析、父任务存在性）落 `domain/usecase/`，ViewModel 消费 `Either` 经 `lastError` StateFlow 呈现。
6. `Clock`/`TimeZone` 通过 `AppGraph` 显式注入，领域层不读全局时钟。

## 不做

- 不引入 HKT/高阶抽象（Kotlin 不支持，Arrow 2.x 已放弃模拟）；不用 `arrow-fx` 的 `Resource`/`Fx`（当前无资源生命周期需求）。
- 查询不改 `Option` 包装，保持可空以服务 Compose UI 便利性。
- 不做 IO Monad，副作用即协程（Kotlin 原生的 Continuation 风格）。

## 后果

- 优点：错误类型即接口文档；编译器强制穷尽处理；use case 可脱离 UI 独立测试（行为层测试面）；静默吞错路径消灭。
- 代价：新增第三方依赖；所有命令调用点需处理 `Either`（ViewModel 模式统一）。
- 后续演进：`addList` 空名校验仍留 VM，可再收敛入 use case；`Persistence.message` 可接入日志。
