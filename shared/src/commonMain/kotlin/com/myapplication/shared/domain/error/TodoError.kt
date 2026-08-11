package com.myapplication.shared.domain.error

sealed interface TodoError {
    data object EmptyTitle : TodoError
    data object ParentNotFound : TodoError
    data object InboxNotFound : TodoError
    data class Persistence(val message: String) : TodoError
}
