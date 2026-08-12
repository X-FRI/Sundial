# ADR 0002: 多端同步——Supabase 先行，SyncClient 端口预留自建服务器

- 日期：2026-08-12
- 状态：已接受

## 背景

spec 原定"纯本地、无同步"。用户决定增加多端实时同步，并明确未来可能自建 Sundial-Server。

## 决策

1. 同步语义：行级 LWW（`updated_at` 大者胜，物理删除带 `updated_at` 守卫），SQL 层实现，不引入 CRDT。
2. 本地 `outbox` 表记录全部本地变更（payload=完整新行 JSON），`SyncCoordinator` 批量推送、成功后清理；`seq` 单调（`MAX(seq)+1`），清理按水位线删除。
3. 端口 `SyncClient`（push + 远端变化 Flow）为 seam；当前适配器：`NoopSyncClient`（本地模式）、`SupabaseSyncClient`（PostgREST + Realtime）；工厂 `SyncClientFactory` 已含 `SundialServer` 分支，未来只需新增适配器。
4. 应用内设置页配置同步模式与凭据（settings 表持久化，deviceId 也持久化并注入 `TodoRepositoryImpl`，所有行打 `updated_by` 标签）；远端变化经 `SyncEngine` 应用，自身设备事件过滤（`updated_by`）。
5. 身份：无登录，RLS 放开 anon（个人使用，安全边界见 docs/sync-setup.md）。
6. 兼容旧 Android：不使用 `ON CONFLICT DO UPDATE`（SQLite ≥ 3.24 才支持），改为 `updateIfNewer` + `insertIfMissing`（`INSERT ... SELECT ... WHERE NOT EXISTS`，SQLite ≥ 3.8），语义等价。
7. 远端 DELETE 事件依赖 `replica identity full`（旧行含 `updated_at`），否则守卫恒假、删除不生效。

## 不做

- 不做 CRDT / 字段级合并 / 墓碑 / 双向删除撤销。
- 不引入认证 UI；Sundial-Server 模式本期不实现（工厂返回 NotConfigured）。
- 不处理多用户数据隔离。
- 不做全量拉取重同步（MVP 仅增量：outbox 推送 + 实时订阅）。

## 后果

- 优点：实时同步代码量最小（约 500 行核心）；同步后端可替换（本地/Supabase/自建）；离线写天然由 outbox 兜底（重连后推送）。
- 代价：LWW 在同时双端编辑同一行时丢更新（个人使用可接受）；Supabase 为外部依赖；RLS 放开的安全风险由部署者自担；无墓碑下"晚到的旧写入"可能复活已删行（见 sync-setup.md）。

---

# 更新（2026-08-12）：同步体验与移动端交互完善

对应规格：`docs/superpowers/specs/2026-08-12-sync-ux-and-polish-design.md`。本轮解决「同步时不可用、另一端无反馈」与移动端下拉刷新/同步动画指示，并修复 TODO.md 全部条目（Bug 1–5）。

## 背景

用户反馈三大问题：① 同步线程与 UI 线程并发访问 SQLite 出现间歇性 `database is locked` 与静默失败；② 移动端无主动拉取入口，Realtime 事件一旦丢失（离线期、订阅失败）数据永久不同步；③ 操作待办后另一端无反馈。另需补齐移动端详情抽屉与同步动画指示。

## 决策

1. **D1 单线程 DB 收敛**：`JdbcDriver` 非线程安全（事务 `ThreadLocal` 绑定、无内部锁），`Transacter.Transaction.endTransaction$runtime` 为 final 方法，驱动级 synchronized 包装无法拦截提交 → 放弃包装方案，所有 SQLDelight 访问（含 Flow 查询的 `flowOn`）收敛到一条专用线程（`AppGraph` 共享 `newSingleThreadContext("sqlite-db")`）。
2. **D2 pull 全量拉取兜底通道**：`SyncClient.pull()` 无参拉取两张表全部行，经 `SyncCoordinator.pullFromRemote()` 逐行 LWW 应用（自身设备回声按 `updated_by` 过滤、单行失败隔离），作为 Realtime 丢事件的兜底；watermark 增量（`since` 参数）留作后续，数据量小、全量幂等。
3. **D3 Realtime 真实连接状态**：`observeConnectionStatus()` 端口方法接入 `Realtime.Status`（DISCONNECTED/CONNECTING/CONNECTED → Boolean 映射），替代「push 成功才置 connected」的代理信号。
4. **D4 移动端下拉刷新 + 底部抽屉详情**：引入 material3（CMP 1.11.1 官方映射 1.9.0），窄屏列表用 `PullToRefreshBox`（`isRefreshing = syncing`、`onRefresh = syncNow()`）；详情改用 `ModalBottomSheet` 底部抽屉（遮罩 + 下滑手势关闭）。**弃右侧抽屉**：material3 1.9.0 无右侧抽屉 API（`ModalNavigationDrawer` 仅支持 start/end 边缘的导航式抽屉，无法承载全高详情内容），用户批准改底部抽屉。
5. **D5 syncing 状态机与动画**：`SyncStatus` 增 `syncing: Boolean`（末尾默认 false，向后兼容）；相位由 UI 派生（syncing → Synced/Error）；置真时机：outbox 观察器发现积压、`syncNow()` 发起、轮询 tick 发现积压（操作后即时反馈，不等 2s 轮询）；置假时机：drain 成功且 pendingCount==0、失败（转 Error）、`configure` 复位。指示器为旋转双箭头（900ms/圈线性旋转）。
6. **D6 坏行隔离与计数**：`pushUpserts`/`pullTable` 逐行 `mapNotNull` + `runCatching`，payload 损坏的坏行跳过并计入错误计数，不再整批失败卡死 outbox；若全部行损坏则返回 Left 报错。
7. **D7 首次启用自动对齐**：`configure` 成功后自动触发一次 `syncNow()`（全量拉取即对齐，覆盖原 B-5「可选的全量拉取/初始化对齐」）。

## 遗留（记入 TODO.md）

- pull 增量水位线（`since` 参数预留，接口已留 pull() 无参形态便于后续加参）。
- 行级 quarantine：坏行当前直接丢弃不入重试队列（若重试需服务端协作或本地旁路队列）。
- Realtime 事件丢失的补偿窗口（依赖 pull 全量兜底）。
- 底部抽屉 Escape/关闭按钮无退场动画（material3 语义限制，可接受）。
- 7 日视界无 NEXT_WEEK 桶（跨周 >7 天的日期仍显示月日）。

## 后果

- 优点：DB 并发根因消除（单线程收敛为官方推荐模式）；双通道（Realtime + pull 兜底）保证远端变更必然到达；同步状态对用户可见且即时（动画反馈 ≤200ms）；移动端体验补齐（下拉刷新 + 底部抽屉）；坏行不再卡死整批同步。
- 代价：全量拉取代价随数据量线性增长（个人数据量可承受，后续换增量水位线）；`ModalBottomSheet` 与右侧详情栏的交互习惯不同（遮罩 + 下滑手势，返回键联动）；material3 1.9.0 右侧抽屉不可得，抽屉方向为妥协结果。
