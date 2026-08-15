package com.myapplication.shared.domain.settings

import com.myapplication.shared.domain.error.TodoError

sealed interface SettingsError {
    data object MissingSupabaseConfig : SettingsError

    data class Persistence(
        val message: String,
    ) : SettingsError
}

fun TodoError.toSettingsError(): SettingsError = SettingsError.Persistence((this as? TodoError.Persistence)?.message ?: "设置保存失败")
