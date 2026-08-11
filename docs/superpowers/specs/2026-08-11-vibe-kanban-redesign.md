# Reminders 生产级 UI 重构设计规格（vibe-kanban 哲学）

日期：2026-08-11
状态：用户已确认全盘采用 vibe-kanban 设计哲学
基础：`2026-08-11-ui-redesign-design.md`（组件库基座）、`2026-08-11-ui-polish-design.md`（数据层基座，flag 已落地）
参考：https://github.com/BloopAI/vibe-kanban（设计哲学来源，源码级分析）

## 1. 设计哲学（vibe-kanban 移植）

**一句话：生产工具 = 背景阶梯分层 + 单一品牌色 + 直角几何 + 高密度信息 + 阴影只属于浮层。**

六条纪律（全部强制）：

1. **层级 = 背景阶梯，不是边框/阴影**。三个语义背景：`bg-primary`（最底层，白/13%）→ `bg-secondary`（次级，95%/11%）→ `bg-panel`（面板/徽章，89%/16%）。层次完全由底色阶差表达。
2. **边框只出现在容器边界**：卡片、输入框、按钮组、浮层。界面内部（行间、分区内）绝无分隔线。
3. **阴影是"脱离文档流"的专属信号**：只有浮层（对话框/弹出菜单）有阴影，其余一律无阴影。
4. **单色系灰阶 + 一点品牌色**。文本三级 high/normal/low（5%/20%/39% 明度），唯一彩色 = 品牌橙 `#EA7A2A` + 状态色（error/success/warning/info）+ 列表色点。
5. **直角几何**：圆角 2px（radius 0.125rem 语义）全局统一，无 r6/r7/r8/r10 混用。
6. **交互纪律**：hover/按压 = 纯背景色过渡（150-200ms ease），无位移、无缩放、无阴影变化。图标全部统一为自绘线性 1.8u stroke（现有 RemIcons 体系保留）。

## 2. 数据层（现状保留，零改动）

- `flag` 列已落地（Task 1-2）；`completedAt`、`due_date`（含时间）已存在。
- 本轮**不改任何数据模型、仓库、ViewModel 逻辑**，纯视觉层重构。

## 3. 设计 Token 全量重写

### 3.1 颜色（DesignTokens.kt 重写）

RemColors 重构为 vibe-kanban 语义字段（所有值来自 HSL 精确转换）：

**亮色：**

| Token | 值 | 对应 vibe |
|---|---|---|
| `bgPrimary` | `#FFFFFF` | bg-primary |
| `bgSecondary` | `#F2F2F2` | bg-secondary |
| `bgPanel` | `#E3E3E3` | bg-panel |
| `textHigh` | `#0D0D0D` | text-high |
| `textNormal` | `#333333` | text-normal |
| `textLow` | `#636363` | text-low |
| `border` | `#D9D9D9` | border |
| `inputBg` | `#F5F5F5` | input |
| `brand` | `#EA7A2A` | brand |
| `brandHover` | `#E79255` | brand-hover |
| `brandSecondary` | `#AC5111` | brand-secondary |
| `error` | `#D25151` | destructive |
| `success` | `#54B04F` | success |
| `warning` | `#DB7706` | warning |
| `info` | `#3C83F6` | info |
| `focusRing` | `#EA7A2A` | ring = brand |

**暗色：**

| Token | 值 |
|---|---|
| `bgPrimary` | `#212121` |
| `bgSecondary` | `#1C1C1C` |
| `bgPanel` | `#292929` |
| `textHigh` | `#F5F5F5` |
| `textNormal` | `#C4C4C4` |
| `textLow` | `#8F8F8F` |
| `border` | `#333333` |
| `inputBg` | `#333333` |
| `brand` | `#EA7A2A` |
| `brandHover` | `#E79255` |
| `brandSecondary` | `#AC5111` |
| `error` | `#FF6B6B`（亮色下可见的暗色错误色，≥3:1 对比） |
| `success` | `#54B04F` |
| `warning` | `#E0913E` |
| `info` | `#3C83F6` |
| `focusRing` | `#E79255` |

**删除的 token**：`selectedBg`、`rowDivider`、`checkboxBorder`、`dialogBg`、`hoverActionBg`、`todayBadgeBg/Text`、`upcomingBadgeBg/Text`、`overdueBadgeBg/Text`、`cardBg`、`cardBorder`、`windowBg`、`accent`、`danger`、`sidebarBg`、`contentBg`——全部由新语义字段替代（选中等价表见 §6）。

**列表色点**（ListColorKeys/ListColorOf）保留现有 7 色（内容数据色，非品牌色）。

### 3.2 尺寸与间距（RemSpacing 重写）

vibe-kanban 节奏：half=4px / base=8px / plusfifty=12px / double=16px：

```kotlin
object RemSpacing {
    val s2 = 2.dp      // 图标与文字微距
    val s4 = 4.dp      // half
    val s8 = 8.dp      // base
    val s12 = 12.dp    // plusfifty
    val s16 = 16.dp    // double
}
```

### 3.3 圆角（RemRadii 重写）

```kotlin
object RemRadii {
    val r2 = 2.dp      // 全局唯一圆角（0.125rem 语义）
}
```

删除 r4/r6/r7/r8/r10。所有 `RoundedCornerShape(RemRadii.rX)` 改用 `RoundedCornerShape(RemRadii.r2)`。

### 3.4 字体（RemType 重写）

vibe-kanban 密度语义（Inter/IBM Plex 系，高密度工具字号）：

```kotlin
object RemType {
    val text10 = 10.sp  // 徽章/计数/等宽时间（高密度）
    val text12 = 12.sp  // 次级文本/备注预览/辅助
    val text14 = 14.sp  // 正文/行标题（500 字重用于行）
    val text16 = 16.sp  // 界面标题/按钮
    val title18 = 18.sp // 页面大标题（今天/计划/搜索）
    val label10 = 10.sp // 分区标签/侧边栏分组（600）
    val label12 = 12.sp // 侧边栏列表名/对话框标题（600）
}
```

等宽用于时间/计数徽章：`FontFamily.Monospace`（时间 "15:00"、"3" 计数）。

### 3.5 阴影（唯一两处）

```kotlin
// 仅浮层使用：0 2px 8px rgba(0,0,0,0.12)（亮）/ rgba(0,0,0,0.5)（暗）
val RemShadows.popover = Shadow(...)  // 或 drawShadow 统一实现
```

## 4. 组件规范（commonMain/ui/components/ 重写）

### 4.1 RemBadge（徽章/计数胶囊）

- 底色 `bgPanel`、文字 `textLow`（常态）；状态色版 = 该色 8% 透明底 + 该色文字（error/success/warning/info）
- 圆角 r2、内边距 h4 v1、字号 text10、`FontFamily.Monospace` 用于数字
- 颜色点（列表色）8px 圆点 + name，仿 KanbanBadge
- 移除 onClick 版的自定义 bg 参数（由 color: Color? 语义参数替代）

### 4.2 RemButton / RemIconButton

vibe-kanban PrimaryButton/Button 语义：

| 变体 | 常态 | hover | 圆角 |
|---|---|---|---|
| `default`（品牌） | `brand` 底 + 白字 | `brandHover` | r2 |
| `ghost` | 透明 + `textNormal` | `bgSecondary` | r2 |
| `danger` | 透明 + `error` 字 + 1px `border` | `error` 8% 底 | r2 |

- 高度 29dp（vibe cta 高）、内边距 h8 v4、字号 text12 600
- 图标按钮 20dp 命中区、图标 14dp
- 禁用 = `textLow` 40% 透明度
- 全部 `transition` 200ms 背景色（Compose `animateColorAsState` 200ms tween）

### 4.3 RemTextField

- 常态：1px `border` + `inputBg` 底 + r2、文字 text14、占位 `textLow`
- 聚焦：边框 `brand` + 1px、无外发光
- 无 filled/底色双模式（去掉 filled 参数视觉差异，统一输入框风格）

### 4.4 RemCheckbox

- 保留圆形（内容语义）、保留 spring 勾选动画（微交互例外）
- 未选中边框 `textLow`、hover 边框 `textHigh`、选中填充 `brand`
- 移除 `checkboxBorder` token 依赖

### 4.5 RemDialog / 浮层

- 底色 `bgPrimary`、1px `border`、r2、**阴影 popover 级**（唯一阴影使用点）
- 标题 text14 600；scrim `rgba(0,0,0,0.4)`（亮）/ `rgba(0,0,0,0.6)`（暗）
- 内容间距 s8 节奏

### 4.6 RemEmptyState

- 保留 Canvas 线稿插画，改直角几何：卡片线稿矩形 r2、勾
- 颜色：线条 `textLow` 35%、勾 `brand`
- 文案：标题 text16 600 `textHigh`、副行 text12 `textLow`

### 4.7 RemIcons

- 保留现有 1.8u 线性风格（符合哲学"图标统一笔画"）
- 新增 `IconName.DotsThree`（更多操作，KanbanCard 同款）

## 5. 布局与界面（App.kt / Sidebar / TodoListScreen / DetailScreen / NarrowShell）

### 5.1 三栏结构

- 侧边栏 220dp：`bgPrimary`（最亮层）
- 列表区：`bgSecondary`（次级层）——**卡片浮起于其上的 bgPrimary**
- 详情面板 340dp：`bgPrimary` + 左 1px `border`（容器边界）
- 窗口底色 = `bgSecondary`（删除 windowBg 概念）

### 5.2 侧边栏（Sidebar.kt 重写）

- 顶部品牌："Reminders" text16 600 `textHigh`
- 搜索框：RemTextField 新样式
- ScopeRow：
  - 选中 = `bgSecondary` 底 + `textHigh` 图标/文字 + 计数 `brand`
  - hover = `bgSecondary` 40%
  - 行高 28dp、图标 14dp `textLow`、名称 text12
  - "今天"计数 = `error` 8% 底胶囊（红字 `error`）
- 分组："列表" label10 600 `textLow` 大写语义（中文免大写）、可折叠（保留现有折叠）
- ListRow：色点 8px、名称 text12、计数 `textLow` Monospace；hover 显示 ＋/垃圾桶（保留现有，改样式）
- "添加列表"：ghost 按钮风格，品牌橙文字 + ＋ 图标
- 分隔：不用整行分隔线——分组间距 s8 留白

### 5.3 列表页（TodoListScreen.kt 重写）

- **头部**：
  - 大标题 title18 600 `textHigh`（今天/计划/搜索…）
  - 副行 text12 `textLow`（日期或计数，Monospace 计数）
  - ＋按钮：ghost 方形 26dp r2，hover `bgSecondary`
  - 计数胶囊（"3 项"）：`bgPanel` + `textLow`，Monospace
- **快速输入**：RemTextField 新样式，占位 text12 `textLow`
- **分区（smart 视图）**：
  - 分区标题 = label10 600 `textHigh` + 计数胶囊（状态色 8% 底）
  - **无横线**（删除 SectionHeader 的 1px 延伸线）
  - 分区内容 = `bgPrimary` 卡片 r2 **无边框无阴影**，卡片间距 s8
  - 卡片内部行间无分隔线，行间距 0，行 padding v6
- **普通列表**：待办卡片 + 已完成卡片（保留折叠，改样式同上）
- **TodoRow（双行式保留）**：
  - 第一行：勾选 14dp + 标题 text14 500 `textHigh`（完成 = `textLow` + 划线）+ 旗标 14dp `warning` + 时间徽章
  - 第二行：备注 text12 `textLow` 截断 + 子任务计数 `textLow` Monospace
  - hover = `bgSecondary` 40%（整行）
  - 时间徽章：状态色 8% 底 + 状态色字（过期 error/今天 warning/未来 textLow），时间部分 Monospace
  - 无行底部分隔线

### 5.4 详情页（DetailScreen.kt 重写）

- 面板底色 `bgPrimary`，左 1px `border`
- 标题行：勾选 + 内联编辑 text16 600 + 时间徽章（同 §5.3）
- 备注块：`inputBg` 底 r2 无边框（编辑态同），文字 text12
- 元数据行（旗标/日期/列表/子任务）：图标 14dp `textLow` + 标签 text12 `textNormal` + 值 text12；**行间无分隔线**，行高 32dp，hover = `bgSecondary` 40%
- 旗标值：`warning`（已标记）/ `textLow`（未标记）
- 底部操作区：ghost 移到列表 + danger 移到垃圾箱（§4.2 变体）
- 完成时间/创建时间：text12 `textLow`
- RemDatePicker：日历格子 r2，选中日 `brand` 底白字，今天 `brand` 字；时间行步进器样式更新

### 5.5 窄屏（NarrowShell.kt）

- 顶部栏 `bgPrimary` + 底部导航 `bgPrimary` + 1px `border` 顶线（容器边界）
- 底部导航项：图标 16dp `textLow`、选中 `brand`；文字 text10
- 其余组件继承新样式

## 6. 旧 token → 新 token 映射表（实施时逐处替换）

| 旧 | 新 |
|---|---|
| `selectedBg` | `bgSecondary`（选中/hover 底） |
| `rowDivider` | 删除（无分隔线）；容器边界用 `border` |
| `checkboxBorder` | `textLow` |
| `dialogBg` | `bgPrimary` |
| `hoverActionBg` | `bgSecondary` |
| `todayBadgeBg/Text` | `warning` 8% 底 / `warning` 字 |
| `upcomingBadgeBg/Text` | `bgPanel` / `textLow` |
| `overdueBadgeBg/Text` | `error` 8% 底 / `error` 字 |
| `cardBg/cardBorder` | `bgPrimary`（无边框） |
| `windowBg` | `bgSecondary` |
| `accent` | `brand`（品牌橙） |
| `danger` | `error` |
| `sidebarBg/contentBg` | `bgPrimary` / `bgSecondary` |
| `textPrimary/textSecondary/textTertiary` | `textHigh` / `textNormal` / `textLow` |
| `flagColor` | `warning` |

## 7. 暗色模式（同精度）

- 三层背景阶梯：`#212121` / `#1C1C1C` / `#292929`——注意暗色下**背景阶梯反向**（面板比主背景亮一档），与 vibe 一致
- 文本三级：`#F5F5F5` / `#C4C4C4` / `#8F8F8F`
- 边框 `#333333`、输入 `#333333`
- 品牌橙保持 `#EA7A2A`（同亮色），hover 更亮 `#E79255`
- 状态色暗色变体按 §3.1 表

## 8. 动效

- hover/按压：`animateColorAsState` 150-200ms tween（背景/文字颜色过渡）
- 勾选 spring：保留（现有 RemCheckbox）
- 面板切换：保留现有 AnimatedVisibility（150ms fade+slide）
- 无位移、无缩放、无阴影过渡

## 9. 测试

- 数据层无改动：现有 52 测试保持全绿
- 新增测试：无（纯视觉重构；不引入 UI 逻辑变更）
- 验证：`./gradlew :shared:desktopTest --rerun-tasks :androidApp:assembleDebug` BUILD SUCCESSFUL + 手工验收清单

## 10. 手工验收清单（重构后）

1. 三栏层次：侧边栏白 / 列表浅灰 / 详情白+左边框
2. 卡片：无边框无阴影，仅底色阶差
3. 行间无任何分隔线
4. 圆角全部 2px
5. 唯一彩色 = 品牌橙 + 状态色 + 列表色点
6. 时间/计数等宽字体
7. 浮层（对话框）有阴影，其余无阴影
8. hover = 纯背景色变化，150-200ms
9. 暗色三层阶梯清晰（面板亮于背景）
10. 全部旧交互保留（折叠/悬停按钮/拖拽无/快速输入/日期选择/旗标）

## 11. 不做（明确排除）

- 键盘快捷键（用户既定排除）
- 数据模型/仓库/ViewModel 改动
- 拖拽排序、右键菜单
- 移动端窄屏重设计（仅样式继承）
- 字体文件引入（用系统字体 + Monospace 近似，避免新增依赖）
