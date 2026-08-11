package com.myapplication.shared.domain.repository

import arrow.core.Either
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.model.TodoList
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface TodoRepository {
    // Queries — 数据流保持 Flow，不包装
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
    suspend fun findById(id: Long): Either<TodoError, TodoItem?>

    // Commands — 类型化错误，纯 Effect
    suspend fun ensureInbox(): Either<TodoError, Long>
    suspend fun addList(name: String, colorKey: String): Either<TodoError, Unit>
    suspend fun deleteList(listId: Long): Either<TodoError, Unit>
    suspend fun insertTodo(listId: Long, title: String, note: String, dueDate: Instant?, parentId: Long?, flag: Boolean): Either<TodoError, Unit>
    suspend fun setCompleted(id: Long, completed: Boolean): Either<TodoError, Unit>
    suspend fun setFlag(id: Long, flag: Boolean): Either<TodoError, Unit>
    suspend fun setTitle(id: Long, title: String): Either<TodoError, Unit>
    suspend fun setNote(id: Long, note: String): Either<TodoError, Unit>
    suspend fun setDueDate(id: Long, dueDate: Instant?): Either<TodoError, Unit>
    suspend fun moveToList(id: Long, listId: Long): Either<TodoError, Unit>
    suspend fun trash(id: Long): Either<TodoError, Unit>
    suspend fun restore(id: Long): Either<TodoError, Unit>
    suspend fun deleteForever(id: Long): Either<TodoError, Unit>
}
