package com.myapplication.shared.domain.usecase

import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.test.FakeTodoRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AddSubTaskUseCaseTest {

    private suspend fun repoWithParent(): Pair<FakeTodoRepository, Long> {
        val repo = FakeTodoRepository()
        repo.ensureInbox()
        repo.insertTodo(1, "父任务", "", null, null, false)
        return repo to repo.lastInserted!!.id
    }

    @Test
    fun addsSubtaskToParentList() = runTest {
        val (repo, parentId) = repoWithParent()
        val result = AddSubTaskUseCase(repo)(parentId, "子任务")
        assertTrue(result.isRight())
        assertEquals(1L, repo.lastInserted?.listId)
        assertEquals(parentId, repo.lastInserted?.parentId)
    }

    @Test
    fun missingParentReturnsParentNotFound() = runTest {
        val repo = FakeTodoRepository()
        repo.ensureInbox()
        val result = AddSubTaskUseCase(repo)(999L, "孤儿")
        assertEquals(TodoError.ParentNotFound, result.leftOrNull())
    }

    @Test
    fun blankTitleReturnsEmptyTitleError() = runTest {
        val (repo, parentId) = repoWithParent()
        val result = AddSubTaskUseCase(repo)(parentId, "  ")
        assertEquals(TodoError.EmptyTitle, result.leftOrNull())
    }
}
