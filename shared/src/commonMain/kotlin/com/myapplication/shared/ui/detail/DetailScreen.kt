package com.myapplication.shared.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.myapplication.shared.di.AppGraph
import com.myapplication.shared.ui.main.MainViewModel

/**
 * 待办详情页（兼容包装）：完整实现见 [DetailContent]。
 * 保持旧调用点（App.kt 宽屏详情栏 / 窄屏 ModalBottomSheet）编译不变，
 * 默认渲染关闭按钮。
 */
@Composable
fun DetailScreen(
    mainVm: MainViewModel,
    graph: AppGraph,
    todoId: Long,
    modifier: Modifier = Modifier,
) {
    DetailContent(
        mainVm = mainVm,
        graph = graph,
        todoId = todoId,
        modifier = modifier,
        showCloseButton = true,
    )
}
