# Reminders UI 重构实施计划（B 方向：Reminders 结构 × 现代轻量）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将所有平台的 UI 统一为自建轻量设计系统（无 Material Design），修复路由/交互缺陷，补完窄屏布局。

**Architecture:** 新增 `DesignTokens.kt`（色板/排版/间距 token，CompositionLocal 注入）+ `ui/components/` 自绘组件库（RemIcons/RemCheckbox/RemBadge/RemButton/RemIconButton/RemTextField/RemDialog/RemEmptyState/RemDatePicker），各屏逐任务替换 Material3 用法，最后移除 MaterialTheme。MainViewModel.selectScope 增加 back() 修复"点侧边栏不返回列表"。Esc 与 Android BackHandler 统一路由回退。窄屏补完顶栏+底部导航。

**Tech Stack:** Kotlin 2.4.10 / Compose Multiplatform 1.11.1 / kotlinx-datetime 0.8.0 / SQLDelight 2.3.2（数据层零改动）/ 现有 49 测试保持全绿。

**验证命令（每任务通用）：** `./gradlew :shared:desktopTest :androidApp:assembleDebug` → 期望 BUILD SUCCESSFUL。UI 任务无新单测，以编译+构建通过为准；逻辑任务按 TDD。最终全量 `./gradlew :shared:desktopTest --rerun-tasks` 期望 50 测试全绿。

**背景色约定（App.kt 最后收口）：** 侧边栏/详情=sidebarBg，列表区=contentBg，窄屏整栏=contentBg。

---

### Task 1: DesignTokens + 主题底座

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/theme/DesignTokens.kt`
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/theme/Theme.kt`（重写，保留 MaterialTheme 外壳仅映射色板——让所有旧屏继续编译）

- [ ] **Step 1: 创建 DesignTokens.kt**

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
    "blue" to Color(0xFF007AFF),
    "red" to Color(0xFFFF3B30),
    "orange" to Color(0xFFFF9500),
    "yellow" to Color(0xFFFFCC00),
    "green" to Color(0xFF34C759),
    "teal" to Color(0xFF5AC8FA),
    "purple" to Color(0xFFAF52DE),
)

data class RemColors(
    val sidebarBg: Color,
    val contentBg: Color,
    val selectedBg: Color,
    val rowDivider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val danger: Color,
    val checkboxBorder: Color,
    val dialogBg: Color,
)

val LightRemColors = RemColors(
    sidebarBg = Color(0xFFFFFFFF),
    contentBg = Color(0xFFFAFAFC),
    selectedBg = Color(0xFFF2F2F7),
    rowDivider = Color(0xFFF0F0F4),
    textPrimary = Color(0xFF111111),
    textSecondary = Color(0xFF3C3C43),
    textTertiary = Color(0xFF8E8E93),
    accent = Color(0xFF0A84FF),
    danger = Color(0xFFFF3B30),
    checkboxBorder = Color(0xFFC7C7CC),
    dialogBg = Color(0xFFFFFFFF),
)

val DarkRemColors = RemColors(
    sidebarBg = Color(0xFF1C1C1E),
    contentBg = Color(0xFF2C2C2E),
    selectedBg = Color(0xFF3A3A3C),
    rowDivider = Color(0xFF38383A),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFD1D1D6),
    textTertiary = Color(0xFF8E8E93),
    accent = Color(0xFF0A84FF),
    danger = Color(0xFFFF453A),
    checkboxBorder = Color(0xFF636366),
    dialogBg = Color(0xFF2C2C2E),
)

object RemType {
    val text12 = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp, fontWeight = FontWeight.Normal)
    val text13 = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, fontWeight = FontWeight.Normal)
    val title15 = TextStyle(fontFamily = FontFamily.Default, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    val title17 = TextStyle(fontFamily = FontFamily.Default, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp)
    val title20 = TextStyle(fontFamily = FontFamily.Default, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp)
    val label12 = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    val label13 = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
}

object RemSpacing {
    val s4 = 4.dp
    val s8 = 8.dp
    val s12 = 12.dp
    val s16 = 16.dp
    val s24 = 24.dp
    val s32 = 32.dp
}

object RemRadii {
    val r4 = 4.dp
    val r6 = 6.dp
    val r7 = 7.dp
    val r8 = 8.dp
}

val LocalRemColors = staticCompositionLocalOf { LightRemColors }
```

- [ ] **Step 2: 重写 Theme.kt**（MaterialTheme 外壳保留，色板改映射 token；本任务后旧屏不受影响）

```kotlin
package com.myapplication.shared.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalTextStyle

private val LightColors = lightColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    background = Color(0xFFFAFAFC),
    onBackground = Color(0xFF111111),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0xFFC7C7CC),
    error = Color(0xFFFF3B30),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    background = Color(0xFF2C2C2E),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF3A3A3C),
    onSurfaceVariant = Color(0xFFAEAEB2),
    outline = Color(0xFF636366),
    error = Color(0xFFFF453A),
    onError = Color.White,
)

@Composable
fun RemindersTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkRemColors else LightRemColors
    CompositionLocalProvider(
        LocalRemColors provides colors,
        LocalTextStyle provides RemType.text13.copy(color = colors.textPrimary),
    ) {
        MaterialTheme(
            colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
            content = content,
        )
    }
}
```

- [ ] **Step 3: 验证**

Run: `./gradlew :shared:desktopTest :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL（ListColorKeys/ListColorOf 移入 DesignTokens.kt，原引用不变，因同包）

- [ ] **Step 4: 提交**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/ui/theme/
git commit -m "feat(theme): add design tokens and rem color scheme"
```

---

### Task 2: RemIcons（自绘线性图标，消灭 emoji 的基础）

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/RemIcons.kt`

- [ ] **Step 1: 创建 RemIcons.kt**

```kotlin
package com.myapplication.shared.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke

enum class IconName { Calendar, Today, Scheduled, Tray, CheckCircle, Trash, Search, Plus, Close, ChevronBack, ChevronRight, ChevronDown }

@Composable
fun RemIcon(name: IconName, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val u = s / 24f
        val st = 1.8f * u
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u), strokeWidth = st, cap = StrokeCap.Round)
        fun circle(cx: Float, cy: Float, r: Float, filled: Boolean = false) =
            drawCircle(tint, radius = r * u, center = Offset(cx * u, cy * u), style = if (filled) Fill else Stroke(width = st))
        fun box(x: Float, y: Float, w: Float, h: Float, r: Float = 2f) =
            drawRoundRect(
                tint,
                topLeft = Offset(x * u, y * u),
                size = Size(w * u, h * u),
                cornerRadius = CornerRadius(r * u, r * u),
                style = Stroke(width = st),
            )
        fun poly(vararg pts: Float) {
            val p = Path()
            var i = 0
            while (i + 1 < pts.size) {
                val x = pts[i] * u
                val y = pts[i + 1] * u
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                i += 2
            }
            drawPath(p, tint, style = Stroke(width = st, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        when (name) {
            IconName.Calendar -> {
                box(3.5f, 5f, 17f, 15f)
                line(8f, 3f, 8f, 6f)
                line(16f, 3f, 16f, 6f)
            }
            IconName.Today -> {
                box(3.5f, 5f, 17f, 15f)
                line(8f, 3f, 8f, 6f)
                line(16f, 3f, 16f, 6f)
                circle(12f, 12.5f, 2.2f, filled = true)
            }
            IconName.Scheduled -> {
                box(3.5f, 5f, 17f, 15f)
                line(8f, 3f, 8f, 6f)
                line(16f, 3f, 16f, 6f)
                line(7.5f, 12f, 16.5f, 12f)
                line(7.5f, 15.5f, 13f, 15.5f)
            }
            IconName.Tray -> {
                box(4f, 7f, 16f, 12f, r = 2.5f)
                line(4f, 10.5f, 20f, 10.5f)
                line(9.5f, 13.8f, 14.5f, 13.8f)
            }
            IconName.CheckCircle -> {
                circle(12f, 12f, 8f)
                poly(8.5f, 12.5f, 11.2f, 15.2f, 16f, 9.5f)
            }
            IconName.Trash -> {
                line(6f, 8.5f, 18f, 8.5f)
                box(10f, 5f, 4f, 3.5f, r = 1f)
                poly(8.2f, 8.5f, 8.6f, 19.5f, 15.4f, 19.5f, 15.8f, 8.5f)
                line(10.6f, 12f, 10.9f, 16.5f)
                line(13.4f, 12f, 13.1f, 16.5f)
            }
            IconName.Search -> {
                circle(10.5f, 10.5f, 5.5f)
                line(14.5f, 14.5f, 20f, 20f)
            }
            IconName.Plus -> {
                line(12f, 6f, 12f, 18f)
                line(6f, 12f, 18f, 12f)
            }
            IconName.Close -> {
                line(7f, 7f, 17f, 17f)
                line(17f, 7f, 7f, 17f)
            }
            IconName.ChevronBack -> poly(14f, 6f, 9f, 12f, 14f, 18f)
            IconName.ChevronRight -> poly(10f, 6f, 15f, 12f, 10f, 18f)
            IconName.ChevronDown -> poly(6f, 10f, 12f, 15f, 18f, 10f)
        }
    }
}
```

- [ ] **Step 2: 验证**

Run: `./gradlew :shared:desktopTest :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/RemIcons.kt
git commit -m "feat(ui): add canvas-drawn vector icon set"
```

---

### Task 3: RemCheckbox + RemBadge + RemButton/RemIconButton

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/RemCheckbox.kt`
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/RemBadge.kt`
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/RemButton.kt`

- [ ] **Step 1: 创建 RemCheckbox.kt**（圆形勾选，spring 弹性，hover 边框变强调色，按压 0.85 缩放）

```kotlin
package com.myapplication.shared.ui.components

import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.myapplication.shared.ui.theme.LocalRemColors

@Composable
fun RemCheckbox(
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 16.dp,
) {
    val colors = LocalRemColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.85f else 1f, tween(150), label = "cb-scale")
    val fill by animateColorAsState(
        if (checked) colors.accent else Color.Transparent,
        spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "cb-fill",
    )
    Box(
        modifier
            .size(size + 10.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .clickable(interactionSource = interactionSource, indication = null) { onToggle() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val d = size.minDimension
            val r = d / 2f
            drawCircle(fill)
            drawCircle(
                color = when {
                    checked -> colors.accent
                    hovered -> colors.accent
                    else -> colors.checkboxBorder
                },
                style = Stroke(width = r * 0.18f),
            )
            if (checked) {
                val p = Path().apply {
                    moveTo(d * 0.24f, d * 0.52f)
                    lineTo(d * 0.44f, d * 0.72f)
                    lineTo(d * 0.78f, d * 0.30f)
                }
                drawPath(p, Color.White, style = Stroke(width = d * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
```

- [ ] **Step 2: 创建 RemBadge.kt**（浅灰底胶囊，圆角 4dp，可带前置小图标与强调色）

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
import androidx.compose.ui.unit.dp
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemType

@Composable
fun RemBadge(
    label: String,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    val colors = LocalRemColors.current
    Row(
        modifier
            .clip(RoundedCornerShape(RemRadii.r4))
            .background(colors.selectedBg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(3.dp))
        }
        androidx.compose.material3.Text(label, style = RemType.text12, color = tint ?: colors.textTertiary)
    }
}
```

- [ ] **Step 3: 创建 RemButton.kt**（RemButton + RemIconButton；hover 底、press 缩放、focus 环）

```kotlin
package com.myapplication.shared.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemType

@Composable
fun RemButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
) {
    val colors = LocalRemColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, tween(150), label = "btn-scale")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(RemRadii.r6))
            .background(if (hovered) colors.selectedBg else Color.Transparent)
            .border(if (focused) 2.dp else 0.dp, colors.accent, RoundedCornerShape(RemRadii.r6))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        androidx.compose.material3.Text(
            text,
            style = RemType.label13,
            color = if (danger) colors.danger else colors.textPrimary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
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
    size: Dp = 18.dp,
) {
    val colors = LocalRemColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (pressed) 0.85f else 1f, tween(150), label = "iconbtn-scale")
    Box(
        modifier
            .size(size + 16.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(if (hovered) colors.selectedBg else Color.Transparent)
            .border(if (focused) 2.dp else 0.dp, colors.accent, CircleShape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        RemIcon(icon, tint ?: colors.textSecondary, Modifier.size(size))
    }
}
```

> 注意：组件内 `androidx.compose.material3.Text` 仅为占位（MaterialTheme 尚存）；Task 10 统一替换为 foundation Text。

- [ ] **Step 4: 验证**

Run: `./gradlew :shared:desktopTest :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/
git commit -m "feat(ui): add checkbox, badge and button components"
```

---

### Task 4: RemTextField + RemDialog + RemEmptyState

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/RemTextField.kt`
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/RemDialog.kt`
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/RemEmptyState.kt`

- [ ] **Step 1: 创建 RemTextField.kt**（圆角 7dp、selectedBg 底、focus 2dp accent 环、可切换 filled 用于详情内嵌输入、trailing 文本按钮）

```kotlin
package com.myapplication.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemType

@Composable
fun RemTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    filled: Boolean = true,
    style: TextStyle = RemType.text13,
    leadingIcon: IconName? = null,
    onEnter: (() -> Unit)? = null,
    trailing: Pair<String, () -> Unit>? = null,
) {
    val colors = LocalRemColors.current
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .clip(RoundedCornerShape(RemRadii.r7))
            .background(if (filled) colors.selectedBg else androidx.compose.ui.graphics.Color.Transparent)
            .border(
                if (focused && filled) 2.dp else 0.dp,
                colors.accent,
                RoundedCornerShape(RemRadii.r7),
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            RemIcon(leadingIcon, colors.textTertiary, Modifier.width(14.dp).height(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focused = it.isFocused },
            textStyle = style.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
            singleLine = singleLine,
            minLines = minLines,
            keyboardOptions = if (onEnter != null) KeyboardOptions(imeAction = ImeAction.Done) else KeyboardOptions.Default,
            keyboardActions = if (onEnter != null) KeyboardActions(onDone = { onEnter() }) else KeyboardActions.Default,
            decorationBox = { inner ->
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    androidx.compose.material3.Text(placeholder, style = style.copy(color = colors.textTertiary))
                }
                inner()
            },
        )
        if (trailing != null) {
            Spacer(Modifier.width(6.dp))
            androidx.compose.material3.Text(
                trailing.first,
                style = RemType.label13,
                color = colors.accent,
                modifier = Modifier.clickable { trailing.second() },
            )
        }
    }
}
```

> 需要 `Modifier.height`：补 import `androidx.compose.foundation.layout.height`。组件内 material3.Text 为占位，Task 10 统一替换。

- [ ] **Step 2: 创建 RemDialog.kt**（scrim 50% 黑、白底圆角 8dp、可选按钮行）

```kotlin
package com.myapplication.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemType

@Composable
fun RemDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    confirmDanger: Boolean = false,
    dismissText: String = "取消",
    showButtons: Boolean = true,
) {
    val colors = LocalRemColors.current
    val scrimInteraction = remember { MutableInteractionSource() }
    val innerInteraction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = scrimInteraction, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 340.dp)
                .clip(RoundedCornerShape(RemRadii.r8))
                .background(colors.dialogBg)
                .clickable(interactionSource = innerInteraction, indication = null) {}
                .padding(16.dp),
        ) {
            androidx.compose.material3.Text(title, style = RemType.title15, color = colors.textPrimary)
            Spacer(Modifier.height(12.dp))
            content()
            if (showButtons) {
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    RemButton(dismissText, onDismiss)
                    Spacer(Modifier.width(8.dp))
                    RemButton(confirmText, onConfirm, danger = confirmDanger)
                }
            }
        }
    }
}
```

> 需要补 import：`androidx.compose.foundation.layout.height`。

- [ ] **Step 3: 创建 RemEmptyState.kt**

```kotlin
package com.myapplication.shared.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemType

@Composable
fun RemEmptyState(title: String, subtitle: String = "", icon: IconName? = null) {
    val colors = LocalRemColors.current
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            RemIcon(icon, colors.textTertiary, Modifier.size(40.dp))
            Spacer(Modifier.height(12.dp))
        }
        androidx.compose.material3.Text(title, style = RemType.title20, color = colors.textPrimary)
        if (subtitle.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            androidx.compose.material3.Text(subtitle, style = RemType.text12, color = colors.textTertiary)
        }
    }
}
```

- [ ] **Step 4: 验证**

Run: `./gradlew :shared:desktopTest :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/
git commit -m "feat(ui): add text field, dialog and empty state components"
```

---

### Task 5: RemDatePicker（自绘日期选择，替代 Material3 DatePicker）

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/RemDatePicker.kt`

- [ ] **Step 1: 创建 RemDatePicker.kt**（月历网格，周一起始，今天强调色，选中填充强调色；点日期即选并关闭）

```kotlin
package com.myapplication.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemType
import com.myapplication.shared.util.todayDate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

@Composable
fun RemDatePicker(
    initialDate: LocalDate?,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalRemColors.current
    val today = todayDate()
    var month by remember {
        mutableStateOf(
            initialDate?.let { YearMonth(it.year, it.monthNumber) }
                ?: YearMonth(today.year, today.monthNumber),
        )
    }
    RemDialog(
        title = "选择日期",
        onDismiss = onDismiss,
        confirmText = "确定",
        onConfirm = onDismiss,
        showButtons = false,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RemIconButton(IconName.ChevronBack, "上个月", onClick = { month = month.minusMonths(1) }, size = 14.dp)
            androidx.compose.material3.Text(
                "${month.year} 年 ${month.monthNumber} 月",
                style = RemType.title15,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            RemIconButton(IconName.ChevronRight, "下个月", onClick = { month = month.plusMonths(1) }, size = 14.dp)
        }
        Spacer(Modifier.height(12.dp))
        val offset = month.atDay(1).dayOfWeek.value - 1 // ISO 周一=1
        val daysInMonth = month.atDay(1).lengthOfMonth()
        val weeks = (offset + daysInMonth + 6) / 7
        val weekHeaders = listOf("一", "二", "三", "四", "五", "六", "日")
        Row(Modifier.fillMaxWidth()) {
            weekHeaders.forEach {
                androidx.compose.material3.Text(
                    it,
                    style = RemType.label12,
                    color = colors.textTertiary,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            }
        }
        var day = 1
        for (w in 0 until weeks) {
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                for (c in 0 until 7) {
                    val idx = w * 7 + c
                    if (idx < offset || day > daysInMonth) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        val d = day++
                        val date = LocalDate(month.year, month.monthNumber, d)
                        val isToday = date == today
                        val isSelected = date == initialDate
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(if (isSelected) colors.accent else Color.Transparent)
                                .clickable { onPick(date); onDismiss() },
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.material3.Text(
                                "$d",
                                style = RemType.text13,
                                color = when {
                                    isSelected -> Color.White
                                    isToday -> colors.accent
                                    else -> colors.textPrimary
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: 验证**

Run: `./gradlew :shared:desktopTest :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL（kotlinx-datetime 0.8.0 提供 YearMonth/atDay/lengthOfMonth/minusMonths/plusMonths）

- [ ] **Step 3: 提交**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/RemDatePicker.kt
git commit -m "feat(ui): add custom date picker dialog"
```

---

### Task 6: 路由修复（点侧边栏返回列表）+ 测试（TDD）

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/main/MainViewModel.kt:78-81`
- Test: `shared/src/commonTest/kotlin/com/myapplication/shared/ui/main/MainViewModelTest.kt`

- [ ] **Step 1: 写失败测试**（追加到 MainViewModelTest 类内）

```kotlin
    @Test
    fun selectScopeClosesDetail() {
        val vm = MainViewModel(FakeRepository())
        vm.openDetail(3)
        vm.selectScope(Scope.Today)
        assertEquals(Route.Main, vm.route.value)
    }
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew :shared:desktopTest --tests "com.myapplication.shared.ui.main.MainViewModelTest" -i` （或直接全量）
Expected: FAIL —— selectScope 未改 route，Route 仍为 Detail(3)

- [ ] **Step 3: 修复 MainViewModel.selectScope**

```kotlin
    fun selectScope(s: Scope) {
        scope.value = s
        searchQuery.value = ""
        back()
    }
```

- [ ] **Step 4: 运行确认通过**

Run: `./gradlew :shared:desktopTest`
Expected: PASS（现有 7 个 MainViewModel 测试 + 新测试全绿；selectScopeClearsSearchQuery 不受影响）

- [ ] **Step 5: 提交**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/ui/main/MainViewModel.kt shared/src/commonTest/kotlin/com/myapplication/shared/ui/main/MainViewModelTest.kt
git commit -m "fix(nav): close detail when switching sidebar scope"
```

---

### Task 7: Sidebar 重做

**Files:**
- Rewrite: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/sidebar/Sidebar.kt`

- [ ] **Step 1: 重写 Sidebar.kt**（全文替换；emoji→RemIcons、Material 组件→Rem 组件、选中胶囊 selectedBg 圆角 7dp、行高 28dp、hover 反馈、对话框→RemDialog）

```kotlin
package com.myapplication.shared.ui.sidebar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemDialog
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.components.RemIconButton
import com.myapplication.shared.ui.components.RemTextField
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.main.Scope
import com.myapplication.shared.ui.theme.ListColorKeys
import com.myapplication.shared.ui.theme.ListColorOf
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemSpacing
import com.myapplication.shared.ui.theme.RemType

@Composable
fun Sidebar(mainVm: MainViewModel) {
    val colors = LocalRemColors.current
    val lists by mainVm.lists.collectAsState()
    val scope by mainVm.scope.collectAsState()
    val query by mainVm.searchQuery.collectAsState()
    val todayCount by mainVm.todayCount.collectAsState()
    val scheduledCount by mainVm.scheduledCount.collectAsState()
    val allCount by mainVm.allCount.collectAsState()
    val completedCount by mainVm.completedCount.collectAsState()
    val trashCount by mainVm.trashCount.collectAsState()
    val listCounts by mainVm.listCounts.collectAsState()
    var showAddList by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxHeight()
            .width(220.dp)
            .background(colors.sidebarBg)
            .padding(vertical = RemSpacing.s16, horizontal = 10.dp),
    ) {
        androidx.compose.material3.Text(
            "提醒事项",
            style = RemType.title17,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(RemSpacing.s12))
        RemTextField(value = query, onValueChange = mainVm::setSearch, placeholder = "搜索", leadingIcon = IconName.Search)
        Spacer(Modifier.height(RemSpacing.s8))
        ScopeRow(IconName.Today, "今天", todayCount, scope == Scope.Today) { mainVm.selectScope(Scope.Today) }
        ScopeRow(IconName.Scheduled, "计划", scheduledCount, scope == Scope.Scheduled) { mainVm.selectScope(Scope.Scheduled) }
        ScopeRow(IconName.Tray, "全部待办", allCount, scope == Scope.All) { mainVm.selectScope(Scope.All) }
        ScopeRow(IconName.CheckCircle, "已完成", completedCount, scope == Scope.Completed) { mainVm.selectScope(Scope.Completed) }
        ScopeRow(IconName.Trash, "垃圾箱", trashCount, scope == Scope.Trash) { mainVm.selectScope(Scope.Trash) }
        Box(
            Modifier
                .padding(horizontal = 8.dp, vertical = RemSpacing.s8)
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.rowDivider),
        )
        androidx.compose.material3.Text(
            "我的列表",
            style = RemType.label12,
            color = colors.textTertiary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
        lists.forEach { list ->
            ListRow(
                list = list,
                count = listCounts[list.id] ?: 0,
                selected = scope == Scope.List(list.id),
                canDelete = list.position != 0,
                onSelect = { mainVm.selectScope(Scope.List(list.id)) },
                onDelete = { mainVm.deleteList(list) },
            )
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(RemRadii.r6))
                .clickable { showAddList = true }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemIcon(IconName.Plus, colors.accent, Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            androidx.compose.material3.Text("添加列表", style = RemType.label13, color = colors.accent)
        }
    }

    if (showAddList) {
        AddListDialog(
            onDismiss = { showAddList = false },
        ) { name, color ->
            mainVm.addList(name, color)
            showAddList = false
        }
    }
}

@Composable
private fun ScopeRow(icon: IconName, label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalRemColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(RemRadii.r7))
            .background(
                when {
                    selected -> colors.selectedBg
                    hovered -> colors.selectedBg.copy(alpha = 0.6f)
                    else -> Color.Transparent
                },
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemIcon(icon, if (selected) colors.accent else colors.textTertiary, Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        androidx.compose.material3.Text(
            label,
            style = RemType.text13.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (count > 0) {
            androidx.compose.material3.Text(
                count.toString(),
                style = RemType.text12,
                color = if (selected) colors.accent else colors.textTertiary,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListRow(
    list: TodoList,
    count: Int,
    selected: Boolean,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalRemColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(RemRadii.r7))
            .background(
                when {
                    selected -> colors.selectedBg
                    hovered -> colors.selectedBg.copy(alpha = 0.6f)
                    else -> Color.Transparent
                },
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelect,
                onLongClick = if (canDelete) ({ confirmDelete = true }) else null,
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).background(ListColorOf[list.colorKey] ?: Color.Gray, CircleShape))
        Spacer(Modifier.width(8.dp))
        androidx.compose.material3.Text(
            list.name,
            style = RemType.text13,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (count > 0) {
            androidx.compose.material3.Text(count.toString(), style = RemType.text12, color = colors.textTertiary)
        }
    }
    if (confirmDelete) {
        RemDialog(
            title = "删除列表",
            onDismiss = { confirmDelete = false },
            content = {
                androidx.compose.material3.Text(
                    "确定删除列表「${list.name}」？该列表的所有待办将移入垃圾箱。",
                    style = RemType.text13,
                    color = colors.textSecondary,
                )
            },
            confirmText = "删除",
            confirmDanger = true,
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AddListDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    val colors = LocalRemColors.current
    var name by remember { mutableStateOf("") }
    var colorKey by remember { mutableStateOf(ListColorKeys.first()) }
    RemDialog(
        title = "新建列表",
        onDismiss = onDismiss,
        confirmText = "确定",
        confirmDanger = false,
        onConfirm = {
            if (name.isNotBlank()) onConfirm(name.trim(), colorKey)
        },
    ) {
        RemTextField(value = name, onValueChange = { name = it }, placeholder = "列表名称")
        Spacer(Modifier.height(12.dp))
        Row {
            ListColorKeys.forEach { key ->
                Box(
                    Modifier
                        .padding(end = 8.dp)
                        .size(24.dp)
                        .background(ListColorOf[key] ?: Color.Gray, CircleShape)
                        .clip(CircleShape)
                        .clickable { colorKey = key },
                    contentAlignment = Alignment.Center,
                ) {
                    if (key == colorKey) {
                        RemIcon(IconName.CheckCircle, Color.White, Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: 验证**

Run: `./gradlew :shared:desktopTest :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/ui/sidebar/Sidebar.kt
git commit -m "feat(ui): redesign sidebar with tokens and rem components"
```

---

### Task 8: TodoListScreen 重做

**Files:**
- Rewrite: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/todolist/TodoListScreen.kt`

- [ ] **Step 1: 重写 TodoListScreen.kt**（栏标题 17/800 + 计数、RemTextField 快速输入、行 36dp+悬停+1px 分割线、RemCheckbox/RemBadge、已完成划线、子任务缩进 16dp、垃圾箱行 RemButton、空状态区分搜索/为空）

```kotlin
package com.myapplication.shared.ui.todolist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemBadge
import com.myapplication.shared.ui.components.RemButton
import com.myapplication.shared.ui.components.RemCheckbox
import com.myapplication.shared.ui.components.RemEmptyState
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.components.RemTextField
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.main.Scope
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemSpacing
import com.myapplication.shared.ui.theme.RemType
import com.myapplication.shared.util.bucketLabel
import com.myapplication.shared.util.bucketOf
import com.myapplication.shared.util.formatDueDate
import com.myapplication.shared.util.todayDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun TodoListScreen(mainVm: MainViewModel, modifier: Modifier = Modifier) {
    val colors = LocalRemColors.current
    val todos by mainVm.todos.collectAsState()
    val scope by mainVm.scope.collectAsState()
    val query by mainVm.searchQuery.collectAsState()

    Column(modifier) {
        val activeCount = todos.count { !it.isCompleted }
        androidx.compose.material3.Text(
            scopeTitle(scope, query),
            style = RemType.title17,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = RemSpacing.s16, vertical = 14.dp),
        )
        if (scope != Scope.Trash) {
            androidx.compose.material3.Text(
                if (scope == Scope.Completed) "${todos.size} 项" else "$activeCount 项未完成",
                style = RemType.text12,
                color = colors.textTertiary,
                modifier = Modifier.padding(horizontal = RemSpacing.s16).padding(bottom = 8.dp),
            )
            QuickAddRow(mainVm)
        }
        val today = todayDate()
        when {
            todos.isEmpty() && query.isNotBlank() -> RemEmptyState("没有找到结果", "换个关键词试试", IconName.Search)
            todos.isEmpty() -> RemEmptyState("没有待办", "", IconName.Tray)
            scope == Scope.Scheduled -> ScheduledGrouped(todos, today, mainVm)
            scope == Scope.Trash -> TrashList(todos, mainVm)
            else -> PlainList(todos, today, mainVm)
        }
    }
}

fun scopeTitle(scope: Scope, query: String): String = when {
    query.isNotBlank() -> "搜索"
    scope == Scope.Today -> "今天"
    scope == Scope.Scheduled -> "计划"
    scope == Scope.All -> "全部待办"
    scope == Scope.Completed -> "已完成"
    scope == Scope.Trash -> "垃圾箱"
    scope is Scope.List -> "列表"
    else -> "待办"
}

@Composable
private fun QuickAddRow(mainVm: MainViewModel) {
    var text by remember { mutableStateOf("") }
    RemTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = "添加待办…（支持“明天 15:00”等日期）",
        leadingIcon = IconName.Plus,
        onEnter = {
            mainVm.addQuick(text)
            text = ""
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RemSpacing.s16),
    )
}

@Composable
private fun PlainList(todos: List<TodoItem>, today: kotlinx.datetime.LocalDate, mainVm: MainViewModel) {
    val colors = LocalRemColors.current
    val active = todos.filter { !it.isCompleted && it.parentId == null }
    val childrenByParent = todos.filter { it.parentId != null }.groupBy { it.parentId!! }
    val completed = todos.filter { it.isCompleted && it.parentId == null }
    var expanded by remember { mutableStateOf(true) }

    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        items(active) { parent ->
            TodoRow(parent, mainVm, today, showChevron = childrenByParent[parent.id] != null, expanded = expanded, onToggleExpand = { expanded = !expanded })
            if (expanded) {
                childrenByParent[parent.id]?.forEach { child ->
                    TodoRow(child, mainVm, today, indent = true)
                }
            }
        }
        if (completed.isNotEmpty()) {
            item {
                androidx.compose.material3.Text(
                    "已完成",
                    style = RemType.label13,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                )
            }
            items(completed) { item -> TodoRow(item, mainVm, today) }
        }
    }
}

@Composable
private fun ScheduledGrouped(todos: List<TodoItem>, today: kotlinx.datetime.LocalDate, mainVm: MainViewModel) {
    val colors = LocalRemColors.current
    val tz = TimeZone.currentSystemDefault()
    val grouped = todos
        .filter { it.dueDate != null }
        .groupBy { bucketOf(it.dueDate!!.toLocalDateTime(tz).date, today) }
    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        listOf(
            com.myapplication.shared.util.DueBucket.OVERDUE,
            com.myapplication.shared.util.DueBucket.TODAY,
            com.myapplication.shared.util.DueBucket.TOMORROW,
            com.myapplication.shared.util.DueBucket.THIS_WEEK,
            com.myapplication.shared.util.DueBucket.LATER,
        ).forEach { bucket ->
            val items = grouped[bucket].orEmpty()
            if (items.isNotEmpty()) {
                item {
                    androidx.compose.material3.Text(
                        bucketLabel(bucket),
                        style = RemType.label13,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                    )
                }
                items(items) { item -> TodoRow(item, mainVm, today) }
            }
        }
    }
}

@Composable
private fun TrashList(todos: List<TodoItem>, mainVm: MainViewModel) {
    val colors = LocalRemColors.current
    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        items(todos) { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Text(
                    item.title,
                    style = RemType.text13,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                RemButton("恢复", onClick = { mainVm.restore(item) })
                Spacer(Modifier.width(8.dp))
                RemButton("彻底删除", onClick = { mainVm.deleteForever(item) }, danger = true)
            }
        }
    }
}

@Composable
fun TodoRow(
    item: TodoItem,
    mainVm: MainViewModel,
    today: kotlinx.datetime.LocalDate,
    indent: Boolean = false,
    showChevron: Boolean = false,
    expanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
) {
    val colors = LocalRemColors.current
    val isOverdue = item.dueDate?.let {
        it.toLocalDateTime(TimeZone.currentSystemDefault()).date < today
    } == true
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interactionSource, indication = null) {
                if (showChevron) onToggleExpand() else mainVm.openDetail(item.id)
            }
            .background(if (hovered) colors.selectedBg.copy(alpha = 0.6f) else Color.Transparent)
            .drawBehind {
                drawLine(colors.rowDivider, Offset(0f, size.height), Offset(size.width, size.height), 1f)
            }
            .padding(start = if (indent) 16.dp else 0.dp)
            .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemCheckbox(item.isCompleted, { mainVm.toggleCompleted(item) })
        Spacer(Modifier.width(10.dp))
        androidx.compose.material3.Text(
            item.title,
            style = RemType.text13,
            color = if (item.isCompleted) colors.textTertiary else colors.textPrimary,
            textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f),
        )
        if (showChevron) {
            RemIcon(if (expanded) IconName.ChevronDown else IconName.ChevronRight, colors.textTertiary, Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
        }
        item.dueDate?.let {
            RemBadge(
                label = formatDueDate(it),
                tint = if (isOverdue) colors.danger else null,
                icon = { RemIcon(IconName.Calendar, if (isOverdue) colors.danger else colors.textTertiary, Modifier.size(10.dp)) },
            )
        }
    }
}
```

- [ ] **Step 2: 验证**

Run: `./gradlew :shared:desktopTest :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/ui/todolist/TodoListScreen.kt
git commit -m "feat(ui): redesign todo list screen with rem components"
```

---

### Task 9: DetailScreen 重做

**Files:**
- Rewrite: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/detail/DetailScreen.kt`

- [ ] **Step 1: 重写 DetailScreen.kt**（标题内联编辑 15/700、RemCheckbox、关闭按钮、备注占位灰字、1px 分割线分区、RemBadge 日期+清除、RemDialog 列表切换、子任务 RemCheckbox 12dp+RemIconButton 删除、RemDatePicker、垃圾箱 RemButton danger、Esc 由 App 层处理）

```kotlin
package com.myapplication.shared.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myapplication.shared.di.AppGraph
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemBadge
import com.myapplication.shared.ui.components.RemButton
import com.myapplication.shared.ui.components.RemCheckbox
import com.myapplication.shared.ui.components.RemDatePicker
import com.myapplication.shared.ui.components.RemDialog
import com.myapplication.shared.ui.components.RemEmptyState
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.components.RemIconButton
import com.myapplication.shared.ui.components.RemTextField
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.theme.ListColorOf
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemSpacing
import com.myapplication.shared.ui.theme.RemType
import com.myapplication.shared.util.formatDueDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun DetailScreen(
    mainVm: MainViewModel,
    graph: AppGraph,
    todoId: Long,
    modifier: Modifier = Modifier,
) {
    val detailVm: DetailViewModel = viewModel(key = "detail-$todoId") {
        DetailViewModel(graph.repository, todoId)
    }
    val colors = LocalRemColors.current
    val todo by detailVm.todo.collectAsState()
    val subtasks by detailVm.subtasks.collectAsState()
    val lists by detailVm.lists.collectAsState()
    val current = todo
    val currentId = current?.id
    var titleText by remember(currentId) { mutableStateOf(current?.title ?: "") }
    var noteText by remember(currentId) { mutableStateOf(current?.note ?: "") }
    var newSub by remember(currentId) { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showListDialog by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = RemSpacing.s16, horizontal = 14.dp),
    ) {
        if (current == null) {
            RemEmptyState("待办不存在或已删除")
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RemCheckbox(current.isCompleted, { mainVm.toggleCompleted(current) })
            Spacer(Modifier.width(10.dp))
            RemTextField(
                value = titleText,
                onValueChange = {
                    titleText = it
                    detailVm.setTitle(it)
                },
                style = RemType.title15,
                filled = false,
                modifier = Modifier.weight(1f),
            )
            RemIconButton(IconName.Close, "关闭详情", onClick = mainVm::back, size = 16.dp)
        }
        Spacer(Modifier.height(10.dp))
        RemTextField(
            value = noteText,
            onValueChange = {
                noteText = it
                detailVm.setNote(it)
            },
            placeholder = "备注…",
            singleLine = false,
            minLines = 3,
            filled = false,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .clickable { showDatePicker = true }
                .drawBehind { drawLine(colors.rowDivider, Offset(0f, size.height), Offset(size.width, size.height), 1f) }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Text("日期", style = RemType.text13, color = colors.textSecondary)
            Spacer(Modifier.weight(1f))
            if (current.dueDate != null) {
                RemBadge(
                    label = formatDueDate(current.dueDate),
                    icon = { RemIcon(IconName.Calendar, colors.textTertiary, Modifier.size(10.dp)) },
                )
                Spacer(Modifier.width(8.dp))
                RemButton("清除", onClick = { detailVm.setDueDate(null) })
            } else {
                androidx.compose.material3.Text("无", style = RemType.text12, color = colors.textTertiary)
            }
        }

        val currentList = lists.firstOrNull { it.id == current.listId }
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { showListDialog = true }
                .drawBehind { drawLine(colors.rowDivider, Offset(0f, size.height), Offset(size.width, size.height), 1f) }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Text("列表", style = RemType.text13, color = colors.textSecondary)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(10.dp).background(ListColorOf[currentList?.colorKey] ?: Color.Gray, CircleShape))
            Spacer(Modifier.width(6.dp))
            androidx.compose.material3.Text(
                currentList?.name ?: "未知列表",
                style = RemType.text13,
                color = colors.textPrimary,
            )
        }
        Spacer(Modifier.height(16.dp))

        androidx.compose.material3.Text("子任务", style = RemType.label13, color = colors.textTertiary)
        Spacer(Modifier.height(6.dp))
        subtasks.forEach { sub ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                RemCheckbox(sub.isCompleted, { detailVm.toggleSubTask(sub) }, size = 12.dp)
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.Text(
                    sub.title,
                    style = RemType.text13,
                    color = if (sub.isCompleted) colors.textTertiary else colors.textPrimary,
                    textDecoration = if (sub.isCompleted) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f),
                )
                RemIconButton(IconName.Trash, "删除子任务", onClick = { detailVm.trashSubTask(sub) }, size = 14.dp)
            }
        }
        Spacer(Modifier.height(6.dp))
        RemTextField(
            value = newSub,
            onValueChange = { newSub = it },
            placeholder = "添加子任务…",
            onEnter = {
                detailVm.addSubTask(newSub)
                newSub = ""
            },
            trailing = "添加" to {
                detailVm.addSubTask(newSub)
                newSub = ""
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        RemButton(
            "移到垃圾箱",
            onClick = {
                mainVm.trash(current)
                mainVm.back()
            },
            danger = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showDatePicker) {
        RemDatePicker(
            initialDate = current?.dueDate?.toLocalDateTime(TimeZone.currentSystemDefault())?.date,
            onPick = { date ->
                val time = current?.dueDate
                    ?.toLocalDateTime(TimeZone.currentSystemDefault())?.time
                    ?: LocalTime(9, 0)
                detailVm.setDueDate(LocalDateTime(date, time))
            },
            onDismiss = { showDatePicker = false },
        )
    }

    if (showListDialog) {
        RemDialog(
            title = "选择列表",
            onDismiss = { showListDialog = false },
            confirmText = "确定",
            onConfirm = { showListDialog = false },
            showButtons = false,
        ) {
            lists.forEach { list ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            showListDialog = false
                            detailVm.moveToList(list.id)
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).background(ListColorOf[list.colorKey] ?: Color.Gray, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.material3.Text(
                        list.name,
                        style = RemType.text13,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (list.id == current?.listId) {
                        RemIcon(IconName.CheckCircle, colors.accent, Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: 验证**

Run: `./gradlew :shared:desktopTest :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add shared/src/commonMain/kotlin/com/myapplication/shared/ui/detail/DetailScreen.kt
git commit -m "feat(ui): redesign detail screen with rem components"
```

---

### Task 10: App 根（Esc/BackHandler/栏宽/窄屏布局）

**Files:**
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/PlatformBackHandler.kt`（expect）
- Create: `shared/src/androidMain/kotlin/com/myapplication/shared/ui/PlatformBackHandler.android.kt`（actual）
- Create: `shared/src/desktopMain/kotlin/com/myapplication/shared/ui/PlatformBackHandler.desktop.kt`（actual）
- Create: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/narrow/NarrowShell.kt`
- Rewrite: `shared/src/commonMain/kotlin/App.kt`

- [ ] **Step 1: 创建 expect/actual 返回键处理**

`PlatformBackHandler.kt`（commonMain）：

```kotlin
package com.myapplication.shared.ui

import androidx.compose.runtime.Composable

@Composable
expect fun PlatformBackHandler(onBack: () -> Unit)
```

`PlatformBackHandler.android.kt`（androidMain）：

```kotlin
package com.myapplication.shared.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
}
```

`PlatformBackHandler.desktop.kt`（desktopMain）：

```kotlin
package com.myapplication.shared.ui

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(onBack: () -> Unit) = Unit
```

- [ ] **Step 2: 创建 NarrowShell.kt**（顶栏：标题+搜索切换；底部导航：5 项图标+文字，选中 accent）

```kotlin
package com.myapplication.shared.ui.narrow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.components.RemIconButton
import com.myapplication.shared.ui.components.RemTextField
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.main.Scope
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemType
import com.myapplication.shared.ui.todolist.scopeTitle

@Composable
fun NarrowTopBar(mainVm: MainViewModel, modifier: Modifier = Modifier) {
    val colors = LocalRemColors.current
    val scope by mainVm.scope.collectAsState()
    val query by mainVm.searchQuery.collectAsState()
    var searching by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.sidebarBg)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (searching) {
            RemTextField(value = query, onValueChange = mainVm::setSearch, placeholder = "搜索", leadingIcon = IconName.Search)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Text(
                    scopeTitle(scope, query),
                    style = RemType.title17,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                RemIconButton(IconName.Search, "搜索", onClick = { searching = true }, size = 18.dp)
            }
        }
    }
}

@Composable
fun NarrowBottomNav(mainVm: MainViewModel, modifier: Modifier = Modifier) {
    val colors = LocalRemColors.current
    val scope by mainVm.scope.collectAsState()
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(colors.sidebarBg),
    ) {
        NavItem(IconName.Today, "今天", scope == Scope.Today) { mainVm.selectScope(Scope.Today) }
        NavItem(IconName.Scheduled, "计划", scope == Scope.Scheduled) { mainVm.selectScope(Scope.Scheduled) }
        NavItem(IconName.Tray, "全部", scope == Scope.All) { mainVm.selectScope(Scope.All) }
        NavItem(IconName.CheckCircle, "已完成", scope == Scope.Completed) { mainVm.selectScope(Scope.Completed) }
        NavItem(IconName.Trash, "垃圾箱", scope == Scope.Trash) { mainVm.selectScope(Scope.Trash) }
    }
}

@Composable
private fun NavItem(icon: IconName, label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalRemColors.current
    val tint = if (selected) colors.accent else colors.textTertiary
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RemIcon(icon, tint, Modifier.size(20.dp))
        Spacer(Modifier.height(2.dp))
        androidx.compose.material3.Text(
            label,
            style = TextStyle(fontSize = 10.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = tint,
        )
    }
}
```

- [ ] **Step 3: 重写 App.kt**（Esc 全局返回、PlatformBackHandler、栏宽 220/340、窄屏用 NarrowShell、背景色收口）

```kotlin
package com.myapplication.shared.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myapplication.shared.di.AppGraph
import com.myapplication.shared.di.createAppGraph
import com.myapplication.shared.ui.PlatformBackHandler
import com.myapplication.shared.ui.detail.DetailScreen
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.main.Route
import com.myapplication.shared.ui.narrow.NarrowBottomNav
import com.myapplication.shared.ui.narrow.NarrowTopBar
import com.myapplication.shared.ui.sidebar.Sidebar
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.todolist.TodoListScreen

@Composable
fun App() {
    RemindersTheme {
        val graph = remember { createAppGraph() }
        AppRoot(graph)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppRoot(graph: AppGraph) {
    val mainVm: MainViewModel = viewModel { MainViewModel(graph.repository) }
    val route by mainVm.route.collectAsState()
    val colors = LocalRemColors.current

    PlatformBackHandler { mainVm.back() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(colors.contentBg)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && event.key == Key.Escape) {
                    mainVm.back()
                    true
                } else {
                    false
                }
            },
    ) {
        val wide = maxWidth >= 900.dp
        val selectedId = (route as? Route.Detail)?.todoId
        when {
            wide -> {
                Row(Modifier.fillMaxSize()) {
                    Sidebar(mainVm)
                    TodoListScreen(mainVm, Modifier.weight(1f).background(colors.contentBg))
                    if (selectedId != null) {
                        DetailScreen(mainVm, graph, selectedId, Modifier.width(340.dp))
                    }
                }
            }
            selectedId != null -> {
                DetailScreen(mainVm, graph, selectedId, Modifier.fillMaxSize())
            }
            else -> {
                Column(Modifier.fillMaxSize()) {
                    NarrowTopBar(mainVm)
                    TodoListScreen(mainVm, Modifier.weight(1f))
                    NarrowBottomNav(mainVm)
                }
            }
        }
    }
}
```

> App.kt 内不再引用 RemindersTheme —— 需补 import `com.myapplication.shared.ui.theme.RemindersTheme`。

- [ ] **Step 4: 验证**

Run: `./gradlew :shared:desktopTest :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL（androidMain 的 activity-compose 1.13.0 已在依赖中，BackHandler 可用）

- [ ] **Step 5: 提交**

```bash
git add shared/src/commonMain/kotlin/App.kt shared/src/commonMain/kotlin/com/myapplication/shared/ui/PlatformBackHandler.kt shared/src/androidMain/kotlin/com/myapplication/shared/ui/ shared/src/desktopMain/kotlin/com/myapplication/shared/ui/ shared/src/commonMain/kotlin/com/myapplication/shared/ui/narrow/
git commit -m "feat(app): escape/back handling and narrow shell layout"
```

---

### Task 11: 移除 Material3 与收尾验证

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/theme/Theme.kt`（去掉 MaterialTheme 外壳）
- Modify: `shared/src/commonMain/kotlin/com/myapplication/shared/ui/components/RemBadge.kt` / `RemButton.kt` / `RemTextField.kt` / `RemDialog.kt` / `RemEmptyState.kt` / `RemDatePicker.kt` / `Sidebar.kt` / `TodoListScreen.kt` / `DetailScreen.kt` / `NarrowShell.kt`（`androidx.compose.material3.Text` → `androidx.compose.ui.platform` 默认 Text，即 `androidx.compose.foundation.text` 无 —— 实际用顶层 `Text`，import `androidx.compose.material3.Text` 改为 `androidx.compose.material3` 移除 + import `androidx.compose.material3.Text` 替换为不引用 material3）

- [ ] **Step 1: 全局替换 material3 引用**

Run（先确认清单）:
```bash
grep -rn "material3" shared/src --include=*.kt
```
Expected: 命中 `androidx.compose.material3.Text` 的 import 与用法、Theme.kt 的 MaterialTheme/darkColorScheme/lightColorScheme。

替换规则（每个文件内机械执行）：
- `import androidx.compose.material3.Text` → `import androidx.compose.foundation.text.BasicText`
- 所有 `Text(` 调用 → `BasicText(`（material3 的 Text 是 BasicText 的薄封装；计划代码中每个 Text 均已显式传 style/color，无行为差异）

涉及文件：Sidebar.kt、TodoListScreen.kt、DetailScreen.kt、NarrowShell.kt、RemBadge.kt、RemButton.kt、RemTextField.kt、RemDialog.kt、RemEmptyState.kt、RemDatePicker.kt。

`Theme.kt` 最终版：

```kotlin
package com.myapplication.shared.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalTextStyle

@Composable
fun RemindersTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkRemColors else LightRemColors
    CompositionLocalProvider(
        LocalRemColors provides colors,
        LocalTextStyle provides RemType.text13.copy(color = colors.textPrimary),
    ) {
        content()
    }
}
```

- [ ] **Step 2: 确认零 material3 引用**

Run: `grep -rn "material3" shared/src --include=*.kt`
Expected: 无输出（空）

- [ ] **Step 3: 全量验证**

Run: `./gradlew :shared:desktopTest --rerun-tasks :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL；测试计数 50 全绿（49 旧 + selectScopeClosesDetail）

- [ ] **Step 4: 桌面冒烟（可选但建议）**

Run: `nohup ./gradlew :desktopApp:run --quiet > /tmp/reminders-ui.log 2>&1 &`，等待窗口出现，确认：三栏渲染、无异常日志（`grep -i "exception" /tmp/reminders-ui.log` 为空），完成后 `pkill -f "MainKt"`。

- [ ] **Step 5: 提交**

```bash
git add shared/src
git commit -m "refactor(ui): drop material3 in favor of foundation primitives"
```

---

### Task 12: 手工验收清单（交付前）

**Files:** 无（验收）

- [ ] **Step 1: 对照规格验收**（桌面 `./gradlew :desktopApp:run`）

1. 宽屏三栏：侧边栏 220 / 列表 / 详情 340，白底侧边栏 + #FAFAFC 列表区
2. 侧边栏选中项为浅灰胶囊（selectedBg 圆角 7dp），hover 有 150ms 淡入反馈
3. 快速输入"明天 15:00 交季度报告"→ 日期徽章"今天 15:00"出现
4. 点侧边栏"已完成"→ 详情关闭、列表切换（本计划修复的核心）
5. 详情页：标题内联编辑、备注灰字占位、日期行/列表行分割线、RemDatePicker 选日期、列表对话框带彩色圆点
6. 勾选动画：spring 弹性 200-250ms；按压缩放
7. Esc 关闭详情；键盘 Tab 可聚焦按钮（焦点环 2px accent）
8. 暗色模式（系统切换）：全界面跟随
9. 无任何 emoji 图标残留（侧边栏/列表/详情全为矢量图标）
10. 垃圾箱：恢复/彻底删除按钮样式统一
11. 空状态：搜索无结果/无待办 有图标+文案
12. （如装模拟器）窄屏：底部导航 5 项、顶栏搜索、返回键回退

- [ ] **Step 2: 最终提交确认**

Run: `git status --porcelain`
Expected: 干净

- [ ] **Step 3: 收尾提交（如验收有微调）**

按需修复后：
```bash
git add -A
git commit -m "fix(ui): polish from manual acceptance"
```
（若无需修改则跳过）

---

## 自检记录（执行前确认）

- 规格 §3 色板/§4 尺寸/§5 排版 → Task 1（token）+ 各屏 Task 7-10 引用
- 规格 §6 组件清单 → Task 2/3/4/5 全部组件
- 规格 §7 布局结构（宽/窄）→ Task 7/8/9/10
- 规格 §8 交互修复（路由/Esc/BackHandler/焦点环/微交互）→ Task 6 + Task 10 + 组件三态
- 规格 §9 数据流（仅 selectScope）→ Task 6
- 规格 §10 测试 → Task 6（TDD）+ Task 11 全量
- 规格 §11 YAGNI → 未引入任何新依赖
- 已知遗留（评审认可，不在本次范围）：共享 expanded 布尔、每次按键写库、DetailViewModel 不清理、Scheduled 含已完成项
