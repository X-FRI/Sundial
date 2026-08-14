package com.myapplication.shared.domain.error

/**
 * 待办领域层的统一错误类型。
 *
 * 设计要点：
 * - sealed interface 保证穷尽匹配（when 无需 else）；
 * - 校验类错误（EmptyTitle / ParentNotFound / InboxNotFound）不携带数据，纯粹是业务状态；
 * - Persistence 携带底层数据库错误信息，供 UI 展示与同步层转为 Transport 错误。
 */
sealed interface TodoError {
    data object EmptyTitle : TodoError

    data object ParentNotFound : TodoError

    data object InboxNotFound : TodoError

    data class Persistence(
        val message: String,
    ) : TodoError
}
