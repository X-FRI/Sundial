package com.myapplication.shared.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.list.DeleteListPolicy
import com.myapplication.shared.domain.recurrence.RecurrenceRule
import com.myapplication.shared.domain.sync.ListRowDto
import com.myapplication.shared.domain.sync.SyncAction
import com.myapplication.shared.domain.sync.TodoRowDto
import com.myapplication.shared.domain.usecase.AddSubTaskUseCase
import kotlin.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TodoRepositoryImpl 的集成测试（真实 SQLite 内存库，desktop 专属）。
 *
 * 与 commonTest 的 FakeTodoRepository 单测互补：这里验证真实 SQL 行为，
 * 覆盖的契约：
 * - 默认列表：ensureInbox 首次建"收件箱"且幂等；
 * - 增删改查：插入/完成/旗标/日期/搜索/子任务继承列表/删除列表级联；
 * - 查询口径：Today 只含当天、Scheduled 只含有日期的、Trashed 软删除隔离；
 * - 搜索转义：% 与 _ 通配符按字面量匹配；
 * - 同步双写：本地写入/删除同时产生 outbox 行（UPSERT/DELETE + payload）；
 * - 远端应用：applyRemoteUpsert 遵循 LWW（updatedAt 大的覆盖小的）。
 */
class TodoRepositoryImplTest {

    // 每个用例一个全新的内存库，互不污染
    private fun newRepo(): TodoRepositoryImpl {
        val driver = JdbcSqliteDriver("jdbc:sqlite::memory:")
        TodoDb.Schema.create(driver)
        return TodoRepositoryImpl(TodoDb(driver))
    }

    // 确保收件箱存在并返回其 id
    private suspend fun TodoRepositoryImpl.inbox(): Long {
        ensureInbox()
        return observeLists().first().first().id
    }

    @Test
    fun ensureInboxCreatesDefaultList() = runTest {
        val repo = newRepo()
        // 空库：无列表 → ensureInbox 创建默认"收件箱"
        assertTrue(repo.observeLists().first().isEmpty())
        assertTrue(repo.ensureInbox().isRight())
        val lists = repo.observeLists().first()
        assertEquals(1, lists.size)
        assertEquals("收件箱", lists.first().name)
    }

    @Test
    fun addTodoStoresTitleAndList() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        assertTrue(repo.insertTodo(inbox, "交季度报告", "", null, null, false).isRight())
        val items = repo.observeAllActive().first()
        // 写入后可见、列表归属正确、默认未完成
        assertEquals(1, items.size)
        assertEquals("交季度报告", items.first().title)
        assertEquals(inbox, items.first().listId)
        assertFalse(items.first().isCompleted)
    }

    @Test
    fun dueDateFallsIntoTodayQuery() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        // 一条今天 1 点、一条无日期：Today 查询只命中前者
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        assertTrue(repo.insertTodo(inbox, "今天的事", "", now.atStartOfDayIn(TimeZone.currentSystemDefault()).plus(1, DateTimeUnit.HOUR), null, false).isRight())
        assertTrue(repo.insertTodo(inbox, "没日期的事", "", null, null, false).isRight())
        val today = repo.observeToday().first()
        assertEquals(1, today.size)
        assertEquals("今天的事", today.first().title)
    }

    @Test
    fun trashThenRestoreThenDeleteForever() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        assertTrue(repo.insertTodo(inbox, "要扔的", "", null, null, false).isRight())
        val item = repo.observeAllActive().first().first()
        // 软删除：移出活跃列表、进入 Trashed
        assertTrue(repo.trash(item.id).isRight())
        assertTrue(repo.observeAllActive().first().isEmpty())
        assertEquals(1, repo.observeTrashed().first().size)
        // 恢复：回到活跃列表
        assertTrue(repo.restore(item.id).isRight())
        assertEquals(1, repo.observeAllActive().first().size)
        // 彻底删除：Trashed 也清空
        assertTrue(repo.trash(item.id).isRight())
        assertTrue(repo.deleteForever(item.id).isRight())
        assertTrue(repo.observeTrashed().first().isEmpty())
    }

    @Test
    fun subtaskLinksToParentList() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        assertTrue(repo.insertTodo(inbox, "父任务", "", null, null, false).isRight())
        val parent = repo.observeAllActive().first().first()
        // 子任务自动继承父任务列表 + 记录 parentId
        assertTrue(AddSubTaskUseCase(repo)(parent.id, "子任务").isRight())
        val children = repo.observeSubTasks(parent.id).first()
        assertEquals(1, children.size)
        assertEquals("子任务", children.first().title)
        assertEquals(parent.listId, children.first().listId)
        assertEquals(parent.id, children.first().parentId)
    }

    @Test
    fun trashAndRestoreParentIncludesSubtasks() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        assertTrue(repo.insertTodo(inbox, "父任务", "", null, null, false).isRight())
        val parent = repo.observeAllActive().first().first()
        assertTrue(AddSubTaskUseCase(repo)(parent.id, "子任务").isRight())

        assertTrue(repo.trash(parent.id).isRight())
        assertTrue(repo.observeAllActive().first().isEmpty())
        assertEquals(setOf("父任务", "子任务"), repo.observeTrashed().first().map { it.title }.toSet())

        assertTrue(repo.restore(parent.id).isRight())
        val active = repo.observeAllActive().first()
        assertEquals(listOf("父任务"), active.filter { it.parentId == null }.map { it.title })
        assertEquals(listOf("子任务"), repo.observeSubTasks(parent.id).first().map { it.title })
    }

    @Test
    fun deleteForeverParentDeletesSubtasksAndWritesDeleteOps() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        assertTrue(repo.insertTodo(inbox, "父任务", "", null, null, false).isRight())
        val parent = repo.observeAllActive().first().first()
        assertTrue(AddSubTaskUseCase(repo)(parent.id, "子任务").isRight())
        val child = repo.observeSubTasks(parent.id).first().single()

        assertTrue(repo.deleteForever(parent.id).isRight())

        assertNull(repo.findById(parent.id).getOrNull())
        assertNull(repo.findById(child.id).getOrNull())
        val deleteRows = repo.readOutbox(20).getOrNull()!!.filter { it.action == SyncAction.DELETE }
        assertEquals(setOf(parent.id, child.id), deleteRows.map { it.rowId }.toSet())
    }

    @Test
    fun searchMatchesTitleAndNote() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        assertTrue(repo.insertTodo(inbox, "买牛奶", "全脂的", null, null, false).isRight())
        assertTrue(repo.insertTodo(inbox, "写周报", "", null, null, false).isRight())
        // 标题与备注都参与搜索
        val byTitle = repo.search("牛奶").first()
        assertEquals(1, byTitle.size)
        val byNote = repo.search("全脂").first()
        assertEquals(1, byNote.size)
        assertTrue(repo.search("不存在").first().isEmpty())
    }

    @Test
    fun setCompletedAndDueDate() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        assertTrue(repo.insertTodo(inbox, "改状态", "", null, null, false).isRight())
        val item = repo.observeAllActive().first().first()
        // 完成标记持久化
        assertTrue(repo.setCompleted(item.id, true).isRight())
        assertTrue(repo.observeAllActive().first().first().isCompleted)
        // 设置/清除到期时间
        val due = Clock.System.now()
        assertTrue(repo.setDueDate(item.id, due).isRight())
        val after = repo.observeTodo(item.id).first()
        assertNotNull(after?.dueDate)
        assertTrue(repo.setDueDate(item.id, null).isRight())
        assertNull(repo.observeTodo(item.id).first()?.dueDate)
    }

    @Test
    fun observeScheduledIncludesDatedTodos() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        val due = Clock.System.now()
        assertTrue(repo.insertTodo(inbox, "有日期的", "", due, null, false).isRight())
        assertTrue(repo.insertTodo(inbox, "没日期的", "", null, null, false).isRight())
        // Scheduled 只含有日期的条目
        val scheduled = repo.observeScheduled().first()
        assertEquals(1, scheduled.size)
        assertEquals("有日期的", scheduled.first().title)
    }

    @Test
    fun searchEscapesWildcards() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        assertTrue(repo.insertTodo(inbox, "100%", "", null, null, false).isRight())
        assertTrue(repo.insertTodo(inbox, "under_score", "", null, null, false).isRight())
        // % 与 _ 在 LIKE 中是通配符，必须转义后按字面量精确匹配
        val percent = repo.search("100%").first()
        assertEquals(1, percent.size)
        assertEquals("100%", percent.first().title)
        val underscore = repo.search("_").first()
        assertEquals(1, underscore.size)
        assertEquals("under_score", underscore.first().title)
    }

    @Test
    fun setCompletedFalseClearsCompletedAt() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        assertTrue(repo.insertTodo(inbox, "改状态", "", null, null, false).isRight())
        val item = repo.observeAllActive().first().first()
        // 完成时记录时间，取消完成时清空——保证"再次完成"时间戳正确
        assertTrue(repo.setCompleted(item.id, true).isRight())
        assertNotNull(repo.observeTodo(item.id).first()?.completedAt)
        assertTrue(repo.setCompleted(item.id, false).isRight())
        assertNull(repo.observeTodo(item.id).first()?.completedAt)
    }

    @Test
    fun ensureInboxIsIdempotent() = runTest {
        val repo = newRepo()
        // 重复调用不产生重复列表
        assertTrue(repo.ensureInbox().isRight())
        assertTrue(repo.ensureInbox().isRight())
        assertEquals(1, repo.observeLists().first().size)
    }

    @Test
    fun inboxCannotBeDeleted() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()

        val result = repo.deleteList(inbox, DeleteListPolicy.MoveTasksToInbox)

        assertTrue(result.isLeft())
        assertEquals(listOf("收件箱"), repo.observeLists().first().map { it.name })
    }

    @Test
    fun addSubTaskMissingParentIsNoOp() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        assertTrue(repo.insertTodo(inbox, "存在的", "", null, null, false).isRight())
        // 父任务不存在 → 业务错误且库内数据不变
        assertEquals(TodoError.ParentNotFound, AddSubTaskUseCase(repo)(999L, "孤儿").leftOrNull())
        assertEquals(1, repo.observeAllActive().first().size)
    }

    @Test
    fun deleteListMovesTasksToInboxByDefaultPolicy() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        assertTrue(repo.addList("项目", "red").isRight())
        val listId = repo.observeLists().first().first { it.name == "项目" }.id
        assertTrue(repo.insertTodo(listId, "A", "", null, null, false).isRight())
        assertTrue(repo.insertTodo(listId, "B", "", null, null, false).isRight())
        val trashed = repo.observeAllActive().first().single { it.title == "B" }
        assertTrue(repo.trash(trashed.id).isRight())
        val beforeDeleteSeq = repo.readOutbox(100).getOrNull()!!.maxOf { it.seq }
        // 默认策略：删列表时保留任务，将任务重新归属到收件箱，避免指向已删除列表。
        assertTrue(repo.deleteList(listId).isRight())

        assertTrue(repo.observeLists().first().none { it.id == listId })
        assertTrue(repo.observeByList(listId).first().isEmpty())
        val active = repo.observeAllActive().first()
        assertEquals(inbox, active.single { it.title == "A" }.listId)
        val trashedAfterDelete = repo.observeTrashed().first()
        assertEquals(inbox, trashedAfterDelete.single { it.title == "B" }.listId)
        assertTrue(trashedAfterDelete.none { it.title == "A" })

        val deleteOutbox = repo.readOutbox(100).getOrNull()!!.filter { it.seq > beforeDeleteSeq }
        assertEquals(listOf("todo", "todo", "reminder_list"), deleteOutbox.map { it.table })
        assertEquals(listOf(SyncAction.UPSERT, SyncAction.UPSERT, SyncAction.DELETE), deleteOutbox.map { it.action })
        val movedPayloads = deleteOutbox.take(2)
            .map { Json.decodeFromString<TodoRowDto>(it.payload!!) }
            .associateBy { it.title }
        assertEquals(inbox, movedPayloads.getValue("A").listId)
        assertFalse(movedPayloads.getValue("A").isTrashed)
        assertEquals(inbox, movedPayloads.getValue("B").listId)
        assertTrue(movedPayloads.getValue("B").isTrashed)
        assertEquals(listId, deleteOutbox.last().rowId)
    }

    @Test
    fun deleteListCanMoveTasksToTrashWithDangerPolicy() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        assertTrue(repo.addList("项目", "red").isRight())
        val listId = repo.observeLists().first().first { it.name == "项目" }.id
        assertTrue(repo.insertTodo(listId, "A", "", null, null, false).isRight())
        assertTrue(repo.insertTodo(listId, "B", "", null, null, false).isRight())
        val alreadyTrashed = repo.observeAllActive().first().single { it.title == "B" }
        assertTrue(repo.trash(alreadyTrashed.id).isRight())
        val beforeDeleteSeq = repo.readOutbox(100).getOrNull()!!.maxOf { it.seq }

        assertTrue(repo.deleteList(listId, DeleteListPolicy.MoveTasksToTrash).isRight())

        assertTrue(repo.observeLists().first().none { it.id == listId })
        assertTrue(repo.observeAllActive().first().none { it.title == "A" })
        val trashed = repo.observeTrashed().first()
        assertEquals(inbox, trashed.single { it.title == "A" }.listId)
        assertEquals(inbox, trashed.single { it.title == "B" }.listId)

        val deleteOutbox = repo.readOutbox(100).getOrNull()!!.filter { it.seq > beforeDeleteSeq }
        assertEquals(listOf("todo", "todo", "reminder_list"), deleteOutbox.map { it.table })
        assertEquals(listOf(SyncAction.UPSERT, SyncAction.UPSERT, SyncAction.DELETE), deleteOutbox.map { it.action })
        val movedPayloads = deleteOutbox.take(2)
            .map { Json.decodeFromString<TodoRowDto>(it.payload!!) }
            .associateBy { it.title }
        assertEquals(inbox, movedPayloads.getValue("A").listId)
        assertTrue(movedPayloads.getValue("A").isTrashed)
        assertEquals(inbox, movedPayloads.getValue("B").listId)
        assertTrue(movedPayloads.getValue("B").isTrashed)
    }

    @Test
    fun remoteListDeleteMovesReferencingTodosToInboxWithoutWritingOutbox() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        assertTrue(repo.addList("远端项目", "red").isRight())
        val listId = repo.observeLists().first().first { it.name == "远端项目" }.id
        assertTrue(repo.insertTodo(listId, "活跃", "", null, null, false).isRight())
        assertTrue(repo.insertTodo(listId, "已删除", "", null, null, false).isRight())
        val trashed = repo.observeAllActive().first().single { it.title == "已删除" }
        assertTrue(repo.trash(trashed.id).isRight())
        val outboxBeforeRemoteDelete = repo.readOutbox(100).getOrNull()!!

        assertTrue(repo.applyRemoteDelete("reminder_list", listId, Long.MAX_VALUE).isRight())

        assertTrue(repo.observeLists().first().none { it.id == listId })
        assertEquals(inbox, repo.observeAllActive().first().single { it.title == "活跃" }.listId)
        assertEquals(inbox, repo.observeTrashed().first().single { it.title == "已删除" }.listId)
        assertEquals(outboxBeforeRemoteDelete, repo.readOutbox(100).getOrNull()!!)
    }

    @Test
    fun remoteListDeleteDoesNotMakeNewerTodosAcceptStaleListReferences() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        val remoteListId = 42L
        val remoteTodoId = 77L
        assertTrue(
            repo.applyRemoteUpsertList(
                ListRowDto(
                    id = remoteListId,
                    name = "远端项目",
                    colorKey = "red",
                    position = 1,
                    createdAt = 1_000,
                    updatedAt = 1_000,
                    updatedBy = "remote-a",
                ),
            ).isRight(),
        )
        assertTrue(
            repo.applyRemoteUpsert(
                TodoRowDto(
                    id = remoteTodoId,
                    listId = remoteListId,
                    title = "较新的任务",
                    note = "",
                    dueDate = null,
                    isCompleted = false,
                    completedAt = null,
                    isTrashed = false,
                    trashedAt = null,
                    parentId = null,
                    sortPosition = 0.0,
                    flag = false,
                    createdAt = 1_000,
                    updatedAt = 2_000,
                    updatedBy = "remote-b",
                ),
            ).isRight(),
        )

        assertTrue(repo.applyRemoteDelete("reminder_list", remoteListId, 1_000).isRight())
        assertTrue(
            repo.applyRemoteUpsert(
                TodoRowDto(
                    id = remoteTodoId,
                    listId = remoteListId,
                    title = "陈旧任务",
                    note = "",
                    dueDate = null,
                    isCompleted = false,
                    completedAt = null,
                    isTrashed = false,
                    trashedAt = null,
                    parentId = null,
                    sortPosition = 0.0,
                    flag = false,
                    createdAt = 1_000,
                    updatedAt = 1_500,
                    updatedBy = "remote-c",
                ),
            ).isRight(),
        )

        val todo = repo.observeTodo(remoteTodoId).first()
        assertNotNull(todo)
        assertEquals(inbox, todo.listId)
        assertEquals("较新的任务", todo.title)
    }

    @Test
    fun updateListPersistsTrimmedNameAndWritesOutbox() = runTest {
        val repo = newRepo()
        repo.inbox()
        assertTrue(repo.addList("项目", "red").isRight())
        val list = repo.observeLists().first().first { it.name == "项目" }
        val beforeUpdateSeq = repo.readOutbox(100).getOrNull()!!.maxOf { it.seq }

        assertTrue(repo.updateList(list.id, "  新项目  ", " green ").isRight())

        val updated = repo.observeLists().first().first { it.id == list.id }
        assertEquals("新项目", updated.name)
        assertEquals("green", updated.colorKey)
        val updateOutbox = repo.readOutbox(100).getOrNull()!!.filter { it.seq > beforeUpdateSeq }
        assertEquals(1, updateOutbox.size)
        assertEquals("reminder_list", updateOutbox.single().table)
        assertEquals(SyncAction.UPSERT, updateOutbox.single().action)
        val payload = Json.decodeFromString<ListRowDto>(updateOutbox.single().payload!!)
        assertEquals("新项目", payload.name)
        assertEquals("green", payload.colorKey)
    }

    @Test
    fun updateListRejectsInvalidRequests() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        assertTrue(repo.addList("项目", "red").isRight())
        val project = repo.observeLists().first().first { it.name == "项目" }

        assertTrue(repo.updateList(project.id, "   ", "blue").isLeft())
        assertTrue(repo.updateList(inbox, "改名", "blue").isLeft())
        assertTrue(repo.updateList(999, "不存在", "blue").isLeft())
        assertEquals(listOf("收件箱", "项目"), repo.observeLists().first().map { it.name })
    }

    @Test
    fun addListAppendsWithPosition() = runTest {
        val repo = newRepo()
        repo.inbox()
        // 新列表追加到末尾，position 递增（保持侧栏顺序）
        assertTrue(repo.addList("甲", "blue").isRight())
        assertTrue(repo.addList("乙", "red").isRight())
        val lists = repo.observeLists().first()
        assertEquals(3, lists.size)
        assertEquals(listOf("收件箱", "甲", "乙"), lists.map { it.name })
        assertTrue(lists[1].position < lists[2].position)
    }

    @Test
    fun setFlagPersistsAndDefaults() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        assertTrue(repo.insertTodo(inbox, "带旗标", "", null, null, false).isRight())
        val item = repo.observeAllActive().first().first()
        // 默认无旗标；设置/清除均持久化
        assertFalse(item.flag)
        assertTrue(repo.setFlag(item.id, true).isRight())
        assertTrue(repo.observeTodo(item.id).first()?.flag == true)
        assertTrue(repo.setFlag(item.id, false).isRight())
        assertFalse(repo.observeTodo(item.id).first()?.flag ?: true)
    }

    @Test
    fun setRecurrencePersistsRuleOnTodo() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        assertTrue(repo.insertTodo(inbox, "重复任务", "", null, null, false).isRight())
        val item = repo.observeAllActive().first().single()

        assertTrue(repo.setRecurrence(item.id, RecurrenceRule.Daily(interval = 2)).isRight())

        assertEquals(RecurrenceRule.Daily(interval = 2), repo.observeTodo(item.id).first()?.recurrenceRule)
    }

    @Test
    fun setRecurrenceNullClearsRuleOnTodo() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        assertTrue(repo.insertTodo(inbox, "清除重复", "", null, null, false).isRight())
        val item = repo.observeAllActive().first().single()
        assertTrue(repo.setRecurrence(item.id, RecurrenceRule.Daily()).isRight())
        val beforeClearSeq = repo.readOutbox(100).getOrNull()!!.maxOf { it.seq }

        assertTrue(repo.setRecurrence(item.id, null).isRight())

        assertNull(repo.observeTodo(item.id).first()?.recurrenceRule)
        val clearPayload = repo.readOutbox(100).getOrNull()!!.single { it.seq > beforeClearSeq }.payload!!
        val clearJson = Json.parseToJsonElement(clearPayload).jsonObject
        assertTrue(clearJson.containsKey("recurrence_frequency"))
        assertTrue(clearJson.containsKey("recurrence_interval"))
        assertEquals(JsonNull, clearJson["recurrence_frequency"])
        assertEquals(JsonNull, clearJson["recurrence_interval"])
    }

    @Test
    fun setRecurrenceWritesOutboxPayload() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        assertTrue(repo.insertTodo(inbox, "同步重复", "", null, null, false).isRight())
        val item = repo.observeAllActive().first().single()
        val beforeUpdateSeq = repo.readOutbox(100).getOrNull()!!.maxOf { it.seq }

        assertTrue(repo.setRecurrence(item.id, RecurrenceRule.Weekly(interval = 3)).isRight())

        val updateRow = repo.readOutbox(100).getOrNull()!!.single { it.seq > beforeUpdateSeq }
        val dto = Json.decodeFromString<TodoRowDto>(updateRow.payload!!)
        assertEquals("weekly", dto.recurrence_frequency)
        assertEquals(3L, dto.recurrence_interval)
    }

    @Test
    fun applyRemoteUpsertPersistsRecurrenceFields() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()

        assertTrue(
            repo.applyRemoteUpsert(
                TodoRowDto(
                    id = 42,
                    listId = inbox,
                    title = "远端重复",
                    note = "",
                    dueDate = null,
                    isCompleted = false,
                    completedAt = null,
                    isTrashed = false,
                    trashedAt = null,
                    parentId = null,
                    sortPosition = 0.0,
                    flag = false,
                    createdAt = 100,
                    updatedAt = 200,
                    updatedBy = "remote",
                    recurrence_frequency = "monthly",
                    recurrence_interval = 2,
                ),
            ).isRight(),
        )

        assertEquals(RecurrenceRule.Monthly(interval = 2), repo.observeTodo(42).first()?.recurrenceRule)
    }

    @Test
    fun applyRemoteUpsertSanitizesInvalidRecurrenceFields() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        suspend fun upsertInvalid(id: Long, frequency: String?, interval: Long?) {
            assertTrue(
                repo.applyRemoteUpsert(
                    TodoRowDto(
                        id = id,
                        listId = inbox,
                        title = "非法重复 $id",
                        note = "",
                        dueDate = null,
                        isCompleted = false,
                        completedAt = null,
                        isTrashed = false,
                        trashedAt = null,
                        parentId = null,
                        sortPosition = 0.0,
                        flag = false,
                        createdAt = 100,
                        updatedAt = 200 + id,
                        updatedBy = "remote",
                        recurrence_frequency = frequency,
                        recurrence_interval = interval,
                    ),
                ).isRight(),
            )
        }

        upsertInvalid(51, "yearly", 1)
        upsertInvalid(52, "daily", 0)
        upsertInvalid(53, "weekly", Int.MAX_VALUE.toLong() + 1)

        assertNull(repo.observeTodo(51).first()?.recurrenceRule)
        assertNull(repo.observeTodo(52).first()?.recurrenceRule)
        assertNull(repo.observeTodo(53).first()?.recurrenceRule)
    }

    @Test
    fun insertTodoWritesOutboxWithPayload() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        assertTrue(repo.insertTodo(inbox, "双写", "", null, null, false).isRight())
        // 本地写入同步产生 outbox 行：ensureInbox 1 行 + 插入 1 行
        val outbox = repo.readOutbox(10).getOrNull()!!
        assertEquals(2, outbox.size)
        val todoRow = outbox.last()
        assertEquals("todo", todoRow.table)
        assertEquals(SyncAction.UPSERT, todoRow.action)
        // payload 可反序列化为完整 DTO（远端据此重建数据）
        val dto = Json.decodeFromString<TodoRowDto>(todoRow.payload!!)
        assertEquals("双写", dto.title)
        assertEquals(inbox, dto.listId)
    }

    @Test
    fun insertTodoWritesRecurrenceToOutboxPayload() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()

        assertTrue(repo.insertTodo(inbox, "每三周", "", null, null, false, RecurrenceRule.Weekly(3)).isRight())

        val dto = Json.decodeFromString<TodoRowDto>(repo.readOutbox(10).getOrNull()!!.last().payload!!)
        assertEquals("weekly", dto.recurrence_frequency)
        assertEquals(3, dto.recurrence_interval)
    }

    @Test
    fun readOutboxUsesSeqWaterlineRatherThanStorageId() = runTest {
        val repo = newRepo()
        repo.inbox()
        assertTrue(repo.clearOutbox(1).isRight())

        assertTrue(repo.addList("项目", "red").isRight())

        val row = repo.readOutbox(10).getOrNull()!!.single()
        assertEquals(1, row.seq)
    }

    @Test
    fun deleteForeverWritesDeleteOp() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        repo.insertTodo(inbox, "要删的", "", null, null, false)
        val item = repo.observeAllActive().first().first()
        // 彻底删除 → outbox 追加 DELETE 操作行
        assertTrue(repo.deleteForever(item.id).isRight())
        val outbox = repo.readOutbox(10).getOrNull()!!
        assertEquals(SyncAction.DELETE, outbox.last().action)
        assertEquals(item.id, outbox.last().rowId)
    }

    @Test
    fun applyRemoteUpsertObeysLww() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        // LWW：updatedAt 更大的一方胜出；先写新（900）再写旧（800）→ 保持"新"
        val fresh = TodoRowDto(
            id = 1,
            listId = inbox,
            title = "新",
            note = "",
            dueDate = null,
            isCompleted = false,
            completedAt = null,
            isTrashed = false,
            trashedAt = null,
            parentId = null,
            sortPosition = 0.0,
            flag = false,
            createdAt = 0,
            updatedAt = 900,
            updatedBy = "remote",
        )
        assertTrue(repo.applyRemoteUpsert(fresh).isRight())
        val stale = fresh.copy(title = "旧", updatedAt = 800)
        assertTrue(repo.applyRemoteUpsert(stale).isRight())
        assertEquals("新", repo.observeTodo(1).first()?.title)
    }
}
