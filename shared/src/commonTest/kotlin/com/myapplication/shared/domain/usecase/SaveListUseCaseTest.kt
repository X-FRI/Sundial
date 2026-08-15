package com.myapplication.shared.domain.usecase

import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.test.FakeTodoRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SaveListUseCaseTest {
    @Test
    fun blankAddReturnsEmptyTitleAndDoesNotAddList() =
        runTest {
            val repo = FakeTodoRepository()
            repo.ensureInbox()
            val beforeLists = repo.listsState.value

            val result = SaveListUseCase(repo).add("   ", "red")

            assertEquals(TodoError.EmptyTitle, result.leftOrNull())
            assertEquals(beforeLists, repo.listsState.value)
        }

    @Test
    fun updateTrimsNameAndColorBeforeWriting() =
        runTest {
            val repo = FakeTodoRepository()
            repo.ensureInbox()
            repo.addList("项目", "blue")

            val result = SaveListUseCase(repo).update(2, "  研究  ", "  red  ")

            assertNull(result.leftOrNull())
            assertEquals("研究", repo.lastUpdatedListName)
            assertEquals("red", repo.lastUpdatedListColor)
        }
}
