package com.myapplication.shared.data

import app.cash.sqldelight.coroutines.asFlow
import arrow.core.Either
import arrow.core.raise.Raise
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.list.DeleteListPolicy
import com.myapplication.shared.domain.list.ListStats
import com.myapplication.shared.domain.list.buildListStats
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.domain.recurrence.RecurrenceRule
import com.myapplication.shared.domain.repository.TodoRepository
import com.myapplication.shared.domain.sync.ListRowDto
import com.myapplication.shared.domain.sync.SyncAction
import com.myapplication.shared.domain.sync.SyncRow
import com.myapplication.shared.domain.sync.TodoRowDto
import com.myapplication.shared.domain.sync.syncJson
import com.myapplication.shared.effects.catchPersistence
import com.myapplication.shared.effects.runTodoEffect
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.encodeToString

private fun RecurrenceRule?.toFrequencyString(): String? = when (this) {
    is RecurrenceRule.Daily -> "daily"
    is RecurrenceRule.Weekly -> "weekly"
    is RecurrenceRule.Monthly -> "monthly"
    null -> null
}

private fun RecurrenceRule?.toIntervalLong(): Long? = this?.interval?.toLong()

private fun decodeRecurrenceRule(frequency: String?, interval: Long?): RecurrenceRule? {
    val safeInterval = interval
        ?.takeIf { it > 0 && it <= Int.MAX_VALUE }
        ?.toInt()
        ?: return null
    return when (frequency) {
        "daily" -> RecurrenceRule.Daily(safeInterval)
        "weekly" -> RecurrenceRule.Weekly(safeInterval)
        "monthly" -> RecurrenceRule.Monthly(safeInterval)
        else -> null
    }
}

/**
 * TodoRepository 的 SQLDelight 实现。
 *
 * 错误风格：所有数据库命令都经 [dbCommand] 解释成 typed effect，异常统一转为
 * TodoError.Persistence（Left），并保留 CancellationException 的协程取消语义。
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
    private val dbDispatcher: CoroutineDispatcher = newSingleThreadContext("sqlite-db"),
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
        recurrenceRule = decodeRecurrenceRule(recurrence_frequency, recurrence_interval),
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
        recurrenceRule = decodeRecurrenceRule(recurrence_frequency, recurrence_interval),
    )

    private fun Reminder_list.toDomain() = TodoList(
        id = id,
        name = name,
        colorKey = color_key,
        position = position.toInt(),
        createdAt = Instant.fromEpochMilliseconds(created_at),
    )

    private fun Todo.toDto(): TodoRowDto {
        val recurrenceRule = decodeRecurrenceRule(recurrence_frequency, recurrence_interval)
        return TodoRowDto(
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
            recurrenceFrequency = recurrenceRule.toFrequencyString(),
            recurrenceInterval = recurrenceRule.toIntervalLong(),
            createdAt = created_at,
            updatedAt = updated_at,
            updatedBy = updated_by,
        )
    }

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
     * 约定：seq 直接取 outbox.seq（水位线字段）；payload 空串归一化为 null，
     * 与远端行的 payload 形态保持一致（DELETE 无 payload）。
     */
    private fun Outbox.toSyncRow() = SyncRow(
        seq = seq,
        table = table_name,
        rowId = row_id,
        action = when (action) { "DELETE" -> SyncAction.DELETE; else -> SyncAction.UPSERT },
        payload = payload.ifEmpty { null },
        updatedAt = created_at,
        updatedBy = "",
    )

    private val now: Long get() = clock.now().toEpochMilliseconds()

    private suspend inline fun <A> dbCommand(
        fallbackMessage: String,
        crossinline block: suspend Raise<TodoError>.() -> A,
    ): Either<TodoError, A> =
        runTodoEffect {
            val raiseScope = this
            catchPersistence(fallbackMessage) {
                withContext(dbDispatcher) {
                    block.invoke(raiseScope)
                }
            }
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
        appendOutbox("todo", row.id, action, if (action == SyncAction.DELETE) null else syncJson.encodeToString(row))
    }

    /** 追加列表行快照，语义同 appendTodoOutbox。 */
    private fun appendListOutbox(row: ListRowDto, action: SyncAction = SyncAction.UPSERT) {
        appendOutbox("reminder_list", row.id, action, if (action == SyncAction.DELETE) null else syncJson.encodeToString(row))
    }

    // ---- 查询（与既有实现一致） ----

    // 全部观察查询共用同一模式：SQLDelight 查询转 Flow，每次数据库变更重查并映射为领域模型。

    override fun observeLists(): Flow<List<TodoList>> =
        db.todoDbQueries.selectLists().asFlow().map { it.executeAsList() }.map { lists -> lists.map { it.toDomain() } }
            .flowOn(dbDispatcher)

    override fun observeAllActive(): Flow<List<TodoItem>> =
        db.todoDbQueries.selectAllActive().asFlow().map { it.executeAsList() }.map { todos -> todos.map { it.toDomain() } }
            .flowOn(dbDispatcher)

    override fun observeByList(listId: Long): Flow<List<TodoItem>> =
        db.todoDbQueries.selectByList(listId).asFlow().map { it.executeAsList() }.map { todos -> todos.map { it.toDomain() } }
            .flowOn(dbDispatcher)

    override fun observeToday(): Flow<List<TodoItem>> {
        // 计算「今天」的 [start, end) 毫秒区间（本地时区零点起 24 小时），交给 SQL 层按区间过滤
        val today = clock.now().toLocalDateTime(timeZone).date
        val start = today.atStartOfDayIn(timeZone).toEpochMilliseconds()
        val end = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone).toEpochMilliseconds()
        return db.todoDbQueries.selectToday(start, end).asFlow().map { it.executeAsList() }.map { todos -> todos.map { it.toDomain() } }
            .flowOn(dbDispatcher)
    }

    override fun observeScheduled(): Flow<List<TodoItem>> =
        db.todoDbQueries.selectWithDueDate().asFlow().map { it.executeAsList() }.map { todos -> todos.map { it.toDomain() } }
            .flowOn(dbDispatcher)

    override fun observeCompleted(): Flow<List<TodoItem>> =
        db.todoDbQueries.selectCompleted().asFlow().map { it.executeAsList() }.map { todos -> todos.map { it.toDomain() } }
            .flowOn(dbDispatcher)

    override fun observeTrashed(): Flow<List<TodoItem>> =
        db.todoDbQueries.selectTrashed().asFlow().map { it.executeAsList() }.map { todos -> todos.map { it.toDomain() } }
            .flowOn(dbDispatcher)

    private fun observeAllIncludingTrashed(): Flow<List<TodoItem>> =
        db.todoDbQueries.selectAllTodos().asFlow()
            .map { it.executeAsList().map { row -> row.toDomain() } }
            .flowOn(dbDispatcher)

    override fun observeListStats(listId: Long): Flow<ListStats> =
        observeAllIncludingTrashed()
            .map { todos ->
                buildListStats(
                    listId = listId,
                    todos = todos,
                    today = clock.now().toLocalDateTime(timeZone).date,
                    timeZone = timeZone,
                )
            }
            .flowOn(dbDispatcher)

    override fun observeSubTasks(parentId: Long): Flow<List<TodoItem>> =
        db.todoDbQueries.selectSubTasks(parentId).asFlow().map { it.executeAsList() }.map { todos -> todos.map { it.toDomain() } }
            .flowOn(dbDispatcher)

    override fun observeTodo(id: Long): Flow<TodoItem?> =
        db.todoDbQueries.selectById(id).asFlow().map { it.executeAsOneOrNull() }.map { it?.toDomain() }
            .flowOn(dbDispatcher)

    override fun search(query: String): Flow<List<TodoItem>> {
        // 转义 LIKE 通配符（\ % _），使搜索按字面量匹配而不是通配
        val escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val pattern = "%$escaped%"
        return db.todoDbQueries.searchTodos(pattern, pattern).asFlow().map { it.executeAsList() }
            .map { todos -> todos.map { it.toDomain() } }.flowOn(dbDispatcher)
    }

    override suspend fun findById(id: Long): Either<TodoError, TodoItem?> =
        dbCommand("读取待办失败") { db.todoDbQueries.selectById(id).executeAsOneOrNull()?.toDomain() }

    override suspend fun findByIdActive(id: Long): Either<TodoError, TodoItem?> =
        dbCommand("读取待办失败") { db.todoDbQueries.selectByIdActive(id).executeAsOneOrNull()?.toDomain() }

    // ---- 命令（双写 outbox） ----

    /**
     * 确保收件箱列表存在，返回其 id。
     *
     * 原子性：检查与插入在同一事务内完成——若并行调用，SQLite 事务串行化
     * 保证只有一个调用真正插入，不会重复建「收件箱」。
     */
    override suspend fun ensureInbox(): Either<TodoError, Long> = dbCommand("初始化收件箱失败") {
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
        // 3. 事务外读回收件箱 id（创建后必有）
        db.todoDbQueries.selectLists().executeAsList().firstOrNull()?.id ?: raise(TodoError.InboxNotFound)
    }

    override suspend fun addList(name: String, colorKey: String): Either<TodoError, Unit> = dbCommand("添加列表失败") {
        db.transaction {
            // 1. 新列表排到末尾（position = 当前列表数）
            val position = db.todoDbQueries.selectLists().executeAsList().size
            // 2. 插入并读回最新行（自增 id），写 outbox 快照
            db.todoDbQueries.insertList(name, colorKey, position.toLong(), now, now, deviceId)
            val row = db.todoDbQueries.selectLists().executeAsList().last()
            appendListOutbox(row.toDto())
        }
    }

    override suspend fun updateList(listId: Long, name: String, colorKey: String): Either<TodoError, Unit> =
        dbCommand("更新列表失败") {
            val trimmed = name.trim()
            val trimmedColorKey = colorKey.trim()
            if (trimmed.isEmpty()) raise(TodoError.Persistence("列表名称不能为空"))
            db.transaction {
                val existing = db.todoDbQueries.selectByIdForList(listId).executeAsOneOrNull()
                    ?: raise(TodoError.Persistence("列表不存在"))
                if (existing.name == "收件箱" && existing.position == 0L) {
                    raise(TodoError.Persistence("收件箱不能改名"))
                }
                db.todoDbQueries.updateList(trimmed, trimmedColorKey, now, deviceId, listId)
                val row = db.todoDbQueries.selectByIdForList(listId).executeAsOne()
                appendListOutbox(row.toDto())
            }
        }

    /**
     * 删除列表。
     *
     * 默认策略保留列表内活跃 todo，将它们移入收件箱；危险策略仍允许显式软删除。
     * 两种策略都会先为受影响 todo 写 UPSERT 快照，再为列表写 DELETE 操作。
     */
    override suspend fun deleteList(listId: Long, policy: DeleteListPolicy): Either<TodoError, Unit> = dbCommand("删除列表失败") {
        db.transaction {
            val list = db.todoDbQueries.selectByIdForList(listId).executeAsOneOrNull()
                ?: raise(TodoError.Persistence("列表不存在"))
            if (list.name == "收件箱" && list.position == 0L) {
                raise(TodoError.Persistence("收件箱是系统待整理池，不能删除"))
            }
            val affected = db.todoDbQueries.selectByListIncludingTrashed(listId).executeAsList()
            val inboxId = db.todoDbQueries.selectLists().executeAsList()
                .firstOrNull { it.name == "收件箱" && it.position == 0L }
                ?.id
                ?: raise(TodoError.InboxNotFound)
            when (policy) {
                DeleteListPolicy.MoveTasksToInbox -> {
                    val timestamp = now
                    db.todoDbQueries.moveTodosInList(inboxId, timestamp, timestamp, deviceId, listId)
                }
                DeleteListPolicy.MoveTasksToTrash -> {
                    val timestamp = now
                    db.todoDbQueries.trashTodosInList(timestamp, timestamp, deviceId, listId)
                    db.todoDbQueries.moveTodosInList(inboxId, timestamp, timestamp, deviceId, listId)
                }
            }
            affected.forEach { old ->
                db.todoDbQueries.selectById(old.id).executeAsOneOrNull()?.let { updated ->
                    appendTodoOutbox(updated.toDto())
                }
            }
            db.todoDbQueries.deleteList(listId)
            appendListOutbox(list.toDto(), SyncAction.DELETE)
        }
    }

    override suspend fun insertTodo(
        listId: Long,
        title: String,
        note: String,
        dueDate: Instant?,
        parentId: Long?,
        flag: Boolean,
    ): Either<TodoError, Unit> = dbCommand("添加待办失败") {
        db.transaction {
            // 1. 插入行（sort_position 默认 0，完成/删除状态为初始值）
            db.todoDbQueries.insertTodo(listId, title, note, dueDate?.toEpochMilliseconds(), parentId, 0.0, flag, now, now, deviceId)
            // 2. 事务内读回最新行（拿到自增 id），写 outbox 快照
            //    读回而非复用参数：快照必须含数据库生成的 id，否则远端行无法按 id 关联
            val row = db.todoDbQueries.selectByIdLast().executeAsOne()
            appendTodoOutbox(row.toDto())
        }
    }

    // 以下更新命令共用同一三步模式：事务内 1) 更新行 2) 读回最新行
    // 3) 写 outbox 快照。读回步骤保证 payload 与库内行完全一致。

    override suspend fun setCompleted(id: Long, completed: Boolean): Either<TodoError, Unit> = dbCommand("更新状态失败") {
        db.transaction {
            // 1. 更新完成状态；取消完成时清空 completed_at
            db.todoDbQueries.updateCompleted(completed, if (completed) now else null, now, deviceId, id)
            // 2. 读回最新行 -> 3. 写 outbox 快照
            val row = db.todoDbQueries.selectById(id).executeAsOne()
            appendTodoOutbox(row.toDto())
        }
    }

    override suspend fun setFlag(id: Long, flag: Boolean): Either<TodoError, Unit> = dbCommand("更新旗标失败") {
        db.transaction {
            db.todoDbQueries.updateFlag(flag, now, deviceId, id)
            val row = db.todoDbQueries.selectById(id).executeAsOne()
            appendTodoOutbox(row.toDto())
        }
    }

    override suspend fun setTitle(id: Long, title: String): Either<TodoError, Unit> = dbCommand("更新标题失败") {
        db.transaction {
            db.todoDbQueries.updateTitle(title, now, deviceId, id)
            val row = db.todoDbQueries.selectById(id).executeAsOne()
            appendTodoOutbox(row.toDto())
        }
    }

    override suspend fun setNote(id: Long, note: String): Either<TodoError, Unit> = dbCommand("更新备注失败") {
        db.transaction {
            db.todoDbQueries.updateNote(note, now, deviceId, id)
            val row = db.todoDbQueries.selectById(id).executeAsOne()
            appendTodoOutbox(row.toDto())
        }
    }

    override suspend fun setDueDate(id: Long, dueDate: Instant?): Either<TodoError, Unit> = dbCommand("更新日期失败") {
        db.transaction {
            // 清空日期时传 null，恢复「未安排」状态
            db.todoDbQueries.updateDueDate(dueDate?.toEpochMilliseconds(), now, deviceId, id)
            val row = db.todoDbQueries.selectById(id).executeAsOne()
            appendTodoOutbox(row.toDto())
        }
    }

    override suspend fun setRecurrence(id: Long, rule: RecurrenceRule?): Either<TodoError, Unit> = dbCommand("更新重复规则失败") {
        db.transaction {
            db.todoDbQueries.updateRecurrence(rule.toFrequencyString(), rule.toIntervalLong(), now, deviceId, id)
            val row = db.todoDbQueries.selectById(id).executeAsOne()
            appendTodoOutbox(row.toDto())
        }
    }

    override suspend fun moveToList(id: Long, listId: Long): Either<TodoError, Unit> = dbCommand("移动列表失败") {
        db.transaction {
            db.todoDbQueries.moveToList(listId, now, deviceId, id)
            val row = db.todoDbQueries.selectById(id).executeAsOne()
            appendTodoOutbox(row.toDto())
        }
    }

    override suspend fun trash(id: Long): Either<TodoError, Unit> = dbCommand("移入垃圾箱失败") {
        db.transaction {
            val affected = listOfNotNull(db.todoDbQueries.selectById(id).executeAsOneOrNull()) +
                db.todoDbQueries.selectChildrenByParent(id).executeAsList()
            val timestamp = now
            affected.forEach { row ->
                db.todoDbQueries.trashTodo(timestamp, timestamp, deviceId, row.id)
            }
            affected.forEach { row ->
                db.todoDbQueries.selectById(row.id).executeAsOneOrNull()?.let { appendTodoOutbox(it.toDto()) }
            }
        }
    }

    override suspend fun restore(id: Long): Either<TodoError, Unit> = dbCommand("恢复待办失败") {
        db.transaction {
            val affected = listOfNotNull(db.todoDbQueries.selectById(id).executeAsOneOrNull()) +
                db.todoDbQueries.selectChildrenByParent(id).executeAsList()
            val timestamp = now
            affected.forEach { row ->
                // 恢复时清空 trashed_at（软删除时间戳随状态一同复位）
                db.todoDbQueries.restoreTodo(timestamp, deviceId, row.id)
            }
            affected.forEach { row ->
                db.todoDbQueries.selectById(row.id).executeAsOneOrNull()?.let { appendTodoOutbox(it.toDto()) }
            }
        }
    }

    override suspend fun deleteForever(id: Long): Either<TodoError, Unit> = dbCommand("彻底删除失败") {
        db.transaction {
            // 1. 先读回待删行与直接子任务（写 DELETE outbox 需要 id，但不需要快照）
            val affected = listOfNotNull(db.todoDbQueries.selectById(id).executeAsOneOrNull()) +
                db.todoDbQueries.selectChildrenByParent(id).executeAsList()
            // 2. 物理删除；先删子任务再删父任务，语义上保持从叶子到根
            affected.sortedByDescending { it.parent_id != null }.forEach { row ->
                db.todoDbQueries.deleteTodo(row.id)
            }
            // 3. 行存在才写 DELETE 操作（幂等删除，删不存在的行无操作）
            affected.forEach { row -> appendTodoOutbox(row.toDto(), SyncAction.DELETE) }
        }
    }

    // ---- 同步专用 ----

    // 以下方法只被 SyncCoordinator/SyncEngine 调用，代表远端数据回写本地，
    // 因此刻意不写 outbox（防 ping-pong：不把刚收的远端变更再推回去）。

    override suspend fun readOutbox(limit: Int): Either<TodoError, List<SyncRow>> =
        dbCommand("读取同步队列失败") { db.todoDbQueries.selectOutbox(limit.toLong()).executeAsList().map { it.toSyncRow() } }

    /** 按水位线清除：seq <= upToSeq 的行视为已推送成功。 */
    override suspend fun clearOutbox(upToSeq: Long): Either<TodoError, Unit> =
        dbCommand("清理同步队列失败") { db.todoDbQueries.deleteOutboxUpTo(upToSeq) }

    override fun observeOutboxCount(): Flow<Int> =
        db.todoDbQueries.selectOutboxCount().asFlow().map { it.executeAsOne().toInt() }.flowOn(dbDispatcher)

    /**
     * 应用远端待办行（LWW：Last-Write-Wins，以 updated_at 判新旧）。
     *
     * 双语句实现：updateTodoIfNewer 只覆盖本地行比远端旧的情况；
     * insertTodoIfMissing 只补插本地不存在的行。之所以不用
     * INSERT ... ON CONFLICT DO UPDATE，是因为旧版 Android SQLite
     * （API < 24 的 SQLite 3.8.0 以下）不支持 ON CONFLICT DO UPDATE 语法，
     * 而这两条语句在所有目标平台上都可用。
     */
    override suspend fun applyRemoteUpsert(row: TodoRowDto): Either<TodoError, Unit> = dbCommand("应用远端待办失败") {
        val recurrenceRule = decodeRecurrenceRule(row.recurrenceFrequency, row.recurrenceInterval)
        val recurrenceFrequency = recurrenceRule.toFrequencyString()
        val recurrenceInterval = recurrenceRule.toIntervalLong()
        db.transaction {
            // 1. 本地已有且较旧 -> 覆盖（WHERE updated_at <= 远端 updated_at）
            db.todoDbQueries.updateTodoIfNewer(
                row.listId, row.title, row.note, row.dueDate, row.isCompleted, row.completedAt,
                row.isTrashed, row.trashedAt, row.parentId, row.sortPosition, row.flag,
                recurrenceFrequency, recurrenceInterval, row.createdAt, row.updatedAt, row.updatedBy, row.id, row.updatedAt,
            )
            // 2. 本地没有 -> 插入（WHERE NOT EXISTS 原子补插）
            db.todoDbQueries.insertTodoIfMissing(
                row.id, row.listId, row.title, row.note, row.dueDate, row.isCompleted, row.completedAt,
                row.isTrashed, row.trashedAt, row.parentId, row.sortPosition, row.flag,
                recurrenceFrequency, recurrenceInterval, row.createdAt, row.updatedAt, row.updatedBy, row.id,
            )
        }
    }

    /** 远端列表行 LWW，同 applyRemoteUpsert 的双语句实现。 */
    override suspend fun applyRemoteUpsertList(row: ListRowDto): Either<TodoError, Unit> = dbCommand("应用远端列表失败") {
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
    override suspend fun applyRemoteDelete(table: String, rowId: Long, updatedAt: Long): Either<TodoError, Unit> =
        dbCommand("应用远端删除失败") {
            db.transaction {
                when (table) {
                    "todo" -> db.todoDbQueries.deleteTodoIfOlder(rowId, updatedAt)
                    "reminder_list" -> applyRemoteListDelete(rowId, updatedAt)
                    else -> Unit
                }
            }
        }

    private fun Raise<TodoError>.applyRemoteListDelete(rowId: Long, updatedAt: Long) {
        val list = db.todoDbQueries.selectByIdForList(rowId).executeAsOneOrNull() ?: return
        if (list.updated_at > updatedAt || (list.name == "收件箱" && list.position == 0L)) return
        val inboxId = db.todoDbQueries.selectLists().executeAsList()
            .firstOrNull { it.name == "收件箱" && it.position == 0L }
            ?.id
            ?: raise(TodoError.InboxNotFound)
        db.todoDbQueries.moveTodosInList(inboxId, updatedAt, updatedAt, list.updated_by, rowId)
        db.todoDbQueries.deleteListIfOlder(rowId, updatedAt)
    }

    override suspend fun getSetting(key: String): Either<TodoError, String?> =
        dbCommand("读取设置失败") { db.todoDbQueries.getSetting(key).executeAsOneOrNull() }

    /**
     * 写设置（同步 token / 设备标识等）。update + insert-if-missing 双语句，
     * 与 applyRemoteUpsert 同理（老 SQLite 无 ON CONFLICT DO UPDATE）。
     */
    override suspend fun setSetting(key: String, value: String): Either<TodoError, Unit> = dbCommand("写入设置失败") {
        db.transaction {
            // 1. 已有键 -> 更新值
            db.todoDbQueries.updateSetting(value, key)
            // 2. 不存在 -> 原子插入（WHERE NOT EXISTS）
            db.todoDbQueries.insertSettingIfMissing(key, value, key)
        }
    }

    override suspend fun getSettings(): Either<TodoError, Map<String, String>> =
        dbCommand("读取设置失败") { db.todoDbQueries.selectAllSettings().executeAsList().associate { it.key to it.value_ } }
}
