package com.myapplication.shared.data

import app.cash.sqldelight.coroutines.asFlow
import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.domain.repository.TodoRepository
import com.myapplication.shared.domain.sync.ListRowDto
import com.myapplication.shared.domain.sync.SyncAction
import com.myapplication.shared.domain.sync.SyncRow
import com.myapplication.shared.domain.sync.TodoRowDto
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val outboxJson = Json

class TodoRepositoryImpl(
    private val db: TodoDb,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val deviceId: String = "local",
) : TodoRepository {

    private fun Todo.toDomain() = TodoItem(
        id = id,
        listId = list_id,
        title = title,
        note = note,
        dueDate = due_date?.let { Instant.fromEpochMilliseconds(it) },
        isCompleted = is_completed,
        flag = flag,
        completedAt = completed_at?.let { Instant.fromEpochMilliseconds(it) },
        isTrashed = is_trashed,
        trashedAt = trashed_at?.let { Instant.fromEpochMilliseconds(it) },
        parentId = parent_id,
        sortPosition = sort_position,
        createdAt = Instant.fromEpochMilliseconds(created_at),
    )

    private fun SelectWithDueDate.toDomain() = TodoItem(
        id = id,
        listId = list_id,
        title = title,
        note = note,
        dueDate = Instant.fromEpochMilliseconds(due_date),
        isCompleted = is_completed,
        flag = flag,
        completedAt = completed_at?.let { Instant.fromEpochMilliseconds(it) },
        isTrashed = is_trashed,
        trashedAt = trashed_at?.let { Instant.fromEpochMilliseconds(it) },
        parentId = parent_id,
        sortPosition = sort_position,
        createdAt = Instant.fromEpochMilliseconds(created_at),
    )

    private fun Reminder_list.toDomain() = TodoList(
        id = id,
        name = name,
        colorKey = color_key,
        position = position.toInt(),
        createdAt = Instant.fromEpochMilliseconds(created_at),
    )

    private fun Todo.toDto() = TodoRowDto(
        id = id,
        listId = list_id,
        title = title,
        note = note,
        dueDate = due_date,
        isCompleted = is_completed,
        completedAt = completed_at,
        isTrashed = is_trashed,
        trashedAt = trashed_at,
        parentId = parent_id,
        sortPosition = sort_position,
        flag = flag,
        createdAt = created_at,
        updatedAt = updated_at,
        updatedBy = updated_by,
    )

    private fun Reminder_list.toDto() = ListRowDto(
        id = id,
        name = name,
        colorKey = color_key,
        position = position.toInt(),
        createdAt = created_at,
        updatedAt = updated_at,
        updatedBy = updated_by,
    )

    private fun Outbox.toSyncRow() = SyncRow(
        seq = id,
        table = table_name,
        rowId = row_id,
        action = when (action) { "DELETE" -> SyncAction.DELETE; else -> SyncAction.UPSERT },
        payload = payload.ifEmpty { null },
        updatedAt = created_at,
        updatedBy = "",
    )

    private val now: Long get() = clock.now().toEpochMilliseconds()

    private inline fun <A> guard(block: () -> A): Either<TodoError, A> =
        try {
            block().right()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TodoError.Persistence(e.message ?: "数据库操作失败").left()
        }

    // ---- outbox 追加（事务内调用） ----

    private fun appendOutbox(table: String, rowId: Long, action: SyncAction, payload: String?) {
        val seq = db.todoDbQueries.selectOutboxMaxSeq().executeAsOne() + 1
        db.todoDbQueries.insertOutbox(
            seq, table, rowId, if (action == SyncAction.DELETE) "DELETE" else "UPSERT",
            payload ?: "", now,
        )
    }

    private fun appendTodoOutbox(row: TodoRowDto, action: SyncAction = SyncAction.UPSERT) {
        appendOutbox("todo", row.id, action, if (action == SyncAction.DELETE) null else outboxJson.encodeToString(row))
    }

    private fun appendListOutbox(row: ListRowDto, action: SyncAction = SyncAction.UPSERT) {
        appendOutbox("reminder_list", row.id, action, if (action == SyncAction.DELETE) null else outboxJson.encodeToString(row))
    }

    // ---- 查询（与既有实现一致） ----

    override fun observeLists(): Flow<List<TodoList>> =
        db.todoDbQueries.selectLists().asFlow().map { it.executeAsList() }.map { lists -> lists.map { it.toDomain() } }

    override fun observeAllActive(): Flow<List<TodoItem>> =
        db.todoDbQueries.selectAllActive().asFlow().map { it.executeAsList() }.map { todos -> todos.map { it.toDomain() } }

    override fun observeByList(listId: Long): Flow<List<TodoItem>> =
        db.todoDbQueries.selectByList(listId).asFlow().map { it.executeAsList() }.map { todos -> todos.map { it.toDomain() } }

    override fun observeToday(): Flow<List<TodoItem>> {
        val today = clock.now().toLocalDateTime(timeZone).date
        val start = today.atStartOfDayIn(timeZone).toEpochMilliseconds()
        val end = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone).toEpochMilliseconds()
        return db.todoDbQueries.selectToday(start, end).asFlow().map { it.executeAsList() }.map { todos -> todos.map { it.toDomain() } }
    }

    override fun observeScheduled(): Flow<List<TodoItem>> =
        db.todoDbQueries.selectWithDueDate().asFlow().map { it.executeAsList() }.map { todos -> todos.map { it.toDomain() } }

    override fun observeCompleted(): Flow<List<TodoItem>> =
        db.todoDbQueries.selectCompleted().asFlow().map { it.executeAsList() }.map { todos -> todos.map { it.toDomain() } }

    override fun observeTrashed(): Flow<List<TodoItem>> =
        db.todoDbQueries.selectTrashed().asFlow().map { it.executeAsList() }.map { todos -> todos.map { it.toDomain() } }

    override fun observeSubTasks(parentId: Long): Flow<List<TodoItem>> =
        db.todoDbQueries.selectSubTasks(parentId).asFlow().map { it.executeAsList() }.map { todos -> todos.map { it.toDomain() } }

    override fun observeTodo(id: Long): Flow<TodoItem?> =
        db.todoDbQueries.selectById(id).asFlow().map { it.executeAsOneOrNull() }.map { it?.toDomain() }

    override fun search(query: String): Flow<List<TodoItem>> {
        val escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val pattern = "%$escaped%"
        return db.todoDbQueries.searchTodos(pattern, pattern).asFlow().map { it.executeAsList() }
            .map { todos -> todos.map { it.toDomain() } }
    }

    override suspend fun findById(id: Long): Either<TodoError, TodoItem?> =
        guard { db.todoDbQueries.selectById(id).executeAsOneOrNull()?.toDomain() }

    // ---- 命令（双写 outbox） ----

    override suspend fun ensureInbox(): Either<TodoError, Long> = either {
        try {
            db.transaction {
                val lists = db.todoDbQueries.selectLists().executeAsList()
                if (lists.isEmpty()) {
                    db.todoDbQueries.insertList("收件箱", "blue", 0, now, now, deviceId)
                    val row = db.todoDbQueries.selectLists().executeAsList().first()
                    appendListOutbox(row.toDto())
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            raise(TodoError.Persistence(e.message ?: "初始化收件箱失败"))
        }
        guard { db.todoDbQueries.selectLists().executeAsList().firstOrNull()?.id }.bind()
            ?: raise(TodoError.InboxNotFound)
    }

    override suspend fun addList(name: String, colorKey: String): Either<TodoError, Unit> = either {
        try {
            db.transaction {
                val position = db.todoDbQueries.selectLists().executeAsList().size
                db.todoDbQueries.insertList(name, colorKey, position.toLong(), now, now, deviceId)
                val row = db.todoDbQueries.selectLists().executeAsList().last()
                appendListOutbox(row.toDto())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            raise(TodoError.Persistence(e.message ?: "添加列表失败"))
        }
    }

    override suspend fun deleteList(listId: Long): Either<TodoError, Unit> = either {
        try {
            db.transaction {
                val affected = db.todoDbQueries.selectByList(listId).executeAsList()
                db.todoDbQueries.trashTodosInList(now, now, deviceId, listId)
                affected.forEach { todo ->
                    val updated = db.todoDbQueries.selectById(todo.id).executeAsOne()
                    appendTodoOutbox(updated.toDto())
                }
                val list = db.todoDbQueries.selectByIdForList(listId).executeAsOneOrNull()
                db.todoDbQueries.deleteList(listId)
                if (list != null) appendListOutbox(list.toDto(), SyncAction.DELETE)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            raise(TodoError.Persistence(e.message ?: "删除列表失败"))
        }
    }

    override suspend fun insertTodo(
        listId: Long,
        title: String,
        note: String,
        dueDate: Instant?,
        parentId: Long?,
        flag: Boolean,
    ): Either<TodoError, Unit> = either {
        try {
            db.transaction {
                db.todoDbQueries.insertTodo(listId, title, note, dueDate?.toEpochMilliseconds(), parentId, 0.0, flag, now, now, deviceId)
                val row = db.todoDbQueries.selectByIdLast().executeAsOne()
                appendTodoOutbox(row.toDto())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            raise(TodoError.Persistence(e.message ?: "添加待办失败"))
        }
    }

    override suspend fun setCompleted(id: Long, completed: Boolean): Either<TodoError, Unit> = either {
        try {
            db.transaction {
                db.todoDbQueries.updateCompleted(completed, if (completed) now else null, now, deviceId, id)
                val row = db.todoDbQueries.selectById(id).executeAsOne()
                appendTodoOutbox(row.toDto())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            raise(TodoError.Persistence(e.message ?: "更新状态失败"))
        }
    }

    override suspend fun setFlag(id: Long, flag: Boolean): Either<TodoError, Unit> = either {
        try {
            db.transaction {
                db.todoDbQueries.updateFlag(flag, now, deviceId, id)
                val row = db.todoDbQueries.selectById(id).executeAsOne()
                appendTodoOutbox(row.toDto())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            raise(TodoError.Persistence(e.message ?: "更新旗标失败"))
        }
    }

    override suspend fun setTitle(id: Long, title: String): Either<TodoError, Unit> = either {
        try {
            db.transaction {
                db.todoDbQueries.updateTitle(title, now, deviceId, id)
                val row = db.todoDbQueries.selectById(id).executeAsOne()
                appendTodoOutbox(row.toDto())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            raise(TodoError.Persistence(e.message ?: "更新标题失败"))
        }
    }

    override suspend fun setNote(id: Long, note: String): Either<TodoError, Unit> = either {
        try {
            db.transaction {
                db.todoDbQueries.updateNote(note, now, deviceId, id)
                val row = db.todoDbQueries.selectById(id).executeAsOne()
                appendTodoOutbox(row.toDto())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            raise(TodoError.Persistence(e.message ?: "更新备注失败"))
        }
    }

    override suspend fun setDueDate(id: Long, dueDate: Instant?): Either<TodoError, Unit> = either {
        try {
            db.transaction {
                db.todoDbQueries.updateDueDate(dueDate?.toEpochMilliseconds(), now, deviceId, id)
                val row = db.todoDbQueries.selectById(id).executeAsOne()
                appendTodoOutbox(row.toDto())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            raise(TodoError.Persistence(e.message ?: "更新日期失败"))
        }
    }

    override suspend fun moveToList(id: Long, listId: Long): Either<TodoError, Unit> = either {
        try {
            db.transaction {
                db.todoDbQueries.moveToList(listId, now, deviceId, id)
                val row = db.todoDbQueries.selectById(id).executeAsOne()
                appendTodoOutbox(row.toDto())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            raise(TodoError.Persistence(e.message ?: "移动列表失败"))
        }
    }

    override suspend fun trash(id: Long): Either<TodoError, Unit> = either {
        try {
            db.transaction {
                db.todoDbQueries.trashTodo(now, now, deviceId, id)
                val row = db.todoDbQueries.selectById(id).executeAsOne()
                appendTodoOutbox(row.toDto())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            raise(TodoError.Persistence(e.message ?: "移入垃圾箱失败"))
        }
    }

    override suspend fun restore(id: Long): Either<TodoError, Unit> = either {
        try {
            db.transaction {
                db.todoDbQueries.restoreTodo(now, deviceId, id)
                val row = db.todoDbQueries.selectById(id).executeAsOne()
                appendTodoOutbox(row.toDto())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            raise(TodoError.Persistence(e.message ?: "恢复待办失败"))
        }
    }

    override suspend fun deleteForever(id: Long): Either<TodoError, Unit> = either {
        try {
            db.transaction {
                val row = db.todoDbQueries.selectById(id).executeAsOneOrNull()
                db.todoDbQueries.deleteTodo(id)
                if (row != null) appendTodoOutbox(row.toDto(), SyncAction.DELETE)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            raise(TodoError.Persistence(e.message ?: "彻底删除失败"))
        }
    }

    // ---- 同步专用 ----

    override suspend fun readOutbox(limit: Int): Either<TodoError, List<SyncRow>> =
        guard { db.todoDbQueries.selectOutbox(limit.toLong()).executeAsList().map { it.toSyncRow() } }

    override suspend fun clearOutbox(upToSeq: Long): Either<TodoError, Unit> = guard {
        db.todoDbQueries.deleteOutboxUpTo(upToSeq)
    }

    override fun observeOutboxCount(): Flow<Int> =
        db.todoDbQueries.selectOutboxCount().asFlow().map { it.executeAsOne().toInt() }

    override suspend fun applyRemoteUpsert(row: TodoRowDto): Either<TodoError, Unit> = guard {
        db.todoDbQueries.upsertTodo(
            row.id, row.listId, row.title, row.note, row.dueDate, row.isCompleted, row.completedAt,
            row.isTrashed, row.trashedAt, row.parentId, row.sortPosition, row.flag,
            row.createdAt, row.updatedAt, row.updatedBy,
        )
    }

    override suspend fun applyRemoteUpsertList(row: ListRowDto): Either<TodoError, Unit> = guard {
        db.todoDbQueries.upsertList(row.id, row.name, row.colorKey, row.position.toLong(), row.createdAt, row.updatedAt, row.updatedBy)
    }

    override suspend fun applyRemoteDelete(table: String, rowId: Long, updatedAt: Long): Either<TodoError, Unit> = guard {
        when (table) {
            "todo" -> db.todoDbQueries.deleteTodoIfOlder(rowId, updatedAt)
            "reminder_list" -> db.todoDbQueries.deleteListIfOlder(rowId, updatedAt)
            else -> Unit
        }
    }

    override suspend fun getSetting(key: String): Either<TodoError, String?> =
        guard { db.todoDbQueries.getSetting(key).executeAsOneOrNull() }

    override suspend fun setSetting(key: String, value: String): Either<TodoError, Unit> = guard {
        db.todoDbQueries.setSetting(key, value)
    }

    override suspend fun getSettings(): Either<TodoError, Map<String, String>> =
        guard { db.todoDbQueries.selectAllSettings().executeAsList().associate { it.key to it.value_ } }
}
