package com.myapplication.shared.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemType

/**
 * 通用模态对话框，基于 [Popup] 自绘（跨平台一致，不依赖 Material Dialog）。
 *
 * 设计要点：
 * - 遮罩：全屏黑色半透明层，点遮罩即 [onDismiss]；对话框面板自身用一个
 *   空 clickable 吞掉点击事件，防止误触把对话框当成遮罩关掉；
 * - 键盘：Popup 设为 focusable，桌面端按 Esc（KeyUp）触发 [onDismiss]；
 * - 动效：首次挂载后把 visible 置 true，触发 fadeIn + 95%→100% scaleIn，
 *   关闭时 fadeOut，避免初次显示闪跳；
 * - 面板配色不取自 token，而是按 isSystemInDarkTheme 硬编码近白/近黑面板色，
 *   保证与遮罩视觉一致性；
 * - [confirmDanger] 控制底部确认按钮为 Danger 变体（删除类操作）；[showButtons]
 *   为 false 时隐藏底部按钮（如日期选择器）。
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RemDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    confirmDanger: Boolean = false,
    dismissText: String = "取消",
    showButtons: Boolean = true,
) {
    val colors = LocalRemColors.current
    // 初始 false 再置 true，让入场动画从隐藏状态开始
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                // 遮罩透明度：深色主题更黑更实，浅色主题稍淡
                .background(Color.Black.copy(alpha = if (isSystemInDarkTheme()) 0.6f else 0.45f))
                // 点遮罩关闭；indication = null 去掉点击水波
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                // 桌面端 Esc 关闭（KeyUp 避免长按重复触发）
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp && event.key == Key.Escape) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.95f, animationSpec = tween(180)),
                exit = fadeOut(tween(120)),
            ) {
                Column(
                    Modifier
                        .widthIn(max = 360.dp)
                        .clip(RoundedCornerShape(RemRadii.r3))
                        .background(if (isSystemInDarkTheme()) Color(0xFF2A2A2E) else Color(0xFFFEFEFE))
                        .border(1.dp, colors.border, RoundedCornerShape(RemRadii.r3))
                        // 空 clickable：消费点击，防止穿透到遮罩触发关闭
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .padding(20.dp),
                ) {
                    androidx.compose.foundation.text.BasicText(
                        title,
                        style = RemType.title18.copy(fontWeight = FontWeight.Bold, color = colors.textHigh),
                    )
                    Spacer(Modifier.height(16.dp))
                    content()
                    if (showButtons) {
                        Spacer(Modifier.height(20.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            // 取消（Ghost）在左，确认（Default/Danger）在右
                            RemButton(dismissText, onDismiss)
                            Spacer(Modifier.width(8.dp))
                            RemButton(
                                confirmText,
                                onConfirm,
                                enabled = confirmEnabled,
                                variant = if (confirmDanger) RemButtonVariant.Danger else RemButtonVariant.Default,
                            )
                        }
                    }
                }
            }
        }
    }
}
