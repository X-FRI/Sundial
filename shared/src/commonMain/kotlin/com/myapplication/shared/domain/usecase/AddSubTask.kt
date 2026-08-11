package com.myapplication.shared.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.repository.TodoRepository

/**
 * 新增子任务用例。
 *
 * 关键点：子任务不直接接收 listId，而是由父任务推导——
 * 子任务必须挂在父任务所在列表（parentId -> parent.listId），
 * 保证父子同列表的不变量。
 */
class AddSubTaskUseCase(private val repository: TodoRepository) {

    suspend operator fun invoke(parentId: Long, title: String): Either<TodoError, Unit> = either {
        // 1. 校验标题非空
        ensure(title.isNotBlank()) { TodoError.EmptyTitle }
        // 2. 校验父任务存在，并取其 listId 作为子任务的归属列表
        val parent = repository.findById(parentId).bind()
            ?: raise(TodoError.ParentNotFound)
        // 3. 插入子任务（标题去空格；无备注、无到期日、不标记）
        repository.insertTodo(parent.listId, title.trim(), "", null, parentId, false).bind()
    }
}
