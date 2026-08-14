package com.myapplication.shared.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.ui.components.RemDialog
import com.myapplication.shared.ui.components.RemTextField
import com.myapplication.shared.ui.theme.ListColorKeys
import com.myapplication.shared.ui.theme.ListColorOf
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemType

@Composable
internal fun ListEditorDialog(
    list: TodoList?,
    onDismiss: () -> Unit,
    onSave: (name: String, colorKey: String) -> Unit,
) {
    var name by remember(list?.id) { mutableStateOf(list?.name ?: "") }
    var colorKey by remember(list?.id) { mutableStateOf(list?.colorKey ?: ListColorKeys.first()) }
    val colors = LocalRemColors.current
    val canSave = name.isNotBlank()
    val colorRows = ListColorKeys.chunked(4)

    RemDialog(
        title = if (list == null) "新建列表" else "编辑列表",
        confirmText = "保存",
        confirmEnabled = canSave,
        onDismiss = onDismiss,
        onConfirm = {
            if (canSave) {
                onSave(name, colorKey)
                onDismiss()
            }
        },
        content = {
            Column {
                RemTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "列表名称",
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!canSave) {
                    Spacer(Modifier.height(6.dp))
                    BasicText("请输入列表名称", style = RemType.text12.copy(color = colors.error))
                }
                Spacer(Modifier.height(14.dp))
                BasicText("颜色", style = RemType.label12.copy(color = colors.textLow))
                Spacer(Modifier.height(8.dp))
                Column(Modifier.selectableGroup()) {
                    colorRows.forEachIndexed { rowIndex, rowKeys ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            rowKeys.forEachIndexed { index, key ->
                                ColorSwatch(
                                    key = key,
                                    color = ListColorOf[key] ?: Color.Gray,
                                    selected = key == colorKey,
                                    onClick = { colorKey = key },
                                )
                                if (index != rowKeys.lastIndex) {
                                    Spacer(Modifier.width(10.dp))
                                }
                            }
                        }
                        if (rowIndex != colorRows.lastIndex) {
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun ColorSwatch(
    key: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalRemColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val label = listColorLabel(key)
    androidx.compose.foundation.layout.Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            ).semantics { contentDescription = "$label 列表颜色" },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) colors.textHigh else colors.border,
                    shape = CircleShape,
                ),
        )
    }
}

private fun listColorLabel(key: String): String =
    when (key) {
        "blue" -> "蓝色"
        "green" -> "绿色"
        "orange" -> "橙色"
        "yellow" -> "黄色"
        "teal" -> "青色"
        "red" -> "红色"
        "purple" -> "紫色"
        "gray" -> "灰色"
        else -> key
    }
