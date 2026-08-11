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
