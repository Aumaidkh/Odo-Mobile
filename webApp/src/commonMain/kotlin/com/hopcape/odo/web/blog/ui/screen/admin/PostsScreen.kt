package com.hopcape.odo.web.blog.ui.screen.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.blog.domain.model.PostRow
import com.hopcape.odo.web.blog.domain.model.PostStatus
import com.hopcape.odo.web.blog.presentation.admin.posts.PostFilter
import com.hopcape.odo.web.blog.presentation.admin.posts.PostsEvent
import com.hopcape.odo.web.blog.presentation.admin.posts.PostsUiState
import com.hopcape.odo.web.blog.resources.Res
import com.hopcape.odo.web.blog.resources.bl_admin_column_status
import com.hopcape.odo.web.blog.resources.bl_admin_column_title
import com.hopcape.odo.web.blog.resources.bl_admin_column_updated
import com.hopcape.odo.web.blog.resources.bl_admin_column_views
import com.hopcape.odo.web.blog.resources.bl_admin_filter_all
import com.hopcape.odo.web.blog.resources.bl_admin_filter_drafts
import com.hopcape.odo.web.blog.resources.bl_admin_filter_published
import com.hopcape.odo.web.blog.resources.bl_admin_no_slug
import com.hopcape.odo.web.blog.resources.bl_admin_no_views
import com.hopcape.odo.web.blog.resources.bl_admin_posts_new
import com.hopcape.odo.web.blog.resources.bl_admin_untitled
import com.hopcape.odo.web.blog.resources.bl_admin_posts_title
import com.hopcape.odo.web.blog.resources.bl_admin_slug_prefix
import com.hopcape.odo.web.blog.resources.bl_admin_status_draft
import com.hopcape.odo.web.blog.resources.bl_admin_status_published
import com.hopcape.odo.web.blog.routing.BlogRoute
import com.hopcape.odo.web.blog.ui.component.Eyebrow
import com.hopcape.odo.web.blog.ui.component.FilterChip
import com.hopcape.odo.web.blog.ui.component.Hairline
import com.hopcape.odo.web.blog.ui.component.LoadableBox
import com.hopcape.odo.web.blog.ui.component.PillButton
import com.hopcape.odo.web.blog.ui.theme.BlogThemeTokens
import org.jetbrains.compose.resources.stringResource

/**
 * Every post, in a table.
 *
 * The slug is under the title rather than in a column of its own: it is the one
 * thing an author checks before publishing and never again, and giving it a
 * column would cost the title the width it needs.
 *
 * A draft's views cell is an em dash, not a zero. Nobody has read it — that is
 * not the same as it having been read no times.
 */
@Composable
fun PostsScreen(
    state: PostsUiState,
    onEvent: (PostsEvent) -> Unit,
    onNavigate: (BlogRoute) -> Unit,
) {
    val colors = BlogThemeTokens.colors
    val compact = BlogThemeTokens.compact

    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.bl_admin_posts_title),
                color = colors.text,
                style = MaterialTheme.typography.displayMedium,
            )
            Spacer(Modifier.weight(1f))
            PillButton(
                text = stringResource(Res.string.bl_admin_posts_new),
                onClick = { onNavigate(BlogRoute.Admin.Editor(null)) },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            FilterChip(
                text = stringResource(Res.string.bl_admin_filter_all, state.totalCount),
                selected = state.filter == PostFilter.ALL,
                onClick = { onEvent(PostsEvent.FilterSelected(PostFilter.ALL)) },
            )
            FilterChip(
                text = stringResource(Res.string.bl_admin_filter_published, state.publishedCount),
                selected = state.filter == PostFilter.PUBLISHED,
                onClick = { onEvent(PostsEvent.FilterSelected(PostFilter.PUBLISHED)) },
            )
            FilterChip(
                text = stringResource(Res.string.bl_admin_filter_drafts, state.draftCount),
                selected = state.filter == PostFilter.DRAFTS,
                onClick = { onEvent(PostsEvent.FilterSelected(PostFilter.DRAFTS)) },
            )
        }

        LoadableBox(state.rows, onRetry = { onEvent(PostsEvent.Retry) }) {
            Column(Modifier.fillMaxWidth()) {
                if (!compact) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        Eyebrow(stringResource(Res.string.bl_admin_column_title), Modifier.weight(1f))
                        Eyebrow(stringResource(Res.string.bl_admin_column_status), Modifier.width(110.dp))
                        Eyebrow(stringResource(Res.string.bl_admin_column_views), Modifier.width(90.dp))
                        Eyebrow(stringResource(Res.string.bl_admin_column_updated), Modifier.width(110.dp))
                    }
                    Hairline()
                }
                state.visible.forEach { row ->
                    PostRowItem(row, compact) { onNavigate(BlogRoute.Admin.Editor(row.id)) }
                    Hairline()
                }
            }
        }
    }
}

@Composable
private fun PostRowItem(row: PostRow, compact: Boolean, onClick: () -> Unit) {
    val colors = BlogThemeTokens.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (hovered) colors.surfaceRaised else colors.surface)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = row.title.ifBlank { stringResource(Res.string.bl_admin_untitled) },
                color = if (row.title.isBlank()) colors.muted else colors.text,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = row.slug?.let { stringResource(Res.string.bl_admin_slug_prefix) + it }
                    ?: stringResource(Res.string.bl_admin_no_slug),
                color = colors.muted,
                style = MaterialTheme.typography.bodyMedium,
            )
            // On a phone the three trailing columns fold under the title; the
            // table's shape is not worth a horizontal scroll.
            if (compact) {
                Text(
                    text = row.statusLabel() + " · " + row.viewsLabel() + " · " +
                        row.updatedLabel.ifBlank { stringResource(Res.string.bl_admin_no_views) },
                    color = colors.dim,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (!compact) {
            Text(
                text = row.statusLabel(),
                color = if (row.status == PostStatus.PUBLISHED) colors.success else colors.muted,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.width(110.dp),
            )
            Text(
                text = row.viewsLabel(),
                color = colors.dim,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(90.dp),
            )
            Text(
                text = row.updatedLabel.ifBlank { stringResource(Res.string.bl_admin_no_views) },
                color = colors.dim,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(110.dp),
            )
        }
    }
}

@Composable
private fun PostRow.statusLabel(): String = when (status) {
    PostStatus.PUBLISHED -> stringResource(Res.string.bl_admin_status_published)
    PostStatus.DRAFT -> stringResource(Res.string.bl_admin_status_draft)
}

/** Grouped with separators, because five figures unspaced is unreadable at 13px. */
@Composable
private fun PostRow.viewsLabel(): String =
    views?.toString()?.reversed()?.chunked(3)?.joinToString(",")?.reversed()
        ?: stringResource(Res.string.bl_admin_no_views)
