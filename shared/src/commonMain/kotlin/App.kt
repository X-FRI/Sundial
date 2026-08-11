package com.myapplication.shared.ui.app

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myapplication.shared.di.AppGraph
import com.myapplication.shared.di.createAppGraph
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.main.Route
import com.myapplication.shared.ui.theme.RemindersTheme

@Composable
fun App() {
    RemindersTheme {
        val graph = remember { createAppGraph() }
        AppRoot(graph)
    }
}

@Composable
fun AppRoot(graph: AppGraph) {
    val mainVm: MainViewModel = viewModel { MainViewModel(graph.repository) }
    val route by mainVm.route.collectAsState()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 900.dp
        val selectedId = (route as? Route.Detail)?.todoId
        when {
            wide -> {
                Row(Modifier.fillMaxSize()) {
                    Sidebar(mainVm)
                    TodoListScreen(mainVm, Modifier.weight(1f))
                    if (selectedId != null) {
                        DetailScreen(mainVm, graph, selectedId)
                    }
                }
            }
            selectedId != null -> {
                DetailScreen(mainVm, graph, selectedId)
            }
            else -> {
                Column(Modifier.fillMaxSize()) {
                    NarrowTopBar(mainVm)
                    TodoListScreen(mainVm, Modifier.weight(1f))
                    NarrowBottomNav(mainVm)
                }
            }
        }
    }
}

@Composable
fun NarrowTopBar(mainVm: MainViewModel) {
    Text("提醒事项", style = MaterialTheme.typography.titleLarge)
}

@Composable
fun NarrowBottomNav(mainVm: MainViewModel) {
    Text("")
}

@Composable
fun Sidebar(mainVm: MainViewModel) = Text("")

@Composable
fun TodoListScreen(mainVm: MainViewModel, modifier: Modifier = Modifier) = Text("")

@Composable
fun DetailScreen(mainVm: MainViewModel, graph: AppGraph, todoId: Long) = Text("")
