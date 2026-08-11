# Reminders UI 重构设计（B 方向：Reminders 结构 × 现代轻量）

日期：2026-08-11
状态：已获用户批准（方向 / 字体 / 色板 / 布局数字 / 架构均逐项确认）

## 1. 背景与目标

MVP（22 提交，49 测试全绿）功能完整，但 UI 基于 Material3 默认组件且混用 emoji 图标，导致：

- 详情页排版混乱（无字体规范、字号混用、emoji 渲染跨平台不一致）
- 交互反馈缺失（无悬停/按压/焦点状态）
- 布局与间距无节奏
- 路由缺陷：宽屏下点侧边栏不关闭详情（与 macOS Reminders 行为不符）
- 窄屏底部导航是空壳

目标：**所有平台统一 UI**，彻底移除 Material Design 观感，自建轻量设计系统（commonMain 自绘组件 + token 驱动），风格 = "Reminders 结构 × 现代轻量"。

## 2. 设计基调（已确认）

| 维度 | 决策 |
|---|---|
| 方向 | B：三栏结构与苹果交互保留，更扁平、更白、间距更松、排版对比更强 |
| 字体 | 平台系统字体（macOS SF Pro 观感 / Android Roboto），FontFamily.Default，不打包字体 |
| 图标 | Canvas 自绘线性矢量图标（SF Symbols 观感），**全项目消灭 emoji 图标** |
| 数据层 | 零改动；仅 MainViewModel.selectScope 增加 `back()` |

## 3. 色板（token，已确认）

### 亮色
| token | 值 | 用途 |
|---|---|---|
| sidebarBg | #FFFFFF | 侧边栏 |
| contentBg | #FAFAFC | 列表/内容区 |
| selectedBg | #F2F2F7 | 侧边栏选中胶囊、列表悬停、行选中 |
| rowDivider | #F0F0F4 | 1px 分割线 |
| textPrimary | #111111 | 主文字 |
| textSecondary | #3C3C43 | 次文字 |
| textTertiary | #8E8E93 | 弱文字/占位 |
| accent | #0A84FF | 强调（焦点、勾选、链接） |
| danger | #FF3B30 | 危险操作 |
| checkboxBorder | #C7C7CC | 未选勾选圆边框 |

### 暗色
| token | 值 | 用途 |
|---|---|---|
| sidebarBg | #1C1C1E | 侧边栏 |
| contentBg | #2C2C2E | 内容区 |
| selectedBg | #3A3A3C | 选中/悬停 |
| rowDivider | #38383A | 分割线 |
| textPrimary | #FFFFFF | 主文字 |
| textSecondary | #D1D1D6 | 次文字 |
| textTertiary | #8E8E93 | 弱文字 |
| accent | #0A84FF | 强调 |
| danger | #FF453A | 危险 |

### 列表配色（经典 7 色，暗色下亮度 +2 档）
#FF3B30 / #FF9500 / #FFCC00 / #34C759 / #007AFF / #AF52DE / #8E8E93

## 4. 布局与尺寸（token，已确认）

| 项 | 值 |
|---|---|
| 侧边栏宽 | 220dp |
| 详情栏宽 | 340dp |
| 列表栏 | 自适应（weight 1f） |
| 宽屏阈值 | ≥900dp 三栏 |
| 侧边栏行高 | 28dp，选中胶囊圆角 7dp |
| 列表行高 | 36dp |
| 勾选圆 | 16dp（子任务 12dp） |
| 快速输入条 | 高 32dp，圆角 7dp |
| 栏内边距 | 16dp |
| 分割线 | 1px rowDivider |
| 日期徽章 | 浅灰底（selectedBg）胶囊，圆角 4dp |

## 5. 排版阶梯（token，系统字体）

| 层级 | 尺寸/字重 | 用途 |
|---|---|---|
| 12 | 12sp/400 | 徽章、弱文字 |
| 13 | 13sp/400 | 列表行文字、详情行 |
| 15 | 15sp/700 | 详情标题 |
| 17 | 17sp/800（letter-spacing -0.3） | 栏标题 |
| 20 | 20sp/800 | 空状态主文案 |
| 字重 | 400 常规 / 600 强调 / 700-800 标题 | |

## 6. 组件清单（commonMain 自绘，无 Material3）

新建 `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/`：

| 组件 | 说明 | 三态 |
|---|---|---|
| RemCheckbox | 圆形勾选框，未选=空心圆（checkboxBorder，1.5px），选中=accent 填充+白勾，spring 弹性动画 200-250ms | hover 边框加深 / press 缩放 / focus 焦点环 |
| RemBadge | 日期徽章（calendar 图标 + 时间文案） | — |
| RemTextField | 快速输入条：占位灰字，focus 时 2px accent 焦点环 | hover/focus |
| RemButton / RemIconButton | 文字按钮与图标按钮 | hover selectedBg / press 90% 缩放 / focus |
| RemDialog | 自绘对话框：scrim 50% 黑、圆角 8dp、白底、标题 15/700、按钮 RemButton | — |
| RemIcons | Canvas 自绘线性图标（1.5-2px 描边统一）：calendar、checkmark、trash、search、plus、close、chevron-back、list-symbol、inbox、colored-dot（列表色圆点） | — |
| RemEmptyState | 空状态：图标 + 20sp 主文案 + 12sp 次文案 | — |

**替换映射**（现有 Material3 用法 → 新组件）：
- AlertDialog（删除列表/彻底删除确认）→ RemDialog
- TextButton / MaterialButton → RemButton
- 所有 emoji（🗑✓📅🗂◉🗓▦＋✕）→ RemIcons
- MaterialTheme.typography/colors → DesignTokens

## 7. 布局结构（宽屏 ≥900dp）

```
Row(fillMaxSize)
├─ Sidebar (220dp)：智能列表（今天/计划/全部待办/已完成/垃圾箱，计数）+ 分割线 + 用户列表（彩色圆点）
│   ├─ 选中项：selectedBg 胶囊 7dp
│   ├─ 新增列表 + 删除列表按钮（图标型，hover 显示）
│   └─ 悬停行：selectedBg 淡入 150ms
├─ TodoListScreen (weight 1f)：栏标题 17/800 + 完成计数 + 快速输入条 + 列表行
│   ├─ 行：RemCheckbox + 标题 13/400 + 日期徽章（右对齐）+ 1px 分割线
│   ├─ 已完成行：标题划线（删除线）+ textTertiary
│   ├─ 子任务缩进 16dp，父行 chevron 指示
│   └─ 空状态：RemEmptyState（搜索无结果 / 列表为空 区分文案）
└─ DetailScreen (340dp)：RemCheckbox + 标题 15/700 + ✕（返回）
    ├─ 备注：可编辑行，空时灰色占位
    ├─ 日期行：RemBadge + 清除按钮
    ├─ 列表行：彩色圆点 + 名称（点击切换）
    ├─ 子任务：缩进列表 + 输入框（enter 添加）+ 删除
    └─ 分区用 1px 分割线
```

### 窄屏（<900dp，Android）
- 顶栏：栏标题 + 搜索图标按钮（点击展开搜索框）
- 主体：列表视图
- 底部导航：今天/计划/全部待办/已完成/垃圾箱（5 项，图标+文字，选中 accent 高亮）——替换现有 NarrowTopBar/NarrowBottomNav 空壳
- 详情页：全屏覆盖，BackHandler + 顶栏 ✕ 返回

## 8. 交互修复（对应已反馈问题）

1. **路由**：`MainViewModel.selectScope` 末尾调用 `back()` → 点侧边栏即关闭详情返回列表（宽屏第三栏随之消失）；`openDetail` 不变
2. **Esc 关闭详情**：宽屏与窄屏详情均监听 Esc → back()
3. **Android 系统返回键**：BackHandler（androidMain）
4. **焦点环**：所有可交互组件 keyboard focus 时显示 2px accent 焦点环（键盘可达性）
5. **微交互**：hover 150ms 淡入、press 90% 缩放 150ms、勾选 spring 200-250ms

## 9. 数据流与错误处理

- 数据层/SQLDelight/仓库：零改动
- 视图模型：仅 MainViewModel.selectScope 增 `back()`；其余不变
- 错误/空状态：RemEmptyState（区分"搜索无结果"与"列表为空"文案）；对话框 scrim 保证前景可读

## 10. 测试策略

- 现有 49 测试保持全绿（数据层/解析层不动）
- 新增：MainViewModel 测试 —— `selectScope` 关闭详情（Route.Main）
- 纯逻辑新增（如有：徽章格式化）补对应测试
- 手动验收：桌面端宽/窄布局、悬停/焦点/勾选动效、暗色模式、Android 窄屏底部导航（如可装模拟器）

## 11. 明确不做（YAGNI）

- 拖拽排序、自定义字体打包、真实毛玻璃 blur、动画库依赖、图标库依赖、数据层改动

## 12. 参考

- 上一版设计规格：docs/superpowers/specs/2026-08-11-reminders-app-design.md（本规格沿用其数据模型与功能范围）
- UI UX Pro Max（ui-ux-pro-max skill）规则：无 emoji 结构图标、触控目标 ≥44pt/48dp、4.5:1 对比度、150-300ms 微交互、token 驱动主题、焦点可见
