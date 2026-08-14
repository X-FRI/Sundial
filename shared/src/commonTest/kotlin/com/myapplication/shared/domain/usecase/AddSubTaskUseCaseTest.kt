package com.myapplication.shared.domain.usecase

import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.test.FakeTodoRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * AddSubTaskUseCase 的契约测试：子任务必须挂到父任务所在列表，且遵守
 * 与 AddTodoUseCase 相同的校验规则（空标题 / 父任务不存在）。
 */
class AddSubTaskUseCaseTest {
    // 预置：空库 + 收件箱 + 一个父任务，返回 (repo, 父任务 id)
    private suspend fun repoWithParent(): Pair<FakeTodoRepository, Long> {
        val repo = FakeTodoRepository()
        repo.ensureInbox()
        repo.insertTodo(1, "父任务", "", null, null, false)
        return repo to repo.lastInserted!!.id
    }

    @Test
    fun addsSubtaskToParentList() =
        runTest {
            val (repo, parentId) = repoWithParent()
            val result = AddSubTaskUseCase(repo)(parentId, "子任务")
            assertTrue(result.isRight())
            // 子任务不指定列表，必须继承父任务的列表
            assertEquals(1L, repo.lastInserted?.listId)
            assertEquals(parentId, repo.lastInserted?.parentId)
        }

    @Test
    fun missingParentReturnsParentNotFound() =
        runTest {
            val repo = FakeTodoRepository()
            repo.ensureInbox()
            // 不存在的父任务 id → 业务错误，且不落库
            val result = AddSubTaskUseCase(repo)(999L, "孤儿")
            assertEquals(TodoError.ParentNotFound, result.leftOrNull())
        }

    @Test
    fun trashedParentReturnsParentNotFound() =
        runTest {
            val (repo, parentId) = repoWithParent()
            // 父任务被软删除（trash 状态）→ 与缺失父任务同一业务错误，且不落库
            repo.todosState.value = repo.todosState.value.map { it.copy(isTrashed = true) }
            val todoCount = repo.todosState.value.size
            val result = AddSubTaskUseCase(repo)(parentId, "子任务")
            assertEquals(TodoError.ParentNotFound, result.leftOrNull())
            assertEquals(todoCount, repo.todosState.value.size)
        }

    @Test
    fun blankTitleReturnsEmptyTitleError() =
        runTest {
            val (repo, parentId) = repoWithParent()
            val todoCount = repo.todosState.value.size
            // 空白标题 → EmptyTitle，且列表数据不被污染
            val result = AddSubTaskUseCase(repo)(parentId, "  ")
            assertEquals(TodoError.EmptyTitle, result.leftOrNull())
            assertEquals(todoCount, repo.todosState.value.size)
        }
}
