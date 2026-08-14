package com.myapplication.shared.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.analytics.AnalyticsRange
import com.myapplication.shared.domain.analytics.buildListAnalyticsModel
import com.myapplication.shared.domain.model.TodoList
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemButton
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.components.RemIconButton
import com.myapplication.shared.ui.list.DeleteListDialog
import com.myapplication.shared.ui.list.ListAnalyticsPanel
import com.myapplication.shared.ui.list.ListEditorDialog
import com.myapplication.shared.ui.main.MainViewModel
import com.myapplication.shared.ui.theme.ListColorOf
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemControlSize
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemType
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

@Composable
internal fun ListSettingsScreen(
    mainVm: MainViewModel,
    showHeader: Boolean = true,
) {
    val colors = LocalRemColors.current
    val lists by mainVm.lists.collectAsState()
    val counts by mainVm.listCounts.collectAsState()
    val analyticsTodos by mainVm.analyticsTodos.collectAsState()
    var selectedListId by remember { mutableStateOf<Long?>(null) }
    val selectedList = resolveSelectedList(lists, selectedListId)
    val timeZone = remember { TimeZone.currentSystemDefault() }
    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Clock.System.now()
            delay(60_000)
        }
    }
    val today = listAnalyticsToday(now, timeZone)
    val analyticsModel =
        remember(selectedList?.id, analyticsTodos, today, timeZone) {
            selectedList?.let { list ->
                buildListAnalyticsModel(
                    listId = list.id,
                    todos = analyticsTodos,
                    today = today,
                    range = AnalyticsRange.Week,
                    timeZone = timeZone,
                )
            }
        }
    var editing by remember { mutableStateOf<TodoList?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TodoList?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 40.dp),
        ) {
            if (showHeader) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        BasicText("列表", style = RemType.title20.copy(color = colors.textHigh))
                        Spacer(Modifier.height(2.dp))
                        BasicText("管理收件箱和自定义列表", style = RemType.text12.copy(color = colors.textLow))
                    }
                    RemButton("新建列表", onClick = { creating = true })
                }
                Spacer(Modifier.height(18.dp))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    RemButton("新建列表", onClick = { creating = true })
                }
                Spacer(Modifier.height(10.dp))
            }
            lists.forEachIndexed { index, list ->
                val isInbox = list.isInbox()
                ListSettingsRow(
                    list = list,
                    count = counts[list.id] ?: 0,
                    isInbox = isInbox,
                    selected = selectedList?.id == list.id,
                    onSelect = { selectedListId = list.id },
                    onEdit = { editing = list },
                    onDelete = { deleting = list },
                )
                if (index != lists.lastIndex) RowDivider()
            }
            analyticsModel?.let { model ->
                Spacer(Modifier.height(18.dp))
                ListAnalyticsPanel(model)
            }
        }
    }

    if (creating) {
        ListEditorDialog(
            list = null,
            onDismiss = { creating = false },
            onSave = { name, color -> mainVm.addList(name, color) },
        )
    }
    editing?.let { list ->
        ListEditorDialog(
            list = list,
            onDismiss = { editing = null },
            onSave = { name, color -> mainVm.updateList(list, name, color) },
        )
    }
    deleting?.let { list ->
        val stats by remember(list.id) { mainVm.observeListStats(list.id) }.collectAsState(initial = null)
        DeleteListDialog(
            list = list,
            stats = stats,
            onDismiss = { deleting = null },
            onDelete = { policy -> mainVm.deleteList(list, policy) },
        )
    }
}

@Composable
private fun ListSettingsRow(
    list: TodoList,
    count: Int,
    isInbox: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalRemColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(RemRadii.r4)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) colors.brandSubtle else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) colors.brand.copy(alpha = 0.28f) else Color.Transparent,
                shape = shape,
            ).clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelect,
            ).padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ListColorDot(list.colorKey, isInbox)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    list.name,
                    style =
                        RemType.text14.copy(
                            color = colors.textHigh,
                            fontWeight = FontWeight.Medium,
                        ),
                )
                if (isInbox) {
                    Spacer(Modifier.width(8.dp))
                    BasicText("系统", style = RemType.label10.copy(color = colors.textLow))
                }
            }
            Spacer(Modifier.height(3.dp))
            BasicText("$count 项", style = RemType.text12.copy(color = colors.textLow))
        }
        if (isInbox) {
            RemIcon(IconName.Inbox, colors.textLow, Modifier.size(17.dp), contentDescription = "收件箱")
        } else {
            RemIconButton(
                icon = IconName.Settings,
                contentDescription = "编辑列表 ${list.name}",
                onClick = onEdit,
                tint = colors.textLow,
                size = 15.dp,
                containerSize = RemControlSize.touch,
            )
            Spacer(Modifier.width(4.dp))
            RemIconButton(
                icon = IconName.Trash,
                contentDescription = "删除列表 ${list.name}",
                onClick = onDelete,
                tint = colors.error,
                size = 15.dp,
                containerSize = RemControlSize.touch,
            )
        }
    }
}

@Composable
private fun ListColorDot(
    colorKey: String,
    isInbox: Boolean,
) {
    val colors = LocalRemColors.current
    Box(
        Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(if (isInbox) colors.textLow else ListColorOf[colorKey] ?: Color.Gray)
            .border(1.dp, colors.border, CircleShape),
    )
}

@Composable
private fun RowDivider() {
    val colors = LocalRemColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .clip(
                androidx.compose.foundation.shape
                    .RoundedCornerShape(RemRadii.r2),
            ).background(colors.borderSubtle),
    )
}

private fun TodoList.isInbox(): Boolean = name == "收件箱" && position == 0

internal fun resolveSelectedList(
    lists: List<TodoList>,
    selectedListId: Long?,
): TodoList? = lists.firstOrNull { it.id == selectedListId } ?: lists.firstOrNull()

internal fun listAnalyticsToday(
    now: Instant,
    timeZone: TimeZone,
): LocalDate = now.toLocalDateTime(timeZone).date
