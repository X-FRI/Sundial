package com.myapplication.shared.domain.repository

import arrow.core.Either
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.list.DeleteListPolicy
import com.myapplication.shared.domain.list.ListStats
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.domain.recurrence.RecurrenceRule
import com.myapplication.shared.domain.sync.ListRowDto
import com.myapplication.shared.domain.sync.SyncRow
import com.myapplication.shared.domain.sync.TodoRowDto
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

/** 查询端口：数据流保持 [Flow]，不包装成 Either。 */
interface TodoQueries {
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

    fun observeListStats(listId: Long): Flow<ListStats>

    suspend fun findById(id: Long): Either<TodoError, TodoItem?>

    suspend fun findByIdActive(id: Long): Either<TodoError, TodoItem?>
}

/** 待办写命令端口：类型化错误，纯 Effect。 */
interface TodoCommands {
    suspend fun ensureInbox(): Either<TodoError, Long>

    suspend fun insertTodo(
        listId: Long,
        title: String,
        note: String,
        dueDate: Instant?,
        parentId: Long?,
        flag: Boolean,
        recurrenceRule: RecurrenceRule? = null,
    ): Either<TodoError, Unit>

    suspend fun setCompleted(
        id: Long,
        completed: Boolean,
    ): Either<TodoError, Unit>

    suspend fun completeRecurringTodo(id: Long): Either<TodoError, Unit>

    suspend fun setFlag(
        id: Long,
        flag: Boolean,
    ): Either<TodoError, Unit>

    suspend fun setTitle(
        id: Long,
        title: String,
    ): Either<TodoError, Unit>

    suspend fun setNote(
        id: Long,
        note: String,
    ): Either<TodoError, Unit>

    suspend fun setDueDate(
        id: Long,
        dueDate: Instant?,
    ): Either<TodoError, Unit>

    suspend fun setRecurrence(
        id: Long,
        rule: RecurrenceRule?,
    ): Either<TodoError, Unit>

    suspend fun moveToList(
        id: Long,
        listId: Long,
    ): Either<TodoError, Unit>

    suspend fun trash(id: Long): Either<TodoError, Unit>

    suspend fun restore(id: Long): Either<TodoError, Unit>

    suspend fun deleteForever(id: Long): Either<TodoError, Unit>
}

/** 清单写命令端口。 */
interface ListCommands {
    suspend fun addList(
        name: String,
        colorKey: String,
    ): Either<TodoError, Unit>

    suspend fun updateList(
        listId: Long,
        name: String,
        colorKey: String,
    ): Either<TodoError, Unit>

    suspend fun deleteList(
        listId: Long,
        policy: DeleteListPolicy = DeleteListPolicy.MoveTasksToInbox,
    ): Either<TodoError, Unit>
}

/** 同步存储端口：由 SyncCoordinator 驱动，不写 outbox（防 ping-pong）。 */
interface SyncStore {
    /**
     * 读取 outbox 中待推送的行，按 seq 升序，最多 [limit] 条。
     * 返回的 [SyncRow] 由 coordinator 推送后按 seq 水位线清理（见 clearOutbox）。
     */
    suspend fun readOutbox(limit: Int): Either<TodoError, List<SyncRow>>

    /**
     * 删除 seq <= [upToSeq] 的 outbox 行——即确认这些行已成功推送（水位线清除）。
     */
    suspend fun clearOutbox(upToSeq: Long): Either<TodoError, Unit>

    /** 待推送条数，用于同步状态卡片展示 pendingCount。 */
    fun observeOutboxCount(): Flow<Int>

    /**
     * 应用远端推送的待办行（LWW：本地行不更新，不写 outbox）。
     * 见 TodoRepositoryImpl.applyRemoteUpsert 的双语句 LWW 说明。
     */
    suspend fun applyRemoteUpsert(row: TodoRowDto): Either<TodoError, Unit>

    /** 应用远端推送的列表行，语义同 applyRemoteUpsert。 */
    suspend fun applyRemoteUpsertList(row: ListRowDto): Either<TodoError, Unit>

    /**
     * 应用远端删除：仅当本地行 updated_at <= [updatedAt] 才删（LWW），
     * 防止旧设备的删除吞掉本机更新。
     */
    suspend fun applyRemoteDelete(
        table: String,
        rowId: Long,
        updatedAt: Long,
    ): Either<TodoError, Unit>
}

/** 本地设置存储端口。 */
interface SettingsStore {
    suspend fun getSetting(key: String): Either<TodoError, String?>

    /** 写本地设置（同步 token 等）。setSetting 不经过 outbox。 */
    suspend fun setSetting(
        key: String,
        value: String,
    ): Either<TodoError, Unit>

    suspend fun getSettings(): Either<TodoError, Map<String, String>>
}

/**
 * 待办仓库兼容聚合接口：UI 与同步引擎访问数据的既有入口。
 *
 * 设计要点：
 * - 查询用 [Flow] 实现响应式（SQLDelight asFlow 推送变更），不包装成 Either；
 * - 命令返回 Either<TodoError, Unit>，校验/持久化失败走 Left；
 * - 所有本地写命令都会在事务内「更新行 + 写 outbox」，供同步引擎推送远端；
 *   而 applyRemote* 系列（远端应用）刻意不写 outbox，防止本机改动又被推回远端
 *   造成 ping-pong 循环。
 */
interface TodoRepository :
    TodoQueries,
    TodoCommands,
    ListCommands,
    SyncStore,
    SettingsStore
