package com.myapplication.shared.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.myapplication.shared.domain.error.TodoError
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TodoRepositoryImplTest {

    private fun newRepo(): TodoRepositoryImpl {
        val driver = JdbcSqliteDriver("jdbc:sqlite::memory:")
        TodoDb.Schema.create(driver)
        return TodoRepositoryImpl(TodoDb(driver))
    }

    private suspend fun TodoRepositoryImpl.inbox(): Long {
        ensureInbox()
        return observeLists().first().first().id
    }

    @Test
    fun ensureInboxCreatesDefaultList() = runTest {
        val repo = newRepo()
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
        assertEquals(1, items.size)
        assertEquals("交季度报告", items.first().title)
        assertEquals(inbox, items.first().listId)
        assertFalse(items.first().isCompleted)
    }

    @Test
    fun dueDateFallsIntoTodayQuery() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
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
        assertTrue(repo.trash(item.id).isRight())
        assertTrue(repo.observeAllActive().first().isEmpty())
        assertEquals(1, repo.observeTrashed().first().size)
        assertTrue(repo.restore(item.id).isRight())
        assertEquals(1, repo.observeAllActive().first().size)
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
        assertTrue(AddSubTaskUseCase(repo)(parent.id, "子任务").isRight())
        val children = repo.observeSubTasks(parent.id).first()
        assertEquals(1, children.size)
        assertEquals("子任务", children.first().title)
        assertEquals(parent.listId, children.first().listId)
        assertEquals(parent.id, children.first().parentId)
    }

    @Test
    fun searchMatchesTitleAndNote() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        assertTrue(repo.insertTodo(inbox, "买牛奶", "全脂的", null, null, false).isRight())
        assertTrue(repo.insertTodo(inbox, "写周报", "", null, null, false).isRight())
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
        assertTrue(repo.setCompleted(item.id, true).isRight())
        assertTrue(repo.observeAllActive().first().first().isCompleted)
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
        assertTrue(repo.setCompleted(item.id, true).isRight())
        assertNotNull(repo.observeTodo(item.id).first()?.completedAt)
        assertTrue(repo.setCompleted(item.id, false).isRight())
        assertNull(repo.observeTodo(item.id).first()?.completedAt)
    }

    @Test
    fun ensureInboxIsIdempotent() = runTest {
        val repo = newRepo()
        assertTrue(repo.ensureInbox().isRight())
        assertTrue(repo.ensureInbox().isRight())
        assertEquals(1, repo.observeLists().first().size)
    }

    @Test
    fun addSubTaskMissingParentIsNoOp() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        assertTrue(repo.insertTodo(inbox, "存在的", "", null, null, false).isRight())
        assertEquals(TodoError.ParentNotFound, AddSubTaskUseCase(repo)(999L, "孤儿").leftOrNull())
        assertEquals(1, repo.observeAllActive().first().size)
    }

    @Test
    fun deleteListTrashesTodosAndRemovesList() = runTest {
        val repo = newRepo()
        assertTrue(repo.addList("项目", "red").isRight())
        val listId = repo.observeLists().first().first { it.name == "项目" }.id
        assertTrue(repo.insertTodo(listId, "要清理的", "", null, null, false).isRight())
        assertTrue(repo.deleteList(listId).isRight())
        assertTrue(repo.observeLists().first().none { it.id == listId })
        assertTrue(repo.observeByList(listId).first().isEmpty())
        val trashed = repo.observeTrashed().first()
        assertEquals(1, trashed.size)
        assertEquals("要清理的", trashed.first().title)
    }

    @Test
    fun addListAppendsWithPosition() = runTest {
        val repo = newRepo()
        repo.inbox()
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
        assertFalse(item.flag)
        assertTrue(repo.setFlag(item.id, true).isRight())
        assertTrue(repo.observeTodo(item.id).first()?.flag == true)
        assertTrue(repo.setFlag(item.id, false).isRight())
        assertFalse(repo.observeTodo(item.id).first()?.flag ?: true)
    }

    @Test
    fun insertTodoWritesOutboxWithPayload() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        assertTrue(repo.insertTodo(inbox, "双写", "", null, null, false).isRight())
        val outbox = repo.readOutbox(10).getOrNull()!!
        assertEquals(2, outbox.size)
        val todoRow = outbox.last()
        assertEquals("todo", todoRow.table)
        assertEquals(SyncAction.UPSERT, todoRow.action)
        val dto = Json.decodeFromString<TodoRowDto>(todoRow.payload!!)
        assertEquals("双写", dto.title)
        assertEquals(inbox, dto.listId)
    }

    @Test
    fun deleteForeverWritesDeleteOp() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        repo.insertTodo(inbox, "要删的", "", null, null, false)
        val item = repo.observeAllActive().first().first()
        assertTrue(repo.deleteForever(item.id).isRight())
        val outbox = repo.readOutbox(10).getOrNull()!!
        assertEquals(SyncAction.DELETE, outbox.last().action)
        assertEquals(item.id, outbox.last().rowId)
    }

    @Test
    fun applyRemoteUpsertObeysLww() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        val fresh = TodoRowDto(1, inbox, "新", "", null, false, null, false, null, null, 0.0, false, 0, 900, "remote")
        assertTrue(repo.applyRemoteUpsert(fresh).isRight())
        val stale = fresh.copy(title = "旧", updatedAt = 800)
        assertTrue(repo.applyRemoteUpsert(stale).isRight())
        assertEquals("新", repo.observeTodo(1).first()?.title)
    }
}
