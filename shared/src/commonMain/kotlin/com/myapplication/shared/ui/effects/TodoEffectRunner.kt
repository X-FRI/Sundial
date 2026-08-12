package com.myapplication.shared.ui.effects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.Either
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.effects.catchPersistence
import com.myapplication.shared.effects.runTodoEffect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * UI 层命令 effect launcher。
 *
 * 领域/数据层把写命令建模为 `suspend () -> Either<TodoError, A>`；
 * ViewModel 只负责触发命令和暴露错误状态，不在每个方法里重复 launch/onLeft 样板。
 */
fun <A> ViewModel.launchTodoEffect(
    lastError: MutableStateFlow<TodoError?>,
    effect: suspend () -> Either<TodoError, A>,
) {
    viewModelScope.launch {
        val result = runTodoEffect {
            catchPersistence("命令执行失败") {
                effect()
            }.bind()
        }
        when (result) {
            is Either.Left -> lastError.value = result.value
            is Either.Right -> Unit
        }
    }
}
