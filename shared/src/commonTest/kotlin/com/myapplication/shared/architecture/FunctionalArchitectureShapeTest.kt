package com.myapplication.shared.architecture

import com.myapplication.shared.domain.repository.ListCommands
import com.myapplication.shared.domain.repository.SettingsStore
import com.myapplication.shared.domain.repository.SyncStore
import com.myapplication.shared.domain.repository.TodoCommands
import com.myapplication.shared.domain.repository.TodoQueries
import kotlin.test.Test
import kotlin.test.assertEquals

class FunctionalArchitectureShapeTest {
    @Test
    fun narrowPortVocabularyStaysVisible() {
        val ports =
            listOf(
                port<TodoQueries>("TodoQueries"),
                port<TodoCommands>("TodoCommands"),
                port<ListCommands>("ListCommands"),
                port<SyncStore>("SyncStore"),
                port<SettingsStore>("SettingsStore"),
            )

        assertEquals(
            listOf("TodoQueries", "TodoCommands", "ListCommands", "SyncStore", "SettingsStore"),
            ports.map { it.name },
        )
    }

    private fun <T> port(name: String): PortVocabulary<T> = PortVocabulary(name)

    private data class PortVocabulary<T>(
        val name: String,
    )
}
