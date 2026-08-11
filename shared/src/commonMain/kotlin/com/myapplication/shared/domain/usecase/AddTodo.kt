package com.myapplication.shared.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.repository.TodoRepository
import kotlinx.datetime.Instant

/** 新增待办的输入参数：listId 与 parentId 至少提供一个（都为空时落入收件箱）。 */
data class AddTodoInput(
    val listId: Long?,
    val parentId: Long?,
    val title: String,
    val note: String,
    val dueDate: Instant?,
    val flag: Boolean,
)

/**
 * 新增待办用例。
 *
 * 目标列表解析优先级：子任务 > 显式列表 > 收件箱（惰性创建）。
 */
class AddTodoUseCase(private val repository: TodoRepository) {

    suspend operator fun invoke(input: AddTodoInput): Either<TodoError, Unit> = either {
        // 1. 校验标题非空
        ensure(input.title.isNotBlank()) { TodoError.EmptyTitle }
        // 2. 决定目标列表：子任务挂父任务的列表；无父任务时用显式 listId；都缺省则落到收件箱
        val targetListId = when {
            input.parentId != null ->
                repository.findById(input.parentId).bind()?.listId
                    ?: raise(TodoError.ParentNotFound)
            input.listId != null -> input.listId
            else -> repository.ensureInbox().bind()
        }
        // 3. 插入待办（标题/备注去首尾空格）
        repository.insertTodo(
            listId = targetListId,
            title = input.title.trim(),
            note = input.note.trim(),
            dueDate = input.dueDate,
            parentId = input.parentId,
            flag = input.flag,
        ).bind()
    }
}
