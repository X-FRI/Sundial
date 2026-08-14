package com.myapplication.shared.ui

import com.myapplication.shared.domain.error.TodoError

/**
 * 错误到中文文案的映射：所有命令失败的 Either 左值最终都经此转为用户可读提示。
 *
 * 约定：这是 UI 层唯一的错误文案来源（Persistence 是兜底文案），
 * 具体到原因的枚举（EmptyTitle / ParentNotFound / InboxNotFound）给出精准提示。
 */
fun TodoError.uiMessage(): String =
    when (this) {
        TodoError.EmptyTitle -> "标题不能为空"
        TodoError.ParentNotFound -> "父任务不存在"
        TodoError.InboxNotFound -> "收件箱初始化失败"
        is TodoError.Persistence -> "操作失败，请重试"
    }
