package com.myapplication.shared.ui.recurrence

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.recurrence.RecurrenceRule
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemButton
import com.myapplication.shared.ui.components.RemDialog
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.components.rememberHoverBackground
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemType

@Composable
fun RecurrencePicker(
    selected: RecurrenceRule?,
    onSelect: (RecurrenceRule?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalRemColors.current
    val options =
        listOf<RecurrenceRule?>(
            null,
            RecurrenceRule.Daily(),
            RecurrenceRule.Weekly(),
            RecurrenceRule.Monthly(),
        )

    RemDialog(
        title = "重复",
        onDismiss = onDismiss,
        confirmText = "确定",
        onConfirm = onDismiss,
        showButtons = false,
        content = {
            Column {
                options.forEach { option ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val checked = option == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(rememberHoverBackground(interactionSource))
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                            ) {
                                onSelect(option)
                                onDismiss()
                            }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicText(
                            recurrenceSummary(option),
                            style = RemType.text14.copy(color = colors.textHigh),
                            modifier = Modifier.weight(1f),
                        )
                        if (checked) {
                            Spacer(Modifier.width(8.dp))
                            RemIcon(IconName.CheckCircle, colors.brand, Modifier.size(16.dp))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    RemButton("关闭", onClick = onDismiss)
                }
            }
        },
    )
}
