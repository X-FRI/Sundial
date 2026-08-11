package com.myapplication.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
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
            .background(if (filled) colors.selectedBg else Color.Transparent)
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
