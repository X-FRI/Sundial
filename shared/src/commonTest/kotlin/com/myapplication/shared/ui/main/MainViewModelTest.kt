package com.myapplication.shared.ui.main

import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.list.DeleteListPolicy
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.domain.recurrence.CompleteRecurringTodoUseCase
import com.myapplication.shared.domain.recurrence.RecurrenceRule
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

/**
 * MainViewModel 的契约测试。
 *
 * 测试策略：
 * - 用 StandardTestDispatcher 接管 Dispatchers.Main（@BeforeTest/@AfterTest 成对
 *   设置/还原），所有协程确定性推进；
 * - [collect] 在 backgroundScope 启动流收集，使 ViewModel 内部挂起的流管道
 *   跑起来，配合 advanceUntilIdle 把待执行任务全部执行完再断言；
 * - 无协程参与的纯状态操作（openDetail/selectScope 等）不套 runTest。
 *
 * 覆盖契约：
 * - createTodo 正确委派（含日期/备注/列表/错误路径）；
 * - toggleCompleted / toggleFlag 委派到仓库；
 * - 导航（openDetail/back）与 scope 切换的副作用（清搜索词、关详情、
 *   删除列表后回退到 All）。
 */
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

    // 在后台作用域收集 UI 状态流：不收集则 vm 内部的流管道不会执行
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
        // 标题/备注/日期全部落到仓库；非子任务 → parentId 为 null
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
        // 空标题：错误进入 lastError，仓库零写入
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
        // 仓库持久化失败 → 原样暴露 Persistence（UI 可据此提示）
        assertTrue(vm.lastError.value is TodoError.Persistence)
    }

    @Test
    fun dismissErrorClearsLastError() {
        // 纯同步状态操作，无需协程调度器
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
        // 构造一个内存 item（id=5）触发切换，验证只转发 id + 目标值
        val item = TodoItem(5, 1, "x", "", null, false, false, null, false, null, null, 0.0, Instant.fromEpochMilliseconds(0))
        vm.toggleCompleted(item)
        advanceUntilIdle()
        assertEquals(5L, repo.toggledId)
        assertEquals(true, repo.toggledValue)
    }

    @Test
    fun completingRecurringTodoCreatesNextOccurrence() = runTest(dispatcher) {
        val repo = FakeTodoRepository()
        repo.ensureInbox()
        val vm = MainViewModel(
            repo,
            AddTodoUseCase(repo),
            TimeZone.UTC,
            CompleteRecurringTodoUseCase(repo, TimeZone.UTC),
        )
        collect(vm)
        repo.insertTodo(1, "站会", "", Instant.parse("2026-08-13T09:00:00Z"), null, false)
        val item = repo.todos.first().copy(recurrenceRule = RecurrenceRule.Daily())
        repo.replaceTodo(item)

        vm.toggleCompleted(item)
        vm.toggleCompleted(item)
        advanceUntilIdle()

        assertEquals(true, repo.todos.first { it.id == item.id }.isCompleted)
        assertEquals(2, repo.todos.size)
        assertEquals(Instant.parse("2026-08-14T09:00:00Z"), repo.todos.last().dueDate)
        assertEquals(RecurrenceRule.Daily(), repo.todos.last().recurrenceRule)
    }

    @Test
    fun uncompletingRecurringTodoKeepsDefaultToggleBehavior() = runTest(dispatcher) {
        val repo = FakeTodoRepository()
        repo.ensureInbox()
        val vm = MainViewModel(
            repo,
            AddTodoUseCase(repo),
            TimeZone.UTC,
            CompleteRecurringTodoUseCase(repo, TimeZone.UTC),
        )
        collect(vm)
        val item = TodoItem(
            7,
            1,
            "站会",
            "",
            Instant.parse("2026-08-13T09:00:00Z"),
            true,
            false,
            null,
            false,
            null,
            null,
            0.0,
            Instant.fromEpochMilliseconds(0),
            RecurrenceRule.Daily(),
        )

        vm.toggleCompleted(item)
        advanceUntilIdle()

        assertEquals(7L, repo.toggledId)
        assertEquals(false, repo.toggledValue)
        assertEquals(0, repo.todos.size)
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
        // 导航是同步路由切换：open → Detail(3)，back → 主界面
        val vm = vm(FakeTodoRepository())
        vm.openDetail(3)
        assertEquals(Route.Detail(3), vm.route.value)
        vm.back()
        assertEquals(Route.Main, vm.route.value)
    }

    @Test
    fun backFromSubtaskDetailReturnsToParentDetail() {
        val vm = vm(FakeTodoRepository())

        vm.openDetail(4)
        vm.openDetail(9, parentTodoId = 4)
        vm.back()

        assertEquals(Route.Detail(4), vm.route.value)
    }

    @Test
    fun closeDetailAlwaysReturnsToMain() {
        val vm = vm(FakeTodoRepository())

        vm.openDetail(4)
        vm.openDetail(9, parentTodoId = 4)
        vm.closeDetail()

        assertEquals(Route.Main, vm.route.value)
    }

    @Test
    fun selectScopeClearsSearchQuery() = runTest(dispatcher) {
        // 切 scope 时清空搜索词，避免跨列表残留过滤条件
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
        // 当前 scope 对应的列表被删 → scope 回退到 All，避免指向不存在的列表
        vm.deleteList(TodoList(7, "x", "blue", 1, Instant.fromEpochMilliseconds(0)))
        advanceUntilIdle()
        assertEquals(Scope.All, vm.scope.value)
    }

    @Test
    fun deleteListDefaultsToMoveTasksToInbox() = runTest(dispatcher) {
        val repo = FakeTodoRepository()
        repo.ensureInbox()
        repo.addList("项目", "blue")
        val vm = MainViewModel(repo, AddTodoUseCase(repo), TimeZone.currentSystemDefault())
        collect(vm)
        val list = TodoList(2, "项目", "blue", 1, Instant.fromEpochMilliseconds(0))

        vm.deleteList(list)
        advanceUntilIdle()

        assertEquals(DeleteListPolicy.MoveTasksToInbox, repo.lastDeleteListPolicy)
    }

    @Test
    fun updateListTrimsNameBeforeWriting() = runTest(dispatcher) {
        val repo = FakeTodoRepository()
        repo.ensureInbox()
        repo.addList("项目", "blue")
        val vm = MainViewModel(repo, AddTodoUseCase(repo), TimeZone.currentSystemDefault())
        collect(vm)
        val list = TodoList(2, "项目", "blue", 1, Instant.fromEpochMilliseconds(0))

        vm.updateList(list, "  研究  ", "red")
        advanceUntilIdle()

        assertEquals("研究", repo.lastUpdatedListName)
        assertEquals("red", repo.lastUpdatedListColor)
    }

    @Test
    fun deleteListWithDangerPolicyDelegatesPolicy() = runTest(dispatcher) {
        val repo = FakeTodoRepository()
        repo.ensureInbox()
        repo.addList("项目", "blue")
        val vm = MainViewModel(repo, AddTodoUseCase(repo), TimeZone.currentSystemDefault())
        collect(vm)
        val list = TodoList(2, "项目", "blue", 1, Instant.fromEpochMilliseconds(0))

        vm.deleteList(list, DeleteListPolicy.MoveTasksToTrash)
        advanceUntilIdle()

        assertEquals(DeleteListPolicy.MoveTasksToTrash, repo.lastDeleteListPolicy)
    }

    @Test
    fun blankListNamesDoNotWrite() = runTest(dispatcher) {
        val repo = FakeTodoRepository()
        repo.ensureInbox()
        repo.addList("项目", "blue")
        val vm = MainViewModel(repo, AddTodoUseCase(repo), TimeZone.currentSystemDefault())
        collect(vm)
        val beforeLists = repo.listsState.value
        val list = TodoList(2, "项目", "blue", 1, Instant.fromEpochMilliseconds(0))

        vm.addList("   ", "red")
        vm.updateList(list, "   ", "red")
        advanceUntilIdle()

        assertEquals(beforeLists, repo.listsState.value)
        assertNull(repo.lastUpdatedListName)
    }

    @Test
    fun selectScopeClosesDetail() {
        // 切 scope 同时关闭详情页，保证详情与列表状态不脱节
        val vm = vm(FakeTodoRepository())
        vm.openDetail(3)
        vm.selectScope(Scope.Today)
        assertEquals(Route.Main, vm.route.value)
    }

    @Test
    fun widgetLaunchTargetOpensTodayScope() {
        val vm = vm(FakeTodoRepository())

        vm.openDetail(3)
        vm.setSearch("牛奶")
        vm.applyLaunchTarget(LaunchTarget.Today)

        assertEquals(Scope.Today, vm.scope.value)
        assertEquals("", vm.searchQuery.value)
        assertEquals(Route.Main, vm.route.value)
    }

    @Test
    fun workbenchLaunchTargetOpensAllScope() {
        val vm = vm(FakeTodoRepository())

        vm.selectScope(Scope.Today)
        vm.openDetail(3)
        vm.setSearch("牛奶")
        vm.applyLaunchTarget(LaunchTarget.Workbench)

        assertEquals(Scope.All, vm.scope.value)
        assertEquals("", vm.searchQuery.value)
        assertEquals(Route.Main, vm.route.value)
    }
}
