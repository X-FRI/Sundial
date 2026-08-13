package com.myapplication.shared.test

import arrow.core.Either
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.list.DeleteListPolicy
import com.myapplication.shared.domain.list.ListStats
import com.myapplication.shared.domain.list.buildListStats
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.domain.recurrence.RecurrenceRule
import com.myapplication.shared.domain.recurrence.nextOccurrence
import com.myapplication.shared.domain.repository.TodoRepository
import com.myapplication.shared.domain.sync.ListRowDto
import com.myapplication.shared.domain.sync.SyncAction
import com.myapplication.shared.domain.sync.SyncRow
import com.myapplication.shared.domain.sync.TodoRowDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * TodoRepository 的内存假实现，供 commonTest 各测试共享。
 *
 * 设计要点：
 * - 所有"查询"用 MutableStateFlow 直接返回内存状态，测试可断言状态本身，
 *   也可用 collect 驱动 ViewModel 的流管道（配合 StandardTestDispatcher）；
 * - 所有"命令"记录副作用到公开字段（lastInserted / toggledId / flaggedId /
 *   failNextInsert…），测试通过这些字段验证用例/ViewModel 是否正确委派；
 * - 模拟真实仓库的同步行为：insertTodo 会追加一条 outbox 记录
 *   （与 TodoRepositoryImpl 的双写一致），applyRemote* 把远端行记入
 *   appliedUpserts / appliedDeletes 供断言；
 * - 未模拟的方法（setTitle/trash/…）直接返回 Right，测试不关心其细节。
 */
class FakeTodoRepository : TodoRepository {
    // —— 状态流（对应真实仓库的 SQL 查询结果）——
    val listsState = MutableStateFlow<List<TodoList>>(emptyList())
    val todosState = MutableStateFlow<List<TodoItem>>(emptyList())
    val todos: List<TodoItem> get() = todosState.value
    // —— 命令副作用记录（供断言"是否正确委派"）——
    var ensureInboxCalls = 0
    var lastInserted: TodoItem? = null
    var toggledId: Long? = null
    var toggledValue: Boolean? = null
    var flaggedId: Long? = null
    var flaggedValue: Boolean? = null
    var recurrenceId: Long? = null
    var recurrenceRule: RecurrenceRule? = null
    var lastSetTitleId: Long? = null
    var lastSetTitleValue: String? = null
    var lastSetDueDateId: Long? = null
    var lastSetDueDateValue: Instant? = null
    var lastMovedTodoId: Long? = null
    var lastMovedListId: Long? = null
    var lastDeleteListPolicy: DeleteListPolicy? = null
    var lastUpdatedListName: String? = null
    var lastUpdatedListColor: String? = null
    var failNextInsert = false
    private var nextId = 1L
    // —— 同步相关状态 ——
    val outboxState = MutableStateFlow<List<SyncRow>>(emptyList())
    val settingsState = MutableStateFlow<Map<String, String>>(emptyMap())
    var appliedUpserts = mutableListOf<TodoRowDto>()
    var appliedDeletes = mutableListOf<Pair<String, Long>>()
    private val fakeToday = LocalDate(2026, 8, 13)

    override fun observeLists(): Flow<List<TodoList>> = listsState
    override fun observeAllActive(): Flow<List<TodoItem>> =
        todosState.map { todos -> todos.filterNot { it.isTrashed } }
    override fun observeByList(listId: Long): Flow<List<TodoItem>> =
        todosState.map { todos -> todos.filter { it.listId == listId && !it.isTrashed } }
    override fun observeToday(): Flow<List<TodoItem>> =
        todosState.map { todos ->
            todos.filter { todo ->
                !todo.isTrashed && todo.dueDate?.toLocalDateTime(TimeZone.UTC)?.date == fakeToday
            }
        }
    override fun observeScheduled(): Flow<List<TodoItem>> =
        todosState.map { todos -> todos.filter { it.dueDate != null && !it.isTrashed } }
    override fun observeCompleted(): Flow<List<TodoItem>> =
        todosState.map { todos -> todos.filter { it.isCompleted && !it.isTrashed } }
    override fun observeTrashed(): Flow<List<TodoItem>> =
        todosState.map { todos -> todos.filter { it.isTrashed } }
    override fun observeSubTasks(parentId: Long): Flow<List<TodoItem>> =
        todosState.map { todos -> todos.filter { it.parentId == parentId && !it.isTrashed } }
    override fun observeTodo(id: Long): Flow<TodoItem?> =
        todosState.map { todos -> todos.firstOrNull { it.id == id } }
    override fun search(query: String): Flow<List<TodoItem>> =
        todosState.map { todos ->
            todos.filter { !it.isTrashed && (it.title.contains(query) || it.note.contains(query)) }
        }
    override fun observeListStats(listId: Long): Flow<ListStats> =
        todosState.map {
            buildListStats(
                listId = listId,
                todos = it,
                today = fakeToday,
                timeZone = TimeZone.UTC,
            )
        }

    override suspend fun findById(id: Long): Either<TodoError, TodoItem?> =
        Either.Right(todosState.value.firstOrNull { it.id == id })

    override suspend fun findByIdActive(id: Long): Either<TodoError, TodoItem?> =
        Either.Right(todosState.value.firstOrNull { it.id == id && !it.isTrashed })

    // 计数 + 首次调用时创建默认"收件箱"列表（id 恒为 1）
    override suspend fun ensureInbox(): Either<TodoError, Long> {
        ensureInboxCalls++
        if (listsState.value.isEmpty()) {
            listsState.value = listOf(TodoList(1, "收件箱", "blue", 0, Instant.fromEpochMilliseconds(0)))
            if (nextId <= 1L) nextId = 2L
        }
        return Either.Right(listsState.value.first().id)
    }

    override suspend fun addList(name: String, colorKey: String): Either<TodoError, Unit> {
        listsState.value = listsState.value + TodoList(
            nextId++, name, colorKey, listsState.value.size, Instant.fromEpochMilliseconds(0),
        )
        return Either.Right(Unit)
    }

    override suspend fun updateList(listId: Long, name: String, colorKey: String): Either<TodoError, Unit> {
        lastUpdatedListName = name
        lastUpdatedListColor = colorKey
        val trimmed = name.trim()
        val trimmedColorKey = colorKey.trim()
        if (trimmed.isEmpty()) return Either.Left(TodoError.Persistence("列表名称不能为空"))
        val list = listsState.value.firstOrNull { it.id == listId }
            ?: return Either.Left(TodoError.Persistence("列表不存在"))
        if (list.name == "收件箱" && list.position == 0) {
            return Either.Left(TodoError.Persistence("收件箱不能改名"))
        }
        listsState.value = listsState.value.map {
            if (it.id == listId) it.copy(name = trimmed, colorKey = trimmedColorKey) else it
        }
        return Either.Right(Unit)
    }

    override suspend fun deleteList(listId: Long, policy: DeleteListPolicy): Either<TodoError, Unit> {
        lastDeleteListPolicy = policy
        val list = listsState.value.firstOrNull { it.id == listId }
            ?: return Either.Left(TodoError.Persistence("列表不存在"))
        if (list.name == "收件箱" && list.position == 0) {
            return Either.Left(TodoError.Persistence("收件箱是系统待整理池，不能删除"))
        }
        val inboxId = listsState.value.firstOrNull { it.name == "收件箱" && it.position == 0 }?.id
            ?: return Either.Left(TodoError.InboxNotFound)
        todosState.value = todosState.value.map { todo ->
            if (todo.listId != listId) {
                todo
            } else {
                when (policy) {
                    DeleteListPolicy.MoveTasksToInbox -> todo.copy(listId = inboxId)
                    DeleteListPolicy.MoveTasksToTrash -> todo.copy(
                        listId = inboxId,
                        isTrashed = true,
                        trashedAt = todo.trashedAt ?: Instant.fromEpochMilliseconds(0),
                    )
                }
            }
        }
        listsState.value = listsState.value.filterNot { it.id == listId }
        return Either.Right(Unit)
    }

    override suspend fun insertTodo(
        listId: Long,
        title: String,
        note: String,
        dueDate: Instant?,
        parentId: Long?,
        flag: Boolean,
        recurrenceRule: RecurrenceRule?,
    ): Either<TodoError, Unit> {
        // 注入一次持久化失败，用于测 Persistence 错误传播
        if (failNextInsert) {
            failNextInsert = false
            return Either.Left(TodoError.Persistence("boom"))
        }
        val item = TodoItem(
            nextId++, listId, title, note, dueDate, false, flag, null, false, null,
            parentId, 0.0, Instant.fromEpochMilliseconds(0), recurrenceRule,
        )
        todosState.value = todosState.value + item
        lastInserted = item
        // 与真实仓库一致：本地写入同时追加 outbox 行，供 SyncCoordinator 测试
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
        todosState.value = todosState.value.map { todo ->
            if (todo.id == id) todo.copy(isCompleted = completed) else todo
        }
        return Either.Right(Unit)
    }

    override suspend fun completeRecurringTodo(id: Long): Either<TodoError, Unit> {
        val current = todosState.value.firstOrNull { it.id == id } ?: return Either.Right(Unit)
        if (current.isCompleted) return Either.Right(Unit)
        val rule = current.recurrenceRule
        val due = current.dueDate
        if (rule != null && due != null && failNextInsert) {
            failNextInsert = false
            return Either.Left(TodoError.Persistence("boom"))
        }

        toggledId = id
        toggledValue = true
        val next = if (rule != null && due != null) {
            val local = due.toLocalDateTime(TimeZone.UTC)
            val nextDate = nextOccurrence(local.date, rule)
            current.copy(
                id = nextId++,
                dueDate = LocalDateTime(nextDate, local.time).toInstant(TimeZone.UTC),
                isCompleted = false,
                completedAt = null,
                createdAt = Instant.fromEpochMilliseconds(0),
            )
        } else {
            null
        }
        todosState.value = todosState.value.map { todo ->
            if (todo.id == id) todo.copy(isCompleted = true) else todo
        } + listOfNotNull(next)
        lastInserted = next
        return Either.Right(Unit)
    }

    fun replaceTodo(item: TodoItem) {
        todosState.value = todosState.value.map { todo ->
            if (todo.id == item.id) item else todo
        }
    }

    override suspend fun setFlag(id: Long, flag: Boolean): Either<TodoError, Unit> {
        flaggedId = id
        flaggedValue = flag
        return Either.Right(Unit)
    }

    // 以下方法只记录测试关心的轻量命令细节，复杂持久化语义由真实仓库测试覆盖
    override suspend fun setTitle(id: Long, title: String): Either<TodoError, Unit> {
        lastSetTitleId = id
        lastSetTitleValue = title
        return Either.Right(Unit)
    }
    override suspend fun setNote(id: Long, note: String): Either<TodoError, Unit> = Either.Right(Unit)
    override suspend fun setDueDate(id: Long, dueDate: Instant?): Either<TodoError, Unit> {
        lastSetDueDateId = id
        lastSetDueDateValue = dueDate
        todosState.value = todosState.value.map { todo ->
            if (todo.id == id) todo.copy(dueDate = dueDate) else todo
        }
        return Either.Right(Unit)
    }
    override suspend fun setRecurrence(id: Long, rule: RecurrenceRule?): Either<TodoError, Unit> {
        recurrenceId = id
        recurrenceRule = rule
        todosState.value = todosState.value.map { todo ->
            if (todo.id == id) todo.copy(recurrenceRule = rule) else todo
        }
        return Either.Right(Unit)
    }
    override suspend fun moveToList(id: Long, listId: Long): Either<TodoError, Unit> {
        lastMovedTodoId = id
        lastMovedListId = listId
        todosState.value = todosState.value.map { todo ->
            if (todo.id == id) todo.copy(listId = listId) else todo
        }
        return Either.Right(Unit)
    }
    override suspend fun trash(id: Long): Either<TodoError, Unit> = Either.Right(Unit)
    override suspend fun restore(id: Long): Either<TodoError, Unit> = Either.Right(Unit)
    override suspend fun deleteForever(id: Long): Either<TodoError, Unit> = Either.Right(Unit)

    // —— 同步接口：outbox 直接操作内存列表 ——
    override suspend fun readOutbox(limit: Int): Either<TodoError, List<SyncRow>> =
        Either.Right(outboxState.value.take(limit))
    override suspend fun clearOutbox(upToSeq: Long): Either<TodoError, Unit> {
        // 只保留 seq > upToSeq 的行（与真实实现语义一致）
        outboxState.value = outboxState.value.filter { it.seq > upToSeq }
        return Either.Right(Unit)
    }
    override fun observeOutboxCount(): Flow<Int> = outboxState.map { it.size }
    // 远端变更不真正落库，只记录 DTO 供断言（LWW 等细节由真实实现测试覆盖）
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
