package com.myapplication.shared.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.myapplication.shared.di.AppGraph
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.theme.LocalRemColors

/**
 * 桌面端详情检查器：固定宽度的右侧详情栏，左侧画分隔线。
 * [todoId] 为空时渲染空容器（不创建 DetailContent / ViewModel）。
 */
@Composable
fun DetailInspector(
    mainVm: MainViewModel,
    graph: AppGraph,
    todoId: Long?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalRemColors.current
    Box(
        modifier
            .width(348.dp)
            .fillMaxHeight()
            .background(colors.surface)
            .drawBehind {
                drawLine(colors.borderSubtle, Offset(0f, 0f), Offset(0f, size.height), 1f)
            },
    ) {
        if (todoId != null) {
            DetailContent(
                mainVm = mainVm,
                graph = graph,
                todoId = todoId,
                showCloseButton = true,
            )
        }
    }
}
