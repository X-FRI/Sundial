package com.myapplication.shared.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.domain.repository.TodoRepository
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
import com.myapplication.shared.util.todayDate

class DetailViewModel(
    private val repository: TodoRepository,
    private val todoId: Long,
) : ViewModel() {

    val todo: StateFlow<TodoItem?> = repository.observeTodo(todoId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val subtasks: StateFlow<List<TodoItem>> = repository.observeSubTasks(todoId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lists: StateFlow<List<TodoList>> = repository.observeLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setTitle(title: String) {
        viewModelScope.launch { repository.setTitle(todoId, title) }
    }

    fun setNote(note: String) {
        viewModelScope.launch { repository.setNote(todoId, note) }
    }

    fun setDueDate(due: LocalDateTime?) {
        viewModelScope.launch {
            repository.setDueDate(todoId, due?.toInstant(TimeZone.currentSystemDefault()))
        }
    }

    fun setTime(hour: Int, minute: Int) {
        val current = todo.value ?: return
        val base = current.dueDate
            ?.toLocalDateTime(TimeZone.currentSystemDefault())
            ?: LocalDateTime(todayDate(), LocalTime(hour, minute))
        val ldt = LocalDateTime(base.date, LocalTime(hour, minute))
        viewModelScope.launch { repository.setDueDate(todoId, ldt.toInstant(TimeZone.currentSystemDefault())) }
    }

    fun setTimeNull() {
        val current = todo.value ?: return
        if (current.dueDate == null) return
        val ldt = current.dueDate.toLocalDateTime(TimeZone.currentSystemDefault())
        val noTime = LocalDateTime(ldt.date, LocalTime(0, 0))
        viewModelScope.launch { repository.setDueDate(todoId, noTime.toInstant(TimeZone.currentSystemDefault())) }
    }

    fun moveToList(listId: Long) {
        viewModelScope.launch { repository.moveToList(todoId, listId) }
    }

    fun addSubTask(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.addSubTask(todoId, trimmed) }
    }

    fun toggleSubTask(item: TodoItem) {
        viewModelScope.launch { repository.setCompleted(item.id, !item.isCompleted) }
    }

    fun trashSubTask(item: TodoItem) {
        viewModelScope.launch { repository.trash(item.id) }
    }
}
