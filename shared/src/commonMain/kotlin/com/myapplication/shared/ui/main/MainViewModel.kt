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

sealed interface Route {
    data object Main : Route
    data class Detail(val todoId: Long) : Route
}

sealed interface Scope {
    data object Today : Scope
    data object Scheduled : Scope
    data object All : Scope
    data object Completed : Scope
    data object Trash : Scope
    data class List(val listId: Long) : Scope
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: TodoRepository,
    private val addTodo: AddTodoUseCase,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    val scope = MutableStateFlow<Scope>(Scope.All)
    val searchQuery = MutableStateFlow("")
    val route = MutableStateFlow<Route>(Route.Main)
    val lastError = MutableStateFlow<TodoError?>(null)

    val lists: StateFlow<List<TodoList>> = repository.observeLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    private fun count(flow: Flow<List<TodoItem>>): StateFlow<Int> =
        flow.map { it.size }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val todayCount: StateFlow<Int> = count(repository.observeToday())
    val scheduledCount: StateFlow<Int> = count(repository.observeScheduled())
    val allCount: StateFlow<Int> = count(repository.observeAllActive())
    val completedCount: StateFlow<Int> = count(repository.observeCompleted())
    val trashCount: StateFlow<Int> = count(repository.observeTrashed())
    val listCounts: StateFlow<Map<Long, Int>> = repository.observeAllActive()
        .map { todos -> todos.groupingBy { it.listId }.eachCount() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        viewModelScope.launch { repository.ensureInbox().onLeft { lastError.value = it } }
    }

    fun dismissError() {
        lastError.value = null
    }

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

    fun back() {
        route.value = Route.Main
    }

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

    fun addList(name: String, colorKey: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.addList(trimmed, colorKey).onLeft { lastError.value = it } }
    }

    fun deleteList(list: TodoList) {
        viewModelScope.launch { repository.deleteList(list.id).onLeft { lastError.value = it } }
        if (scope.value == Scope.List(list.id)) scope.value = Scope.All
    }
}
