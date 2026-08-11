package com.myapplication.shared.data

import app.cash.sqldelight.coroutines.asFlow
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import arrow.core.raise.either
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.domain.repository.TodoRepository
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

class TodoRepositoryImpl(
    private val db: TodoDb,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
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

    private inline fun <A> guard(block: () -> A): Either<TodoError, A> =
        try {
            block().right()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TodoError.Persistence(e.message ?: "数据库操作失败").left()
        }

    override suspend fun ensureInbox(): Either<TodoError, Long> = either {
        val lists = guard { db.todoDbQueries.selectLists().executeAsList() }.bind()
        if (lists.isEmpty()) {
            guard {
                db.todoDbQueries.insertList("收件箱", "blue", 0, clock.now().toEpochMilliseconds())
            }.bind()
        }
        guard { db.todoDbQueries.selectLists().executeAsList().firstOrNull()?.id }.bind()
            ?: raise(TodoError.InboxNotFound)
    }

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

    override suspend fun addList(name: String, colorKey: String): Either<TodoError, Unit> = guard {
        val position = db.todoDbQueries.selectLists().executeAsList().size
        db.todoDbQueries.insertList(name, colorKey, position.toLong(), clock.now().toEpochMilliseconds())
    }

    override suspend fun deleteList(listId: Long): Either<TodoError, Unit> = either {
        try {
            db.transaction {
                db.todoDbQueries.trashTodosInList(clock.now().toEpochMilliseconds(), listId)
                db.todoDbQueries.deleteList(listId)
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
    ): Either<TodoError, Unit> = guard {
        db.todoDbQueries.insertTodo(listId, title, note, dueDate?.toEpochMilliseconds(), parentId, 0.0, flag, clock.now().toEpochMilliseconds())
    }

    override suspend fun setCompleted(id: Long, completed: Boolean): Either<TodoError, Unit> = guard {
        db.todoDbQueries.updateCompleted(completed, if (completed) clock.now().toEpochMilliseconds() else null, id)
    }

    override suspend fun setFlag(id: Long, flag: Boolean): Either<TodoError, Unit> = guard {
        db.todoDbQueries.updateFlag(flag, id)
    }

    override suspend fun setTitle(id: Long, title: String): Either<TodoError, Unit> = guard {
        db.todoDbQueries.updateTitle(title, id)
    }

    override suspend fun setNote(id: Long, note: String): Either<TodoError, Unit> = guard {
        db.todoDbQueries.updateNote(note, id)
    }

    override suspend fun setDueDate(id: Long, dueDate: Instant?): Either<TodoError, Unit> = guard {
        db.todoDbQueries.updateDueDate(dueDate?.toEpochMilliseconds(), id)
    }

    override suspend fun moveToList(id: Long, listId: Long): Either<TodoError, Unit> = guard {
        db.todoDbQueries.moveToList(listId, id)
    }

    override suspend fun trash(id: Long): Either<TodoError, Unit> = guard {
        db.todoDbQueries.trashTodo(clock.now().toEpochMilliseconds(), id)
    }

    override suspend fun restore(id: Long): Either<TodoError, Unit> = guard {
        db.todoDbQueries.restoreTodo(id)
    }

    override suspend fun deleteForever(id: Long): Either<TodoError, Unit> = guard {
        db.todoDbQueries.deleteTodo(id)
    }
}
