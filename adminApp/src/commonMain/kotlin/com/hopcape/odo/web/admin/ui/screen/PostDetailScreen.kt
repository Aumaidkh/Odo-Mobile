package com.hopcape.odo.web.admin.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.admin.domain.PostBlock
import com.hopcape.odo.web.admin.domain.PostDetail
import com.hopcape.odo.web.admin.presentation.content.PostDetailEvent
import com.hopcape.odo.web.admin.presentation.content.PostDetailUiState
import com.hopcape.odo.web.admin.resources.Res
import com.hopcape.odo.web.admin.resources.ad_cities_cancel
import com.hopcape.odo.web.admin.resources.ad_content_body_empty
import com.hopcape.odo.web.admin.resources.ad_content_col_status
import com.hopcape.odo.web.admin.resources.ad_content_edit
import com.hopcape.odo.web.admin.resources.ad_content_field_dek
import com.hopcape.odo.web.admin.resources.ad_content_field_meta
import com.hopcape.odo.web.admin.resources.ad_content_field_seo
import com.hopcape.odo.web.admin.resources.ad_content_field_slug
import com.hopcape.odo.web.admin.resources.ad_content_field_title
import com.hopcape.odo.web.admin.resources.ad_content_no_category
import com.hopcape.odo.web.admin.resources.ad_content_publish
import com.hopcape.odo.web.admin.resources.ad_content_status_draft
import com.hopcape.odo.web.admin.resources.ad_content_status_published
import com.hopcape.odo.web.admin.resources.ad_content_unpublish
import com.hopcape.odo.web.admin.resources.ad_content_delete
import com.hopcape.odo.web.admin.resources.ad_post_back
import com.hopcape.odo.web.admin.resources.ad_post_body
import com.hopcape.odo.web.admin.resources.ad_post_body_note
import com.hopcape.odo.web.admin.resources.ad_post_meta
import com.hopcape.odo.web.admin.resources.ad_post_block_up
import com.hopcape.odo.web.admin.resources.ad_post_block_down
import com.hopcape.odo.web.admin.resources.ad_post_block_image
import com.hopcape.odo.web.admin.resources.ad_post_block_table
import com.hopcape.odo.web.admin.resources.ad_post_block_app
import com.hopcape.odo.web.admin.resources.ad_post_block_other
import com.hopcape.odo.web.admin.resources.ad_post_block_add
import com.hopcape.odo.web.admin.resources.ad_post_block_bullets
import com.hopcape.odo.web.admin.resources.ad_post_block_divider
import com.hopcape.odo.web.admin.resources.ad_post_block_flatten
import com.hopcape.odo.web.admin.resources.ad_post_block_kept
import com.hopcape.odo.web.admin.resources.ad_post_block_label
import com.hopcape.odo.web.admin.resources.ad_post_block_text
import com.hopcape.odo.web.admin.resources.ad_post_body_editing
import com.hopcape.odo.web.admin.resources.ad_post_live
import com.hopcape.odo.web.admin.resources.ad_post_reading
import com.hopcape.odo.web.admin.resources.ad_post_save
import com.hopcape.odo.web.admin.ui.component.AdminField
import com.hopcape.odo.web.admin.ui.component.Banner
import com.hopcape.odo.web.admin.ui.component.FieldLabel
import com.hopcape.odo.web.admin.ui.component.Hairline
import com.hopcape.odo.web.admin.ui.component.LoadingPanel
import com.hopcape.odo.web.admin.ui.component.Muted
import com.hopcape.odo.web.admin.ui.component.Panel
import com.hopcape.odo.web.admin.ui.component.RowPanel
import com.hopcape.odo.web.admin.ui.component.StatusText
import com.hopcape.odo.web.admin.ui.component.PanelHeader
import com.hopcape.odo.web.admin.ui.component.Pill
import com.hopcape.odo.web.admin.ui.component.PrimaryAction
import com.hopcape.odo.web.admin.ui.component.RowAction
import com.hopcape.odo.web.admin.ui.theme.AdminTokens
import com.hopcape.odo.web.admin.ui.theme.AdminType
import com.hopcape.odo.web.core.platform.openExternal
import com.hopcape.odo.web.core.presentation.state.Loadable
import com.hopcape.odo.web.core.presentation.state.resolve
import org.jetbrains.compose.resources.stringResource

/**
 * One post, read and edited without leaving the panel.
 *
 * The whole point of this screen is that clicking a post used to send somebody to
 * another origin's sign-in page. Everything a post needs in order to go live
 * correctly — title, dek, slug, category, the two SEO fields — is editable here,
 * and the body is rendered read-only beside it so the words can be checked before
 * publishing.
 *
 * The body is edited here too, block by block. The kinds this panel cannot author —
 * an image's URL, a table's cells — stay put across a save rather than being
 * rewritten from a model that never held them; see [PostBlock.raw].
 */
@Composable
fun PostDetailScreen(state: PostDetailUiState, onEvent: (PostDetailEvent) -> Unit, onBack: () -> Unit) {
    val detail = state.value

    if (detail == null) {
        val failure = state.detail as? Loadable.Failed
        LoadingPanel(
            message = failure?.message?.resolve(),
            onRetry = if (failure?.retryable == true) ({ onEvent(PostDetailEvent.Refresh) }) else null,
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { HeaderPanel(detail, state, onEvent, onBack) }
            item { MetaPanel(detail, state, onEvent) }
            item { BodyPanel(state, onEvent) }
        }

        state.message?.let { Banner(it.resolve()) { onEvent(PostDetailEvent.MessageDismissed) } }
    }
}

@Composable
private fun HeaderPanel(
    detail: PostDetail,
    state: PostDetailUiState,
    onEvent: (PostDetailEvent) -> Unit,
    onBack: () -> Unit,
) {
    Panel {
        PanelHeader(detail.post.title) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill(
                    if (detail.post.isPublished) {
                        stringResource(Res.string.ad_content_status_published)
                    } else {
                        stringResource(Res.string.ad_content_status_draft)
                    },
                    dot = if (detail.post.isPublished) null else AdminTokens.accent,
                )
                RowAction(stringResource(Res.string.ad_post_back), onBack)
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (detail.dek.isNotBlank()) {
                Text(detail.dek, style = AdminType.body, color = AdminTokens.textMuted)
            }
            Text(
                stringResource(Res.string.ad_post_reading, detail.wordCount, detail.readingMinutes, detail.post.views),
                style = AdminType.micro,
                color = AdminTokens.textDim,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RowAction(
                    if (detail.post.isPublished) {
                        stringResource(Res.string.ad_content_unpublish)
                    } else {
                        stringResource(Res.string.ad_content_publish)
                    },
                    { onEvent(PostDetailEvent.PublishToggled) },
                    !state.busy,
                )
                // The only link that leaves the panel, and only for a post that is
                // actually live. It is not a preview — the preview is below — it is
                // "show me the real page", which is the one thing a canvas rendering
                // of the blocks genuinely cannot stand in for.
                detail.post.publicUrl?.let { url ->
                    RowAction(stringResource(Res.string.ad_post_live), { openExternal(url) })
                }
            }
        }
    }
}

@Composable
private fun MetaPanel(detail: PostDetail, state: PostDetailUiState, onEvent: (PostDetailEvent) -> Unit) {
    val draft = state.draft
    Panel {
        PanelHeader(stringResource(Res.string.ad_post_meta)) {
            if (draft == null) {
                RowAction(stringResource(Res.string.ad_content_edit), { onEvent(PostDetailEvent.EditStarted) }, !state.busy)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryAction(
                        stringResource(Res.string.ad_post_save),
                        { onEvent(PostDetailEvent.Saved) },
                        draft.canSubmit(state.isPublished) && !state.busy,
                    )
                    RowAction(stringResource(Res.string.ad_cities_cancel), { onEvent(PostDetailEvent.EditCancelled) })
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (draft == null) {
                ReadLine(stringResource(Res.string.ad_content_field_title), detail.post.title)
                ReadLine(stringResource(Res.string.ad_content_field_dek), detail.dek)
                ReadLine(stringResource(Res.string.ad_content_field_slug), detail.post.slug ?: "—")
                ReadLine(stringResource(Res.string.ad_content_col_status), detail.categorySlug ?: "—")
                ReadLine(stringResource(Res.string.ad_content_field_seo), detail.seoTitle.ifBlank { "—" })
                ReadLine(stringResource(Res.string.ad_content_field_meta), detail.metaDescription.ifBlank { "—" })
            } else {
                Field(stringResource(Res.string.ad_content_field_title), draft.title) {
                    onEvent(PostDetailEvent.TitleChanged(it))
                }
                Field(stringResource(Res.string.ad_content_field_dek), draft.dek) {
                    onEvent(PostDetailEvent.DekChanged(it))
                }
                Field(stringResource(Res.string.ad_content_field_slug), draft.slug) {
                    onEvent(PostDetailEvent.SlugChanged(it))
                }
                Column {
                    FieldLabel(stringResource(Res.string.ad_content_col_status).uppercase())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CategoryPill(stringResource(Res.string.ad_content_no_category), draft.categorySlug == null) {
                            onEvent(PostDetailEvent.CategoryChanged(null))
                        }
                        state.categories.forEach { category ->
                            CategoryPill(category.name, draft.categorySlug == category.slug) {
                                onEvent(PostDetailEvent.CategoryChanged(category.slug))
                            }
                        }
                    }
                }
                Field(stringResource(Res.string.ad_content_field_seo), draft.seoTitle) {
                    onEvent(PostDetailEvent.SeoTitleChanged(it))
                }
                Field(stringResource(Res.string.ad_content_field_meta), draft.metaDescription) {
                    onEvent(PostDetailEvent.MetaDescriptionChanged(it))
                }
            }
        }
    }
}

@Composable
private fun CategoryPill(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) Pill(label, textColor = AdminTokens.text) else RowAction(label, onClick)
}

@Composable
private fun ReadLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(label.uppercase(), style = AdminType.eyebrow, color = AdminTokens.textFaint, modifier = Modifier.width(130.dp))
        Text(value, style = AdminType.body, color = AdminTokens.textStrong, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    Column {
        FieldLabel(label.uppercase())
        AdminField(value, onChange, label, Modifier.fillMaxWidth())
    }
}

/**
 * The post: read, and edited.
 *
 * One panel for both, rather than a preview beside an editor. A post is a column of
 * text either way, and two columns of the same words at slightly different widths is
 * how somebody ends up fixing the wrong one.
 *
 * Block kinds the panel does not know are shown with their type as a label rather
 * than skipped — a preview that quietly drops something is a preview that lies about
 * what will publish.
 */
@Composable
private fun BodyPanel(state: PostDetailUiState, onEvent: (PostDetailEvent) -> Unit) {
    val blocks = state.body
    val editing = state.editingBody
    Panel {
        PanelHeader(stringResource(Res.string.ad_post_body)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill(blocks.size.toString())
                if (editing) {
                    PrimaryAction(
                        stringResource(Res.string.ad_post_save),
                        { onEvent(PostDetailEvent.BodySaved) },
                        !state.busy,
                    )
                    RowAction(
                        stringResource(Res.string.ad_cities_cancel),
                        { onEvent(PostDetailEvent.BodyEditCancelled) },
                    )
                } else {
                    RowAction(
                        stringResource(Res.string.ad_content_edit),
                        { onEvent(PostDetailEvent.BodyEditStarted) },
                        !state.busy,
                    )
                }
            }
        }
        Text(
            stringResource(
                if (editing) Res.string.ad_post_body_editing else Res.string.ad_post_body_note,
            ),
            style = AdminType.caption,
            color = AdminTokens.textDim,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )

        if (blocks.isEmpty() && !editing) {
            Muted(stringResource(Res.string.ad_content_body_empty))
            return@Panel
        }

        Column(
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(if (editing) 10.dp else 12.dp),
        ) {
            blocks.forEachIndexed { index, block ->
                if (editing) {
                    BlockEditor(index, block, blocks.size, state.busy, onEvent)
                } else {
                    BlockView(block)
                }
            }
            if (editing) AddBlockRow(state.busy, onEvent)
        }
    }
}

/** One block, as a text box with the handful of controls a column of blocks needs. */
@Composable
private fun BlockEditor(
    index: Int,
    block: PostBlock,
    total: Int,
    busy: Boolean,
    onEvent: (PostDetailEvent) -> Unit,
) {
    RowPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    block.kind.name.uppercase(),
                    style = AdminType.eyebrow,
                    color = AdminTokens.textFaint,
                    modifier = Modifier.weight(1f),
                )
                RowAction(stringResource(Res.string.ad_post_block_up), { onEvent(PostDetailEvent.BlockMoved(index, -1)) }, !busy && index > 0)
                RowAction(stringResource(Res.string.ad_post_block_down), { onEvent(PostDetailEvent.BlockMoved(index, 1)) }, !busy && index < total - 1)
                RowAction(
                    stringResource(Res.string.ad_content_delete),
                    { onEvent(PostDetailEvent.BlockRemoved(index)) },
                    !busy,
                    color = AdminTokens.danger,
                )
            }

            // Said before somebody types, not after they save: this model holds
            // plain text, so editing a block that carries bold or italic loses it.
            if (block.hasRichRuns) {
                StatusText(stringResource(Res.string.ad_post_block_flatten), AdminTokens.accent)
            }

            when {
                block.kind == PostBlock.Kind.Divider ->
                    Text(stringResource(Res.string.ad_post_block_divider), style = AdminType.caption, color = AdminTokens.textDim)

                block.isTextual -> {
                    if (block.kind == PostBlock.Kind.Callout) {
                        AdminField(
                            block.label,
                            { onEvent(PostDetailEvent.BlockLabelChanged(index, it)) },
                            stringResource(Res.string.ad_post_block_label),
                            Modifier.fillMaxWidth(),
                        )
                    }
                    AdminField(
                        block.text,
                        { onEvent(PostDetailEvent.BlockChanged(index, it)) },
                        stringResource(
                            if (block.kind == PostBlock.Kind.Bullets) {
                                Res.string.ad_post_block_bullets
                            } else {
                                Res.string.ad_post_block_text
                            },
                        ),
                        Modifier.fillMaxWidth(),
                    )
                }

                // An image is a URL and a table is a grid of cells. Neither is
                // authored here, and both survive the save untouched — shown so the
                // column is honest about what is in the post.
                else -> Text(
                    block.text.ifBlank { stringResource(Res.string.ad_post_block_kept) },
                    style = AdminType.caption,
                    color = AdminTokens.textDim,
                )
            }
        }
    }
}

@Composable
private fun AddBlockRow(busy: Boolean, onEvent: (PostDetailEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(Res.string.ad_post_block_add), style = AdminType.eyebrow, color = AdminTokens.textFaint)
        // The four text kinds and a rule. Images and tables are absent because this
        // panel cannot author them, and an "Add image" that produced an empty block
        // nobody could fill would be worse than no button.
        listOf(
            PostBlock.Kind.Paragraph,
            PostBlock.Kind.Section,
            PostBlock.Kind.Callout,
            PostBlock.Kind.Bullets,
            PostBlock.Kind.Divider,
        ).forEach { kind ->
            RowAction(
                kind.name,
                { onEvent(PostDetailEvent.BlockAdded(kind)) },
                !busy,
            )
        }
    }
}

/** One block, typeset. A heading that looks like a paragraph defeats the preview. */
@Composable
private fun BlockView(block: PostBlock) {
    when (block.kind) {
        PostBlock.Kind.Section -> Text(
            block.text,
            style = AdminType.title,
            color = AdminTokens.text,
            modifier = Modifier.padding(top = 8.dp),
        )

        PostBlock.Kind.Paragraph -> Text(block.text, style = AdminType.body, color = AdminTokens.textStrong)

        PostBlock.Kind.Callout -> Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (block.label.isNotBlank()) {
                    Text(block.label.uppercase(), style = AdminType.eyebrow, color = AdminTokens.accent)
                }
                Text(block.text, style = AdminType.body, color = AdminTokens.textStrong)
            }
        }

        PostBlock.Kind.Bullets -> Text(block.text, style = AdminType.body, color = AdminTokens.textStrong)

        PostBlock.Kind.Divider -> Hairline()

        // The picture itself is not fetched: the preview is about the words, and a
        // body full of remote images would make this screen slower than the blog.
        PostBlock.Kind.Image -> Labelled(stringResource(Res.string.ad_post_block_image), block.text.ifBlank { "\u2014" })
        PostBlock.Kind.Table -> Labelled(stringResource(Res.string.ad_post_block_table), block.text)
        PostBlock.Kind.Showcase -> Labelled(block.label.ifBlank { stringResource(Res.string.ad_post_block_app) }, block.text)
        PostBlock.Kind.Unknown -> Labelled(block.label.uppercase().ifBlank { stringResource(Res.string.ad_post_block_other) }, block.text)
    }
}

@Composable
private fun Labelled(label: String, text: String) {
    Column(
        modifier = Modifier.fillMaxWidth().background(AdminTokens.field).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = AdminType.eyebrow, color = AdminTokens.textFaint)
        if (text.isNotBlank()) Text(text, style = AdminType.caption, color = AdminTokens.textMuted)
        Spacer(Modifier.height(0.dp))
    }
}
