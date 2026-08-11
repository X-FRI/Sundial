package com.myapplication.shared.ui.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
import com.myapplication.shared.ui.theme.RemindersTheme
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

    val isDetail = (route as? Route.Detail) != null
    PlatformBackHandler(enabled = isDetail) { mainVm.back() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(colors.bgSecondary)
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
                    TodoListScreen(mainVm, Modifier.weight(1f).background(colors.bgSecondary))
                    AnimatedVisibility(
                        visible = selectedId != null,
                        enter = fadeIn(tween(150)) + slideInHorizontally(initialOffsetX = { it / 8 }),
                        exit = fadeOut(tween(100)),
                        modifier = Modifier
                            .width(340.dp)
                            .background(colors.bgPrimary)
                            .drawBehind {
                                drawLine(
                                    colors.border,
                                    Offset(0f, 0f),
                                    Offset(0f, size.height),
                                    1f,
                                )
                            },
                    ) {
                        selectedId?.let { DetailScreen(mainVm, graph, it) }
                    }
                }
            }
            selectedId != null -> {
                DetailScreen(mainVm, graph, selectedId, Modifier.fillMaxSize())
            }
            else -> {
                Column(Modifier.fillMaxSize().background(colors.bgPrimary)) {
                    NarrowTopBar(mainVm)
                    TodoListScreen(mainVm, Modifier.weight(1f), showHeader = false)
                    NarrowBottomNav(mainVm)
                }
            }
        }
    }
}
