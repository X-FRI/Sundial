package com.myapplication.shared.domain.usecase

import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.test.FakeTodoRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AddTodoUseCaseTest {

    private suspend fun repoWithInbox(): FakeTodoRepository {
        val repo = FakeTodoRepository()
        repo.ensureInbox()
        return repo
    }

    private fun input(
        listId: Long? = null,
        parentId: Long? = null,
        title: String = "买牛奶",
        note: String = "",
    ) = AddTodoInput(listId, parentId, title, note, null, false)

    @Test
    fun addsTodoToExplicitList() = runTest {
        val repo = repoWithInbox()
        val result = AddTodoUseCase(repo)(input(listId = 1, title = "交报告"))
        assertTrue(result.isRight())
        assertEquals(1L, repo.lastInserted?.listId)
        assertEquals("交报告", repo.lastInserted?.title)
    }

    @Test
    fun blankTitleReturnsEmptyTitleError() = runTest {
        val repo = repoWithInbox()
        val result = AddTodoUseCase(repo)(input(title = "   "))
        assertEquals(TodoError.EmptyTitle, result.leftOrNull())
        assertNull(repo.lastInserted)
    }

    @Test
    fun noListFallsBackToInbox() = runTest {
        val repo = repoWithInbox()
        val result = AddTodoUseCase(repo)(input(title = "无列表"))
        assertTrue(result.isRight())
        assertEquals(1L, repo.lastInserted?.listId)
    }

    @Test
    fun emptyDbCreatesInboxOnDemand() = runTest {
        val repo = FakeTodoRepository()
        val result = AddTodoUseCase(repo)(input(title = "冷启动"))
        assertTrue(result.isRight())
        assertEquals(1, repo.ensureInboxCalls)
        assertEquals(1L, repo.lastInserted?.listId)
    }

    @Test
    fun subtaskInheritsParentList() = runTest {
        val repo = repoWithInbox()
        repo.addList("项目", "red")
        repo.insertTodo(2, "父任务", "", null, null, false)
        val parentId = repo.lastInserted!!.id
        val result = AddTodoUseCase(repo)(input(parentId = parentId, title = "子任务"))
        assertTrue(result.isRight())
        assertEquals(2L, repo.lastInserted?.listId)
        assertEquals(parentId, repo.lastInserted?.parentId)
    }

    @Test
    fun missingParentReturnsParentNotFound() = runTest {
        val repo = repoWithInbox()
        val result = AddTodoUseCase(repo)(input(parentId = 999L, title = "孤儿"))
        assertEquals(TodoError.ParentNotFound, result.leftOrNull())
    }

    @Test
    fun persistenceFailurePropagates() = runTest {
        val repo = repoWithInbox()
        repo.failNextInsert = true
        val result = AddTodoUseCase(repo)(input(title = "写不了"))
        assertIs<TodoError.Persistence>(result.leftOrNull())
    }
}
