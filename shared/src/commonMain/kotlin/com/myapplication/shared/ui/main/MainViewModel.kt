package com.myapplication.shared.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.domain.repository.TodoRepository
import com.myapplication.shared.domain.usecase.AddTodoInput
import com.myapplication.shared.domain.usecase.AddTodoUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * 全局路由状态机：决定当前显示哪个「页面」。
 *
 * 语义：
 * - [Main] 主列表（窄屏还包含顶栏/底栏，宽屏对应三栏中的左两栏）；
 * - [Detail] 选中某个待办详情（todoId 供 DetailContent 定位数据）；
 * - [Settings] 全屏设置页，优先于宽/窄屏分支渲染。
 *
 * 返回语义：任意非 Main 状态调用 [MainViewModel.back] 都会回到 [Main]，
 * 因此它是「单层栈」，不支持多级回退。
 */
sealed interface Route {
    data object Main : Route
    data class Detail(val todoId: Long) : Route
    data object Settings : Route
}

/**
 * 列表过滤范围：决定 [MainViewModel.todos] 展示哪类待办。
 *
 * 说明：
 * - [List] 携带 listId 指向用户自定义列表；
 * - 范围与搜索互斥——有搜索词时忽略范围（见 [MainViewModel.todos] 的 combine 逻辑）。
 */
sealed interface Scope {
    data object Today : Scope
    data object Scheduled : Scope
    data object All : Scope
    data object Completed : Scope
    data object Trash : Scope
    data class List(val listId: Long) : Scope
}

/**
 * 主界面 ViewModel：路由状态机 + 待办数据流的唯一权威来源。
 *
 * 数据流要点：
 * - [todos]：scope × searchQuery 的组合流，经 `flatMapLatest` 切换底层查询源，
 *   搜索词非空时走 `search(q)`，否则按 scope 走对应查询；
 * - 各计数（[todayCount] 等）：由仓库查询流 map 出 size 再 `stateIn`，
 *   让 Sidebar / 窄屏底栏只订阅轻量 Int，避免重复订阅大列表；
 * - [lastError]：所有命令（create/toggle/trash/delete…）的失败统一经此通道上报，
 *   由 AppRoot 统一弹窗，UI 层不做 try-catch；
 * - 所有 StateFlow 都用 `WhileSubscribed(5000)`：界面不再订阅 5 秒后才停止
 *   底层数据库监听，避免快速切换页面时反复重建查询。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: TodoRepository,
    private val addTodo: AddTodoUseCase,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    /** 当前列表范围（智能列表或自定义列表）。 */
    val scope = MutableStateFlow<Scope>(Scope.All)
    /** 搜索关键词；非空时 [todos] 走搜索查询，scope 失效。 */
    val searchQuery = MutableStateFlow("")
    /** 当前路由（Main / Detail / Settings），由 AppRoot 消费。 */
    val route = MutableStateFlow<Route>(Route.Main)
    /** 最近一次命令失败（若为 null 表示无错误）；AppRoot 订阅并弹窗。 */
    val lastError = MutableStateFlow<TodoError?>(null)

    /** 全部自定义列表（含收件箱），供 Sidebar / 表单对话框渲染。 */
    val lists: StateFlow<List<TodoList>> = repository.observeLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 主列表数据流。
     *
     * 结构说明：`combine(scope, searchQuery)` 先把两个状态捏成一个 Pair，
     * 再用 `flatMapLatest` 根据（scope, query）切换查询源——任一变化都会取消旧查询、
     * 订阅新查询，保证列表始终反映最新选择且不产生过期数据交错。
     */
    val todos: StateFlow<List<TodoItem>> =
        combine(scope, searchQuery) { s, q -> s to q }
            .flatMapLatest { (s, q) ->
                if (q.isNotBlank()) repository.search(q.trim())
                else when (s) {
                    Scope.Today -> repository.observeToday()
                    Scope.Scheduled -> repository.observeScheduled()
                    Scope.All -> repository.observeAllActive()
                    Scope.Completed -> repository.observeCompleted()
                    Scope.Trash -> repository.observeTrashed()
                    is Scope.List -> repository.observeByList(s.listId)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 把查询流折叠成计数流；各智能列表徽标共用这一条逻辑。 */
    private fun count(flow: Flow<List<TodoItem>>): StateFlow<Int> =
        flow.map { it.size }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** 智能列表计数：各自独立订阅查询流，互不干扰。 */
    val todayCount: StateFlow<Int> = count(repository.observeToday())
    val scheduledCount: StateFlow<Int> = count(repository.observeScheduled())
    val allCount: StateFlow<Int> = count(repository.observeAllActive())
    val completedCount: StateFlow<Int> = count(repository.observeCompleted())
    val trashCount: StateFlow<Int> = count(repository.observeTrashed())
    /**
     * 每个自定义列表的待办计数（listId -> 数量）。
     * 复用了全部活动待办这一条查询流做分组，避免为每个列表各开一条数据库监听。
     */
    val listCounts: StateFlow<Map<Long, Int>> = repository.observeAllActive()
        .map { todos -> todos.groupingBy { it.listId }.eachCount() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        // 启动时确保「收件箱」列表存在：它是所有新待办的默认归属，
        // 若初始化失败则记录到 lastError（此时 UI 仍可进入，但新建可能失败）。
        viewModelScope.launch { repository.ensureInbox().onLeft { lastError.value = it } }
    }

    fun dismissError() {
        lastError.value = null
    }

    /**
     * 切换列表范围：重置搜索词并回主路由。
     * 注意：若当前已在 Detail 路由，切范围会同时退出详情页（回到主列表）。
     */
    fun selectScope(s: Scope) {
        scope.value = s
        searchQuery.value = ""
        back()
    }

    fun setSearch(q: String) {
        searchQuery.value = q
    }

    fun openDetail(id: Long) {
        route.value = Route.Detail(id)
    }

    fun openSettings() {
        route.value = Route.Settings
    }

    /** 返回主路由；详情/设置页的「关闭」与系统返回键都走这里。 */
    fun back() {
        route.value = Route.Main
    }

    /** 新建待办：同步校验在 usecase 内，失败统一进 [lastError]。 */
    fun createTodo(title: String, note: String, due: LocalDateTime?, flag: Boolean = false, listId: Long? = null) {
        viewModelScope.launch {
            addTodo(
                AddTodoInput(
                    listId = listId,
                    parentId = null,
                    title = title,
                    note = note,
                    dueDate = due?.toInstant(timeZone),
                    flag = flag,
                ),
            ).onLeft { lastError.value = it }
        }
    }

    fun toggleCompleted(item: TodoItem) {
        viewModelScope.launch { repository.setCompleted(item.id, !item.isCompleted).onLeft { lastError.value = it } }
    }

    fun toggleFlag(item: TodoItem) {
        viewModelScope.launch { repository.setFlag(item.id, !item.flag).onLeft { lastError.value = it } }
    }

    fun trash(item: TodoItem) {
        viewModelScope.launch { repository.trash(item.id).onLeft { lastError.value = it } }
    }

    fun restore(item: TodoItem) {
        viewModelScope.launch { repository.restore(item.id).onLeft { lastError.value = it } }
    }

    fun deleteForever(item: TodoItem) {
        viewModelScope.launch { repository.deleteForever(item.id).onLeft { lastError.value = it } }
    }

    /** 新建自定义列表；空名称直接忽略。 */
    fun addList(name: String, colorKey: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.addList(trimmed, colorKey).onLeft { lastError.value = it } }
    }

    /**
     * 删除列表。删除成功后，若当前正停留在该列表范围，会先切回「全部」，
     * 避免 UI 停留在已不存在的范围上（此处为乐观切换，不等待删除结果）。
     */
    fun deleteList(list: TodoList) {
        viewModelScope.launch { repository.deleteList(list.id).onLeft { lastError.value = it } }
        if (scope.value == Scope.List(list.id)) scope.value = Scope.All
    }
}

/** 顶栏/页头标题：搜索词非空时统一显示「搜索」，否则按范围取标题。 */
fun scopeTitle(scope: Scope, query: String): String = when {
    query.isNotBlank() -> "搜索"
    scope == Scope.Today -> "今天"
    scope == Scope.Scheduled -> "计划"
    scope == Scope.All -> "工作台"
    scope == Scope.Completed -> "已完成"
    scope == Scope.Trash -> "垃圾箱"
    scope is Scope.List -> "列表"
    else -> "待办"
}
