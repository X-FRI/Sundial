package com.myapplication.shared.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (isSystemInDarkTheme()) 0.6f else 0.45f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
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
                        .shadow(
                            elevation = if (isSystemInDarkTheme()) 24.dp else 16.dp,
                            shape = RoundedCornerShape(RemRadii.r3),
                            ambientColor = if (isSystemInDarkTheme()) Color(0x80000000) else Color(0x26000000),
                            spotColor = if (isSystemInDarkTheme()) Color(0x80000000) else Color(0x26000000),
                            clip = false,
                        )
                        .clip(RoundedCornerShape(RemRadii.r3))
                        .background(if (isSystemInDarkTheme()) Color(0xFF2A2A2E) else Color(0xFFFEFEFE))
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
                            RemButton(dismissText, onDismiss)
                            Spacer(Modifier.width(8.dp))
                            RemButton(confirmText, onConfirm, variant = if (confirmDanger) RemButtonVariant.Danger else RemButtonVariant.Default)
                        }
                    }
                }
            }
        }
    }
}
