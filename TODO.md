# TODO / 已知问题

> 从代码审查与注释工作中收集的待修项。按严重程度排序。

## 待办改进

### A. [feature] 移动端待办详情改用抽屉式组件 ✅ 已完成（2026-08-12）

- 位置：`shared/src/commonMain/kotlin/App.kt` 窄屏分支 + `ui/detail/DetailScreen.kt`
- 结果：窄屏（<900dp）详情改用 **material3 `ModalBottomSheet` 底部抽屉**（遮罩 + 下滑手势 + 返回键联动关闭），与宽屏右侧详情栏体验对齐
- 说明：原计划 `ModalNavigationDrawer` 右侧抽屉——material3 1.9.0 无右侧抽屉 API（导航式抽屉无法承载全高详情内容），经用户批准改为底部抽屉

### B. [improve] 优化同步体验 ✅ 已完成（2026-08-12）

- 目标：提升多端同步的稳定性与可感知性
- 完成子项：
  - [x] 推送失败行的隔离（坏行跳过 + 计数，不再卡死整批，见下方 Bug 5）
  - [x] `SyncEngine.configure` 失败分支 `pendingCount` 失真（保留现值，见 SyncEngine.kt:67）
  - [x] 同步状态连接信号接入 Realtime 真实连接状态（`observeConnectionStatus` → CONNECTED 映射）
  - [x] 待同步积压时的重试退避（2s → 指数退避 30s 封顶）
  - [x] 首次启用同步自动对齐（`configure` 成功后触发一次 `syncNow()` 全量拉取）
  - [x] `AppGraph.loadSyncConfig` 的 `runBlocking` 主线程阻塞（改 `runBlocking(dbDispatcher)`）
  - [x] 侧边栏同步指示器 + 「立即同步」按钮、设置页同步动画、移动端下拉刷新（`PullToRefreshBox`）
  - [x] 单线程 DB 收敛（所有 SQLDelight 访问限定一条专用线程，见 ADR 0002 更新）

## 待修 Bug

### 1. [bug] 周日当天没有「本周」时间桶（Formatting.bucketOf） ✅ 已修复（2026-08-12）

- 位置：`shared/src/commonMain/kotlin/com/myapplication/shared/util/Formatting.kt` 的 `bucketOf`
- 修复：改为基于「距今天日差 ≤ 本周剩余天数」的统一公式，周日也落入「本周」桶

### 2. [bug] 跨周近未来日期不显示「周X」而显示月日（Formatting.formatDueDate） ✅ 已修复（2026-08-12）

- 位置：`shared/src/commonMain/kotlin/com/myapplication/shared/util/Formatting.kt` 的 `formatDueDate`
- 修复：跨周判定改为「距今天 ≤7 天且目标星期几 ≠ 今天」即显示「周X」

### 3. [bug] DateParser 两个边缘解析错误 ✅ 已修复（2026-08-12）

- 位置：`shared/src/commonMain/kotlin/com/myapplication/shared/util/DateParser.kt`
- 修复：「上午12点」→ 0 点；「中午N点」→ `N==12 ? 12 : N+12`

### 4. [bug] 已软删除的父任务下仍可添加子任务（AddTodo/AddSubTask） ✅ 已修复（2026-08-12）

- 位置：`shared/src/commonMain/kotlin/com/myapplication/shared/domain/usecase/AddSubTask.kt`、`AddTodo.kt`
- 修复：改用 `selectByIdActive`（`is_trashed = 0` 过滤）查父任务，校验失败走既有错误通道

### 5. [bug] Supabase 单条脏数据卡死整批推送（SupabaseSyncClient.pushUpserts） ✅ 已修复（2026-08-12）

- 位置：`shared/src/commonMain/kotlin/com/myapplication/shared/data/sync/SupabaseSyncClient.kt`
- 修复：逐行 `mapNotNull` + `runCatching`，坏行跳过并计入错误计数；全部行损坏才返回 Left

## 已记录的设计限制（不修，仅供了解）

- 无墓碑：晚到的旧 upsert 可能复活已删行（ADR 0002 / docs/sync-setup.md）
- 等毫秒级 `updated_at` 平局时后到者胜
- RLS 对 anon 全开，仅适合个人自用
- 窄屏设置入口：已通过顶栏「设置」图标解决（2026-08-12）

## 观察项（非 bug，待验证 / 遗留）

- pull 增量水位线：当前 `pull()` 全量拉取，数据量增长后需换 `since` 参数增量（接口已预留形态）
- 行级 quarantine：坏行当前直接丢弃不入重试队列（若需重试要服务端协作或本地旁路队列）
- Realtime 补偿窗口：事件丢失的主动补偿未实现，依赖 pull 全量兜底
- 底部抽屉 Escape / 关闭按钮无退场动画（material3 语义限制，可接受）
- 7 日视界无 NEXT_WEEK 桶：跨周 >7 天的日期仍显示月日而非「下周X」
