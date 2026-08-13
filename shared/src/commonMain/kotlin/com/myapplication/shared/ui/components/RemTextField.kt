package com.myapplication.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemType

/**
 * 通用文本输入框（BasicTextField 自绘，不依赖 Material）。
 *
 * 设计要点：
 * - 焦点态反馈：onFocusChanged 记录焦点，边框颜色随之在 brand/border 间切换；
 *   [bordered] 为 false 时边框恒为 0dp（无宽度变化，不引起布局跳动）；
 * - placeholder 通过 decorationBox 在值为空时叠加显示，不占用布局空间；
 * - [onEnter] 非空时把键盘 IME 设为 Done 并拦截确认键（桌面端回车、移动端键盘完成键）；
 * - [trailing] 为右侧可点击文字（如"清除"），颜色恒为品牌色；
 * - [trailingContent] 为右侧自定义操作区（如显示/隐藏密码的小图标）；
 * - 光标颜色为品牌色，文字颜色固定 textHigh。
 */
@Composable
fun RemTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    style: TextStyle = RemType.text14,
    leadingIcon: IconName? = null,
    onEnter: (() -> Unit)? = null,
    trailing: Pair<String, () -> Unit>? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    bordered: Boolean = true,
    readOnly: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val colors = LocalRemColors.current
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .clip(RoundedCornerShape(RemRadii.r2))
            .background(colors.inputBg)
            // 焦点时边框变品牌色；bordered=false 时保持 0dp 无边框
            .border(
                if (bordered) {
                    1.dp
                } else {
                    0.dp
                },
                if (focused) colors.brand else colors.border,
                RoundedCornerShape(RemRadii.r2),
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 可选前置图标（如搜索），弱化文字色，固定 14dp
        if (leadingIcon != null) {
            RemIcon(leadingIcon, colors.textLow, Modifier.width(14.dp).height(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focused = it.isFocused },
            textStyle = style.copy(color = colors.textHigh),
            readOnly = readOnly,
            cursorBrush = SolidColor(colors.brand),
            singleLine = singleLine,
            minLines = minLines,
            visualTransformation = visualTransformation,
            // 有 onEnter 才声明 Done 动作，否则保持默认键盘
            keyboardOptions = if (onEnter != null) KeyboardOptions(imeAction = ImeAction.Done) else KeyboardOptions.Default,
            keyboardActions = if (onEnter != null) KeyboardActions(onDone = { onEnter() }) else KeyboardActions.Default,
            decorationBox = { inner ->
                // 值为空且有 placeholder 时先画占位文字，再画真正的输入内容
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    androidx.compose.foundation.text.BasicText(placeholder, style = style.copy(color = colors.textLow))
                }
                inner()
            },
        )
        if (trailing != null) {
            Spacer(Modifier.width(6.dp))
            androidx.compose.foundation.text.BasicText(
                trailing.first,
                style = RemType.label12.copy(color = colors.brand),
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { trailing.second() },
            )
        }
        if (trailingContent != null) {
            Spacer(Modifier.width(6.dp))
            trailingContent()
        }
    }
}
