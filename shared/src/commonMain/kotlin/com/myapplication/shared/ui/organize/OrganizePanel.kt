package com.myapplication.shared.ui.organize

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.myapplication.shared.domain.organize.OrganizationAction
import com.myapplication.shared.domain.organize.OrganizationSuggestion

internal const val OrganizationSuggestionLimit = 5

@Composable
fun OrganizePanel(
    suggestions: List<OrganizationSuggestion>,
    selectedId: Long?,
    onOpen: (Long) -> Unit,
    onAction: (OrganizationSuggestion, OrganizationAction) -> Unit,
    modifier: Modifier = Modifier,
    showRowContainer: Boolean = true,
) {
    val visible = suggestions.visibleOrganizationSuggestions()
    if (visible.isEmpty()) return

    OrganizeSection(
        title = "建议处理",
        suggestions = visible,
        selectedId = selectedId,
        onOpen = onOpen,
        onAction = onAction,
        modifier = modifier,
        showRowContainer = showRowContainer,
    )
}

internal fun List<OrganizationSuggestion>.visibleOrganizationSuggestions(): List<OrganizationSuggestion> =
    filter { it.reasons.size >= 2 }.take(OrganizationSuggestionLimit)
