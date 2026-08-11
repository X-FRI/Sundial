package com.myapplication.shared.ui.todolist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.main.Scope
import com.myapplication.shared.ui.theme.LocalRemColors

/**
 * 悬浮新建按钮（仅窄屏主列表右下角使用）。
 *
 * 行为：点击弹出 [TodoFormDialog]；若当前范围是某个自定义列表
 * （Scope.List），对话框默认选中该列表——用户在列表页里新建的待办
 * 自动归入该列表。
 */
@Composable
fun TodoFAB(mainVm: MainViewModel, modifier: Modifier = Modifier) {
    val colors = LocalRemColors.current
    val lists by mainVm.lists.collectAsState()
    val scope by mainVm.scope.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    // FAB 本体：56dp 圆形按钮，hover 时加深品牌色。
    Box(
        modifier
            .size(56.dp)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(if (hovered) colors.brandHover else colors.brand)
            .clickable(interactionSource = interaction, indication = null) { showCreate = true }
            .semantics { contentDescription = "新建待办" },
        contentAlignment = Alignment.Center,
    ) {
        RemIcon(IconName.Plus, androidx.compose.ui.graphics.Color.White, Modifier.size(22.dp))
    }

    // 表单对话框：确认后交给 mainVm.createTodo 落库并关闭。
    if (showCreate) {
        TodoFormDialog(
            lists = lists,
            defaultListId = (scope as? Scope.List)?.listId,
            onDismiss = { showCreate = false },
            onConfirm = { title, note, due, flag, listId ->
                mainVm.createTodo(title, note, due, flag, listId)
                showCreate = false
            },
        )
    }
}
