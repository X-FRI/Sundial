package com.myapplication.shared.domain.repository

import com.myapplication.shared.test.FakeTodoRepository
import kotlin.test.Test
import kotlin.test.assertNotNull

class RepositoryInterfaceShapeTest {
    @Test
    fun fakeRepositoryCanBeAssignedToNarrowPorts() {
        val queries: TodoQueries = FakeTodoRepository()
        val todoCommands: TodoCommands = FakeTodoRepository()
        val listCommands: ListCommands = FakeTodoRepository()
        val syncStore: SyncStore = FakeTodoRepository()
        val settingsStore: SettingsStore = FakeTodoRepository()

        assertNotNull(queries)
        assertNotNull(todoCommands)
        assertNotNull(listCommands)
        assertNotNull(syncStore)
        assertNotNull(settingsStore)
    }
}
