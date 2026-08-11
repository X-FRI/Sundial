package com.myapplication.shared.test

import arrow.core.Either
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.domain.repository.TodoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Instant

class FakeTodoRepository : TodoRepository {
    val listsState = MutableStateFlow<List<TodoList>>(emptyList())
    val todosState = MutableStateFlow<List<TodoItem>>(emptyList())
    var ensureInboxCalls = 0
    var lastInserted: TodoItem? = null
    var toggledId: Long? = null
    var toggledValue: Boolean? = null
    var flaggedId: Long? = null
    var flaggedValue: Boolean? = null
    var failNextInsert = false
    private var nextId = 1L

    override fun observeLists(): Flow<List<TodoList>> = listsState
    override fun observeAllActive(): Flow<List<TodoItem>> = todosState
    override fun observeByList(listId: Long): Flow<List<TodoItem>> = todosState
    override fun observeToday(): Flow<List<TodoItem>> = todosState
    override fun observeScheduled(): Flow<List<TodoItem>> = todosState
    override fun observeCompleted(): Flow<List<TodoItem>> = todosState
    override fun observeTrashed(): Flow<List<TodoItem>> = todosState
    override fun observeSubTasks(parentId: Long): Flow<List<TodoItem>> = todosState
    override fun observeTodo(id: Long): Flow<TodoItem?> =
        MutableStateFlow(todosState.value.firstOrNull { it.id == id })
    override fun search(query: String): Flow<List<TodoItem>> = todosState

    override suspend fun findById(id: Long): Either<TodoError, TodoItem?> =
        Either.Right(todosState.value.firstOrNull { it.id == id })

    override suspend fun ensureInbox(): Either<TodoError, Long> {
        ensureInboxCalls++
        if (listsState.value.isEmpty()) {
            listsState.value = listOf(TodoList(1, "收件箱", "blue", 0, Instant.fromEpochMilliseconds(0)))
        }
        return Either.Right(listsState.value.first().id)
    }

    override suspend fun addList(name: String, colorKey: String): Either<TodoError, Unit> {
        listsState.value = listsState.value + TodoList(
            nextId++, name, colorKey, listsState.value.size, Instant.fromEpochMilliseconds(0),
        )
        return Either.Right(Unit)
    }

    override suspend fun deleteList(listId: Long): Either<TodoError, Unit> = Either.Right(Unit)

    override suspend fun insertTodo(
        listId: Long,
        title: String,
        note: String,
        dueDate: Instant?,
        parentId: Long?,
        flag: Boolean,
    ): Either<TodoError, Unit> {
        if (failNextInsert) {
            failNextInsert = false
            return Either.Left(TodoError.Persistence("boom"))
        }
        val item = TodoItem(
            nextId++, listId, title, note, dueDate, false, flag, null, false, null,
            parentId, 0.0, Instant.fromEpochMilliseconds(0),
        )
        todosState.value = todosState.value + item
        lastInserted = item
        return Either.Right(Unit)
    }

    override suspend fun setCompleted(id: Long, completed: Boolean): Either<TodoError, Unit> {
        toggledId = id
        toggledValue = completed
        return Either.Right(Unit)
    }

    override suspend fun setFlag(id: Long, flag: Boolean): Either<TodoError, Unit> {
        flaggedId = id
        flaggedValue = flag
        return Either.Right(Unit)
    }

    override suspend fun setTitle(id: Long, title: String): Either<TodoError, Unit> = Either.Right(Unit)
    override suspend fun setNote(id: Long, note: String): Either<TodoError, Unit> = Either.Right(Unit)
    override suspend fun setDueDate(id: Long, dueDate: Instant?): Either<TodoError, Unit> = Either.Right(Unit)
    override suspend fun moveToList(id: Long, listId: Long): Either<TodoError, Unit> = Either.Right(Unit)
    override suspend fun trash(id: Long): Either<TodoError, Unit> = Either.Right(Unit)
    override suspend fun restore(id: Long): Either<TodoError, Unit> = Either.Right(Unit)
    override suspend fun deleteForever(id: Long): Either<TodoError, Unit> = Either.Right(Unit)
}
