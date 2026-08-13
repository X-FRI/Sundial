# Sundial 可付费价值功能蓝图

> 日期：2026-08-13
> 状态：待实现
> 目标：先做出值得未来收费的产品能力，不引入支付、订阅或 paywall。

## 背景

Sundial 当前已经具备本地优先待办、SQLDelight 持久化、Supabase 同步、工作台、列表、分析页、Android 今天小组件雏形，以及 Arrow typed effect 架构。下一步不应先接支付，而应先补齐能让用户长期留下来的产品价值。

本 spec 定义一组“未来可收费，但当前免费开放”的高级能力：

- 整理系统：把收件箱、无日期、逾期和不清晰任务变成可执行计划。
- 重复任务：支持日常节律，而不是只记录一次性事项。
- 成熟图表分析页：用图表库呈现完成趋势、精力输出、压力分布。
- 小组件增强：让用户不打开完整 app 也能看到今天任务。
- 同步健康：让跨设备同步变得可解释、可信。
- 智能增强骨架：先做规则引擎，未来可接 LLM。

## 非目标

- 不做支付、订阅、会员墙或 license server。
- 不把任何现有功能锁到 Pro。
- 不在第一阶段实现 macOS WidgetKit extension。
- 不在第一阶段接 LLM 服务。
- 不为分析页继续新增手写核心图表。

## 产品原则

1. **工作台优先**：第一屏回答“现在我该处理什么”。
2. **整理优先于堆积**：收件箱不是列表装饰，而是待整理池。
3. **节律优先于提醒**：重复任务、小组件和今天视图共同帮助用户持续推进。
4. **复盘优先于炫技**：分析页展示有行动意义的趋势，不做无解释的漂亮图。
5. **本地优先**：所有高级能力必须在离线、本地数据下可用。
6. **库驱动图表**：分析页图表使用成熟 Compose Multiplatform 图表库。

## Milestone 规划

### Milestone 1：整理与计划基础

目标：把 Sundial 从待办列表升级成执行工作台。

范围：

- 收件箱整理面板。
- 待整理原因识别：收件箱、无日期、逾期、缺少列表、标题过长、缺少下一步。
- 快捷整理动作：安排今天、安排明天、移动列表、拆为子任务、移入垃圾箱。
- 重复任务数据模型、基础 UI 和完成后生成下一次。
- 分析页改用成熟图表库。
- 分析模型扩展到周/月范围。

验收标准：

- 用户能在工作台看到待整理入口。
- 用户能按原因处理待整理任务。
- 用户能在详情页设置基础重复规则。
- 完成重复任务后自动生成下一次任务。
- 分析页主要图表不再手写 Canvas。
- Android 与 desktop debug/compile 验证通过。

### Milestone 2：跨端节律

目标：让用户不用打开完整 app 也能看到今天要做什么。

范围：

- Android 小组件增强：今天、逾期、待整理摘要。
- 小组件尺寸：small、medium、large。
- 点击小组件打开 app 到工作台或今天范围。
- macOS WidgetKit extension 技术方案和共享数据通道。
- Widget snapshot 本地缓存。

### Milestone 3：同步可靠性产品化

目标：让同步变成用户信任的一部分。

范围：

- 同步健康页。
- 最近同步事件。
- 失败原因分类。
- 手动重试、重新拉取。
- 本地 outbox 可解释。
- 冲突策略说明和用户可见状态。

### Milestone 4：智能增强

目标：让整理不只是规则，而是半自动规划。

范围：

- 规则版智能整理建议。
- 可插拔智能 provider 接口。
- 自然语言日期增强。
- “把收件箱整理成今天计划”。
- “根据精力输出建议明天安排”。

## 功能设计

### 整理系统

整理系统服务于工作台和收件箱。它不替代列表，而是帮助用户把“捕获的任务”变成“可执行的任务”。

待整理原因：

- `Inbox`: 任务仍在收件箱。
- `NoDate`: 任务没有日期。
- `Overdue`: 任务已经逾期。
- `NoList`: 任务缺少明确列表；当前等价于收件箱。
- `LongTitle`: 标题过长，可能需要拆分或补备注。
- `MissingNextStep`: 标题像项目或目标，但缺少具体动作。

建议动作：

- `ScheduleToday`
- `ScheduleTomorrow`
- `MoveToList`
- `CreateSubtask`
- `Trash`
- `DismissSuggestion`

UI：

- 工作台顶部显示轻量整理入口：`待整理 N 项`。
- 点击进入整理面板。
- 整理面板按原因分组，默认展开。
- 每条任务只显示 2-3 个最高相关动作。
- 处理完一组后给出正反馈，不弹打断式庆祝。

### 重复任务

重复任务用于稳定节律，不用于复杂项目管理。

第一阶段支持：

- 不重复。
- 每天。
- 每周。
- 每月。
- 自定义间隔：每 N 天 / 每 N 周 / 每 N 月。

完成逻辑：

- 用户完成当前任务。
- 如果任务有重复规则，当前任务标记完成。
- 系统基于当前 due date 或完成时间计算下一次 due date。
- 系统生成新的未完成任务，保留标题、备注、列表、旗标和重复规则。
- 生成失败时当前完成操作不回滚，但 UI 显示错误。

暂不支持：

- 复杂 RRULE 全量语法。
- 工作日、节假日、跳过实例。
- 修改一个实例时选择“仅本次 / 全部后续”。

### 分析页

分析页要从“手写可视化 demo”升级为产品级 dashboard。核心图表必须用成熟库实现。

默认图表库：**Vico**。

选择理由：

- Vico 是面向 Compose Multiplatform 的成熟图表库，适合产品 dashboard 的折线、柱状和趋势图。
- 当前 Sundial 的分析需求以完成趋势、精力输出、压力分布为主，不需要科学绘图级别的复杂坐标系。
- 如果后续需要更宽图表类型，可评估 Koala Plot 作为补充。Koala Plot 同样支持 Compose Multiplatform 和多图表类型。

硬性要求：

- 不再在 `AnalyticsScreen` 中新增手写核心图表。
- 图表渲染通过 `ui/analytics/charts/` 适配层。
- `AnalyticsModel` 继续保持纯函数和可测试数据模型。
- 图表组件只接收稳定 series view model。
- 颜色、字体、间距从现有 design token 映射到图表主题。

图表类型：

- 完成趋势：7 天 / 30 天折线图。
- 精力输出：周/月柱状图。
- 压力分布：逾期、今天、未来、无日期的分布图。
- 列表投入：按列表聚合的完成/活跃任务对比。

数据模型：

```text
AnalyticsRange
  Week
  Month

CompletionSeries
  points: List<ChartPoint>

EnergySeries
  points: List<ChartPoint>

PressureSeries
  buckets: List<ChartBucket>

ListFocusSeries
  buckets: List<ChartBucket>
```

资料来源：

- Vico: https://github.com/patrykandpatrick/vico
- Koala Plot: https://github.com/KoalaPlot/koalaplot-core

### 小组件

小组件的产品目标是“今天不用打开 app 也能看到最重要的任务”。

Android：

- 复用现有 `TodayWidget`。
- 增强 snapshot：今天任务、逾期数量、待整理数量、完成数量。
- medium/large 尺寸展示更多任务。
- 点击任务或空白区域打开 app。

macOS：

- 需要新增 WidgetKit + SwiftUI extension。
- Compose Desktop/JVM app 不能直接提供系统桌面小组件。
- 需要共享数据通道，把 `TodayWidgetSnapshot` 写入 app group 或可读文件。
- 第一阶段只写技术方案，不实现。

### 同步健康

同步健康不是简单显示“已同步”，而是解释系统是否可信。

状态：

- 本地模式。
- 未配置。
- 同步中。
- 已同步。
- 有待同步。
- 同步失败。
- 订阅断开。

后续 UI：

- 设置页显示同步健康卡。
- 详情页可显示最近失败原因。
- 提供重新同步入口。
- 对 outbox 数量给出解释。

### 智能增强骨架

第一阶段只做规则引擎，不接远程 AI。

规则引擎：

- 输入：todos、lists、today、timeZone。
- 输出：organization suggestions。
- 纯函数，可测试。

未来 LLM provider：

- 输入经过本地脱敏和压缩。
- 输出必须转成结构化 suggestion。
- 用户确认后才执行写操作。

## 架构设计

新增模块：

```text
shared/src/commonMain/kotlin/com/myapplication/shared/domain/organize/
  OrganizationReason.kt
  OrganizationSuggestion.kt
  OrganizationRules.kt
  OrganizeInboxUseCase.kt

shared/src/commonMain/kotlin/com/myapplication/shared/domain/recurrence/
  RecurrenceRule.kt
  RecurrenceCalculator.kt
  CompleteRecurringTodoUseCase.kt

shared/src/commonMain/kotlin/com/myapplication/shared/domain/analytics/
  AnalyticsRange.kt
  ChartSeries.kt

shared/src/commonMain/kotlin/com/myapplication/shared/ui/analytics/charts/
  AnalyticsChartTheme.kt
  CompletionTrendChart.kt
  EnergyOutputChart.kt
  PressureDistributionChart.kt

shared/src/commonMain/kotlin/com/myapplication/shared/ui/organize/
  OrganizePanel.kt
  OrganizeSection.kt
  SuggestionRow.kt

shared/src/commonMain/kotlin/com/myapplication/shared/ui/recurrence/
  RecurrencePicker.kt
  RecurrenceSummary.kt
```

Repository 扩展：

```text
TodoRepository
  observeRecurrenceRules()
  setRecurrence(todoId, rule)
  clearRecurrence(todoId)
  completeWithRecurrence(todoId)
```

写命令继续返回 `Either<TodoError, A>`，并通过现有 `launchTodoEffect` 在 UI 层解释。

## 数据库设计

新增表：

```sql
CREATE TABLE recurrence_rule (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  frequency TEXT NOT NULL,
  interval_count INTEGER NOT NULL DEFAULT 1,
  days_of_week TEXT,
  day_of_month INTEGER,
  end_at INTEGER,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  updated_by TEXT NOT NULL
);
```

扩展 todo：

```sql
ALTER TABLE todo ADD COLUMN recurrence_rule_id INTEGER;
ALTER TABLE todo ADD COLUMN recurring_template_id INTEGER;
```

同步：

- `recurrence_rule` 需要加入 outbox。
- `todo.recurrence_rule_id` 和 `todo.recurring_template_id` 进入 todo payload。
- 远端 SQL setup 需要补充新表和列。

第一阶段可以不创建独立 `recurring_template` 表，先用当前 todo 作为模板来源；后续如需“修改全部后续实例”，再补模板表。

## 函数式边界

纯函数：

- `buildOrganizationSuggestions(...)`
- `nextOccurrence(...)`
- `buildAnalyticsSeries(...)`
- `buildTodayWidgetSnapshot(...)`

Effect 边界：

- Repository 数据库写入。
- 同步 client。
- Widget snapshot 持久化。
- UI command launcher。

原则：

- 规则计算不读数据库、不读系统时间、不写状态。
- 时间通过 `Clock` / `today` 参数注入。
- 错误通过 typed error 返回。
- 同步生命周期继续使用 Arrow Fx / Resource / Schedule 风格。

## 测试策略

Milestone 1 必须覆盖：

- 整理规则纯函数测试。
- 重复规则下一次日期测试。
- 完成重复任务生成下一次测试。
- SQL migration 测试。
- Repository recurrence 写命令测试。
- Analytics chart series 模型测试。
- ViewModel command 测试。
- `desktopTest`、desktop compile、Android debug build。

## 风险与决策

### 图表库风险

Vico 版本和 Compose Multiplatform 版本可能存在兼容性问题。实现计划必须先做 dependency spike：引入最小图表并跑 Android/Desktop 构建。

若 Vico 当前版本无法兼容项目：

1. 优先尝试 Vico 稳定版本和 multiplatform artifact。
2. 若仍失败，切到 Koala Plot。
3. 不回退到手写核心图表，除非只做极小装饰性 sparkline。

### 重复任务范围风险

复杂 RRULE 会显著扩大范围。第一阶段只支持简单重复，后续再扩展。

### 同步 schema 风险

新增 recurrence 字段会影响本地和远端 schema。实现时必须保证 migration 可重复、远端 SQL 文档更新、旧数据默认不重复。

## 第一阶段交付清单

1. 整理系统领域模型与规则。
2. 工作台整理入口和整理面板。
3. 重复任务模型、数据库迁移和详情页 picker。
4. 完成重复任务后生成下一次。
5. 分析页改用成熟图表库，默认 Vico。
6. 分析页周/月 range 和 chart series 模型。
7. 对应测试与构建验证。

## 未来付费映射

当前不实现付费，但这些能力未来可以自然映射到 Pro：

- 高级同步健康与历史。
- 跨设备小组件。
- 长周期分析。
- 智能整理。
- 高级重复任务。
- 备份、导出和恢复。

免费版本仍应保留本地待办、基础列表、基础工作台和基础分析。

## 开放问题

- macOS 小组件分发路线依赖未来是否走 App Store。
- LLM provider 是否使用本地模型、用户 API key，还是 Sundial 服务端。
- 重复任务是否需要“跳过本次”和“暂停规则”。
- 分析页是否需要导出图片或报告。
