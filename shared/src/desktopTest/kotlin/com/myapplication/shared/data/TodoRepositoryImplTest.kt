package com.myapplication.shared.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        repo.ensureInbox()
        val lists = repo.observeLists().first()
        assertEquals(1, lists.size)
        assertEquals("收件箱", lists.first().name)
    }

    @Test
    fun addTodoStoresTitleAndList() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        repo.addTodo(inbox, "交季度报告", "", null, null)
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
        repo.addTodo(inbox, "今天的事", "", now.atStartOfDayIn(TimeZone.currentSystemDefault()).plus(1, DateTimeUnit.HOUR), null)
        repo.addTodo(inbox, "没日期的事", "", null, null)
        val today = repo.observeToday().first()
        assertEquals(1, today.size)
        assertEquals("今天的事", today.first().title)
    }

    @Test
    fun trashThenRestoreThenDeleteForever() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        repo.addTodo(inbox, "要扔的", "", null, null)
        val item = repo.observeAllActive().first().first()
        repo.trash(item.id)
        assertTrue(repo.observeAllActive().first().isEmpty())
        assertEquals(1, repo.observeTrashed().first().size)
        repo.restore(item.id)
        assertEquals(1, repo.observeAllActive().first().size)
        repo.trash(item.id)
        repo.deleteForever(item.id)
        assertTrue(repo.observeTrashed().first().isEmpty())
    }

    @Test
    fun subtaskLinksToParentList() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        repo.addTodo(inbox, "父任务", "", null, null)
        val parent = repo.observeAllActive().first().first()
        repo.addSubTask(parent.id, "子任务")
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
        repo.addTodo(inbox, "买牛奶", "全脂的", null, null)
        repo.addTodo(inbox, "写周报", "", null, null)
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
        repo.addTodo(inbox, "改状态", "", null, null)
        val item = repo.observeAllActive().first().first()
        repo.setCompleted(item.id, true)
        assertTrue(repo.observeAllActive().first().first().isCompleted)
        val due = Clock.System.now()
        repo.setDueDate(item.id, due)
        val after = repo.observeTodo(item.id).first()
        assertNotNull(after?.dueDate)
        repo.setDueDate(item.id, null)
        assertNull(repo.observeTodo(item.id).first()?.dueDate)
    }

    @Test
    fun observeScheduledIncludesDatedTodos() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        val due = Clock.System.now()
        repo.addTodo(inbox, "有日期的", "", due, null)
        repo.addTodo(inbox, "没日期的", "", null, null)
        val scheduled = repo.observeScheduled().first()
        assertEquals(1, scheduled.size)
        assertEquals("有日期的", scheduled.first().title)
    }

    @Test
    fun searchEscapesWildcards() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        repo.addTodo(inbox, "100%", "", null, null)
        repo.addTodo(inbox, "under_score", "", null, null)
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
        repo.addTodo(inbox, "改状态", "", null, null)
        val item = repo.observeAllActive().first().first()
        repo.setCompleted(item.id, true)
        assertNotNull(repo.observeTodo(item.id).first()?.completedAt)
        repo.setCompleted(item.id, false)
        assertNull(repo.observeTodo(item.id).first()?.completedAt)
    }

    @Test
    fun ensureInboxIsIdempotent() = runTest {
        val repo = newRepo()
        repo.ensureInbox()
        repo.ensureInbox()
        assertEquals(1, repo.observeLists().first().size)
    }

    @Test
    fun addSubTaskMissingParentIsNoOp() = runTest {
        val repo = newRepo()
        val inbox = repo.inbox()
        repo.addTodo(inbox, "存在的", "", null, null)
        repo.addSubTask(999L, "孤儿")
        assertEquals(1, repo.observeAllActive().first().size)
    }

    @Test
    fun deleteListTrashesTodosAndRemovesList() = runTest {
        val repo = newRepo()
        repo.addList("项目", "red")
        val listId = repo.observeLists().first().first { it.name == "项目" }.id
        repo.addTodo(listId, "要清理的", "", null, null)
        repo.deleteList(listId)
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
        repo.addList("甲", "blue")
        repo.addList("乙", "red")
        val lists = repo.observeLists().first()
        assertEquals(3, lists.size)
        assertEquals(listOf("收件箱", "甲", "乙"), lists.map { it.name })
        assertTrue(lists[1].position < lists[2].position)
    }
}
