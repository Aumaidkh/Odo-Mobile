package com.hopcape.odo.web.blog.ui.screen.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.blog.domain.model.SeoDraft
import com.hopcape.odo.web.blog.presentation.admin.editor.EditorEvent
import com.hopcape.odo.web.blog.presentation.admin.editor.EditorSheet
import com.hopcape.odo.web.blog.presentation.admin.editor.EditorUiState
import com.hopcape.odo.web.blog.presentation.state.resolve
import com.hopcape.odo.web.blog.resources.Res
import com.hopcape.odo.web.blog.resources.bl_admin_slug_prefix
import com.hopcape.odo.web.blog.resources.bl_conflict_message
import com.hopcape.odo.web.blog.resources.bl_discard_cancel
import com.hopcape.odo.web.blog.resources.bl_discard_confirm
import com.hopcape.odo.web.blog.resources.bl_discard_dek
import com.hopcape.odo.web.blog.resources.bl_discard_heading
import com.hopcape.odo.web.blog.resources.bl_import_action
import com.hopcape.odo.web.blog.resources.bl_import_dek
import com.hopcape.odo.web.blog.resources.bl_import_heading
import com.hopcape.odo.web.blog.resources.bl_import_placeholder
import com.hopcape.odo.web.blog.resources.bl_conflict_new_slug
import com.hopcape.odo.web.blog.resources.bl_conflict_options
import com.hopcape.odo.web.blog.resources.bl_conflict_replace
import com.hopcape.odo.web.blog.resources.bl_conflict_replace_dek
import com.hopcape.odo.web.blog.resources.bl_conflict_replace_heading
import com.hopcape.odo.web.blog.resources.bl_conflict_use
import com.hopcape.odo.web.blog.resources.bl_media_cancel
import com.hopcape.odo.web.blog.resources.bl_media_empty
import com.hopcape.odo.web.blog.resources.bl_media_insert_heading
import com.hopcape.odo.web.blog.resources.bl_publish_breadcrumb
import com.hopcape.odo.web.blog.resources.bl_publish_category
import com.hopcape.odo.web.blog.resources.bl_publish_counter
import com.hopcape.odo.web.blog.resources.bl_publish_google_preview
import com.hopcape.odo.web.blog.resources.bl_publish_meta
import com.hopcape.odo.web.blog.resources.bl_publish_publish_now
import com.hopcape.odo.web.blog.resources.bl_publish_save_draft
import com.hopcape.odo.web.blog.resources.bl_publish_seo_title
import com.hopcape.odo.web.blog.resources.bl_publish_slug
import com.hopcape.odo.web.blog.resources.bl_published_copy
import com.hopcape.odo.web.blog.resources.bl_published_dek
import com.hopcape.odo.web.blog.resources.bl_published_heading
import com.hopcape.odo.web.blog.resources.bl_published_view
import com.hopcape.odo.web.blog.resources.bl_unpublish_advice
import com.hopcape.odo.web.blog.resources.bl_unpublish_anyway
import com.hopcape.odo.web.blog.resources.bl_unpublish_heading
import com.hopcape.odo.web.blog.resources.bl_unpublish_to_draft
import com.hopcape.odo.web.blog.resources.bl_unsaved_dek
import com.hopcape.odo.web.blog.resources.bl_unsaved_discard
import com.hopcape.odo.web.blog.resources.bl_unsaved_heading
import com.hopcape.odo.web.blog.resources.bl_unsaved_save
import com.hopcape.odo.web.blog.ui.component.Eyebrow
import com.hopcape.odo.web.blog.ui.component.FilterChip
import com.hopcape.odo.web.blog.ui.component.BlogTextField
import com.hopcape.odo.web.blog.ui.component.LabelledField
import com.hopcape.odo.web.blog.ui.component.PillButton
import com.hopcape.odo.web.blog.ui.component.TextLink
import com.hopcape.odo.web.blog.ui.theme.BlogThemeTokens
import org.jetbrains.compose.resources.stringResource

/**
 * Everything that appears on top of the editor.
 *
 * One composable with one `when`, because [EditorSheet] is one value: they are
 * mutually exclusive by construction, so a conflict cannot end up drawn behind
 * the publish form it replaced.
 */
@Composable
fun EditorSheets(state: EditorUiState, onEvent: (EditorEvent) -> Unit) {
    when (val sheet = state.sheet) {
        EditorSheet.None -> Unit
        EditorSheet.Publish -> PublishSheet(state, onEvent)
        is EditorSheet.Conflict -> ConflictSheet(sheet, onEvent)
        is EditorSheet.Published -> PublishedSheet(sheet, onEvent)
        EditorSheet.Unsaved -> UnsavedSheet(onEvent)
        EditorSheet.InsertImage -> InsertImageSheet(state, onEvent)
        is EditorSheet.Unpublish -> UnpublishSheet(onEvent)
        EditorSheet.Import -> ImportSheet(state, onEvent)
        EditorSheet.Category -> CategorySheet(state, onEvent)
        EditorSheet.Discard -> DiscardSheet(onEvent)
    }
}

/**
 * Paste a post in.
 *
 * The field is deliberately large and monospaced — what goes in it is JSON, and a
 * proportional font at body size makes a missing bracket impossible to spot.
 * Nothing is saved by importing: it lands in the editor and the author reads it
 * before deciding, which is what separates an import from an overwrite.
 */
@Composable
private fun ImportSheet(state: EditorUiState, onEvent: (EditorEvent) -> Unit) {
    val colors = BlogThemeTokens.colors
    var pasted by remember { mutableStateOf("") }

    Overlay(onDismiss = { onEvent(EditorEvent.SheetDismissed) }, maxWidth = 620) {
        Text(
            text = stringResource(Res.string.bl_import_heading),
            color = colors.text,
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(Res.string.bl_import_dek),
            color = colors.dim,
            style = MaterialTheme.typography.bodyLarge,
        )
        BlogTextField(
            value = pasted,
            onValueChange = { pasted = it },
            placeholder = stringResource(Res.string.bl_import_placeholder),
            singleLine = false,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
        )
        state.error?.let {
            Text(it.resolve(), color = colors.danger, style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(
                text = stringResource(Res.string.bl_media_cancel),
                onClick = { onEvent(EditorEvent.SheetDismissed) },
                filled = false,
            )
            PillButton(
                text = stringResource(Res.string.bl_import_action),
                onClick = { onEvent(EditorEvent.Imported(pasted)) },
                enabled = pasted.isNotBlank(),
            )
        }
    }
}

/**
 * Which category the post is filed under.
 *
 * Its own sheet, reachable from the editor bar, rather than only from the publish
 * form. A category is a decision an author makes while writing — it is what the
 * piece is about — and burying it behind the publish button meant it was picked
 * once, at the end, under time pressure.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategorySheet(state: EditorUiState, onEvent: (EditorEvent) -> Unit) {
    val colors = BlogThemeTokens.colors
    Overlay(onDismiss = { onEvent(EditorEvent.SheetDismissed) }) {
        Text(
            text = stringResource(Res.string.bl_publish_category),
            color = colors.text,
            style = MaterialTheme.typography.headlineMedium,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            state.categories.forEach { category ->
                FilterChip(
                    text = category.name,
                    selected = state.seo.categorySlug == category.slug,
                    onClick = {
                        onEvent(EditorEvent.CategoryChanged(category.slug))
                        onEvent(EditorEvent.SheetDismissed)
                    },
                )
            }
        }
    }
}

/**
 * Throwing a draft away.
 *
 * Only ever offered for something unpublished, so the copy can say the true thing:
 * nothing links to it. A published post gets the unpublish sheet instead, which
 * argues for keeping the URL alive.
 */
@Composable
private fun DiscardSheet(onEvent: (EditorEvent) -> Unit) {
    val colors = BlogThemeTokens.colors
    Overlay(onDismiss = { onEvent(EditorEvent.SheetDismissed) }) {
        Text(
            text = stringResource(Res.string.bl_discard_heading),
            color = colors.text,
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(Res.string.bl_discard_dek),
            color = colors.dim,
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Keeping it is the filled button. The destructive choice should never
            // be the one a hurried hand lands on.
            PillButton(
                text = stringResource(Res.string.bl_discard_cancel),
                onClick = { onEvent(EditorEvent.SheetDismissed) },
            )
            PillButton(
                text = stringResource(Res.string.bl_discard_confirm),
                onClick = { onEvent(EditorEvent.DiscardConfirmed) },
                filled = false,
                danger = true,
            )
        }
    }
}

/**
 * The scrim and the card.
 *
 * The scrim is clickable and dismisses — except where the caller says otherwise.
 * The slug conflict does not dismiss on a stray click: it is a question with two
 * answers and no third one, and closing it would silently abandon a publish that
 * the author thought was in progress.
 */
@Composable
private fun Overlay(
    onDismiss: (() -> Unit)?,
    maxWidth: Int = 480,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = BlogThemeTokens.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .then(if (onDismiss != null) Modifier.clickable(onClick = onDismiss) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = maxWidth.dp)
                .fillMaxWidth()
                .padding(24.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(18.dp))
                // Swallows clicks so they do not reach the scrim behind.
                .clickable(enabled = false) {}
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            content = content,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PublishSheet(state: EditorUiState, onEvent: (EditorEvent) -> Unit) {
    val colors = BlogThemeTokens.colors
    Overlay(onDismiss = { onEvent(EditorEvent.SheetDismissed) }, maxWidth = 560) {
        Text(
            text = stringResource(Res.string.bl_publish_publish_now),
            color = colors.text,
            style = MaterialTheme.typography.headlineMedium,
        )

        LabelledField(
            label = stringResource(Res.string.bl_publish_seo_title),
            value = state.seo.seoTitle,
            onValueChange = { onEvent(EditorEvent.SeoTitleChanged(it)) },
            trailingLabel = stringResource(
                Res.string.bl_publish_counter,
                state.seo.seoTitle.length,
                SeoDraft.TITLE_LIMIT,
            ),
            // Amber past the limit, not a hard stop: Google truncates, it does not
            // reject, and blocking the keystroke would be lying about that.
            trailingIsWarning = state.seoTitleOverLimit,
        )

        LabelledField(
            label = stringResource(Res.string.bl_publish_slug),
            value = state.seo.slug,
            onValueChange = { onEvent(EditorEvent.SlugChanged(it)) },
            prefix = stringResource(Res.string.bl_admin_slug_prefix),
        )

        LabelledField(
            label = stringResource(Res.string.bl_publish_meta),
            value = state.seo.metaDescription,
            onValueChange = { onEvent(EditorEvent.MetaChanged(it)) },
            trailingLabel = stringResource(
                Res.string.bl_publish_counter,
                state.seo.metaDescription.length,
                SeoDraft.DESCRIPTION_LIMIT,
            ),
            trailingIsWarning = state.metaOverLimit,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(Res.string.bl_publish_category),
                color = colors.dim,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.categories.forEach { category ->
                    FilterChip(
                        text = category.name,
                        selected = state.seo.categorySlug == category.slug,
                        onClick = { onEvent(EditorEvent.CategoryChanged(category.slug)) },
                    )
                }
            }
        }

        GooglePreview(state)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(
                text = stringResource(Res.string.bl_publish_save_draft),
                onClick = { onEvent(EditorEvent.SaveTapped) },
                filled = false,
                enabled = !state.saving,
            )
            PillButton(
                text = stringResource(Res.string.bl_publish_publish_now),
                onClick = { onEvent(EditorEvent.PublishConfirmed) },
                enabled = state.canPublish,
            )
        }
    }
}

/**
 * What the post will look like in a search result.
 *
 * The point of the whole sheet. An author writing for search intent is writing
 * *this* — three lines a stranger decides on — and showing it next to the fields
 * is what makes the character counters mean something.
 */
@Composable
private fun GooglePreview(state: EditorUiState) {
    val colors = BlogThemeTokens.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceRaised)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Eyebrow(stringResource(Res.string.bl_publish_google_preview))
        Text(
            text = stringResource(Res.string.bl_publish_breadcrumb, state.seo.slug),
            color = colors.muted,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = state.seo.seoTitle.take(SeoDraft.TITLE_LIMIT),
            color = colors.link,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = state.seo.metaDescription.take(SeoDraft.DESCRIPTION_LIMIT),
            color = colors.dim,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ConflictSheet(sheet: EditorSheet.Conflict, onEvent: (EditorEvent) -> Unit) {
    val colors = BlogThemeTokens.colors
    // No dismiss: two answers, no third one. Closing it would abandon a publish
    // the author believes is in progress.
    Overlay(onDismiss = null) {
        Text(
            text = stringResource(Res.string.bl_conflict_message, sheet.outcome.heldBy),
            color = colors.text,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(Res.string.bl_conflict_options),
            color = colors.muted,
            style = MaterialTheme.typography.bodyMedium,
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(Res.string.bl_conflict_new_slug),
                color = colors.text,
                style = MaterialTheme.typography.titleMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = sheet.outcome.suggestion,
                    color = colors.dim,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.weight(1f))
                PillButton(
                    text = stringResource(Res.string.bl_conflict_use),
                    onClick = { onEvent(EditorEvent.ConflictResolved(replaceExisting = false)) },
                    filled = false,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(Res.string.bl_conflict_replace_heading),
                color = colors.text,
                style = MaterialTheme.typography.titleMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.bl_conflict_replace_dek),
                    color = colors.dim,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.weight(1f))
                PillButton(
                    text = stringResource(Res.string.bl_conflict_replace),
                    onClick = { onEvent(EditorEvent.ConflictResolved(replaceExisting = true)) },
                    filled = false,
                    danger = true,
                )
            }
        }
    }
}

@Composable
private fun PublishedSheet(sheet: EditorSheet.Published, onEvent: (EditorEvent) -> Unit) {
    val colors = BlogThemeTokens.colors
    Overlay(onDismiss = { onEvent(EditorEvent.SheetDismissed) }) {
        Text(
            text = stringResource(Res.string.bl_published_heading),
            color = colors.success,
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            // The one honest thing to say at this moment: it is live, and Google
            // has not seen it yet. Without this line the next question is always
            // "why can't I find it".
            text = stringResource(Res.string.bl_published_dek),
            color = colors.dim,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(Res.string.bl_admin_slug_prefix) + sheet.slug,
            color = colors.text,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(
                text = stringResource(Res.string.bl_published_copy),
                onClick = { onEvent(EditorEvent.SheetDismissed) },
                filled = false,
            )
            PillButton(
                text = stringResource(Res.string.bl_published_view),
                onClick = { onEvent(EditorEvent.SheetDismissed) },
            )
        }
    }
}

@Composable
private fun UnsavedSheet(onEvent: (EditorEvent) -> Unit) {
    val colors = BlogThemeTokens.colors
    Overlay(onDismiss = { onEvent(EditorEvent.SheetDismissed) }) {
        Text(
            text = stringResource(Res.string.bl_unsaved_heading),
            color = colors.text,
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(Res.string.bl_unsaved_dek),
            color = colors.dim,
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Discard is the quiet one and save is the filled one, deliberately:
            // the destructive choice should never be the one a hurried hand hits.
            PillButton(
                text = stringResource(Res.string.bl_unsaved_discard),
                onClick = { onEvent(EditorEvent.LeaveConfirmed) },
                filled = false,
            )
            PillButton(
                text = stringResource(Res.string.bl_unsaved_save),
                onClick = { onEvent(EditorEvent.SaveTapped) },
            )
        }
    }
}

@Composable
private fun UnpublishSheet(onEvent: (EditorEvent) -> Unit) {
    val colors = BlogThemeTokens.colors
    Overlay(onDismiss = { onEvent(EditorEvent.SheetDismissed) }) {
        Text(
            text = stringResource(Res.string.bl_unpublish_heading),
            color = colors.text,
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(Res.string.bl_unpublish_advice),
            color = colors.dim,
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(
                text = stringResource(Res.string.bl_unpublish_to_draft),
                onClick = { onEvent(EditorEvent.UnpublishConfirmed) },
            )
            PillButton(
                text = stringResource(Res.string.bl_unpublish_anyway),
                onClick = { onEvent(EditorEvent.UnpublishConfirmed) },
                filled = false,
                danger = true,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InsertImageSheet(state: EditorUiState, onEvent: (EditorEvent) -> Unit) {
    val colors = BlogThemeTokens.colors
    Overlay(onDismiss = { onEvent(EditorEvent.SheetDismissed) }, maxWidth = 520) {
        Text(
            text = stringResource(Res.string.bl_media_insert_heading),
            color = colors.text,
            style = MaterialTheme.typography.headlineMedium,
        )
        if (state.media.isEmpty()) {
            Text(
                text = stringResource(Res.string.bl_media_empty),
                color = colors.muted,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.media.forEach { item ->
                FilterChip(
                    text = item.name,
                    selected = false,
                    onClick = { onEvent(EditorEvent.ImageChosen(item)) },
                )
            }
        }
        TextLink(
            text = stringResource(Res.string.bl_media_cancel),
            onClick = { onEvent(EditorEvent.SheetDismissed) },
            color = colors.muted,
        )
    }
}
