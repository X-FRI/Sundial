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

/**
 * TodoRepository 的 SQLDelight 实现。
 *
 * 错误风格：所有数据库操作包在 [guard] 里，异常统一转为 TodoError.Persistence（Left）；
 * 写命令再加 either{} 包一层，把 try/catch 折叠成箭头式错误流。
 *
 * 同步不变量：
 * - 每个本地写命令都在同一事务内完成「更新行 -> 读回最新行 -> 写 outbox 快照」三步，
 *   保证 outbox 中的 payload 永远是该行最新状态的完整快照；
 * - appendOutbox 的 seq = 当前 MAX + 1，单调递增，是 outbox 水位线的唯一依据；
 * - applyRemote*（远端应用）不写 outbox，防止自身变更被回放再推回远端（ping-pong）。
 */
class TodoRepositoryImpl(
    private val db: TodoDb,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val deviceId: String = "local",
) : TodoRepository {

    // ---- DB 行 <-> 领域/DTO 映射（epoch 毫秒 <-> Instant） ----

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

    /**
     * outbox 行 -> SyncRow。
     * 约定：seq 直接取 outbox.id（自增主键，天然单调）；payload 空串归一化为 null，
     * 与远端行的 payload 形态保持一致（DELETE 无 payload）。
     */
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

    /**
     * 数据库操作守卫：异常 -> TodoError.Persistence(Left)。
     * 注意 CancellationException 必须原样重抛，不能吞掉协程取消信号。
     */
    private inline fun <A> guard(block: () -> A): Either<TodoError, A> =
        try {
            block().right()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TodoError.Persistence(e.message ?: "数据库操作失败").left()
        }

    // ---- outbox 追加（事务内调用） ----

    /**
     * 追加一条 outbox 记录（必须在调用方事务内执行）。
     * seq = 当前 MAX + 1：SQLite 并发写被事务串行化，MAX+1 保证单调递增，
     * 这是 drainOutbox 水位线（按最后一条 seq 清除）成立的前提。
     */
    private fun appendOutbox(table: String, rowId: Long, action: SyncAction, payload: String?) {
        val seq = db.todoDbQueries.selectOutboxMaxSeq().executeAsOne() + 1
        db.todoDbQueries.insertOutbox(
            seq, table, rowId, if (action == SyncAction.DELETE) "DELETE" else "UPSERT",
            payload ?: "", now,
        )
    }

    /** 追加 todo 行快照（UPSERT 带 payload；DELETE 只记 id，无需快照）。 */
    private fun appendTodoOutbox(row: TodoRowDto, action: SyncAction = SyncAction.UPSERT) {
        appendOutbox("todo", row.id, action, if (action == SyncAction.DELETE) null else outboxJson.encodeToString(row))
    }

    /** 追加列表行快照，语义同 appendTodoOutbox。 */
    private fun appendListOutbox(row: ListRowDto, action: SyncAction = SyncAction.UPSERT) {
        appendOutbox("reminder_list", row.id, action, if (action == SyncAction.DELETE) null else outboxJson.encodeToString(row))
    }

    // ---- 查询（与既有实现一致） ----

    // 全部观察查询共用同一模式：SQLDelight 查询转 Flow，每次数据库变更重查并映射为领域模型。

    override fun observeLists(): Flow<List<TodoList>> =
        db.todoDbQueries.selectLists().asFlow().map { it.executeAsList() }.map { lists -> lists.map { it.toDomain() } }

    override fun observeAllActive(): Flow<List<TodoItem>> =
        db.todoDbQueries.selectAllActive().asFlow().map { it.executeAsList() }.map { todos -> todos.map { it.toDomain() } }

    override fun observeByList(listId: Long): Flow<List<TodoItem>> =
        db.todoDbQueries.selectByList(listId).asFlow().map { it.executeAsList() }.map { todos -> todos.map { it.toDomain() } }

    override fun observeToday(): Flow<List<TodoItem>> {
        // 计算「今天」的 [start, end) 毫秒区间（本地时区零点起 24 小时），交给 SQL 层按区间过滤
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
        // 转义 LIKE 通配符（\ % _），使搜索按字面量匹配而不是通配
        val escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val pattern = "%$escaped%"
        return db.todoDbQueries.searchTodos(pattern, pattern).asFlow().map { it.executeAsList() }
            .map { todos -> todos.map { it.toDomain() } }
    }

    override suspend fun findById(id: Long): Either<TodoError, TodoItem?> =
        guard { db.todoDbQueries.selectById(id).executeAsOneOrNull()?.toDomain() }

    // ---- 命令（双写 outbox） ----

    /**
     * 确保收件箱列表存在，返回其 id。
     *
     * 原子性：检查与插入在同一事务内完成——若并行调用，SQLite 事务串行化
     * 保证只有一个调用真正插入，不会重复建「收件箱」。
     */
    override suspend fun ensureInbox(): Either<TodoError, Long> = either {
        try {
            db.transaction {
                // 1. 事务内检查：列表为空才创建收件箱
                val lists = db.todoDbQueries.selectLists().executeAsList()
                if (lists.isEmpty()) {
                    // 2. 插入收件箱行并读回（拿到自增 id），写 outbox 快照供同步
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
        // 3. 事务外读回收件箱 id（创建后必有）
        guard { db.todoDbQueries.selectLists().executeAsList().firstOrNull()?.id }.bind()
            ?: raise(TodoError.InboxNotFound)
    }

    override suspend fun addList(name: String, colorKey: String): Either<TodoError, Unit> = either {
        try {
            db.transaction {
                // 1. 新列表排到末尾（position = 当前列表数）
                val position = db.todoDbQueries.selectLists().executeAsList().size
                // 2. 插入并读回最新行（自增 id），写 outbox 快照
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

    /**
     * 删除列表（级联）。
     *
     * 注意删除采用两级 outbox：列表内的 todo 全部软删除（trash，逐条写 UPSERT 快照），
     * 列表本身写 DELETE 操作——远端按顺序应用后先收到 todo 更新、再收到列表删除，
     * 不会留下孤儿行（服务端 FK 已移除，此处顺序仍保证语义一致）。
     */
    override suspend fun deleteList(listId: Long): Either<TodoError, Unit> = either {
        try {
            db.transaction {
                // 1. 读出列表内全部 todo（后续逐条写快照）
                val affected = db.todoDbQueries.selectByList(listId).executeAsList()
                // 2. 级联软删除：列表内所有未删除的 todo 标记为 trash
                db.todoDbQueries.trashTodosInList(now, now, deviceId, listId)
                // 3. 逐条读回删除后的最新行，写 outbox UPSERT 快照
                affected.forEach { todo ->
                    val updated = db.todoDbQueries.selectById(todo.id).executeAsOne()
                    appendTodoOutbox(updated.toDto())
                }
                // 4. 读回列表行并物理删除，写 outbox DELETE 操作
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
                // 1. 插入行（sort_position 默认 0，完成/删除状态为初始值）
                db.todoDbQueries.insertTodo(listId, title, note, dueDate?.toEpochMilliseconds(), parentId, 0.0, flag, now, now, deviceId)
                // 2. 事务内读回最新行（拿到自增 id），写 outbox 快照
                //    读回而非复用参数：快照必须含数据库生成的 id，否则远端行无法按 id 关联
                val row = db.todoDbQueries.selectByIdLast().executeAsOne()
                appendTodoOutbox(row.toDto())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            raise(TodoError.Persistence(e.message ?: "添加待办失败"))
        }
    }

    // 以下更新命令共用同一三步模式：事务内 1) 更新行 2) 读回最新行
    // 3) 写 outbox 快照。读回步骤保证 payload 与库内行完全一致。

    override suspend fun setCompleted(id: Long, completed: Boolean): Either<TodoError, Unit> = either {
        try {
            db.transaction {
                // 1. 更新完成状态；取消完成时清空 completed_at
                db.todoDbQueries.updateCompleted(completed, if (completed) now else null, now, deviceId, id)
                // 2. 读回最新行 -> 3. 写 outbox 快照
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
                // 清空日期时传 null，恢复「未安排」状态
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
                // 恢复时清空 trashed_at（软删除时间戳随状态一同复位）
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
                // 1. 先读回待删行（写 DELETE outbox 需要它，但不需要快照）
                val row = db.todoDbQueries.selectById(id).executeAsOneOrNull()
                // 2. 物理删除
                db.todoDbQueries.deleteTodo(id)
                // 3. 行存在才写 DELETE 操作（幂等删除，删不存在的行无操作）
                if (row != null) appendTodoOutbox(row.toDto(), SyncAction.DELETE)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            raise(TodoError.Persistence(e.message ?: "彻底删除失败"))
        }
    }

    // ---- 同步专用 ----

    // 以下方法只被 SyncCoordinator/SyncEngine 调用，代表远端数据回写本地，
    // 因此刻意不写 outbox（防 ping-pong：不把刚收的远端变更再推回去）。

    override suspend fun readOutbox(limit: Int): Either<TodoError, List<SyncRow>> =
        guard { db.todoDbQueries.selectOutbox(limit.toLong()).executeAsList().map { it.toSyncRow() } }

    /** 按水位线清除：seq <= upToSeq 的行视为已推送成功。 */
    override suspend fun clearOutbox(upToSeq: Long): Either<TodoError, Unit> = guard {
        db.todoDbQueries.deleteOutboxUpTo(upToSeq)
    }

    override fun observeOutboxCount(): Flow<Int> =
        db.todoDbQueries.selectOutboxCount().asFlow().map { it.executeAsOne().toInt() }

    /**
     * 应用远端待办行（LWW：Last-Write-Wins，以 updated_at 判新旧）。
     *
     * 双语句实现：updateTodoIfNewer 只覆盖本地行比远端旧的情况；
     * insertTodoIfMissing 只补插本地不存在的行。之所以不用
     * INSERT ... ON CONFLICT DO UPDATE，是因为旧版 Android SQLite
     * （API < 24 的 SQLite 3.8.0 以下）不支持 ON CONFLICT DO UPDATE 语法，
     * 而这两条语句在所有目标平台上都可用。
     */
    override suspend fun applyRemoteUpsert(row: TodoRowDto): Either<TodoError, Unit> = guard {
        db.transaction {
            // 1. 本地已有且较旧 -> 覆盖（WHERE updated_at <= 远端 updated_at）
            db.todoDbQueries.updateTodoIfNewer(
                row.listId, row.title, row.note, row.dueDate, row.isCompleted, row.completedAt,
                row.isTrashed, row.trashedAt, row.parentId, row.sortPosition, row.flag,
                row.createdAt, row.updatedAt, row.updatedBy, row.id, row.updatedAt,
            )
            // 2. 本地没有 -> 插入（WHERE NOT EXISTS 原子补插）
            db.todoDbQueries.insertTodoIfMissing(
                row.id, row.listId, row.title, row.note, row.dueDate, row.isCompleted, row.completedAt,
                row.isTrashed, row.trashedAt, row.parentId, row.sortPosition, row.flag,
                row.createdAt, row.updatedAt, row.updatedBy, row.id,
            )
        }
    }

    /** 远端列表行 LWW，同 applyRemoteUpsert 的双语句实现。 */
    override suspend fun applyRemoteUpsertList(row: ListRowDto): Either<TodoError, Unit> = guard {
        db.transaction {
            // 1. 较旧则覆盖 -> 2. 缺失则插入（同一事务，保证半途失败可回滚）
            db.todoDbQueries.updateListIfNewer(
                row.name, row.colorKey, row.position.toLong(), row.updatedAt, row.updatedBy, row.id, row.updatedAt,
            )
            db.todoDbQueries.insertListIfMissing(
                row.id, row.name, row.colorKey, row.position.toLong(), row.createdAt, row.updatedAt, row.updatedBy, row.id,
            )
        }
    }

    /**
     * 应用远端删除（LWW 删除）：仅当本地行 updated_at <= [updatedAt] 才删。
     * 防止「本机刚编辑过、远端收到一条更早的删除」时误删新数据。
     */
    override suspend fun applyRemoteDelete(table: String, rowId: Long, updatedAt: Long): Either<TodoError, Unit> = guard {
        when (table) {
            "todo" -> db.todoDbQueries.deleteTodoIfOlder(rowId, updatedAt)
            "reminder_list" -> db.todoDbQueries.deleteListIfOlder(rowId, updatedAt)
            else -> Unit
        }
    }

    override suspend fun getSetting(key: String): Either<TodoError, String?> =
        guard { db.todoDbQueries.getSetting(key).executeAsOneOrNull() }

    /**
     * 写设置（同步 token / 设备标识等）。update + insert-if-missing 双语句，
     * 与 applyRemoteUpsert 同理（老 SQLite 无 ON CONFLICT DO UPDATE）。
     */
    override suspend fun setSetting(key: String, value: String): Either<TodoError, Unit> = guard {
        db.transaction {
            // 1. 已有键 -> 更新值
            db.todoDbQueries.updateSetting(value, key)
            // 2. 不存在 -> 原子插入（WHERE NOT EXISTS）
            db.todoDbQueries.insertSettingIfMissing(key, value, key)
        }
    }

    override suspend fun getSettings(): Either<TodoError, Map<String, String>> =
        guard { db.todoDbQueries.selectAllSettings().executeAsList().associate { it.key to it.value_ } }
}
