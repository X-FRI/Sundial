package com.myapplication.shared.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.domain.repository.TodoRepository
import com.myapplication.shared.domain.usecase.AddSubTaskUseCase
import com.myapplication.shared.util.todayDate
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

class DetailViewModel(
    private val repository: TodoRepository,
    private val addSubTask: AddSubTaskUseCase,
    private val todoId: Long,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    val todo: StateFlow<TodoItem?> = repository.observeTodo(todoId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val subtasks: StateFlow<List<TodoItem>> = repository.observeSubTasks(todoId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lists: StateFlow<List<TodoList>> = repository.observeLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lastError = MutableStateFlow<TodoError?>(null)

    fun dismissError() {
        lastError.value = null
    }

    fun setTitle(title: String) {
        viewModelScope.launch { repository.setTitle(todoId, title).onLeft { lastError.value = it } }
    }

    fun setNote(note: String) {
        viewModelScope.launch { repository.setNote(todoId, note).onLeft { lastError.value = it } }
    }

    fun setDueDate(due: LocalDateTime?) {
        viewModelScope.launch {
            repository.setDueDate(todoId, due?.toInstant(timeZone)).onLeft { lastError.value = it }
        }
    }

    fun setTime(hour: Int, minute: Int) {
        val current = todo.value ?: return
        val base = current.dueDate
            ?.toLocalDateTime(timeZone)
            ?: LocalDateTime(todayDate(clock, timeZone), LocalTime(hour, minute))
        val ldt = LocalDateTime(base.date, LocalTime(hour, minute))
        viewModelScope.launch {
            repository.setDueDate(todoId, ldt.toInstant(timeZone)).onLeft { lastError.value = it }
        }
    }

    fun setTimeNull() {
        val current = todo.value ?: return
        if (current.dueDate == null) return
        val ldt = current.dueDate.toLocalDateTime(timeZone)
        val noTime = LocalDateTime(ldt.date, LocalTime(0, 0))
        viewModelScope.launch {
            repository.setDueDate(todoId, noTime.toInstant(timeZone)).onLeft { lastError.value = it }
        }
    }

    fun moveToList(listId: Long) {
        viewModelScope.launch { repository.moveToList(todoId, listId).onLeft { lastError.value = it } }
    }

    fun addSubTask(title: String) {
        viewModelScope.launch { addSubTask(todoId, title).onLeft { lastError.value = it } }
    }

    fun toggleSubTask(item: TodoItem) {
        viewModelScope.launch { repository.setCompleted(item.id, !item.isCompleted).onLeft { lastError.value = it } }
    }

    fun trashSubTask(item: TodoItem) {
        viewModelScope.launch { repository.trash(item.id).onLeft { lastError.value = it } }
    }
}
