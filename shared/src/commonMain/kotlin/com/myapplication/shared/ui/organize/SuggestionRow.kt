package com.myapplication.shared.ui.organize

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.organize.OrganizationAction
import com.myapplication.shared.domain.organize.OrganizationReason
import com.myapplication.shared.domain.organize.OrganizationSuggestion
import com.myapplication.shared.ui.components.RemButton
import com.myapplication.shared.ui.components.RemButtonVariant
import com.myapplication.shared.ui.components.rememberHoverBackground
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemControlSize
import com.myapplication.shared.ui.theme.RemType

@Composable
fun SuggestionRow(
    suggestion: OrganizationSuggestion,
    selected: Boolean,
    onOpen: () -> Unit,
    onAction: (OrganizationAction) -> Unit,
    modifier: Modifier = Modifier,
    edgeToEdge: Boolean = false,
) {
    val colors = LocalRemColors.current
    val interaction = remember { MutableInteractionSource() }
    val hover = rememberHoverBackground(interaction)
    val rowBg = if (selected) colors.brandSubtle else hover
    val rowModifier =
        modifier
            .fillMaxWidth()
            .background(rowBg)
            .clickable(interactionSource = interaction, indication = null, onClick = onOpen)
            .semantics { contentDescription = "打开整理建议：${suggestion.todo.title}" }
            .heightIn(min = if (edgeToEdge) RemControlSize.rowMobile else RemControlSize.rowDesktop)
            .padding(horizontal = if (edgeToEdge) 16.dp else 10.dp, vertical = if (edgeToEdge) 8.dp else 7.dp)

    if (edgeToEdge) {
        Column(rowModifier) {
            SuggestionText(suggestion)
            Spacer(Modifier.height(6.dp))
            SuggestionActions(suggestion.actions, onAction)
        }
        return
    }

    Row(
        rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SuggestionText(suggestion, Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        SuggestionActions(suggestion.actions, onAction)
    }
}

@Composable
private fun SuggestionText(
    suggestion: OrganizationSuggestion,
    modifier: Modifier = Modifier,
) {
    val colors = LocalRemColors.current
    Column(modifier) {
        androidx.compose.foundation.text.BasicText(
            suggestion.todo.title,
            style = RemType.text14.copy(color = colors.textHigh, fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        androidx.compose.foundation.text.BasicText(
            organizationReasonText(suggestion.reasons),
            style = RemType.text12.copy(color = colors.textLow),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SuggestionActions(
    actions: List<OrganizationAction>,
    onAction: (OrganizationAction) -> Unit,
) {
    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        actions.forEachIndexed { index, action ->
            if (index > 0) Spacer(Modifier.width(4.dp))
            RemButton(
                text = action.organizationActionLabel(),
                onClick = { onAction(action) },
                variant = if (action == OrganizationAction.Trash) RemButtonVariant.Danger else RemButtonVariant.Ghost,
            )
        }
    }
}

internal fun organizationReasonText(reasons: Set<OrganizationReason>): String =
    OrganizationReason.entries
        .filter { it in reasons }
        .joinToString(" · ") { it.organizationReasonLabel() }

internal fun OrganizationReason.organizationReasonLabel(): String =
    when (this) {
        OrganizationReason.Inbox -> "待归类"
        OrganizationReason.NoDate -> "无日期"
        OrganizationReason.Overdue -> "已逾期"
        OrganizationReason.LongTitle -> "标题过长"
    }

internal fun OrganizationAction.organizationActionLabel(): String =
    when (this) {
        OrganizationAction.ScheduleToday -> "安排今天"
        OrganizationAction.ScheduleTomorrow -> "安排明天"
        OrganizationAction.MoveToList -> "移动列表"
        OrganizationAction.EditTitle -> "编辑标题"
        OrganizationAction.Trash -> "删除"
    }
