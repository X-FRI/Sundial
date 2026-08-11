package com.myapplication.shared.domain.repository

import arrow.core.Either
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.domain.sync.ListRowDto
import com.myapplication.shared.domain.sync.SyncRow
import com.myapplication.shared.domain.sync.TodoRowDto
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

    // Sync support
    suspend fun readOutbox(limit: Int): Either<TodoError, List<SyncRow>>
    suspend fun clearOutbox(upToSeq: Long): Either<TodoError, Unit>
    fun observeOutboxCount(): Flow<Int>
    suspend fun applyRemoteUpsert(row: TodoRowDto): Either<TodoError, Unit>
    suspend fun applyRemoteUpsertList(row: ListRowDto): Either<TodoError, Unit>
    suspend fun applyRemoteDelete(table: String, rowId: Long, updatedAt: Long): Either<TodoError, Unit>
    suspend fun getSetting(key: String): Either<TodoError, String?>
    suspend fun setSetting(key: String, value: String): Either<TodoError, Unit>
    suspend fun getSettings(): Either<TodoError, Map<String, String>>
}
