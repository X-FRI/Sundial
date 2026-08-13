package com.myapplication.shared.ui.design

import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.main.Scope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.Instant

class SundialDesignLanguageTest {
    @Test
    fun topLevelDestinationMapsSmartScopesToWorkbench() {
        assertEquals(SundialDestination.Workbench, destinationForScope(Scope.All))
        assertEquals(SundialDestination.Workbench, destinationForScope(Scope.Today))
        assertEquals(SundialDestination.Workbench, destinationForScope(Scope.Scheduled))
        assertEquals(SundialDestination.Workbench, destinationForScope(Scope.Completed))
        assertEquals(SundialDestination.Workbench, destinationForScope(Scope.Trash))
    }

    @Test
    fun topLevelDestinationMapsListAndAnalytics() {
        assertEquals(SundialDestination.Lists, destinationForScope(Scope.List(7)))
        assertEquals(SundialDestination.Analytics, destinationForScope(Scope.Analytics))
    }

    @Test
    fun selectingListsUsesFirstListOrWorkbenchFallback() {
        val lists = listOf(TodoList(9, "收件箱", "blue", 0, Instant.fromEpochMilliseconds(0)))

        assertEquals(Scope.List(9), scopeForDestination(SundialDestination.Lists, lists))
        assertEquals(Scope.All, scopeForDestination(SundialDestination.Lists, emptyList()))
    }

    @Test
    fun primaryDestinationsUseStableLabelsAndIcons() {
        assertEquals(listOf("工作台", "列表", "分析"), sundialPrimaryDestinations().map { it.label })
        assertEquals(listOf(IconName.Layers, IconName.Tray, IconName.Chart), sundialPrimaryDestinations().map { it.icon })
    }

    @Test
    fun workbenchLensesUseStableOrderLabelsAndScopes() {
        assertEquals(
            listOf("全部", "今天", "计划", "已完成", "垃圾箱"),
            sundialWorkbenchLenses().map { it.label },
        )
        assertEquals(
            listOf(Scope.All, Scope.Today, Scope.Scheduled, Scope.Completed, Scope.Trash),
            sundialWorkbenchLenses().map { it.scope },
        )
    }

    @Test
    fun topLevelDestinationDoesNotTreatSmartLensAsPrimaryNavigation() {
        val primaryScopes = sundialPrimaryDestinations().map { scopeForDestination(it.destination, emptyList()) }

        assertEquals(listOf(Scope.All, Scope.All, Scope.Analytics), primaryScopes)
    }
}
