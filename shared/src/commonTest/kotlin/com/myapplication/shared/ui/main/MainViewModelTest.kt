package com.myapplication.shared.ui.main

import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.domain.repository.TodoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private class FakeRepository : TodoRepository {
    val listsState = MutableStateFlow<List<TodoList>>(emptyList())
    val todosState = MutableStateFlow<List<TodoItem>>(emptyList())
    var addedTitle: String? = null
    var addedListId: Long? = null
    var addedDue: Instant? = null
    var addedParent: Long? = null
    var toggledId: Long? = null
    var toggledValue: Boolean? = null
    var flaggedId: Long? = null
    var flaggedValue: Boolean? = null

    override suspend fun ensureInbox() {
        listsState.value = listOf(TodoList(1, "收件箱", "blue", 0, Instant.fromEpochMilliseconds(0)))
    }

    override fun observeLists(): Flow<List<TodoList>> = listsState
    override fun observeAllActive(): Flow<List<TodoItem>> = todosState
    override fun observeByList(listId: Long): Flow<List<TodoItem>> = todosState
    override fun observeToday(): Flow<List<TodoItem>> = todosState
    override fun observeScheduled(): Flow<List<TodoItem>> = todosState
    override fun observeCompleted(): Flow<List<TodoItem>> = todosState
    override fun observeTrashed(): Flow<List<TodoItem>> = todosState
    override fun observeSubTasks(parentId: Long): Flow<List<TodoItem>> = todosState
    override fun observeTodo(id: Long): Flow<TodoItem?> = MutableStateFlow(null)
    override fun search(query: String): Flow<List<TodoItem>> = todosState

    override suspend fun addList(name: String, colorKey: String) = Unit
    override suspend fun deleteList(listId: Long) = Unit
    override suspend fun addTodo(listId: Long?, title: String, note: String, dueDate: Instant?, parentId: Long?, flag: Boolean) {
        addedTitle = title
        addedListId = listId
        addedDue = dueDate
        addedParent = parentId
    }

    override suspend fun addSubTask(parentId: Long, title: String) = Unit
    override suspend fun setCompleted(id: Long, completed: Boolean) {
        toggledId = id
        toggledValue = completed
    }

    override suspend fun setFlag(id: Long, flag: Boolean) {
        flaggedId = id
        flaggedValue = flag
    }

    override suspend fun setTitle(id: Long, title: String) = Unit
    override suspend fun setNote(id: Long, note: String) = Unit
    override suspend fun setDueDate(id: Long, dueDate: Instant?) = Unit
    override suspend fun moveToList(id: Long, listId: Long) = Unit
    override suspend fun trash(id: Long) = Unit
    override suspend fun restore(id: Long) = Unit
    override suspend fun deleteForever(id: Long) = Unit
}

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

    @Test
    fun createTodoWithDateAndNote() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = MainViewModel(repo)
        collect(vm)
        val due = LocalDateTime(2026, 8, 12, 15, 0)
        vm.createTodo("交报告", "备注内容", due)
        advanceUntilIdle()
        assertEquals("交报告", repo.addedTitle)
        assertNotNull(repo.addedDue)
        assertNull(repo.addedParent)
    }

    @Test
    fun createTodoInListAddsToList() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = MainViewModel(repo)
        collect(vm)
        vm.createTodo("写周报", "", null, false, 7)
        advanceUntilIdle()
        assertEquals(7L, repo.addedListId)
    }

    @Test
    fun createTodoBlankIgnored() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = MainViewModel(repo)
        collect(vm)
        vm.createTodo("   ", "", null)
        advanceUntilIdle()
        assertNull(repo.addedTitle)
    }

    @Test
    fun toggleCompletedDelegates() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = MainViewModel(repo)
        collect(vm)
        val item = TodoItem(5, 1, "x", "", null, false, false, null, false, null, null, 0.0, Instant.fromEpochMilliseconds(0))
        vm.toggleCompleted(item)
        advanceUntilIdle()
        assertEquals(5L, repo.toggledId)
        assertEquals(true, repo.toggledValue)
    }

    @Test
    fun toggleFlagDelegates() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = MainViewModel(repo)
        collect(vm)
        val item = TodoItem(6, 1, "x", "", null, false, false, null, false, null, null, 0.0, Instant.fromEpochMilliseconds(0))
        vm.toggleFlag(item)
        advanceUntilIdle()
        assertEquals(6L, repo.flaggedId)
        assertEquals(true, repo.flaggedValue)
    }

    @Test
    fun openDetailAndBack() {
        val vm = MainViewModel(FakeRepository())
        vm.openDetail(3)
        assertEquals(Route.Detail(3), vm.route.value)
        vm.back()
        assertEquals(Route.Main, vm.route.value)
    }

    @Test
    fun selectScopeClearsSearchQuery() = runTest(dispatcher) {
        val vm = MainViewModel(FakeRepository())
        vm.setSearch("牛奶")
        vm.selectScope(Scope.Today)
        assertEquals("", vm.searchQuery.value)
    }

    @Test
    fun deleteSelectedListResetsScopeToAll() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = MainViewModel(repo)
        collect(vm)
        vm.selectScope(Scope.List(7))
        vm.deleteList(TodoList(7, "x", "blue", 1, Instant.fromEpochMilliseconds(0)))
        advanceUntilIdle()
        assertEquals(Scope.All, vm.scope.value)
    }

    @Test
    fun selectScopeClosesDetail() {
        val vm = MainViewModel(FakeRepository())
        vm.openDetail(3)
        vm.selectScope(Scope.Today)
        assertEquals(Route.Main, vm.route.value)
    }
}
