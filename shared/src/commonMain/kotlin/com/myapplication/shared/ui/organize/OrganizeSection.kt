package com.myapplication.shared.ui.organize

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myapplication.shared.domain.organize.OrganizationAction
import com.myapplication.shared.domain.organize.OrganizationSuggestion
import com.myapplication.shared.ui.components.IconName
import com.myapplication.shared.ui.components.RemIcon
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemType

@Composable
fun OrganizeSection(
    title: String,
    suggestions: List<OrganizationSuggestion>,
    selectedId: Long?,
    onOpen: (Long) -> Unit,
    onAction: (OrganizationSuggestion, OrganizationAction) -> Unit,
    modifier: Modifier = Modifier,
    showRowContainer: Boolean = true,
) {
    val colors = LocalRemColors.current
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = if (showRowContainer) 4.dp else 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemIcon(IconName.Send, colors.brand, Modifier.size(14.dp))
            Spacer(Modifier.width(7.dp))
            androidx.compose.foundation.text
                .BasicText(title, style = RemType.label12.copy(color = colors.brand))
            Spacer(Modifier.width(6.dp))
            androidx.compose.foundation.text
                .BasicText(suggestions.size.toString(), style = RemType.label12.copy(color = colors.brand))
            Spacer(Modifier.weight(1f))
        }

        val rowContainer =
            if (showRowContainer) {
                Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
            } else {
                Modifier.fillMaxWidth()
            }
        Column(rowContainer) {
            suggestions.forEachIndexed { index, suggestion ->
                SuggestionRow(
                    suggestion = suggestion,
                    selected = selectedId == suggestion.todo.id,
                    onOpen = { onOpen(suggestion.todo.id) },
                    onAction = { action -> onAction(suggestion, action) },
                    edgeToEdge = !showRowContainer,
                )
                if (!showRowContainer && index < suggestions.lastIndex) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp)
                            .height(1.dp)
                            .background(colors.borderSubtle),
                    )
                }
            }
        }
    }
}
