package com.myapplication.shared.ui.detail

import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.usecase.AddSubTaskUseCase
import com.myapplication.shared.test.FakeTodoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun setSubTaskTitleWritesSubtaskId() = runTest(dispatcher) {
        val repo = FakeTodoRepository()
        val vm = DetailViewModel(repo, AddSubTaskUseCase(repo), todoId = 1)
        val subtask = TodoItem(
            id = 42,
            listId = 1,
            title = "旧标题",
            note = "",
            dueDate = null,
            isCompleted = false,
            flag = false,
            completedAt = null,
            isTrashed = false,
            trashedAt = null,
            parentId = 1,
            sortPosition = 0.0,
            createdAt = Instant.fromEpochMilliseconds(0),
        )

        vm.setSubTaskTitle(subtask, "新标题")
        advanceUntilIdle()

        assertEquals(42L, repo.lastSetTitleId)
        assertEquals("新标题", repo.lastSetTitleValue)
    }
}
