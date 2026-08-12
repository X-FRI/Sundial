package com.myapplication.shared.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myapplication.shared.di.AppGraph
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemBadge
import com.myapplication.shared.ui.components.RemButton
import com.myapplication.shared.ui.components.RemButtonVariant
import com.myapplication.shared.ui.components.RemCheckbox
import com.myapplication.shared.ui.components.RemDatePicker
import com.myapplication.shared.ui.components.RemDialog
import com.myapplication.shared.ui.components.RemEmptyState
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.components.RemIconButton
import com.myapplication.shared.ui.components.RemTextField
import com.myapplication.shared.ui.components.rememberHoverBackground
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.theme.ListColorOf
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemSpacing
import com.myapplication.shared.ui.theme.RemType
import com.myapplication.shared.ui.uiMessage
import com.myapplication.shared.util.formatDueDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * 待办详情内容：编辑标题/备注/日期/旗标/所属列表 + 子任务管理。
 *
 * 数据流要点：
 * - 标题与备注是「本地乐观状态 + 实时写库」：输入框的值存在本地的
 *   [titleText]/[noteText]，每次 onValueChange 同时回调 detailVm.setXxx 落库；
 *   用 `remember(currentId)` 作为 key——切换待办时整个状态组随 currentId 重建，
 *   从新待办数据重新初始化，避免显示上一个待办的残留内容；
 * - 编辑字段通过 repository 的 Flow 回流更新（详情页数据权威在 DetailViewModel）；
 * - 底部操作：移到列表（弹窗选择）、移到垃圾箱（软删 + 返回主列表）。
 * - 关闭按钮仅在 [showCloseButton] 为 true 时渲染（窄屏抽屉/桌面 inspector 可隐藏）。
 */
@Composable
fun DetailContent(
    mainVm: MainViewModel,
    graph: AppGraph,
    todoId: Long,
    modifier: Modifier = Modifier,
    showCloseButton: Boolean,
) {
    // 按 todoId 隔离 ViewModel：每个待办一个实例，防止切换详情时串数据。
    val detailVm: DetailViewModel = viewModel(key = "detail-$todoId") {
        DetailViewModel(graph.repository, graph.addSubTask, todoId)
    }
    val colors = LocalRemColors.current
    val todo by detailVm.todo.collectAsState()
    val subtasks by detailVm.subtasks.collectAsState()
    val lists by detailVm.lists.collectAsState()
    val current = todo
    val currentId = current?.id
    // 本地编辑状态：key 绑定 currentId，待办切换/删除时整体重置。
    var titleText by remember(currentId) { mutableStateOf(current?.title ?: "") }
    var noteText by remember(currentId) { mutableStateOf(current?.note ?: "") }
    var newSub by remember(currentId) { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showListDialog by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = RemSpacing.s16, horizontal = 14.dp),
    ) {
        // 待办不存在（已删除或尚未加载）：显示空态并直接结束组合。
        if (current == null) {
            RemEmptyState("待办不存在或已删除")
            return@Column
        }
        // 标题行：完成勾选 + 标题输入框 + 截止日期徽标 + 关闭按钮。
        Row(verticalAlignment = Alignment.CenterVertically) {
            RemCheckbox(current.isCompleted, { mainVm.toggleCompleted(current) })
            Spacer(Modifier.width(10.dp))
            RemTextField(
                value = titleText,
                onValueChange = {
                    titleText = it
                    detailVm.setTitle(it)
                },
                style = RemType.text16.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f),
            )
            // 已有截止日期时显示可点击的日期徽标（点击重新打开日期选择器）。
            if (current.dueDate != null) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { showDatePicker = true },
                ) {
                    RemBadge(
                        label = formatDueDate(current.dueDate),
                        color = colors.warning,
                        monospace = true,
                    )
                }
            }
            if (showCloseButton) {
                RemIconButton(IconName.Close, "关闭详情", onClick = mainVm::back, size = 16.dp)
            }
        }
        // 已完成时间提示（仅已完成且记录了完成时间时显示）。
        if (current.isCompleted && current.completedAt != null) {
            Spacer(Modifier.height(4.dp))
            androidx.compose.foundation.text.BasicText(
                "已完成 ${formatDueDate(current.completedAt)}",
                style = RemType.text12.copy(color = colors.textLow),
            )
        }
        Spacer(Modifier.height(10.dp))
        // 备注编辑区：独立圆角容器内的多行输入。
        Box(
            Modifier
                .fillMaxWidth()
                .background(colors.inputBg, RoundedCornerShape(RemRadii.r2))
                .padding(4.dp),
        ) {
            RemTextField(
                value = noteText,
                onValueChange = {
                    noteText = it
                    detailVm.setNote(it)
                },
                placeholder = "备注…",
                singleLine = false,
                minLines = 3,
                style = RemType.text12,
                bordered = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(4.dp))

        // 「日期」行：整行可点击打开日期选择器；有日期时显示徽标 + 清除按钮。
        val dateInteraction = remember { MutableInteractionSource() }
        val dateBg = rememberHoverBackground(dateInteraction)
        Row(
            Modifier
                .fillMaxWidth()
                .background(dateBg)
                .clickable(
                    interactionSource = dateInteraction,
                    indication = null,
                ) { showDatePicker = true }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemIcon(IconName.Calendar, colors.textLow, Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            androidx.compose.foundation.text.BasicText("日期", style = RemType.text12.copy(color = colors.textNormal))
            Spacer(Modifier.weight(1f))
            if (current.dueDate != null) {
                RemBadge(
                    label = formatDueDate(current.dueDate),
                    color = colors.warning,
                    monospace = true,
                    icon = { RemIcon(IconName.Calendar, colors.warning, Modifier.size(10.dp)) },
                )
                Spacer(Modifier.width(8.dp))
                // 清除日期：直接调 setDueDate(null) 落库。
                RemButton("清除", onClick = { detailVm.setDueDate(null) })
            } else {
                androidx.compose.foundation.text.BasicText("无", style = RemType.text12.copy(color = colors.textLow))
            }
        }

        // 「旗标」行：整行点击切换标记状态（写回 mainVm，共享列表页的旗标流）。
        val flagInteraction = remember { MutableInteractionSource() }
        val flagBg = rememberHoverBackground(flagInteraction)
        Row(
            Modifier
                .fillMaxWidth()
                .background(flagBg)
                .clickable(
                    interactionSource = flagInteraction,
                    indication = null,
                ) { mainVm.toggleFlag(current) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.text.BasicText("旗标", style = RemType.text12.copy(color = colors.textNormal))
            Spacer(Modifier.weight(1f))
            RemIcon(IconName.Flag, if (current.flag) colors.warning else colors.textLow, Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            androidx.compose.foundation.text.BasicText(if (current.flag) "已标记" else "未标记", style = RemType.text12.copy(color = if (current.flag) colors.warning else colors.textLow))
        }

        // 「列表」行：显示当前所属列表（带颜色圆点），点击打开列表选择弹窗。
        val currentList = lists.firstOrNull { it.id == current.listId }
        val listInteraction = remember { MutableInteractionSource() }
        val listBg = rememberHoverBackground(listInteraction)
        Row(
            Modifier
                .fillMaxWidth()
                .background(listBg)
                .clickable(
                    interactionSource = listInteraction,
                    indication = null,
                ) { showListDialog = true }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemIcon(IconName.Tray, colors.textLow, Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            androidx.compose.foundation.text.BasicText("列表", style = RemType.text12.copy(color = colors.textNormal))
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(10.dp).background(ListColorOf[currentList?.colorKey] ?: Color.Gray, CircleShape))
            Spacer(Modifier.width(6.dp))
            androidx.compose.foundation.text.BasicText(
                currentList?.name ?: "未知列表",
                style = RemType.text12.copy(color = colors.textHigh),
            )
        }
        Spacer(Modifier.height(16.dp))

        // 子任务区块：列表（勾选/删除）+ 底部添加输入框（回车或点「添加」提交）。
        Row(verticalAlignment = Alignment.CenterVertically) {
            RemIcon(IconName.ChevronDown, colors.textLow, Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            androidx.compose.foundation.text.BasicText("子任务", style = RemType.label12.copy(color = colors.textLow))
        }
        Spacer(Modifier.height(6.dp))
        subtasks.forEach { sub ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                RemCheckbox(sub.isCompleted, { detailVm.toggleSubTask(sub) }, size = 12.dp)
                Spacer(Modifier.width(8.dp))
                androidx.compose.foundation.text.BasicText(
                    sub.title,
                    style = RemType.text14.copy(
                        color = if (sub.isCompleted) colors.textLow else colors.textHigh,
                        textDecoration = if (sub.isCompleted) TextDecoration.LineThrough else null,
                    ),
                    modifier = Modifier.weight(1f),
                )
                RemIconButton(IconName.Trash, "删除子任务", onClick = { detailVm.trashSubTask(sub) }, size = 14.dp)
            }
        }
        Spacer(Modifier.height(6.dp))
        // 添加子任务输入：提交后清空输入框（标题为空时由 usecase 层拒绝）。
        RemTextField(
            value = newSub,
            onValueChange = { newSub = it },
            placeholder = "添加子任务…",
            onEnter = {
                detailVm.addSubTask(newSub)
                newSub = ""
            },
            trailing = "添加" to {
                detailVm.addSubTask(newSub)
                newSub = ""
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        // 页脚：创建时间 + 操作按钮区。
        androidx.compose.foundation.text.BasicText(
            "创建于 ${current.createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).let { "${it.monthNumber} 月 ${it.dayOfMonth} 日" }}",
            style = RemType.text12.copy(color = colors.textLow),
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            RemButton("移到列表", onClick = { showListDialog = true }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            if (current.isTrashed) {
                // 已删除待办：恢复后立即返回主列表。
                RemButton(
                    "恢复",
                    onClick = {
                        mainVm.restore(current)
                        mainVm.back()
                    },
                    modifier = Modifier.weight(1f),
                )
            } else {
                // 移到垃圾箱：软删后立即返回主列表（窄屏下的常规退场路径）。
                RemButton(
                    "移到垃圾箱",
                    onClick = {
                        mainVm.trash(current)
                        mainVm.back()
                    },
                    variant = RemButtonVariant.Danger,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    // 日期选择器：初始值取当前日期与时间；仅选择日期时保留原时间，
    // 无原时间则默认 9:00；onPickTime(h=-1,m=-1) 表示「清除时间」。
    if (showDatePicker) {
        RemDatePicker(
            initialDate = current?.dueDate?.toLocalDateTime(TimeZone.currentSystemDefault())?.date,
            initialTime = current?.dueDate?.toLocalDateTime(TimeZone.currentSystemDefault())?.time?.takeIf { !(it.hour == 0 && it.minute == 0) },
            onPick = { date ->
                val time = current?.dueDate
                    ?.toLocalDateTime(TimeZone.currentSystemDefault())?.time
                    ?: LocalTime(9, 0)
                detailVm.setDueDate(LocalDateTime(date, time))
            },
            onPickTime = { h, m ->
                if (h == -1 && m == -1) detailVm.setTimeNull() else detailVm.setTime(h, m)
            },
            onDismiss = { showDatePicker = false },
        )
    }

    // 列表选择弹窗：点击某行即选中并写库；当前所在列表显示勾选标记。
    if (showListDialog) {
        RemDialog(
            title = "选择列表",
            onDismiss = { showListDialog = false },
            confirmText = "确定",
            onConfirm = { showListDialog = false },
            showButtons = false,
            content = {
                lists.forEach { list ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                showListDialog = false
                                detailVm.moveToList(list.id)
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(10.dp).background(ListColorOf[list.colorKey] ?: Color.Gray, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.foundation.text.BasicText(
                            list.name,
                            style = RemType.text14.copy(color = colors.textHigh),
                            modifier = Modifier.weight(1f),
                        )
                        if (list.id == current?.listId) {
                            RemIcon(IconName.CheckCircle, colors.brand, Modifier.size(16.dp))
                        }
                    }
                }
            },
        )
    }

    // 本页错误弹窗：只消费 detailVm 的错误（主列表错误由 AppRoot 统一弹）。
    val detailError by detailVm.lastError.collectAsState()
    val detailErrorMsg = detailError?.uiMessage()
    if (detailErrorMsg != null) {
        RemDialog(
            title = "出错了",
            onDismiss = detailVm::dismissError,
            confirmText = "知道了",
            onConfirm = detailVm::dismissError,
            showButtons = false,
            content = {
                androidx.compose.foundation.text.BasicText(detailErrorMsg, style = RemType.text14.copy(color = colors.textNormal))
            },
        )
    }
}
