package com.myapplication.shared.data

import app.cash.sqldelight.coroutines.asFlow
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.domain.repository.TodoRepository
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class TodoRepositoryImpl(private val db: TodoDb) : TodoRepository {

    private fun Todo.toDomain() = TodoItem(
        id = id,
        listId = list_id,
        title = title,
        note = note,
        dueDate = due_date?.let { Instant.fromEpochMilliseconds(it) },
        isCompleted = is_completed,
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

    override suspend fun ensureInbox() {
        if (db.todoDbQueries.selectLists().executeAsList().isEmpty()) {
            db.todoDbQueries.insertList("收件箱", "blue", 0, Clock.System.now().toEpochMilliseconds())
        }
    }

    override fun observeLists(): Flow<List<TodoList>> =
        db.todoDbQueries.selectLists().asFlow().map { it.executeAsList() }.map { lists -> lists.map { it.toDomain() } }

    override fun observeAllActive(): Flow<List<TodoItem>> =
        db.todoDbQueries.selectAllActive().asFlow().map { it.executeAsList() }.map { todos -> todos.map { it.toDomain() } }

    override fun observeByList(listId: Long): Flow<List<TodoItem>> =
        db.todoDbQueries.selectByList(listId).asFlow().map { it.executeAsList() }.map { todos -> todos.map { it.toDomain() } }

    override fun observeToday(): Flow<List<TodoItem>> {
        val tz = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(tz).date
        val start = today.atStartOfDayIn(tz).toEpochMilliseconds()
        val end = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz).toEpochMilliseconds()
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

    override suspend fun addList(name: String, colorKey: String) {
        val position = db.todoDbQueries.selectLists().executeAsList().size
        db.todoDbQueries.insertList(name, colorKey, position.toLong(), Clock.System.now().toEpochMilliseconds())
    }

    override suspend fun deleteList(listId: Long) {
        db.todoDbQueries.trashTodosInList(Clock.System.now().toEpochMilliseconds(), listId)
        db.todoDbQueries.deleteList(listId)
    }

    override suspend fun addTodo(listId: Long?, title: String, note: String, dueDate: Instant?, parentId: Long?) {
        val now = Clock.System.now().toEpochMilliseconds()
        val targetList = parentId
            ?.let { pid -> db.todoDbQueries.selectById(pid).executeAsOneOrNull()?.list_id }
            ?: listId
            ?: run {
                ensureInbox()
                db.todoDbQueries.selectLists().executeAsList().first().id
            }
        db.todoDbQueries.insertTodo(targetList, title, note, dueDate?.toEpochMilliseconds(), parentId, 0.0, now)
    }

    override suspend fun addSubTask(parentId: Long, title: String) {
        val parent = db.todoDbQueries.selectById(parentId).executeAsOneOrNull()
            ?: return
        db.todoDbQueries.insertTodo(parent.list_id, title, "", null, parentId, 0.0, Clock.System.now().toEpochMilliseconds())
    }

    override suspend fun setCompleted(id: Long, completed: Boolean) {
        db.todoDbQueries.updateCompleted(completed, if (completed) Clock.System.now().toEpochMilliseconds() else null, id)
    }

    override suspend fun setTitle(id: Long, title: String) {
        db.todoDbQueries.updateTitle(title, id)
    }

    override suspend fun setNote(id: Long, note: String) {
        db.todoDbQueries.updateNote(note, id)
    }

    override suspend fun setDueDate(id: Long, dueDate: Instant?) {
        db.todoDbQueries.updateDueDate(dueDate?.toEpochMilliseconds(), id)
    }

    override suspend fun moveToList(id: Long, listId: Long) {
        db.todoDbQueries.moveToList(listId, id)
    }

    override suspend fun trash(id: Long) {
        db.todoDbQueries.trashTodo(Clock.System.now().toEpochMilliseconds(), id)
    }

    override suspend fun restore(id: Long) {
        db.todoDbQueries.restoreTodo(id)
    }

    override suspend fun deleteForever(id: Long) {
        db.todoDbQueries.deleteTodo(id)
    }
}
