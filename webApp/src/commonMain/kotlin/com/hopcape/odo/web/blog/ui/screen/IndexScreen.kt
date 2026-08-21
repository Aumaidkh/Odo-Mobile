package com.hopcape.odo.web.blog.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.blog.presentation.index.IndexEvent
import com.hopcape.odo.web.blog.presentation.index.IndexUiState
import com.hopcape.odo.web.blog.resources.Res
import com.hopcape.odo.web.blog.resources.bl_all
import com.hopcape.odo.web.blog.resources.bl_category_more_coming
import com.hopcape.odo.web.blog.resources.bl_index_dek
import com.hopcape.odo.web.blog.resources.bl_index_title
import com.hopcape.odo.web.blog.routing.BlogRoute
import com.hopcape.odo.web.blog.ui.component.FilterChip
import com.hopcape.odo.web.blog.ui.component.LeadStory
import com.hopcape.odo.web.blog.ui.component.LoadableBox
import com.hopcape.odo.web.blog.ui.component.PostGrid
import com.hopcape.odo.web.blog.ui.theme.BlogThemeTokens
import org.jetbrains.compose.resources.stringResource

/**
 * The index: one lead story, then the grid.
 *
 * The chips filter what is already on screen rather than fetching again, so
 * tapping between categories is instant and never blanks the page. That is a
 * ViewModel decision; this screen only draws which one is on.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IndexScreen(
    state: IndexUiState,
    onEvent: (IndexEvent) -> Unit,
    onNavigate: (BlogRoute) -> Unit,
) {
    val colors = BlogThemeTokens.colors
    val compact = BlogThemeTokens.compact

    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(Res.string.bl_index_title),
                color = colors.text,
                style = if (compact) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displayLarge,
            )
            Text(
                text = stringResource(Res.string.bl_index_dek),
                color = colors.dim,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.widthIn(max = 620.dp),
            )
        }

        LoadableBox(state.page, onRetry = { onEvent(IndexEvent.Retry) }) {
            Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    FilterChip(
                        text = stringResource(Res.string.bl_all),
                        selected = state.selectedCategory == null,
                        onClick = { onEvent(IndexEvent.CategorySelected(null)) },
                    )
                    state.categories.forEach { category ->
                        FilterChip(
                            text = category.name,
                            selected = state.selectedCategory == category.slug,
                            onClick = { onEvent(IndexEvent.CategorySelected(category.slug)) },
                        )
                    }
                }

                state.lead?.let { lead ->
                    LeadStory(post = lead, onClick = { onNavigate(BlogRoute.Public.Article(lead.slug)) })
                }

                PostGrid(
                    posts = state.grid,
                    onOpen = { onNavigate(BlogRoute.Public.Article(it.slug)) },
                )

                // A chip with nothing behind it. Reachable only because the chips
                // are drawn from every category, not from the ones on this page.
                if (state.visible.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.bl_category_more_coming),
                        color = colors.muted,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 32.dp),
                    )
                }
            }
        }
    }
}
