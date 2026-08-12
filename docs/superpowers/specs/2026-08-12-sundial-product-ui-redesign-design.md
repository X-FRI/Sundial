# Sundial 产品级 UI/UX 重构设计规格

> 日期：2026-08-12 · 状态：已获用户方向确认（融合方向待实现）  
> 视觉方向：原生任务台账 × 今日节奏 × 执行状态总览  
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

1. **先执行，再管理**：首屏优先回答“现在/今天该做什么”，而不是展示所有功能入口。
2. **时间是产品灵魂**：Sundial 这个名字应体现在“今日节奏”“下一件事”“到期状态”的体验里，而不是只停留在 logo。
3. **本地优先必须可感知**：同步状态要安静但可信；用户不应被同步打扰，但必须知道数据有没有安全到达另一端。

## 3. 融合视觉方向

用户确认“三个方向全部都需要”。合并规则如下：

| 来源方向 | 在最终设计中的职责 |
|---|---|
| 原生任务台账 | 作为主结构：左侧导航、中央任务列表、右侧详情检查器 |
| 时间感日计划 | 作为产品灵魂：今日节奏、下一件事、到期时间聚合 |
| 执行驾驶舱 | 作为状态总览：待办、计划、完成、收件箱、同步状态的轻量概览 |

最终界面不应把三者并列堆叠，而应形成一套层级：

1. **左侧**负责范围选择和长期组织。
2. **顶部**负责今日/当前状态认知。
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
│  ├─ SmartListRows
│  ├─ UserListRows
│  └─ SyncSettingsFooter
├─ MainLedger                  自适应
│  ├─ PageHeader
│  ├─ TodayRhythm
│  ├─ CompactOverview
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
3. 智能列表：
   - 今天
   - 计划
   - 全部
   - 已完成
   - 垃圾箱
4. 我的列表：
   - 收件箱
   - 用户创建列表
5. 底部：
   - `已同步 · 刚刚` / `同步中...` / `同步中断`
   - 设置图标
   - 可选手动同步按钮

交互要求：

- 每个导航 row 高度 36-40dp，桌面可点击区域不低于 32dp。
- 选中态使用浅橙 tint 背景 + 左侧 2dp 品牌色竖线，避免整块粗边框。
- count 右对齐，数字不应改变 row 宽度。
- hover 只改变背景，不改变布局。
- `我的列表` 支持折叠；新增列表入口放在 section header 右侧，不占据主操作优先级。

### 4.2 MainLedger

MainLedger 是桌面首屏的核心。它必须在 5 秒内回答：

- 今天是什么日期？
- 当前节奏里下一件事是什么？
- 还有几项待办？
- 哪些已经完成？
- 从哪里快速添加？

结构：

1. **PageHeader**
   - 主标题：`今天`、`全部待办`、`计划`、自定义列表名等。
   - 日期上下文：`2026-08-12 · 星期三`。
   - 主操作：`添加待办`。
   - 次操作：排序/筛选、更多。

2. **TodayRhythm**
   - 只在 `今天`、`全部`、有今日任务的自定义列表中显示。
   - 不是完整日历，而是一条轻量时间带。
   - 显示 `现在`、`下一件`、今日关键到期点。
   - 当前 MVP 可先用静态/派生数据实现：最早未完成 due time、当前时间、今日完成时间。

3. **CompactOverview**
   - 一行轻量概览，不是大卡片。
   - 展示：`待办`、`计划`、`已完成`、`收件箱`。
   - 点击可切换 scope。
   - 在窄高度窗口或移动端可隐藏/折叠。

4. **QuickAddComposer**
   - 位于列表上方，靠近任务上下文。
   - 支持输入标题、日期、旗标、列表选择。
   - 初期可先点击后打开现有 `TodoFormDialog`，后续再演进为真正 inline composer。

5. **TaskSections**
   - `待办` section：高对比、可扫描。
   - `已完成` section：默认可折叠或视觉压缩。
   - 行分隔使用 1px divider，不把每行做成独立 card。

### 4.3 DetailInspector

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

## 5. 移动端信息架构

移动端不是桌面缩小版，而是同一产品模型的单手执行形态。

```text
NarrowApp
├─ MobileTopBar
│  ├─ Scope title / date
│  ├─ Sync indicator
│  ├─ Search
│  └─ Settings
├─ TodayRhythmCompact
├─ QuickAddBar / FAB
├─ TaskList
├─ BottomSmartNav
└─ DetailBottomSheet
```

### 5.1 MobileTopBar

- 显示当前 scope 标题。
- 今天/计划页显示日期上下文。
- 搜索和设置为右侧图标。
- 同步状态可以是小圆点/小图标，点击触发手动同步。

### 5.2 TodayRhythmCompact

- 移动端只展示最必要信息：
  - `下一件 9:00`
  - `今日 3 项`
  - `已完成 3`
- 不显示完整时间轴，避免挤占列表空间。

### 5.3 QuickAdd

- 主入口应在拇指可达区域。
- 可以保留右下角 FAB，但列表顶部也需要轻量 `添加待办...` 行，防止用户不知道从哪里开始。
- FAB 点击打开现有表单，后续可演进为底部 composer。

### 5.4 BottomSmartNav

保留五个核心入口：

- 今天
- 计划
- 全部
- 已完成
- 垃圾箱

规则：

- 选中态必须清楚，但不使用过重背景。
- 图标和文字对齐，触控目标不低于 48dp。
- 自定义列表不塞入底部导航，通过列表页或侧边入口访问。

### 5.5 DetailBottomSheet

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
| `MobileShell` | 移动端 top/rhythm/list/nav/sheet 布局 |
| `SidebarNav` | 替代当前 SmartGrid 主导的 sidebar |
| `MainLedger` | 页面标题、今日节奏、概览、任务 section |
| `TodayRhythm` | 桌面时间节奏条 |
| `TodayRhythmCompact` | 移动端节奏摘要 |
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
2. 默认进入 `今天` 或上次 scope。
3. 顶部看到今日节奏与下一件事。
4. 在 QuickAdd 添加待办，默认落到当前 scope/list。
5. 点击任务 row，右侧 DetailInspector 打开。
6. 在详情中编辑日期、备注、子任务或移动列表。
7. 勾选任务后，row 移入已完成 section；同步状态短暂显示同步中，然后回到已同步。

### 8.2 移动主路径

1. 用户打开应用，看到今天。
2. 顶部显示下一件和今日进度。
3. 右下 FAB 或 quick add 行添加任务。
4. 点 row 打开底部详情抽屉。
5. 下滑/返回关闭。
6. 底部导航切换智能列表。
7. 下拉刷新触发 `syncNow()`。

### 8.3 任务 row 规则

row 内容从左到右：

1. 拖拽把手（桌面 hover 时可见；本期如无排序，可不实现）
2. checkbox
3. 标题 + note/subtask hint
4. due badge
5. flag
6. overflow / detail affordance

状态：

- active：textPrimary，checkbox 空心。
- selected：浅品牌背景或左侧细线。
- due today：brand/warning badge。
- overdue：error badge。
- completed：textTertiary + strike-through，row 高度更紧凑。

### 8.4 完成区规则

- 已完成 section 在 `全部/今天/自定义列表` 下默认展开或半展开取决于数据量。
- 当 active 为 0 时，已完成可以展开展示，避免空白。
- 已完成行不得抢占 active 区视觉重心。

## 9. 数据与 ViewModel 影响

本次重构主要是 UI/UX，不重写仓库与同步架构。

允许的小幅 ViewModel 增强：

- 为 `TodayRhythm` 派生今日 due items。
- 为 `CompactOverview` 复用已有 count flows。
- 为 `selectedTodoId` 明确表达桌面 inspector 状态；可继续复用 `Route.Detail`，但实现上应避免宽屏与窄屏语义混乱。
- QuickAdd 初期继续调用 `createTodo()`；后续如做 inline composer，再扩展输入状态。

不做：

- 不新增数据表。
- 不实现拖拽排序。
- 不新增优先级字段，除非后续产品明确需要。
- 不重做同步核心。

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
- 重构 Sidebar：移除 SmartGrid 主导，改为 compact row nav。
- 建立 DesktopShell：三栏布局。
- 建立 MainLedger：header、overview、quick add、task sections。
- 建立 DetailInspector：复用 DetailScreen 逻辑但改成桌面 inspector 表现。

### Phase 2：今日节奏与任务 row

- 实现 TodayRhythm 桌面版本。
- 实现 TodayRhythmCompact 移动版本。
- 重构 TodoRow 为 TaskRow。
- due badge、selected row、completed section polish。

### Phase 3：移动端产品化

- 重构 NarrowTopBar / NarrowBottomNav。
- 增加移动 quick add。
- 保持底部详情抽屉，但统一字段层级与桌面 inspector。
- 调整触控尺寸、底部 padding、键盘避让。

### Phase 4：状态、无障碍、验收

- 同步 footer 和状态文案统一。
- contentDescription 补全。
- 键盘/返回/Esc 行为统一。
- 桌面与移动截图验收。
- 测试 ViewModel 逻辑未回退。

## 12. 验收标准

### 12.1 产品体验验收

- 桌面首屏能清楚看到：当前 scope、今日日期、下一件事/今日节奏、待办列表、完成区、添加入口、同步状态。
- 桌面点选任务后右侧详情出现，编辑路径清晰，不需要弹窗式跳转。
- Sidebar 看起来像成熟产品导航，不再像应用启动器。
- 移动端可以单手完成：切换智能列表、添加任务、打开/关闭详情、下拉同步。
- 桌面和移动端看起来是同一产品系统，而不是两个拼凑界面。

### 12.2 工程验收

- 不改数据库 schema。
- 不破坏现有同步状态、设置页、日期解析、子任务能力。
- `./gradlew :shared:desktopTest` 通过。
- `./gradlew :androidApp:assembleDebug` 通过。
- 桌面端实际运行并截图检查。
- 移动窄屏布局通过截图或 Android 构建/模拟器检查；如模拟器不可用，需说明验证边界。

### 12.3 视觉验收

- 无大号彩色智能列表 tile。
- 无网格纸背景。
- 无 card 套 card。
- 无按钮/徽章文字裁切。
- 任务 row、section header、due badge、同步状态层级明确。
- active tasks 的视觉优先级高于 completed tasks。

## 13. 风险与取舍

- **右侧 DetailInspector 与现有 DetailScreen 复用边界**：直接复用可减少风险，但可能保留旧排版；推荐拆出 `DetailContent`，桌面和移动分别包容器。
- **TodayRhythm 数据派生**：当前模型只有 dueDate，没有独立 duration 或 schedule block；本期只做 due time 节奏，不伪装成日历。
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
