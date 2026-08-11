# TODO / 已知问题

> 从代码审查与注释工作中收集的待修项。按严重程度排序。

## 待修 Bug

### 1. [bug] 周日当天没有「本周」时间桶（Formatting.bucketOf）

- 位置：`shared/src/commonMain/kotlin/com/myapplication/shared/util/Formatting.kt` 的 `bucketOf`
- 问题：周日（`isoDayNumber == 7`）时边界计算 `8 - 7 = 1`，`diff == 1` 已被 TOMORROW 分支截走 → 周日新增的任何日期直接落入 LATER 桶，本周桶在周日永远为空
- 影响：今天（今日）页的日期分组文案在周日不正确

### 2. [bug] 跨周近未来日期不显示「周X」而显示月日（Formatting.formatDueDate）

- 位置：`shared/src/commonMain/kotlin/com/myapplication/shared/util/Formatting.kt` 的 `formatDueDate`
- 问题：`date.dayOfWeek > todayDow && days in 2..7` 条件错误——如周二看下周一（跨周、days≤7）走月日分支而非「周一」；本周已过的星期几（周二看周一）也走月日
- 影响：日期显示不一致，降低可读性

### 3. [bug] DateParser 两个边缘解析错误

- 位置：`shared/src/commonMain/kotlin/com/myapplication/shared/util/DateParser.kt`
- 问题：
  - 「上午12点」解析成 12 点而非 0 点
  - 「中午N点」忽略输入数字（`"中午" -> 12` 固定）
- 影响：自然语言日期解析在上午/中午边界不精确（已知口语取舍，需决定是否修）

### 4. [bug] 已软删除的父任务下仍可添加子任务（AddTodo/AddSubTask）

- 位置：`shared/src/commonMain/kotlin/com/myapplication/shared/domain/usecase/AddSubTask.kt`、`AddTodo.kt`
- 问题：经 `selectById` 查父任务，不过滤 trash/删除态——父任务已移入垃圾箱后仍能追加子任务
- 影响：语义瑕疵（非破坏性）

### 5. [bug] Supabase 单条脏数据卡死整批推送（SupabaseSyncClient.pushUpserts）

- 位置：`shared/src/commonMain/kotlin/com/myapplication/shared/data/sync/SupabaseSyncClient.kt` 的 `pushUpserts`
- 问题：任一条 UPSERT 行缺 payload 抛 `IllegalStateException`，整批返回 Left → outbox 后续所有行（含无关行）被阻塞，2s 循环永久重试
- 影响：一条脏数据导致同步完全卡死，无隔离/隔离机制
- 候选方案：跳过坏行并记日志，或按行重试隔离；push 失败行不进 outbox 水位线

## 已记录的设计限制（不修，仅供了解）

- 无墓碑：晚到的旧 upsert 可能复活已删行（ADR 0002 / docs/sync-setup.md）
- 等毫秒级 `updated_at` 平局时后到者胜
- RLS 对 anon 全开，仅适合个人自用
- 窄屏设置入口：已通过顶栏「设置」图标解决（2026-08-12）

## 观察项（非 bug，待验证）

- `SyncEngine.configure` 失败分支把 `pendingCount` 重置为 0，outbox 有积压时 UI 计数短暂失真
- `AppGraph.loadSyncConfig` 的 `runBlocking` 在主线程首次访问 engine 时会阻塞（当前路径在后台协程）
