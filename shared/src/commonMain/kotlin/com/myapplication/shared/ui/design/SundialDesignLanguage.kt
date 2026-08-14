package com.myapplication.shared.ui.design

import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.main.Scope

/**
 * Sundial 的产品级导航语义。
 *
 * 这层刻意不关心桌面 sidebar 或移动 bottom nav 的具体绘制方式，只回答：
 * 当前 scope 属于哪个一级目的地，以及用户点击一级目的地时应落到哪个 scope。
 */
enum class SundialDestination {
    Workbench,
    Lists,
    Analytics,
}

data class SundialNavItem(
    val destination: SundialDestination,
    val label: String,
    val icon: IconName,
)

data class SundialLensItem(
    val scope: Scope,
    val label: String,
    val icon: IconName,
)

fun sundialPrimaryDestinations(): List<SundialNavItem> =
    listOf(
        SundialNavItem(SundialDestination.Workbench, "工作台", IconName.Layers),
        SundialNavItem(SundialDestination.Lists, "列表", IconName.Tray),
        SundialNavItem(SundialDestination.Analytics, "分析", IconName.Chart),
    )

fun sundialWorkbenchLenses(): List<SundialLensItem> =
    listOf(
        SundialLensItem(Scope.All, "全部", IconName.Layers),
        SundialLensItem(Scope.Today, "今天", IconName.Today),
        SundialLensItem(Scope.Scheduled, "计划", IconName.Scheduled),
        SundialLensItem(Scope.Completed, "已完成", IconName.CheckCircle),
        SundialLensItem(Scope.Trash, "垃圾箱", IconName.Trash),
    )

fun destinationForScope(scope: Scope): SundialDestination =
    when (scope) {
        Scope.Analytics -> SundialDestination.Analytics
        is Scope.List -> SundialDestination.Lists
        Scope.All,
        Scope.Today,
        Scope.Scheduled,
        Scope.Completed,
        Scope.Trash,
        -> SundialDestination.Workbench
    }

fun scopeForDestination(
    destination: SundialDestination,
    lists: List<TodoList>,
): Scope =
    when (destination) {
        SundialDestination.Workbench -> Scope.All
        SundialDestination.Lists -> lists.firstOrNull()?.let { Scope.List(it.id) } ?: Scope.All
        SundialDestination.Analytics -> Scope.Analytics
    }
