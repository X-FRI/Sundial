package com.myapplication.shared.test

import arrow.core.Either
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.domain.repository.TodoRepository
import com.myapplication.shared.domain.sync.ListRowDto
import com.myapplication.shared.domain.sync.SyncAction
import com.myapplication.shared.domain.sync.SyncRow
import com.myapplication.shared.domain.sync.TodoRowDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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
    val outboxState = MutableStateFlow<List<SyncRow>>(emptyList())
    val settingsState = MutableStateFlow<Map<String, String>>(emptyMap())
    var appliedUpserts = mutableListOf<TodoRowDto>()
    var appliedDeletes = mutableListOf<Pair<String, Long>>()

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
        outboxState.value = outboxState.value + SyncRow(
            seq = outboxState.value.size.toLong() + 1,
            table = "todo",
            rowId = item.id,
            action = SyncAction.UPSERT,
            payload = "",
            updatedAt = 0L,
            updatedBy = "",
        )
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

    override suspend fun readOutbox(limit: Int): Either<TodoError, List<SyncRow>> =
        Either.Right(outboxState.value.take(limit))
    override suspend fun clearOutbox(upToSeq: Long): Either<TodoError, Unit> {
        outboxState.value = outboxState.value.filter { it.seq > upToSeq }
        return Either.Right(Unit)
    }
    override fun observeOutboxCount(): Flow<Int> = outboxState.map { it.size }
    override suspend fun applyRemoteUpsert(row: TodoRowDto): Either<TodoError, Unit> {
        appliedUpserts += row
        return Either.Right(Unit)
    }
    override suspend fun applyRemoteUpsertList(row: ListRowDto): Either<TodoError, Unit> = Either.Right(Unit)
    override suspend fun applyRemoteDelete(table: String, rowId: Long, updatedAt: Long): Either<TodoError, Unit> {
        appliedDeletes += table to rowId
        return Either.Right(Unit)
    }
    override suspend fun getSetting(key: String): Either<TodoError, String?> =
        Either.Right(settingsState.value[key])
    override suspend fun setSetting(key: String, value: String): Either<TodoError, Unit> {
        settingsState.value = settingsState.value + (key to value)
        return Either.Right(Unit)
    }
    override suspend fun getSettings(): Either<TodoError, Map<String, String>> =
        Either.Right(settingsState.value)
}
