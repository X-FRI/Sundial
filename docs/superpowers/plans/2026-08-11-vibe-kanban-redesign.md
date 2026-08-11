# Reminders vibe-kanban 哲学 UI 重构实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Reminders 的 UI 全盘重构为 vibe-kanban 设计哲学：背景阶梯分层（bgPrimary/bgSecondary/bgPanel）、单一品牌橙、全局 2px 直角、高密度信息、阴影只属于浮层、行间零分隔线、hover 纯色过渡。

**Architecture:** 纯视觉层重构。数据层/ViewModel 零改动（flag/completedAt/due_date 均已落地）。重构路径：DesignTokens 全量重写（新语义 token 表 + 旧→新映射）→ 组件层（RemBadge/Button/TextField/Checkbox/Dialog/EmptyState）→ 布局层（App 三栏/Sidebar）→ 列表页（头部/分区/卡片/行）→ 详情页（行/日期选择）→ 窄屏 + 暗色核对 + 全量验证。

**Tech Stack:** Kotlin Multiplatform + Compose Multiplatform（commonMain）。无新依赖（字体用系统 + FontFamily.Monospace）。测试：kotlin.test，现有 52 测试必须保持全绿。

**规格:** `docs/superpowers/specs/2026-08-11-vibe-kanban-redesign.md`（用户已确认）

**验证命令:** `./gradlew :shared:desktopTest --rerun-tasks :androidApp:assembleDebug`（预期 BUILD SUCCESSFUL，52 测试）

**当前 HEAD:** `72360ac`（Task 9 完成）。**禁止修改**：TodoDb.sq、TodoItem.kt、TodoRepository.kt、TodoRepositoryImpl.kt、MainViewModel.kt、DetailViewModel.kt、PlatformBackHandler.*、DateParser.kt、Formatting.kt。

---

### Task 1: DesignTokens 全量重写 + 全局旧 token 替换（编译保绿）

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/theme/DesignTokens.kt`（全量重写）
- Modify: 所有引用旧 token 的文件（编译错误驱动，见 Step 3）

- [ ] **Step 1: 重写 DesignTokens.kt**

完整替换文件内容为：

```kotlin
package com.myapplication.shared.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val ListColorKeys = listOf("blue", "red", "orange", "yellow", "green", "teal", "purple")

val ListColorOf = mapOf(
    "blue" to Color(0xFF0A84FF),
    "red" to Color(0xFFFF3B30),
    "orange" to Color(0xFFFF9500),
    "yellow" to Color(0xFFFFCC00),
    "green" to Color(0xFF34C759),
    "teal" to Color(0xFF5AC8FA),
    "purple" to Color(0xFFAF52DE),
)

data class RemColors(
    val bgPrimary: Color,
    val bgSecondary: Color,
    val bgPanel: Color,
    val textHigh: Color,
    val textNormal: Color,
    val textLow: Color,
    val border: Color,
    val inputBg: Color,
    val brand: Color,
    val brandHover: Color,
    val brandSecondary: Color,
    val error: Color,
    val success: Color,
    val warning: Color,
    val info: Color,
    val focusRing: Color,
)

val LightRemColors = RemColors(
    bgPrimary = Color(0xFFFFFFFF),
    bgSecondary = Color(0xFFF2F2F2),
    bgPanel = Color(0xFFE3E3E3),
    textHigh = Color(0xFF0D0D0D),
    textNormal = Color(0xFF333333),
    textLow = Color(0xFF636363),
    border = Color(0xFFD9D9D9),
    inputBg = Color(0xFFF5F5F5),
    brand = Color(0xFFEA7A2A),
    brandHover = Color(0xFFE79255),
    brandSecondary = Color(0xFFAC5111),
    error = Color(0xFFD25151),
    success = Color(0xFF54B04F),
    warning = Color(0xFFDB7706),
    info = Color(0xFF3C83F6),
    focusRing = Color(0xFFEA7A2A),
)

val DarkRemColors = RemColors(
    bgPrimary = Color(0xFF212121),
    bgSecondary = Color(0xFF1C1C1C),
    bgPanel = Color(0xFF292929),
    textHigh = Color(0xFFF5F5F5),
    textNormal = Color(0xFFC4C4C4),
    textLow = Color(0xFF8F8F8F),
    border = Color(0xFF333333),
    inputBg = Color(0xFF333333),
    brand = Color(0xFFEA7A2A),
    brandHover = Color(0xFFE79255),
    brandSecondary = Color(0xFFAC5111),
    error = Color(0xFFFF6B6B),
    success = Color(0xFF54B04F),
    warning = Color(0xFFE0913E),
    info = Color(0xFF3C83F6),
    focusRing = Color(0xFFE79255),
)

object RemType {
    val text10 = TextStyle(fontFamily = FontFamily.Default, fontSize = 10.sp)
    val text12 = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp)
    val text14 = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp)
    val text16 = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp)
    val title18 = TextStyle(fontFamily = FontFamily.Default, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    val label10 = TextStyle(fontFamily = FontFamily.Default, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    val label12 = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}

object RemSpacing {
    val s2 = 2.dp
    val s4 = 4.dp
    val s8 = 8.dp
    val s12 = 12.dp
    val s16 = 16.dp
}

object RemRadii {
    val r2 = 2.dp
}

val LocalRemColors = staticCompositionLocalOf { LightRemColors }
```

- [ ] **Step 2: 写失败测试（确认测试侧不依赖旧 token）**

Run: `./gradlew :shared:desktopTest --rerun-tasks`
Expected: FAIL 编译（旧 token 引用全部报错）——这正是 Step 3 的替换驱动清单。

- [ ] **Step 3: 全局替换旧 token（编译错误驱动，逐文件）**

按规格 §6 映射表替换所有 UI 文件中的旧 token（Sidebar.kt、TodoListScreen.kt、DetailScreen.kt、App.kt、NarrowShell.kt、RemBadge.kt、RemButton.kt、RemTextField.kt、RemCheckbox.kt、RemDialog.kt、RemEmptyState.kt、RemDatePicker.kt）：

| 旧 | 新 |
|---|---|
| `selectedBg` | `bgSecondary` |
| `rowDivider` | 删除分隔线绘制（改用 `border` 容器边界，界面内部线删除） |
| `checkboxBorder` | `textLow` |
| `dialogBg` | `bgPrimary` |
| `hoverActionBg` | `bgSecondary` |
| `todayBadgeBg` | `warning.copy(alpha = 0.08f)` |
| `todayBadgeText` | `warning` |
| `upcomingBadgeBg` | `bgPanel` |
| `upcomingBadgeText` | `textLow` |
| `overdueBadgeBg` | `error.copy(alpha = 0.08f)` |
| `overdueBadgeText` | `error` |
| `cardBg` | `bgPrimary` |
| `cardBorder` | 删除（无边框） |
| `windowBg` | `bgSecondary` |
| `accent` | `brand` |
| `danger` | `error` |
| `sidebarBg` | `bgPrimary` |
| `contentBg` | `bgSecondary` |
| `textPrimary` | `textHigh` |
| `textSecondary` | `textNormal` |
| `textTertiary` | `textLow` |
| `flagColor` | `warning` |

具体替换规则：
- `RemRadii.r4/r6/r7/r8/r10` → `RemRadii.r2`
- `RemType.title15/title17/title20/label13/text13` → 按语义映射：标题 title18、正文 text14、次级 text12、标签 label12、小字 text10（视觉细节在后续 Task 统一，本步只求编译通过 + 合理近似）
- `RoundedCornerShape(RemRadii.rX)` → `RoundedCornerShape(RemRadii.r2)`
- `CircleShape` 仅保留于：勾选框、列表色点、彩色圆点（语义圆形），其余按钮圆角改 r2
- 删除 `drawBehind { drawLine(colors.rowDivider, ...) }` 行分隔线绘制（TodoListScreen TodoRow、DetailScreen 元数据行、Sidebar 分隔线 Box）

- [ ] **Step 4: 验证编译**

Run: `./gradlew :shared:desktopTest --rerun-tasks :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL（52 测试全绿，纯色值替换无行为变化）

- [ ] **Step 5: 提交**

```bash
git add -A
git commit -m "refactor(theme): rewrite design tokens to vibe-kanban semantics"
```

---

### Task 2: 组件层重构（Badge/Button/TextField/Checkbox/Dialog/EmptyState）

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/RemBadge.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/RemButton.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/RemTextField.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/RemCheckbox.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/RemDialog.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/RemEmptyState.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/RemIcons.kt`（新增 DotsThree）

- [ ] **Step 1: RemBadge 新签名（规格 §3.6）**

`RemBadge.kt` 整体替换为：

```kotlin
package com.myapplication.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemType

@Composable
fun RemBadge(
    label: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
    monospace: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
) {
    val colors = LocalRemColors.current
    val bg = color?.copy(alpha = 0.08f) ?: colors.bgPanel
    val fg = color ?: colors.textLow
    Row(
        modifier
            .clip(RoundedCornerShape(RemRadii.r2))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(3.dp))
        }
        androidx.compose.foundation.text.BasicText(
            label,
            style = RemType.text10.copy(
                color = fg,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            ),
        )
    }
}
```

删除 `tint/bg/onClick` 参数。调用点更新（见 Task 4/5）。

- [ ] **Step 2: RemButton 变体重构**

`RemButton.kt` 替换为：

```kotlin
package com.myapplication.shared.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemType

enum class RemButtonVariant { Default, Ghost, Danger }

@Composable
fun RemButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: RemButtonVariant = RemButtonVariant.Ghost,
) {
    val colors = LocalRemColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val bg by animateColorAsState(
        when {
            variant == RemButtonVariant.Default && hovered -> colors.brandHover
            variant == RemButtonVariant.Default -> colors.brand
            hovered -> colors.bgSecondary
            else -> Color.Transparent
        },
        tween(200),
        label = "btn-bg",
    )
    val fg by animateColorAsState(
        when {
            variant == RemButtonVariant.Default -> Color.White
            variant == RemButtonVariant.Danger -> colors.error
            else -> colors.textNormal
        },
        tween(200),
        label = "btn-fg",
    )
    val borderColor = if (variant == RemButtonVariant.Danger) colors.border else Color.Transparent
    Box(
        modifier
            .clip(RoundedCornerShape(RemRadii.r2))
            .background(bg)
            .border(if (variant == RemButtonVariant.Danger) 1.dp else 0.dp, borderColor, RoundedCornerShape(RemRadii.r2))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .height(29.dp)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.text.BasicText(
            text,
            style = RemType.label12.copy(color = if (pressed) fg.copy(alpha = 0.8f) else fg),
        )
    }
}

@Composable
fun RemIconButton(
    icon: IconName,
    contentDescription: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    size: Dp = 14.dp,
) {
    val colors = LocalRemColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val bg by animateColorAsState(if (hovered) colors.bgSecondary else Color.Transparent, tween(200), label = "ib-bg")
    Box(
        modifier
            .size(size + 12.dp)
            .clip(RoundedCornerShape(RemRadii.r2))
            .background(bg)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        RemIcon(icon, if (pressed) tint?.copy(alpha = 0.8f) ?: colors.textLow else tint ?: colors.textLow, Modifier.size(size))
    }
}
```

保留 `Dp` import。删除 `graphicsLayer`/`CircleShape`/`collectIsFocusedAsState`（焦点环移到 Task 3 统一处理，用 `border` 实现）。

- [ ] **Step 3: RemTextField 统一风格**

`RemTextField.kt` 修改：
- 删除 `filled` 参数（或保留但忽略——改为统一 1px border + inputBg 底），聚焦边框 `brand` 1dp
- 圆角 r7 → r2
- 内部文本 `text14`（占位 `textLow`）
- 修改：

```kotlin
        modifier
            .clip(RoundedCornerShape(RemRadii.r2))
            .background(colors.inputBg)
            .border(
                if (focused) 1.dp else 1.dp,
                if (focused) colors.brand else colors.border,
                RoundedCornerShape(RemRadii.r2),
            )
```

- 删除 `filled` 条件（背景恒 inputBg、边框恒 1dp，聚焦换品牌色）
- `style` 默认值改 `RemType.text14`

- [ ] **Step 4: RemCheckbox 颜色语义**

`RemCheckbox.kt` 修改：
- `checkboxBorder` → `textLow`；hover 边框 → `textHigh`
- `accent` → `brand`

- [ ] **Step 5: RemDialog 阴影 + 直角**

`RemDialog.kt` 修改：
- 对话框 Column：圆角 r8 → r2，`dialogBg` → `bgPrimary`，加 `Modifier.shadow(8.dp, RoundedCornerShape(RemRadii.r2), ambientColor = Color(0x1F000000), spotColor = Color(0x1F000000), clip = false)`（亮色）——暗色 shadow 用 12.dp + 0x80000000，用 `isSystemInDarkTheme()` 分支
- scrim：`Color.Black.copy(alpha = 0.5f)` → 亮 `0.4f` / 暗 `0.6f`
- import `androidx.compose.ui.draw.shadow`、`androidx.compose.foundation.isSystemInDarkTheme`

- [ ] **Step 6: RemEmptyState 直角线稿**

`RemEmptyState.kt` 修改：Canvas 插画中所有 `CornerRadius(s * 0.08f)` 改为 `CornerRadius(s * 0.02f)`（r2 比例），保持其余结构。

- [ ] **Step 7: RemIcons 新增 DotsThree**

`RemIcons.kt`：枚举加 `DotsThree`，分支：

```kotlin
            IconName.DotsThree -> {
                circle(5.5f, 12f, 1.2f, filled = true)
                circle(12f, 12f, 1.2f, filled = true)
                circle(18.5f, 12f, 1.2f, filled = true)
            }
```

- [ ] **Step 8: 验证 + 提交**

Run: `./gradlew :shared:desktopTest --rerun-tasks :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL（若 RemBadge 调用点编译失败，用 `color = ...`/`monospace = ...` 修复，保持视觉合理）

```bash
git add -A
git commit -m "feat(ui): rewrite components to vibe-kanban variants"
```

---

### Task 3: 布局层（App 三栏 / Sidebar）

**Files:**
- Modify: `shared/src/commonMain/kotlin/App.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/sidebar/Sidebar.kt`

- [ ] **Step 1: App.kt 三栏底色**

`App.kt` 修改：
- 窗口 `BoxWithConstraints` 背景 `colors.contentBg` → `colors.bgSecondary`
- 列表区 `TodoListScreen(...)` 背景 `colors.contentBg` → `colors.bgSecondary`
- 详情面板外层包 `Modifier.width(340.dp).background(colors.bgPrimary).border(width = 1.dp, color = colors.border, brush = SolidColor(colors.border), shape = RectangleShape)` —— 用 `drawBehind` 画左边缘线更简单：

```kotlin
                    Modifier
                        .width(340.dp)
                        .background(colors.bgPrimary)
                        .drawBehind {
                            drawLine(
                                colors.border,
                                Offset(0f, 0f),
                                Offset(0f, size.height),
                                1f,
                            )
                        }
```

（AnimatedVisibility 结构保留，modifier 内加 background + drawBehind）

- [ ] **Step 2: Sidebar 重构**

`Sidebar.kt` 修改：
- 根 Column：`background(colors.sidebarBg)` → `background(colors.bgPrimary)`；分隔线 Box（rowDivider）删除，改为 Spacer s8
- 标题 "提醒事项"：`RemType.title17` → `RemType.text16.copy(fontWeight = FontWeight.SemiBold)`
- "我的列表"标签：`RemType.label12` → `RemType.label10`
- ScopeRow：`selectedBg` → `bgSecondary`（选中）；hover `bgSecondary.copy(alpha = 0.4f)`；`accent` → `brand`；计数 `textTertiary` → `textLow`
- 今天红色计数：`danger` → `error`（8% 底胶囊已实现，检查用 `RoundedCornerShape(RemRadii.r8)` → r2）
- ListRow：同上替换；hover 按钮 `hoverActionBg` → `bgSecondary`
- "添加列表"：`accent` → `brand`
- 行高保持 28dp，圆角 r7 → r2

- [ ] **Step 3: 焦点环统一**

给 Sidebar 的 ScopeRow/ListRow 和 TodoListScreen 的行加 `collectIsFocusedAsState` + 2dp `brand` 焦点边框（规格 §3.4 交互纪律——vibe 用 focusBorder = brand）。实现：

```kotlin
    val focused by interactionSource.collectIsFocusedAsState()
    // modifier 链中：
    .border(if (focused) 1.dp else 0.dp, colors.focusRing, RoundedCornerShape(RemRadii.r2))
```

加到 ScopeRow 与 ListRow 即可（其余可 Tab 到的控件后续 Task 视情况）。

- [ ] **Step 4: 验证 + 提交**

Run: `./gradlew :shared:desktopTest --rerun-tasks :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL

```bash
git add -A
git commit -m "feat(ui): three-pane backgrounds and sidebar restyle"
```

---

### Task 4: 列表页重构（头部/分区/卡片/行）

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/todolist/TodoListScreen.kt`

- [ ] **Step 1: 头部（规格 §5.3）**

替换头部 block：
- 大标题 `RemType.title28` → `RemType.title18.copy(color = colors.textHigh)`
- 副行：日期/计数保持结构，颜色 `textTertiary` → `textLow`、字号 `RemType.text13` → `RemType.text12`；计数部分用 Monospace
- ＋按钮：hover 背景 `hoverActionBg` → `bgSecondary`，圆角 r6 → r2，图标 `textPrimary` → `textHigh`
- 计数胶囊 `RemBadge("$count 项", bg = ..., tint = ...)` → `RemBadge("$count 项", monospace = true)`（常态 bgPanel/textLow；今天视图 `color = colors.error`）

- [ ] **Step 2: SectionHeader 去横线**

`SectionHeader` 重写为（删除 1px 延伸线）：

```kotlin
@Composable
private fun SectionHeader(title: String, count: Int, overdue: Boolean) {
    val colors = LocalRemColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.text.BasicText(
            title,
            style = RemType.label10.copy(color = colors.textHigh),
        )
        if (count > 0) {
            Spacer(Modifier.width(6.dp))
            RemBadge(
                label = "$count",
                color = if (overdue) colors.error else null,
                monospace = true,
            )
        }
    }
}
```

- [ ] **Step 3: 卡片去边框去阴影**

TodayGrouped/PlainList/ScheduledGrouped 中所有卡片 Column：
- 删除 `.border(1.dp, colors.cardBorder, ...)`
- `background(colors.cardBg, RoundedCornerShape(RemRadii.r10))` → `background(colors.bgPrimary, RoundedCornerShape(RemRadii.r2))`
- 卡片间距 Spacer(12.dp) → Spacer(s8)
- 列表横向 padding 16.dp 保持不变

- [ ] **Step 4: TodoRow 去分隔线 + 新徽章**

`TodoRow` 修改：
- 删除 `drawBehind { drawLine(colors.rowDivider, ...) }`
- hover 背景 `selectedBg.copy(alpha = 0.6f)` → `bgSecondary.copy(alpha = 0.4f)`
- 标题 `textPrimary` → `textHigh`、`text13` → `text14.copy(fontWeight = FontWeight.Medium)`；完成态 `textTertiary` → `textLow`
- 旗标 `flagColor` → `warning`
- 第二行备注 `textTertiary` → `textLow`、`text12` 保持；子任务计数 `textTertiary` → `textLow` + Monospace
- 时间徽章（TodoBadge）重写为新 RemBadge 签名：

```kotlin
@Composable
private fun TodoBadge(item: TodoItem, today: kotlinx.datetime.LocalDate) {
    val colors = LocalRemColors.current
    val due = item.dueDate ?: return
    val tz = TimeZone.currentSystemDefault()
    val date = due.toLocalDateTime(tz).date
    val bucket = bucketOf(date, today)
    val label = formatDueDate(due, tz, today)
    val badgeColor = when (bucket) {
        DueBucket.OVERDUE -> colors.error
        DueBucket.TODAY -> colors.warning
        else -> null
    }
    RemBadge(
        label = label,
        color = badgeColor,
        monospace = true,
        icon = { RemIcon(IconName.Calendar, badgeColor ?: colors.textLow, Modifier.size(10.dp)) },
    )
}
```

- [ ] **Step 5: 空状态/垃圾箱**

`RemEmptyState` 调用处文案样式已由组件接管；TrashList 行内按钮改新变体：`RemButton("恢复", ...)` → Ghost 默认；`RemButton("彻底删除", ..., danger = true)` → `variant = RemButtonVariant.Danger`。

- [ ] **Step 6: 验证 + 冒烟**

Run: `./gradlew :shared:desktopTest --rerun-tasks :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL

冒烟：`./gradlew :desktopApp:run`——检查头部、分区卡片（无边框）、行（无分隔线）、徽章、hover。杀进程（pkill -f MainKt）。

- [ ] **Step 7: 提交**

```bash
git add -A
git commit -m "feat(ui): restyle list screen to vibe-kanban hierarchy"
```

---

### Task 5: 详情页重构 + RemDatePicker

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/detail/DetailScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/RemDatePicker.kt`

- [ ] **Step 1: 元数据行去分隔线 + 直角**

`DetailScreen.kt` 修改：
- 删除所有 `drawBehind { drawLine(colors.rowDivider, ...) }`（日期/旗标/列表三行）
- 行高 padding vertical 10.dp → 8.dp（32dp 行高）
- 行标签 `text13` → `text12`、`textSecondary` → `textNormal`；值 `textPrimary` → `textHigh`
- 图标 `textTertiary` → `textLow`
- 旗标行：`flagColor` → `warning`、`textPrimary/textTertiary` → `textHigh/textLow`

- [ ] **Step 2: 备注块 + 标题行**

- 备注块：`background(colors.cardBg, RoundedCornerShape(RemRadii.r6))` → `background(colors.inputBg, RoundedCornerShape(RemRadii.r2))`，删除 `.border(1.dp, colors.cardBorder, ...)`
- 标题行：`RemType.title15` → `RemType.text16.copy(fontWeight = FontWeight.SemiBold)`；时间徽章改用新签名 `RemBadge(label, color = colors.warning, monospace = true, onClick 由外层 clickable 提供)`——注意 RemBadge 已删除 onClick，改为外部：

```kotlin
            if (current.dueDate != null) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.clickable { showDatePicker = true }) {
                    RemBadge(
                        label = formatDueDate(current.dueDate),
                        color = colors.warning,
                        monospace = true,
                    )
                }
            }
```

- [ ] **Step 3: 底部操作区变体**

- `RemButton("移到列表", ...)` → 默认 Ghost
- `RemButton("移到垃圾箱", ..., danger = true)` → `variant = RemButtonVariant.Danger`
- 完成时间/创建时间：`textTertiary` → `textLow`、`text12` 保持

- [ ] **Step 4: RemDatePicker 直角 + 品牌色**

`RemDatePicker.kt` 修改：
- 选中日 `colors.accent` → `colors.brand`；今天字色 `accent` → `brand`
- 日期格子圆角 `CircleShape` → `RoundedCornerShape(RemRadii.r2)`
- TimePickerRow 的 RemIconButton/RemButton 自动继承新样式
- 标题 `${month.year} 年 ${month.month.number} 月`：`title15` → `text14 600`

- [ ] **Step 5: 验证 + 提交**

Run: `./gradlew :shared:desktopTest --rerun-tasks :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL

```bash
git add -A
git commit -m "feat(ui): restyle detail screen and date picker"
```

---

### Task 6: 窄屏 + 暗色核对 + 全量验证

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/narrow/NarrowShell.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/theme/DesignTokens.kt`（如需）

- [ ] **Step 1: NarrowShell 样式**

`NarrowShell.kt` 修改：
- 顶部栏/底部导航：`contentBg` → `bgPrimary`；顶部栏底边、底部导航顶边 1px `border` 线
- 底部导航项：`accent` → `brand`；图标 `textTertiary` → `textLow`；文字 10sp

- [ ] **Step 2: 暗色 token 核对**

对照规格 §7 检查 DarkRemColors 全部 16 字段已正确（Task 1 已写入）——跳过或微调。

- [ ] **Step 3: 全量验证 + 全量冒烟**

Run: `./gradlew :shared:desktopTest --rerun-tasks :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL，**52 测试全绿**

冒烟（亮+暗）：`./gradlew :desktopApp:run`——三栏层次、卡片无边框、行无分隔线、2px 圆角、品牌橙、等宽徽章、对话框阴影、hover 纯色过渡；系统切深色重开验证暗色三层阶梯。杀进程。

- [ ] **Step 4: 提交**

```bash
git add -A
git commit -m "feat(ui): restyle narrow shell and dark mode parity"
```

---

## 自审查记录

- **规格 §3.1 token** → Task 1（16 字段亮暗双色板）
- **规格 §3.2-3.5 间距/圆角/字体/阴影** → Task 1（RemSpacing 2/4/8/12/16、RemRadii.r2、RemType 新字号）+ Task 2 Step 5（阴影）
- **规格 §3.6/§4.1 RemBadge** → Task 2 Step 1 + Task 4 Step 4 + Task 5 Step 2
- **规格 §4.2 RemButton** → Task 2 Step 2 + Task 4 Step 5 + Task 5 Step 3
- **规格 §4.3-4.7 其余组件** → Task 2 Step 3-7
- **规格 §5.1 三栏** → Task 3 Step 1
- **规格 §5.2 侧边栏** → Task 3 Step 2-3
- **规格 §5.3 列表页** → Task 4
- **规格 §5.4 详情页** → Task 5
- **规格 §5.5 窄屏** → Task 6 Step 1
- **规格 §7 暗色** → Task 6 Step 2
- **规格 §8 动效** → Task 2（200ms 颜色过渡）+ 保留 spring/AnimatedVisibility
- **规格 §9 测试** → 每 Task 验证 52 测试；无新测试
- **数据层零改动** → 各 Task 明确禁止文件清单

**类型一致性核对**：`RemBadge(label, modifier, color: Color?, monospace: Boolean, icon)`（Task 2 定义，Task 4/5 使用）；`RemButton(text, onClick, modifier, variant: RemButtonVariant)`（Task 2 定义，Task 4/5/6 使用）；`RemColors` 16 字段（Task 1 定义，全程使用）；`RemRadii.r2` 唯一（Task 1 定义）；`RemType.title18/text10/text12/text14/text16/label10/label12`（Task 1 定义）。
