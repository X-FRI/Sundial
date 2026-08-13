package com.myapplication.shared.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.model.TodoItem
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.domain.repository.TodoRepository
import com.myapplication.shared.domain.usecase.AddSubTaskUseCase
import com.myapplication.shared.ui.effects.launchTodoEffect
import com.myapplication.shared.util.todayDate
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * 详情页 ViewModel：以 todoId 为作用域，负责单个待办的读写与子任务管理。
 *
 * 设计要点：
 * - 由 DetailContent 以 `viewModel(key = "detail-$todoId")` 按 id 隔离实例，
 *   保证每个详情页只观察自己那一条数据；
 * - 标题/备注/日期/列表/子任务等修改全部走「写后即忘」命令（repository.setXxx），
 *   界面数据靠 [todo]/[subtasks] 的 Flow 自动回流刷新，无需手动同步本地状态；
 * - 与 MainViewModel 相同：命令失败统一进 [lastError]，由详情页弹窗展示。
 */
class DetailViewModel(
    private val repository: TodoRepository,
    private val addSubTask: AddSubTaskUseCase,
    private val todoId: Long,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    /** 当前待办；被删除或不存在时为 null（UI 显示「待办不存在或已删除」）。 */
    val todo: StateFlow<TodoItem?> = repository.observeTodo(todoId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 该待办下的子任务列表（按 parentId 过滤）。 */
    val subtasks: StateFlow<List<TodoItem>> = repository.observeSubTasks(todoId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 全部自定义列表，供「移到列表」对话框渲染。 */
    val lists: StateFlow<List<TodoList>> = repository.observeLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 命令失败通道，由 DetailContent 订阅弹窗。 */
    val lastError = MutableStateFlow<TodoError?>(null)

    fun dismissError() {
        lastError.value = null
    }

    /** 标题：输入框每次 onValueChange 直接落库（写后即忘）。 */
    fun setTitle(title: String) {
        launchTodoEffect(lastError) { repository.setTitle(todoId, title) }
    }

    /** 备注：与标题相同，随输入实时写入。 */
    fun setNote(note: String) {
        launchTodoEffect(lastError) { repository.setNote(todoId, note) }
    }

    /** 设置截止时间；传 null 表示清除日期。 */
    fun setDueDate(due: LocalDateTime?) {
        launchTodoEffect(lastError) { repository.setDueDate(todoId, due?.toInstant(timeZone)) }
    }

    /**
     * 仅修改时间部分：以现有截止日期为基准拼出新 LocalDateTime；
     * 若原来没有日期，则以「今天」为基准。未加载到待办（todo == null）时直接忽略。
     */
    fun setTime(hour: Int, minute: Int) {
        val current = todo.value ?: return
        val base = current.dueDate
            ?.toLocalDateTime(timeZone)
            ?: LocalDateTime(todayDate(clock, timeZone), LocalTime(hour, minute))
        val ldt = LocalDateTime(base.date, LocalTime(hour, minute))
        launchTodoEffect(lastError) { repository.setDueDate(todoId, ldt.toInstant(timeZone)) }
    }

    /**
     * 清除时间（保留日期）：日期选择器中点「清除时间」时调用。
     * 约定：00:00 表示「无时间」，所以把日期拼 00:00 写回。
     */
    fun setTimeNull() {
        val current = todo.value ?: return
        if (current.dueDate == null) return
        val ldt = current.dueDate.toLocalDateTime(timeZone)
        val noTime = LocalDateTime(ldt.date, LocalTime(0, 0))
        launchTodoEffect(lastError) { repository.setDueDate(todoId, noTime.toInstant(timeZone)) }
    }

    /** 把待办移到另一个列表。 */
    fun moveToList(listId: Long) {
        launchTodoEffect(lastError) { repository.moveToList(todoId, listId) }
    }

    /** 新增子任务（usecase 内部处理 parentId 关联）。 */
    fun addSubTask(title: String) {
        launchTodoEffect(lastError) { addSubTask(todoId, title) }
    }

    /** 勾选/取消子任务：子任务本身也是 TodoItem，复用同一写接口。 */
    fun toggleSubTask(item: TodoItem) {
        launchTodoEffect(lastError) { repository.setCompleted(item.id, !item.isCompleted) }
    }

    /** 更新子任务标题：子任务本身也是 TodoItem，按子任务 id 写标题。 */
    fun setSubTaskTitle(item: TodoItem, title: String) {
        launchTodoEffect(lastError) { repository.setTitle(item.id, title) }
    }

    /** 删除子任务（软删除，进垃圾箱）。 */
    fun trashSubTask(item: TodoItem) {
        launchTodoEffect(lastError) { repository.trash(item.id) }
    }
}
