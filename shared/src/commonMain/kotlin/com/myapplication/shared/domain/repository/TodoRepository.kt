package com.myapplication.shared.domain.repository

import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.model.TodoList
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface TodoRepository {
    suspend fun ensureInbox()
    fun observeLists(): Flow<List<TodoList>>
    fun observeAllActive(): Flow<List<TodoItem>>
    fun observeByList(listId: Long): Flow<List<TodoItem>>
    fun observeToday(): Flow<List<TodoItem>>
    fun observeScheduled(): Flow<List<TodoItem>>
    fun observeCompleted(): Flow<List<TodoItem>>
    fun observeTrashed(): Flow<List<TodoItem>>
    fun observeSubTasks(parentId: Long): Flow<List<TodoItem>>
    fun observeTodo(id: Long): Flow<TodoItem?>
    fun search(query: String): Flow<List<TodoItem>>

    suspend fun addList(name: String, colorKey: String)
    suspend fun deleteList(listId: Long)
    suspend fun addTodo(listId: Long?, title: String, note: String, dueDate: Instant?, parentId: Long? = null)
    suspend fun addSubTask(parentId: Long, title: String)
    suspend fun setCompleted(id: Long, completed: Boolean)
    suspend fun setFlag(id: Long, flag: Boolean)
    suspend fun setTitle(id: Long, title: String)
    suspend fun setNote(id: Long, note: String)
    suspend fun setDueDate(id: Long, dueDate: Instant?)
    suspend fun moveToList(id: Long, listId: Long)
    suspend fun trash(id: Long)
    suspend fun restore(id: Long)
    suspend fun deleteForever(id: Long)
}
