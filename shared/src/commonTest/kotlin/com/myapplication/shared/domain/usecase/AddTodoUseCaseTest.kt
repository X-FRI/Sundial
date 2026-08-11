package com.myapplication.shared.domain.usecase

import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.test.FakeTodoRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * AddTodoUseCase 的契约测试，覆盖四条核心规则：
 * 1. 列表归属：显式 listId > 父任务继承 > 收件箱兜底（ensureInbox 幂等）；
 * 2. 校验：空标题 → EmptyTitle；父任务不存在 → ParentNotFound；
 * 3. 冷启动：空库首次添加时自动创建"收件箱"；
 * 4. 错误传播：底层持久化失败原样返回 Persistence。
 */
class AddTodoUseCaseTest {

    // 预置：空库 + 已存在的收件箱
    private suspend fun repoWithInbox(): FakeTodoRepository {
        val repo = FakeTodoRepository()
        repo.ensureInbox()
        return repo
    }

    // AddTodoInput 工厂：默认"买牛奶 / 无列表 / 无父任务"
    private fun input(
        listId: Long? = null,
        parentId: Long? = null,
        title: String = "买牛奶",
        note: String = "",
    ) = AddTodoInput(listId, parentId, title, note, null, false)

    @Test
    fun addsTodoToExplicitList() = runTest {
        val repo = FakeTodoRepository()
        repo.addList("收件箱", "blue")
        repo.addList("项目", "red")
        val listId = repo.listsState.value.first { it.name == "项目" }.id
        val result = AddTodoUseCase(repo)(input(listId = listId, title = "交报告"))
        assertTrue(result.isRight())
        // 显式列表优先于收件箱兜底：列表正确 + 不触发 ensureInbox
        assertEquals(listId, repo.lastInserted?.listId)
        assertEquals(0, repo.ensureInboxCalls)
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
        // 未指定列表 → 落到收件箱（id 恒为 1）
        assertEquals(1L, repo.lastInserted?.listId)
    }

    @Test
    fun emptyDbCreatesInboxOnDemand() = runTest {
        val repo = FakeTodoRepository()
        val result = AddTodoUseCase(repo)(input(title = "冷启动"))
        assertTrue(result.isRight())
        // 全新空库：首次写入前自动建收件箱，且只建一次
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
        // 子任务未指定列表 → 继承父任务所在列表（id=2 的"项目"）
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
    fun parentListWinsOverExplicitList() = runTest {
        val repo = repoWithInbox()
        repo.addList("项目", "red")
        val listId = repo.listsState.value.first { it.name == "项目" }.id
        repo.insertTodo(listId, "父任务", "", null, null, false)
        val parentId = repo.lastInserted!!.id
        // 同时给了 listId=1（收件箱）与 parentId → 父任务列表优先
        val result = AddTodoUseCase(repo)(input(listId = 1, parentId = parentId, title = "子任务"))
        assertTrue(result.isRight())
        assertEquals(listId, repo.lastInserted?.listId)
        assertEquals(parentId, repo.lastInserted?.parentId)
    }

    @Test
    fun persistenceFailurePropagates() = runTest {
        val repo = repoWithInbox()
        repo.failNextInsert = true
        val result = AddTodoUseCase(repo)(input(title = "写不了"))
        // 底层失败不吞错、不转成业务错误
        assertIs<TodoError.Persistence>(result.leftOrNull())
    }
}
