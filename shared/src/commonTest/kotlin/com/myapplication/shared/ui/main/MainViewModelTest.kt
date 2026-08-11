package com.myapplication.shared.ui.main

import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.domain.usecase.AddTodoUseCase
import com.myapplication.shared.test.FakeTodoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.collect(vm: MainViewModel) {
        backgroundScope.launch { vm.todos.collect {} }
        backgroundScope.launch { vm.lists.collect {} }
    }

    private fun vm(repo: FakeTodoRepository): MainViewModel =
        MainViewModel(repo, AddTodoUseCase(repo), TimeZone.currentSystemDefault())

    @Test
    fun createTodoWithDateAndNote() = runTest(dispatcher) {
        val repo = FakeTodoRepository()
        repo.ensureInbox()
        val vm = MainViewModel(repo, AddTodoUseCase(repo), TimeZone.currentSystemDefault())
        collect(vm)
        val due = LocalDateTime(2026, 8, 12, 15, 0)
        vm.createTodo("交报告", "备注内容", due)
        advanceUntilIdle()
        assertEquals("交报告", repo.lastInserted?.title)
        assertNotNull(repo.lastInserted?.dueDate)
        assertNull(repo.lastInserted?.parentId)
    }

    @Test
    fun createTodoInListAddsToList() = runTest(dispatcher) {
        val repo = FakeTodoRepository()
        repo.ensureInbox()
        val vm = MainViewModel(repo, AddTodoUseCase(repo), TimeZone.currentSystemDefault())
        collect(vm)
        vm.createTodo("写周报", "", null, false, 7)
        advanceUntilIdle()
        assertEquals(7L, repo.lastInserted?.listId)
    }

    @Test
    fun createTodoBlankShowsEmptyTitleError() = runTest(dispatcher) {
        val repo = FakeTodoRepository()
        val vm = MainViewModel(repo, AddTodoUseCase(repo), TimeZone.currentSystemDefault())
        collect(vm)
        vm.createTodo("   ", "", null)
        advanceUntilIdle()
        assertEquals(TodoError.EmptyTitle, vm.lastError.value)
        assertNull(repo.lastInserted)
    }

    @Test
    fun persistenceFailureSurfacesError() = runTest(dispatcher) {
        val repo = FakeTodoRepository()
        repo.ensureInbox()
        repo.failNextInsert = true
        val vm = MainViewModel(repo, AddTodoUseCase(repo), TimeZone.currentSystemDefault())
        collect(vm)
        vm.createTodo("写不了", "", null)
        advanceUntilIdle()
        assertTrue(vm.lastError.value is TodoError.Persistence)
    }

    @Test
    fun dismissErrorClearsLastError() {
        val repo = FakeTodoRepository()
        val vm = MainViewModel(repo, AddTodoUseCase(repo), TimeZone.currentSystemDefault())
        vm.lastError.value = TodoError.EmptyTitle
        vm.dismissError()
        assertNull(vm.lastError.value)
    }

    @Test
    fun toggleCompletedDelegates() = runTest(dispatcher) {
        val repo = FakeTodoRepository()
        repo.ensureInbox()
        val vm = MainViewModel(repo, AddTodoUseCase(repo), TimeZone.currentSystemDefault())
        collect(vm)
        val item = TodoItem(5, 1, "x", "", null, false, false, null, false, null, null, 0.0, Instant.fromEpochMilliseconds(0))
        vm.toggleCompleted(item)
        advanceUntilIdle()
        assertEquals(5L, repo.toggledId)
        assertEquals(true, repo.toggledValue)
    }

    @Test
    fun toggleFlagDelegates() = runTest(dispatcher) {
        val repo = FakeTodoRepository()
        repo.ensureInbox()
        val vm = MainViewModel(repo, AddTodoUseCase(repo), TimeZone.currentSystemDefault())
        collect(vm)
        val item = TodoItem(6, 1, "x", "", null, false, false, null, false, null, null, 0.0, Instant.fromEpochMilliseconds(0))
        vm.toggleFlag(item)
        advanceUntilIdle()
        assertEquals(6L, repo.flaggedId)
        assertEquals(true, repo.flaggedValue)
    }

    @Test
    fun openDetailAndBack() {
        val vm = vm(FakeTodoRepository())
        vm.openDetail(3)
        assertEquals(Route.Detail(3), vm.route.value)
        vm.back()
        assertEquals(Route.Main, vm.route.value)
    }

    @Test
    fun selectScopeClearsSearchQuery() = runTest(dispatcher) {
        val vm = vm(FakeTodoRepository())
        vm.setSearch("牛奶")
        vm.selectScope(Scope.Today)
        assertEquals("", vm.searchQuery.value)
    }

    @Test
    fun deleteSelectedListResetsScopeToAll() = runTest(dispatcher) {
        val repo = FakeTodoRepository()
        repo.ensureInbox()
        val vm = MainViewModel(repo, AddTodoUseCase(repo), TimeZone.currentSystemDefault())
        collect(vm)
        vm.selectScope(Scope.List(7))
        vm.deleteList(TodoList(7, "x", "blue", 1, Instant.fromEpochMilliseconds(0)))
        advanceUntilIdle()
        assertEquals(Scope.All, vm.scope.value)
    }

    @Test
    fun selectScopeClosesDetail() {
        val vm = vm(FakeTodoRepository())
        vm.openDetail(3)
        vm.selectScope(Scope.Today)
        assertEquals(Route.Main, vm.route.value)
    }
}
