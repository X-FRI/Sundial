# Sundial 产品级 UI/UX 重构设计规格

> 日期：2026-08-12 · 状态：已获用户方向确认（融合方向已更新）
> 视觉方向：工作台总览 × 上下文时间线 × 列表整理系统
> 适用范围：桌面端与移动端前端 UI/UX、交互模型、设计系统与核心工作流

## 1. 背景与问题判断

当前 Sundial 已经具备本地优先、SQLDelight 持久化、Supabase 多端同步、子任务、智能列表、自然语言日期等核心能力，但前端体验仍像“功能可用的工程界面”，而不是一个完整产品。

用户反馈的核心判断成立：现有界面不是单纯“不够漂亮”，而是缺少统一的产品模型、信息架构和交互逻辑。主要问题如下：

- **产品重心不清**：截图中的主界面同时出现大号智能列表卡片、空旷内容区、已完成任务块、右上角孤立加号，但用户不知道第一优先级是“今天执行”“快速捕获”还是“列表管理”。
- **桌面端没有完整工作流**：桌面天然适合三栏效率布局，但当前体验更像 sidebar + list 的半成品；详情、编辑、状态反馈没有形成连续的 `浏览 → 选择 → 编辑 → 完成` 工作链。
- **移动端不能只是桌面压缩**：移动端需要单手触达、快速捕获、今日执行和底部详情抽屉；不能把桌面的大块导航和列表粗暴缩窄。
- **视觉系统缺少产品性**：大号彩色智能列表 tile、网格背景、过强橙色边框、空旷灰底让界面像 demo 或练习项目；它没有建立 Sundial 自己的气质。
- **状态反馈位置分散**：同步状态、完成状态、日期状态、列表状态都存在，但缺少稳定的层级和读法，用户不容易判断“数据是否可靠”和“下一步该做什么”。

本次重构目标不是再做一轮视觉 polish，而是把 Sundial 设计成一个真正的跨端个人执行中枢。

## 2. 产品定位

Sundial 的产品定位：

> Sundial 是一个本地优先、跨端同步的个人执行中心。它帮助用户在任何设备上快速捕获任务，在桌面端清晰规划与整理，在移动端快速执行今天最重要的事项，并始终知道数据是否已经同步可信。

这个定位带来三个设计原则：

1. **先总览，再下钻**：首屏默认进入工作台，优先回答“当前所有待办处在什么状态”，而不是直接把用户推入“今天”。
2. **时间是产品骨架**：Sundial 这个名字应体现在每个视图的时间分布里，而不是只在“今天”页出现一条时间线。
3. **本地优先必须可感知**：同步状态要安静但可信；用户不应被同步打扰，但必须知道数据有没有安全到达另一端。

## 3. 融合视觉方向

用户确认“三个方向全部都需要”。合并规则如下：

| 来源方向 | 在最终设计中的职责 |
|---|---|
| 原生任务台账 | 作为主结构：左侧导航、中央任务列表、右侧详情检查器 |
| 时间感日计划 | 作为产品骨架：每个视图都有自己的上下文时间线 |
| 执行驾驶舱 | 作为状态总览：全部待办、待整理、逾期、今天、未来、同步状态 |

最终界面不应把三者并列堆叠，而应形成一套层级：

1. **左侧**负责范围选择和长期组织。
2. **顶部**负责当前视图的时间分布与状态认知。
3. **中央**负责任务执行与快速捕获。
4. **右侧**负责选中任务的上下文编辑。
5. **底部/角落**负责同步可信度与设置入口。

## 4. 桌面端信息架构

桌面端采用三栏生产力布局：

```text
AppWindow
├─ Sidebar                     260dp 左右
│  ├─ Brand
│  ├─ Search
│  ├─ TopLevelNav              工作台 / 列表
│  ├─ WorkbenchFilters         全部 / 今天 / 计划 / 已完成 / 垃圾箱
│  ├─ ListRows                 收件箱 / 用户列表
│  └─ SyncSettingsFooter
├─ MainLedger                  自适应
│  ├─ PageHeader
│  ├─ ContextTimeline
│  ├─ ScopeSummary
│  ├─ QuickAddComposer
│  └─ TaskSections
└─ DetailInspector             320-360dp，可按选中状态显示
   ├─ TaskTitleBlock
   ├─ Date/List fields
   ├─ Notes
   ├─ Subtasks
   └─ DestructiveAction
```

### 4.1 Sidebar

Sidebar 不再使用大号彩色智能列表 tile。它应是安静、密集、可扫描的导航栏。

内容顺序：

1. `Sundial` brand：小型太阳/日晷 logo + 产品名。
2. 搜索框：全局搜索，支持快捷键提示。
3. 顶层导航：
   - 工作台
   - 列表
4. 工作台筛选（仅在工作台模式下显示）：
   - 全部
   - 今天
   - 计划
   - 已完成
   - 垃圾箱
5. 列表行（仅在列表模式下显示）：
   - 收件箱
   - 用户创建列表
6. 底部：
   - `已同步 · 刚刚` / `同步中...` / `同步中断`
   - 设置图标
   - 可选手动同步按钮

交互要求：

- 每个导航 row 高度 36-40dp，桌面可点击区域不低于 32dp。
- 选中态使用浅橙 tint 背景 + 左侧 2dp 品牌色竖线，避免整块粗边框。
- count 右对齐，数字不应改变 row 宽度。
- hover 只改变背景，不改变布局。
- 顶层导航只保留“工作台 / 列表”，避免把时间筛选、状态筛选和用户列表全部摊在同一层。
- `收件箱` 是系统列表，排在列表模式第一位；它可以显示计数，但不应被当成普通自定义列表处理。
- `我的列表` 支持折叠；新增列表入口放在 section header 右侧，不占据主操作优先级。

### 4.2 MainLedger

MainLedger 是桌面首屏的核心。它必须在 5 秒内回答：

- 当前视图是什么？
- 任务主要堆在哪里？
- 哪些需要今天处理？
- 哪些还没有整理？
- 从哪里快速添加？

结构：

1. **PageHeader**
   - 主标题：`工作台`、`今天`、`计划`、`收件箱`、自定义列表名等。
   - 日期上下文：工作台显示今日日期；列表显示列表描述或整理状态。
   - 主操作：`添加待办`。
   - 次操作：排序/筛选、更多。

2. **ContextTimeline**
   - 每个 scope 都显示，但表达不同的“时间线”。
   - 它不是重复的 tab，也不是固定今日时间轴，而是当前视图的节奏摘要。
   - 点击时间线中的分段可以切换到对应筛选或滚动到对应 section，但不能取代主导航。
   - 当前 MVP 可以用已有 `dueDate`、`isCompleted`、`isTrashed`、`listId`、`createdAt`、`completedAt` 派生，不新增 schema。

3. **ScopeSummary**
   - 一行轻量概览，不是大卡片。
   - 工作台展示：`全部`、`今天`、`计划`、`已完成`、`待整理`。
   - 列表展示：当前列表内 `逾期`、`今天`、`本周`、`无日期`。
   - 在窄高度窗口或移动端可隐藏/折叠。

4. **QuickAddComposer**
   - 位于列表上方，靠近任务上下文。
   - 支持输入标题、日期、旗标、列表选择。
   - 初期可先点击后打开现有 `TodoFormDialog`，后续再演进为真正 inline composer。

5. **TaskSections**
   - `待办` section：高对比、可扫描。
   - `已完成` section：默认可折叠或视觉压缩。
   - 行分隔使用 1px divider，不把每行做成独立 card。

### 4.3 ContextTimeline 规则

时间线组件统一命名为 `ContextTimeline`。不同 scope 的表达如下：

| 当前视图 | 时间线内容 | 设计目的 |
|---|---|---|
| 工作台 / 全部 | 逾期、今天、未来 7 天、无日期、待整理 | 让用户看到全局压力分布 |
| 今天 | 06:00 / 12:00 / 18:00 / 24:00，当下时间、下一件、今日完成 | 支持当天执行 |
| 计划 | 今天、明天、本周、下周、以后 | 支持未来安排和日期清理 |
| 收件箱 | 待整理、已有日期、无日期、逾期 | 把收件箱明确成整理入口 |
| 自定义列表 | 逾期、今天、本周、以后、无日期 | 显示项目/列表内部节奏 |
| 已完成 | 今天完成、本周完成、最近完成 | 回顾而非安排，视觉上更轻 |
| 垃圾箱 | 最近删除、可恢复数量、永久删除入口 | 恢复/清理摘要，不伪装成执行视图 |

要求：

- `今天` 可以使用日内时间轴；其他视图不要硬套 06:00-24:00。
- 时间线下方不要再放重复的“添加待办”和分类 tab。
- 桌面端时间线可显示为横向节奏条；移动端显示为紧凑摘要条。
- 空数据时也要解释状态，例如“无待整理任务”“本周没有计划任务”，避免出现空白卡片。

### 4.4 DetailInspector

桌面端选中任务后显示右侧详情检查器，替代“点开之后只靠弹窗或右侧临时栏”的半成品感。

字段顺序：

1. 完成 checkbox + 标题。
2. 旗标/收藏。
3. 日期与时间。
4. 所属列表。
5. 备注。
6. 子任务。
7. 创建/更新时间。
8. 删除/移到垃圾箱。

交互要求：

- 详情栏只在宽屏显示；窄屏使用底部抽屉。
- 字段以 row group 呈现，轻分隔，不堆叠多层 card。
- 输入修改仍沿用现有即时写库模型，但需要避免每个字符都造成明显同步噪音；UI 侧同步反馈应聚合而非闪烁。
- 关闭详情使用 `Esc`、右上角 close、切换 scope。

### 4.5 核心概念语义

#### 收件箱

收件箱不是普通分类，也不是邮件隐喻的装饰。它的产品意义是 **待整理池 / Capture Inbox**：

- 快速新增任务时，如果用户没有选择列表，任务默认进入收件箱。
- 收件箱里的任务代表“还没有被正式归类或整理”。
- 工作台应显示 `待整理 N 项`，提醒用户定期清空收件箱。
- 收件箱应位于“列表”模式第一位，可以显示为 `收件箱`，在移动端或摘要文案中可补充 `待整理`。
- 收件箱不能删除、不能改名；自定义列表才支持名称、颜色、删除。
- 当收件箱长期堆积时，产品应鼓励用户添加日期、移动到项目列表，或完成/删除。

#### 列表

列表表示任务的长期归属，适合项目、领域、客户、工作流等稳定容器。列表不是时间筛选，也不是状态筛选。

- `今天`、`计划`、`完成`、`垃圾箱` 属于工作台筛选。
- `收件箱`、用户创建列表属于列表模式。
- 新增和详情页里的“列表”字段应被理解为归属，而不是顶部导航的一部分。

#### 旗标

当前 `flag` 是布尔值，不是完整标签系统。产品上应定义为 **重点 / 关注**：

- 旗标只表示“这件事需要我额外注意”。
- 旗标不等同于优先级，不承诺高/中/低排序。
- 旗标不等同于标签，不提供多标签、颜色标签或标签管理。
- 旗标任务可以在工作台中获得轻量强调；未来如需要，可增加“重点”筛选，但本期不把它做成顶层入口。
- 设置页不需要配置旗标，除非未来引入旗标默认行为、颜色或排序规则。

## 5. 移动端信息架构

移动端不是桌面缩小版，而是同一产品模型的单手执行形态。

```text
NarrowApp
├─ MobileTopBar
│  ├─ Scope title / date
│  ├─ Sync indicator
│  ├─ Search
│  └─ Settings
├─ ScopeFilterStrip / ListStrip
├─ ContextTimelineCompact
├─ QuickAddBar / FAB
├─ TaskList
├─ BottomPrimaryNav            工作台 / 列表
└─ DetailBottomSheet
```

### 5.1 MobileTopBar

- 显示当前 scope 标题。
- 工作台显示今日日期；列表显示当前列表名或 `收件箱 · 待整理`。
- 搜索和设置为右侧图标。
- 同步状态可以是小圆点/小图标，点击触发手动同步。

### 5.2 ScopeFilterStrip / ListStrip

- 工作台模式下显示二级筛选：`全部`、`今天`、`计划`、`完成`、`垃圾箱`。
- 列表模式下显示列表条：`收件箱`、用户列表。
- 二级筛选条是横向滚动 chip，不进入底部主导航。
- 当前选中项需要有明确视觉状态，但不能抢过任务列表。

### 5.3 ContextTimelineCompact

- 移动端只展示最必要信息：
  - 工作台：`逾期 1`、`今天 3`、`待整理 2`
  - 今天：`下一件 9:00`、`今日 3`、`完成 1`
  - 计划：`明天 2`、`本周 5`、`以后 4`
  - 收件箱：`待整理 4`、`无日期 3`
- 不显示完整桌面时间轴，避免挤占列表空间。
- 只有“今天”视图需要显示当天时间感；其他视图显示分布摘要。

### 5.4 QuickAdd

- 主入口应在拇指可达区域。
- 可以保留右下角 FAB，但列表顶部也需要轻量 `添加待办...` 行，防止用户不知道从哪里开始。
- FAB 点击打开现有表单，后续可演进为底部 composer。

### 5.5 BottomPrimaryNav

底部主导航只保留两个核心入口：

- 工作台
- 列表

规则：

- 选中态必须清楚，但不使用过重背景。
- 图标和文字对齐，触控目标不低于 48dp。
- `今天`、`计划`、`完成`、`垃圾箱` 不进入底部主导航，只作为工作台内筛选出现。
- 自定义列表不塞入底部导航，通过列表模式访问。

### 5.6 DetailBottomSheet

移动端详情使用底部抽屉，而不是全屏强跳转。

- 点任务：打开底部抽屉。
- 下滑/遮罩/返回键：关闭抽屉。
- 抽屉初始高度可接近全屏，但仍保留 sheet 语义。
- 表单项顺序与桌面 DetailInspector 一致，保证跨端学习成本低。

## 6. 视觉系统

### 6.1 设计气质

关键词：

- 原生
- 克制
- 清晰
- 温暖
- 有时间感
- 可信

明确避免：

- 大号彩色 tile 作为主导航。
- 网格纸背景。
- 每个 section 都做 card。
- 过强边框和大量橙色描边。
- 类营销页 hero。
- 玩具化 icon。

### 6.2 色彩

保留当前品牌橙，但降低使用面积。

建议 token：

| Token | 用途 |
|---|---|
| `brand` | 主操作、当前/下一件、选中强调 |
| `brandSubtle` | 选中背景、浅色提示 |
| `success` | 已同步、已完成 |
| `info` | 计划/时间辅助状态 |
| `surface` | 主内容面 |
| `surfaceAlt` | sidebar / hover / grouped area |
| `borderSubtle` | row divider |
| `textPrimary` | 标题和任务标题 |
| `textSecondary` | 日期、备注、辅助信息 |
| `textTertiary` | 已完成、占位、弱状态 |

当前 `LightRemColors` 可以演进，但不需要引入全新主题系统。先在既有 `DesignTokens.kt` 内扩展或重命名，逐步迁移组件。

### 6.3 排版

当前 `RemType` 的 18sp 标题不足以支撑桌面主标题层级。建议扩展：

| Token | 建议 | 用途 |
|---|---|---|
| `title24` | 24sp / SemiBold | 桌面主标题 |
| `title20` | 20sp / SemiBold | 移动标题、section title |
| `text16` | 16sp | 任务标题/详情标题 |
| `text14` | 14sp | 行正文 |
| `text12` | 12sp | 辅助信息、日期 |
| `label12` | 12sp / Medium | 标签和按钮 |

要求：

- 中文不可裁切。
- 任务标题单行 ellipsis，详情页允许多行编辑。
- 不使用负 letter spacing。
- 桌面列表密度应优于当前大空白布局。

### 6.4 形状与分隔

- 页面区域优先用空间、分组和 divider。
- list surface 可使用 8dp radius，但行内部不再做 card。
- Sidebar row 使用 8dp radius。
- Inspector field group 可使用 8dp radius 或纯 divider，不能 card 套 card。
- 主操作按钮可使用 8dp radius，保持产品温度但不过度圆润。

## 7. 组件设计

### 7.1 新增/重构组件

| 组件 | 职责 |
|---|---|
| `SundialScaffold` | 宽/窄屏整体布局分发 |
| `DesktopShell` | 桌面三栏布局 |
| `MobileShell` | 移动端 top/filter/timeline/list/nav/sheet 布局 |
| `SidebarNav` | 替代当前 SmartGrid 主导的 sidebar |
| `MainLedger` | 页面标题、上下文时间线、概览、任务 section |
| `ContextTimeline` | 桌面当前视图时间线 |
| `ContextTimelineCompact` | 移动端当前视图节奏摘要 |
| `ScopeFilterStrip` | 工作台内二级筛选 |
| `ListStrip` | 移动端列表模式下的列表选择 |
| `QuickAddComposer` | 快速添加入口 |
| `TaskSection` | 待办/已完成 section |
| `TaskRow` | 替代当前 `TodoRow` 的产品级 row |
| `DetailInspector` | 桌面右侧详情 |
| `DetailBottomSheet` | 移动端详情容器 |
| `SyncStatusFooter` | 同步状态和设置入口 |

### 7.2 复用组件

保留并升级：

- `RemCheckbox`
- `RemBadge`
- `RemButton`
- `RemIconButton`
- `RemTextField`
- `RemDialog`
- `RemDatePicker`
- `RemSyncIndicator`
- `RemIcons`

需要调整：

- `RemBadge` 应支持更细的 due/status 样式。
- `RemTextField` 应支持 quick-add compact variant。
- `RemIconButton` 需要稳定 32/36/44dp 三种尺寸。
- `RemCheckbox` 在桌面 row 中建议 16dp，在移动端 20dp。

## 8. 交互模型

### 8.1 桌面主路径

1. 用户打开应用。
2. 默认进入 `工作台 / 全部` 或上次 scope。
3. 顶部看到当前视图的 `ContextTimeline`，理解逾期、今天、未来、待整理的分布。
4. 在 QuickAdd 添加待办；未指定列表时默认落到收件箱，指定列表时落到当前列表。
5. 点击任务 row，右侧 DetailInspector 打开。
6. 在详情中编辑日期、备注、子任务或移动列表。
7. 勾选任务后，row 移入已完成 section；同步状态短暂显示同步中，然后回到已同步。

### 8.2 移动主路径

1. 用户打开应用，看到工作台。
2. 顶部显示工作台筛选条和当前视图的紧凑时间线。
3. 右下 FAB 或 quick add 行添加任务。
4. 点 row 打开底部详情抽屉。
5. 下滑/返回关闭。
6. 底部导航只在 `工作台 / 列表` 两个顶层入口间切换。
7. 下拉刷新触发 `syncNow()`。

### 8.3 任务 row 规则

row 内容从左到右：

1. 拖拽把手（桌面 hover 时可见；本期如无排序，可不实现）
2. checkbox
3. 标题 + note/subtask hint
4. due badge
5. flag（重点/关注，不作为标签系统）
6. overflow / detail affordance

状态：

- active：textPrimary，checkbox 空心。
- selected：浅品牌背景或左侧细线。
- due today：brand/warning badge。
- overdue：error badge。
- completed：textTertiary + strike-through，row 高度更紧凑。
- flagged：只做轻量强调，不改变完成/日期/列表的主排序语义。

### 8.4 完成区规则

- 已完成 section 在 `全部/今天/自定义列表` 下默认展开或半展开取决于数据量。
- 当 active 为 0 时，已完成可以展开展示，避免空白。
- 已完成行不得抢占 active 区视觉重心。

## 9. 数据与 ViewModel 影响

本次重构主要是 UI/UX，不重写仓库与同步架构。

允许的小幅 ViewModel 增强：

- 为 `ContextTimeline` 派生各 scope 的时间分布。
- 为 `ScopeSummary` 复用已有 count flows，并补充待整理/收件箱计数。
- 为 `selectedTodoId` 明确表达桌面 inspector 状态；可继续复用 `Route.Detail`，但实现上应避免宽屏与窄屏语义混乱。
- QuickAdd 初期继续调用 `createTodo()`；后续如做 inline composer，再扩展输入状态。

不做：

- 不新增数据表。
- 不实现拖拽排序。
- 不新增优先级字段，除非后续产品明确需要。
- 不把 `flag` 扩展为多标签或复杂优先级系统。
- 不重做同步核心。

### 9.1 桌面小组件数据模型

Sundial 后续需要支持 Android 和 macOS 桌面小组件，让用户不用打开应用也能看到今天的待办。为了避免平台各自实现一套业务逻辑，应先定义跨平台的今日快照模型：

```text
TodayWidgetSnapshot
├─ dateLabel
├─ pendingTodayCount
├─ completedTodayCount
├─ nextTaskTitle
├─ nextTaskDueLabel
├─ topTodayTasks[3-5]
├─ overdueCount
├─ inboxCount
└─ lastUpdatedAt
```

产品要求：

- 小组件只展示“今天一眼可见”的信息，不承担完整任务管理。
- 小尺寸展示：今天剩余数量 + 下一件待办。
- 中尺寸展示：今天待办 3-5 条 + 完成进度。
- 大尺寸展示：今日时间线摘要 + 逾期/待整理提醒。
- 点击任务打开应用内对应详情；点击添加入口打开新增待办。
- 小组件内容以本地数据为准，离线也应可读；同步状态可弱提示，不在小组件里制造焦虑。

平台边界：

- Android 使用 Jetpack Glance / App Widget，实现成本较低，可作为第一期。
- macOS 使用 WidgetKit + SwiftUI Widget Extension。现有 Compose Desktop/JVM 应用不能直接提供系统桌面小组件，需要新增原生 macOS extension 与共享数据通道。
- macOS 需要处理签名、bundle、共享容器或快照文件读取；它是独立工程任务，不应和普通 Compose Desktop UI 混为一谈。

## 10. 可访问性与人体工程学

### 10.1 桌面

- 所有 icon-only control 必须有 contentDescription。
- `Esc` 关闭详情/弹窗。
- `Cmd+F` 聚焦搜索（如果快捷键实现成本可控）。
- `Cmd+N` 或 `N` 快速添加（后续可做）。
- row 点击区域高度 40-48dp。
- checkbox 与 row click 要避免误触；checkbox 只切完成，row 只选中/打开详情。

### 10.2 移动

- 底部导航触控目标不低于 48dp。
- FAB 不遮挡最后一行任务；列表需要 bottom padding。
- 底部抽屉编辑区避让键盘。
- 搜索、设置、同步按钮不小于 44dp 可触达区域。
- 下拉刷新必须有视觉反馈。

### 10.3 视觉可读性

- 正文对比度目标至少接近 WCAG AA。
- `textLow` 不用于关键任务标题。
- 已完成状态不能只靠颜色表达，必须有 checkbox/strike-through/section 语义。
- 同步错误不能只靠红点，需在设置或 footer 中有可读文案。

## 11. 实现分期

### Phase 1：设计系统和桌面骨架

- 扩展 tokens：颜色、字体、尺寸、row heights。
- 重构 Sidebar：移除 SmartGrid 主导，改为“工作台 / 列表”两级 compact nav。
- 建立 DesktopShell：三栏布局。
- 建立 MainLedger：header、ContextTimeline、ScopeSummary、quick add、task sections。
- 建立 DetailInspector：复用 DetailScreen 逻辑但改成桌面 inspector 表现。

### Phase 2：上下文时间线与任务 row

- 实现 ContextTimeline 桌面版本。
- 实现 ContextTimelineCompact 移动版本。
- 为工作台、今天、计划、收件箱、自定义列表、已完成、垃圾箱分别定义派生状态。
- 重构 TodoRow 为 TaskRow。
- due badge、selected row、completed section polish。

### Phase 3：移动端产品化

- 重构 NarrowTopBar / NarrowBottomNav，底部仅保留工作台和列表。
- 增加工作台筛选条和列表选择条。
- 增加移动 quick add。
- 保持底部详情抽屉，但统一字段层级与桌面 inspector。
- 调整触控尺寸、底部 padding、键盘避让。

### Phase 4：状态、无障碍、验收

- 同步 footer 和状态文案统一。
- contentDescription 补全。
- 键盘/返回/Esc 行为统一。
- 桌面与移动截图验收。
- 测试 ViewModel 逻辑未回退。

### Phase 5：桌面小组件

- 定义 `TodayWidgetSnapshot` 生成逻辑。
- Android 端用 Glance 实现今天待办小组件。
- macOS 端规划 WidgetKit extension、共享容器和分发签名。
- 先验收 Android，小步推进 macOS 原生扩展。

## 12. 验收标准

### 12.1 产品体验验收

- 桌面首屏能清楚看到：工作台、今日日期、当前视图时间线、待办列表、完成区、添加入口、同步状态。
- 桌面点选任务后右侧详情出现，编辑路径清晰，不需要弹窗式跳转。
- Sidebar 看起来像成熟产品导航，不再像应用启动器。
- 移动端可以单手完成：切换工作台/列表、切换二级筛选、添加任务、打开/关闭详情、下拉同步。
- 收件箱的语义清楚：它是待整理池，不是普通分类。
- 旗标的语义清楚：它是重点/关注，不是标签系统或优先级系统。
- 每个 scope 都有自己的上下文时间线，而不是只有今天页显示时间线。
- 桌面和移动端看起来是同一产品系统，而不是两个拼凑界面。

### 12.2 工程验收

- 不改数据库 schema。
- 不破坏现有同步状态、设置页、日期解析、子任务能力。
- `./gradlew :shared:desktopTest` 通过。
- `./gradlew :androidApp:assembleDebug` 通过。
- 桌面端实际运行并截图检查。
- 移动窄屏布局通过截图或 Android 构建/模拟器检查；如模拟器不可用，需说明验证边界。
- Android 小组件一期需能从本地快照读取今天待办；macOS 小组件需有明确 WidgetKit extension 工程方案后再进入实现。

### 12.3 视觉验收

- 无大号彩色智能列表 tile。
- 无网格纸背景。
- 无 card 套 card。
- 无按钮/徽章文字裁切。
- 任务 row、section header、due badge、同步状态层级明确。
- active tasks 的视觉优先级高于 completed tasks。

## 13. 风险与取舍

- **右侧 DetailInspector 与现有 DetailScreen 复用边界**：直接复用可减少风险，但可能保留旧排版；推荐拆出 `DetailContent`，桌面和移动分别包容器。
- **ContextTimeline 数据派生**：当前模型只有 dueDate，没有独立 duration 或 schedule block；本期只做时间分布和 due time 节奏，不伪装成完整日历。
- **QuickAdd inline 化成本**：完整 inline composer 涉及日期/列表/flag 快速选择；第一期可做清晰入口，后续再升级。
- **视觉稿里的优先级/提醒字段**：当前数据模型未支持，本期真实实现不显示为可编辑能力；只在组件边界上保留未来扩展空间，避免虚假功能。
- **误触风险**：checkbox 与 row selection 必须分离；此前可访问性操作暴露出列表重排后索引易变，真实 UI 也要避免完成动作导致用户失去位置感。

## 14. 明确不做

- 不新增登录、多用户、协作。
- 不新增 kanban、日历全屏视图、拖拽排序。
- 不引入第三方 UI 框架或图标库。
- 不重写同步核心。
- 不实现完整提醒系统/通知调度。
- 不把移动端做成网页式响应式压缩版。

## 15. 后续计划入口

用户确认本规格后，进入实现计划，建议计划文件：

`docs/superpowers/plans/2026-08-12-sundial-product-ui-redesign-plan.md`

实现计划应按 Phase 1-4 拆解，并优先保证桌面主路径完整，再派生移动端。
