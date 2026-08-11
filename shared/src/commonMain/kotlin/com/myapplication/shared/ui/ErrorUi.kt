package com.myapplication.shared.ui

import com.myapplication.shared.domain.error.TodoError

fun TodoError.uiMessage(): String = when (this) {
    TodoError.EmptyTitle -> "标题不能为空"
    TodoError.ParentNotFound -> "父任务不存在"
    TodoError.InboxNotFound -> "收件箱初始化失败"
    is TodoError.Persistence -> "操作失败，请重试"
}
