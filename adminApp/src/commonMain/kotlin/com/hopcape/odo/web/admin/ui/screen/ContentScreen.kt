package com.hopcape.odo.web.admin.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.admin.domain.BlogPost
import com.hopcape.odo.web.admin.presentation.content.ContentEvent
import com.hopcape.odo.web.admin.presentation.content.ContentUiState
import com.hopcape.odo.web.admin.resources.Res
import com.hopcape.odo.web.admin.resources.ad_cities_cancel
import com.hopcape.odo.web.admin.resources.ad_content_col_action
import com.hopcape.odo.web.admin.resources.ad_content_col_author
import com.hopcape.odo.web.admin.resources.ad_content_col_status
import com.hopcape.odo.web.admin.resources.ad_content_col_title
import com.hopcape.odo.web.admin.resources.ad_content_col_updated
import com.hopcape.odo.web.admin.resources.ad_content_col_views
import com.hopcape.odo.web.admin.resources.ad_content_count
import com.hopcape.odo.web.admin.resources.ad_content_create
import com.hopcape.odo.web.admin.resources.ad_content_create_hint
import com.hopcape.odo.web.admin.resources.ad_content_field_category
import com.hopcape.odo.web.admin.resources.ad_content_field_dek
import com.hopcape.odo.web.admin.resources.ad_content_field_slug
import com.hopcape.odo.web.admin.resources.ad_content_field_title
import com.hopcape.odo.web.admin.resources.ad_content_new
import com.hopcape.odo.web.admin.resources.ad_content_new_title
import com.hopcape.odo.web.admin.resources.ad_content_no_category
import com.hopcape.odo.web.admin.resources.ad_content_delete
import com.hopcape.odo.web.admin.resources.ad_content_delete_body
import com.hopcape.odo.web.admin.resources.ad_content_delete_title
import com.hopcape.odo.web.admin.resources.ad_content_drafts
import com.hopcape.odo.web.admin.resources.ad_content_edit
import com.hopcape.odo.web.admin.resources.ad_content_view
import com.hopcape.odo.web.admin.resources.ad_content_editor_note
import com.hopcape.odo.web.admin.resources.ad_content_empty
import com.hopcape.odo.web.admin.resources.ad_content_no_author
import com.hopcape.odo.web.admin.resources.ad_content_no_slug
import com.hopcape.odo.web.admin.resources.ad_content_posts
import com.hopcape.odo.web.admin.resources.ad_content_publish
import com.hopcape.odo.web.admin.resources.ad_content_status_draft
import com.hopcape.odo.web.admin.resources.ad_content_status_published
import com.hopcape.odo.web.admin.resources.ad_content_unpublish
import com.hopcape.odo.web.admin.resources.ad_users_showing
import com.hopcape.odo.web.admin.ui.component.AdminField
import com.hopcape.odo.web.admin.ui.component.Banner
import com.hopcape.odo.web.admin.ui.component.Cell
import com.hopcape.odo.web.admin.ui.component.CellPrimary
import com.hopcape.odo.web.admin.ui.component.CellSecondary
import com.hopcape.odo.web.admin.ui.component.FieldLabel
import com.hopcape.odo.web.admin.ui.component.LoadingPanel
import com.hopcape.odo.web.admin.ui.component.Muted
import com.hopcape.odo.web.admin.ui.component.Pager
import com.hopcape.odo.web.admin.ui.component.Panel
import com.hopcape.odo.web.admin.ui.component.RowPanel
import com.hopcape.odo.web.admin.ui.component.PanelHeader
import com.hopcape.odo.web.admin.ui.component.Pill
import com.hopcape.odo.web.admin.ui.component.PrimaryAction
import com.hopcape.odo.web.admin.ui.component.RowAction
import com.hopcape.odo.web.admin.ui.component.StatusText
import com.hopcape.odo.web.admin.ui.component.TableHead
import com.hopcape.odo.web.admin.ui.component.TableRow
import com.hopcape.odo.web.admin.ui.theme.AdminTokens
import com.hopcape.odo.web.admin.ui.theme.AdminType
import com.hopcape.odo.web.core.presentation.state.Loadable
import com.hopcape.odo.web.core.presentation.state.resolve
import org.jetbrains.compose.resources.stringResource

private val COLUMNS = listOf(2.4f, 1.1f, 0.6f, 0.9f, 0.8f, 2.0f)

/**
 * The blog's posts, from `/admin`.
 *
 * The list, and the two verbs that act on a whole post: publish, and delete a
 * draft. Everything else — reading it, rewriting it — happens on the post's own
 * page, which both the row and its actions open.
 *
 * Nothing here leaves the panel. It used to: the row linked to the blog's own
 * editor on another origin, which meant clicking a post landed on a sign-in page.
 */
@Composable
fun ContentScreen(state: ContentUiState, onEvent: (ContentEvent) -> Unit, onOpen: (String) -> Unit) {
    // Loading is not emptiness. Before this guard the table drew its "nothing here"
    // copy while the first read was still in flight, which is indistinguishable
    // from a genuinely empty table — and on a cold Wasm boot that is a long time to
    // be telling somebody a lie.
    if (state.posts is Loadable.Loading) {
        LoadingPanel()
        return
    }
    (state.posts as? Loadable.Failed)?.let { failure ->
        LoadingPanel(
            message = failure.message.resolve(),
            onRetry = if (failure.retryable) ({ onEvent(ContentEvent.Refresh) }) else null,
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Panel {
                    PanelHeader(stringResource(Res.string.ad_content_posts)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (state.draftCount > 0) {
                                Pill(
                                    stringResource(Res.string.ad_content_drafts, state.draftCount),
                                    dot = AdminTokens.accent,
                                )
                            }
                            Pill(stringResource(Res.string.ad_content_count, state.matching.size))
                            PrimaryAction(
                                stringResource(Res.string.ad_content_new),
                                { onEvent(ContentEvent.CreateRequested) },
                                enabled = !state.busy,
                            )
                        }
                    }
                    Text(
                        stringResource(Res.string.ad_content_editor_note),
                        style = AdminType.caption,
                        color = AdminTokens.textDim,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    TableHead(
                        listOf(
                            stringResource(Res.string.ad_content_col_title),
                            stringResource(Res.string.ad_content_col_author),
                            stringResource(Res.string.ad_content_col_views),
                            stringResource(Res.string.ad_content_col_updated),
                            stringResource(Res.string.ad_content_col_status),
                            stringResource(Res.string.ad_content_col_action),
                        ),
                        COLUMNS,
                    )
                }
            }

            if (state.visible.isEmpty()) {
                item { Panel { Muted(stringResource(Res.string.ad_content_empty)) } }
            } else {
                items(state.visible, key = { it.id }) { post -> PostRow(post, state.busy, onEvent, onOpen) }
                item {
                    Pager(
                        page = state.page,
                        total = state.matching.size,
                        label = stringResource(
                            Res.string.ad_users_showing,
                            state.page.first(state.matching.size),
                            state.page.last(state.matching.size),
                            state.matching.size,
                        ),
                        onPrevious = { onEvent(ContentEvent.PreviousPage) },
                        onNext = { onEvent(ContentEvent.NextPage) },
                    )
                }
            }
        }

        state.message?.let { message ->
            Banner(message.resolve()) { onEvent(ContentEvent.MessageDismissed) }
        }
    }

    state.pendingDelete?.let { DeleteDialog(it, state.busy, onEvent) }
    state.draft?.let { CreateDialog(it, state, state.busy, onEvent) }
}

/**
 * The create form.
 *
 * Title, dek, slug, category — and nothing else. The body is the editor's, and a
 * form here that could publish a post with no body is a form that eventually does,
 * so this only ever makes a draft.
 */
@Composable
private fun CreateDialog(
    draft: com.hopcape.odo.web.admin.presentation.content.NewPostDraft,
    state: ContentUiState,
    busy: Boolean,
    onEvent: (ContentEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(ContentEvent.CreateDismissed) },
        containerColor = AdminTokens.card,
        titleContentColor = AdminTokens.text,
        textContentColor = AdminTokens.textStrong,
        title = { Text(stringResource(Res.string.ad_content_new_title), style = AdminType.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    FieldLabel(stringResource(Res.string.ad_content_field_title).uppercase())
                    AdminField(
                        draft.title.value,
                        { onEvent(ContentEvent.DraftTitleChanged(it)) },
                        stringResource(Res.string.ad_content_field_title),
                        Modifier.fillMaxWidth(),
                    )
                }
                Column {
                    FieldLabel(stringResource(Res.string.ad_content_field_dek).uppercase())
                    AdminField(
                        draft.dek.value,
                        { onEvent(ContentEvent.DraftDekChanged(it)) },
                        stringResource(Res.string.ad_content_field_dek),
                        Modifier.fillMaxWidth(),
                    )
                }
                Column {
                    FieldLabel(stringResource(Res.string.ad_content_field_slug).uppercase())
                    AdminField(
                        draft.slug.value,
                        { onEvent(ContentEvent.DraftSlugChanged(it)) },
                        stringResource(Res.string.ad_content_field_slug),
                        Modifier.fillMaxWidth(),
                    )
                    draft.slugError?.let { StatusText(it.resolve(), AdminTokens.danger, Modifier.padding(top = 4.dp)) }
                }
                Column {
                    FieldLabel(stringResource(Res.string.ad_content_field_category).uppercase())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CategoryChip(
                            stringResource(Res.string.ad_content_no_category),
                            draft.categorySlug == null,
                        ) { onEvent(ContentEvent.DraftCategoryChanged(null)) }
                        state.categories.forEach { category ->
                            CategoryChip(category.name, draft.categorySlug == category.slug) {
                                onEvent(ContentEvent.DraftCategoryChanged(category.slug))
                            }
                        }
                    }
                }
                Text(
                    stringResource(Res.string.ad_content_create_hint),
                    style = AdminType.caption,
                    color = AdminTokens.textDim,
                )
            }
        },
        confirmButton = {
            PrimaryAction(
                stringResource(Res.string.ad_content_create),
                { onEvent(ContentEvent.DraftSubmitted) },
                enabled = draft.canSubmit && !busy,
            )
        },
        dismissButton = {
            RowAction(stringResource(Res.string.ad_cities_cancel), { onEvent(ContentEvent.CreateDismissed) })
        },
    )
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) AdminTokens.text else AdminTokens.field)
            .border(1.dp, if (selected) AdminTokens.text else AdminTokens.border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(
            label,
            style = AdminType.strong,
            color = if (selected) AdminTokens.canvas else AdminTokens.textStrong,
            maxLines = 1,
        )
    }
}

/**
 * One post.
 *
 * The whole row opens the editor, and there is an Edit action as well. Both, on
 * purpose: the row being clickable is not discoverable — the first thing anybody
 * did with this list was try to click a post and conclude the list was dead — and
 * a button in the actions column is where the other two verbs already are.
 */
@Composable
private fun PostRow(post: BlogPost, busy: Boolean, onEvent: (ContentEvent) -> Unit, onOpen: (String) -> Unit) {
    RowPanel {
        TableRow(onClick = { onOpen(post.id) }) {
            Column(Modifier.weight(COLUMNS[0])) {
                CellPrimary(post.title)
                // A draft has never had a URL, which is a real state rather than
                // missing data — the CMS draws it the same way.
                CellSecondary(post.slug ?: stringResource(Res.string.ad_content_no_slug))
            }
            Cell(post.authorName ?: stringResource(Res.string.ad_content_no_author), Modifier.weight(COLUMNS[1]))
            Cell(post.views.toString(), Modifier.weight(COLUMNS[2]))
            Cell(post.updatedAt, Modifier.weight(COLUMNS[3]))
            StatusText(
                if (post.isPublished) {
                    stringResource(Res.string.ad_content_status_published)
                } else {
                    stringResource(Res.string.ad_content_status_draft)
                },
                if (post.isPublished) AdminTokens.text else AdminTokens.accent,
                Modifier.weight(COLUMNS[4]),
            )
            Row(
                modifier = Modifier.weight(COLUMNS[5]),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                RowAction(
                    stringResource(Res.string.ad_content_edit),
                    { onOpen(post.id) },
                    !busy,
                )
                // Opens the post's own page in the panel, not the live site. Both
                // verbs land in the same place on purpose: that page is the preview
                // and the editor, and sending "View" to another origin was the thing
                // that made this list feel like a dead end.
                RowAction(stringResource(Res.string.ad_content_view), { onOpen(post.id) }, !busy)
                RowAction(
                    if (post.isPublished) {
                        stringResource(Res.string.ad_content_unpublish)
                    } else {
                        stringResource(Res.string.ad_content_publish)
                    },
                    { onEvent(ContentEvent.PublishToggled(post)) },
                    !busy,
                )
                // Only ever for a draft. A published post has a URL somebody may
                // have shared, and unpublishing is what keeps that from becoming
                // a 404 — so delete is not offered as the easier of the two.
                if (!post.isPublished) {
                    RowAction(
                        stringResource(Res.string.ad_content_delete),
                        { onEvent(ContentEvent.DeleteRequested(post)) },
                        !busy,
                        color = AdminTokens.danger,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeleteDialog(post: BlogPost, busy: Boolean, onEvent: (ContentEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(ContentEvent.DeleteDismissed) },
        containerColor = AdminTokens.card,
        titleContentColor = AdminTokens.text,
        textContentColor = AdminTokens.textStrong,
        title = { Text(stringResource(Res.string.ad_content_delete_title, post.title), style = AdminType.title) },
        text = {
            Text(
                stringResource(Res.string.ad_content_delete_body),
                style = AdminType.body,
                color = AdminTokens.textStrong,
            )
        },
        confirmButton = {
            PrimaryAction(
                stringResource(Res.string.ad_content_delete),
                { onEvent(ContentEvent.DeleteConfirmed) },
                enabled = !busy,
            )
        },
        dismissButton = {
            RowAction(stringResource(Res.string.ad_cities_cancel), { onEvent(ContentEvent.DeleteDismissed) })
        },
    )
}
