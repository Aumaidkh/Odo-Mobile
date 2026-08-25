package com.hopcape.odo.web.blog.ui.screen.admin

import com.hopcape.odo.web.blog.resources.bl_editor_divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.blog.domain.model.ArticleBlock
import com.hopcape.odo.web.blog.domain.model.PostStatus
import com.hopcape.odo.web.blog.presentation.admin.editor.BOLD_MARKER
import com.hopcape.odo.web.blog.presentation.admin.editor.BlockKind
import com.hopcape.odo.web.blog.presentation.admin.editor.ITALIC_MARKER
import com.hopcape.odo.web.blog.presentation.admin.editor.EditorEvent
import com.hopcape.odo.web.blog.presentation.admin.editor.EditorSheet
import com.hopcape.odo.web.blog.presentation.admin.editor.EditorUiState
import com.hopcape.odo.web.blog.presentation.admin.editor.editableText
import com.hopcape.odo.web.blog.presentation.state.Loadable
import com.hopcape.odo.web.blog.presentation.state.resolve
import com.hopcape.odo.web.blog.resources.Res
import com.hopcape.odo.web.blog.resources.bl_editor_body_placeholder
import com.hopcape.odo.web.blog.resources.bl_editor_back
import com.hopcape.odo.web.blog.resources.bl_editor_bold
import com.hopcape.odo.web.blog.resources.bl_editor_action_card
import com.hopcape.odo.web.blog.resources.bl_editor_callout
import com.hopcape.odo.web.blog.resources.bl_editor_category_none
import com.hopcape.odo.web.blog.resources.bl_editor_discard
import com.hopcape.odo.web.blog.resources.bl_editor_import
import com.hopcape.odo.web.blog.resources.bl_editor_counts
import com.hopcape.odo.web.blog.resources.bl_editor_h2
import com.hopcape.odo.web.blog.resources.bl_editor_image
import com.hopcape.odo.web.blog.resources.bl_editor_italic
import com.hopcape.odo.web.blog.resources.bl_editor_paragraph
import com.hopcape.odo.web.blog.resources.bl_editor_publish
import com.hopcape.odo.web.blog.resources.bl_editor_published_badge
import com.hopcape.odo.web.blog.resources.bl_editor_remove_block
import com.hopcape.odo.web.blog.resources.bl_editor_save
import com.hopcape.odo.web.blog.resources.bl_editor_saved_now
import com.hopcape.odo.web.blog.resources.bl_editor_saving
import com.hopcape.odo.web.blog.resources.bl_editor_tip
import com.hopcape.odo.web.blog.resources.bl_editor_tip_label
import com.hopcape.odo.web.blog.resources.bl_editor_title_placeholder
import com.hopcape.odo.web.blog.resources.bl_editor_unpublish
import com.hopcape.odo.web.blog.resources.bl_editor_unsaved
import com.hopcape.odo.web.blog.resources.bl_editor_unsaved_changes
import com.hopcape.odo.web.blog.ui.component.Eyebrow
import com.hopcape.odo.web.blog.ui.component.FilterChip
import com.hopcape.odo.web.blog.ui.component.Hairline
import com.hopcape.odo.web.blog.ui.component.PillButton
import com.hopcape.odo.web.blog.ui.component.TextLink
import com.hopcape.odo.web.blog.ui.theme.BlogThemeTokens
import org.jetbrains.compose.resources.stringResource
import com.hopcape.odo.web.blog.presentation.admin.editor.ShowcaseField
import com.hopcape.odo.web.blog.presentation.admin.editor.field
import com.hopcape.odo.web.blog.resources.bl_editor_action_body
import com.hopcape.odo.web.blog.resources.bl_editor_action_body_hint
import com.hopcape.odo.web.blog.resources.bl_editor_action_cta
import com.hopcape.odo.web.blog.resources.bl_editor_action_cta_hint
import com.hopcape.odo.web.blog.resources.bl_editor_action_eyebrow
import com.hopcape.odo.web.blog.resources.bl_editor_action_link
import com.hopcape.odo.web.blog.resources.bl_editor_action_link_hint
import com.hopcape.odo.web.blog.resources.bl_editor_action_screenshot
import com.hopcape.odo.web.blog.resources.bl_editor_action_screenshot_hint
import com.hopcape.odo.web.blog.resources.bl_editor_action_title
import com.hopcape.odo.web.blog.resources.bl_editor_action_title_hint

/** The measure the design writes at. Wider and the line length stops being prose. */
private val EDITOR_MEASURE = 720.dp

/**
 * Writing a post.
 *
 * It does not sit inside [com.hopcape.odo.web.blog.ui.chrome.AdminShell]: a page
 * that is one long text field should not spend its top edge on links away from
 * itself, so the shell's tabs are replaced by this screen's own bar.
 *
 * The body is a list of blocks, each its own field, styled as what it is — a
 * heading looks like a heading while it is being typed. Bold inside a paragraph
 * is written as `**markers**` and stays visible; see
 * [com.hopcape.odo.web.blog.presentation.admin.editor.BlockText] for why.
 */
@Composable
fun EditorScreen(
    state: EditorUiState,
    onEvent: (EditorEvent) -> Unit,
    onBack: () -> Unit,
) {
    val colors = BlogThemeTokens.colors

    // Which block the toolbar acts on, and what is in each field. Both are UI
    // concerns — the ViewModel has no business knowing where a caret is — but the
    // screen has to own them rather than each field, because **B** and *I* act on
    // a selection that lives in a field the toolbar is not inside.
    var focused by remember { mutableStateOf<Int?>(null) }
    val values = remember { mutableStateMapOf<Int, TextFieldValue>() }

    // A load or an import replaced the body; the fields have to be re-read.
    LaunchedEffect(state.revision) {
        values.clear()
        focused = null
    }

    /**
     * Wraps the selection in [marker], or opens an empty pair at the caret.
     *
     * The caret lands inside the markers either way, so the next keystroke is the
     * emphasised word rather than the thing after it.
     */
    fun wrap(marker: String) {
        val index = focused ?: return
        val current = values[index] ?: return
        val text = current.text
        val selection = current.selection
        val wrapped = if (selection.collapsed) {
            text.take(selection.start) + marker + marker + text.drop(selection.start)
        } else {
            text.take(selection.min) + marker + text.substring(selection.min, selection.max) +
                marker + text.drop(selection.max)
        }
        val caret = if (selection.collapsed) {
            selection.start + marker.length
        } else {
            selection.max + marker.length * 2
        }
        values[index] = TextFieldValue(wrapped, TextRange(caret))
        onEvent(EditorEvent.BlockChanged(index, wrapped))
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize()) {
            EditorBar(state, onEvent, onBack)
            Hairline()
            EditorToolbar(state, focused, ::wrap, onEvent)
            Hairline()

            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = EDITOR_MEASURE)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 36.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // The read has to be drawn, not assumed. Without this a post
                    // that failed to load looks exactly like a blank new one, and
                    // the author starts typing over a post they think is empty.
                    if (state.loaded is Loadable.Failed) {
                        Text(
                            text = (state.loaded as Loadable.Failed).message.resolve(),
                            color = colors.danger,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        return@Column
                    }
                    TitleField(
                        value = state.title,
                        onValueChange = { onEvent(EditorEvent.TitleChanged(it)) },
                    )

                    // The design shows this only on an untouched post — it is
                    // advice for somebody staring at an empty page, and it would
                    // be clutter next to eight hundred words.
                    if (state.blocks.isEmpty()) {
                        Tip()
                    }

                    state.blocks.forEachIndexed { index, block ->
                        // Seeded once, then the field's own. Deliberately not
                        // re-synced against the block on every render: pressing
                        // **B** with nothing selected leaves an empty `****`, which
                        // parses to no runs and would be written straight back over
                        // the caret sitting between them. `revision` above is what
                        // says the body really was replaced.
                        // An action card is five fields, not one. It gets its own
                        // editor and never touches `values` — none of its fields
                        // carry markers, so there is nothing to parse and nothing
                        // to be written back over a caret.
                        // Nothing to type into, so it never joins `values` and
                        // never takes focus — it is drawn as the rule it will be,
                        // with the one action it has.
                        if (block is ArticleBlock.Divider) {
                            DividerBlockEditor(
                                onRemove = {
                                    values.remove(index)
                                    onEvent(EditorEvent.BlockRemoved(index))
                                },
                            )
                            return@forEachIndexed
                        }

                        if (block is ArticleBlock.AppShowcase) {
                            ActionBlockEditor(
                                block = block,
                                onFieldChange = { field, value ->
                                    onEvent(EditorEvent.ShowcaseFieldChanged(index, field, value))
                                },
                                onRemove = {
                                    values.remove(index)
                                    onEvent(EditorEvent.BlockRemoved(index))
                                },
                            )
                            return@forEachIndexed
                        }

                        val text = block.editableText()
                        if (values[index] == null) values[index] = TextFieldValue(text)

                        BlockField(
                            block = block,
                            value = values[index] ?: TextFieldValue(text),
                            onValueChange = {
                                values[index] = it
                                onEvent(EditorEvent.BlockChanged(index, it.text))
                            },
                            onRemove = {
                                values.remove(index)
                                onEvent(EditorEvent.BlockRemoved(index))
                            },
                            onFocused = { focused = index },
                        )
                    }

                    state.error?.let {
                        Text(
                            text = it.resolve(),
                            color = colors.danger,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
        }

        EditorSheets(state, onEvent)
    }
}

@Composable
private fun EditorBar(state: EditorUiState, onEvent: (EditorEvent) -> Unit, onBack: () -> Unit) {
    val colors = BlogThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Back is a request, not a navigation: the editor answers it, and answers
        // differently when there is unsaved work.
        TextLink(stringResource(Res.string.bl_editor_back), onBack, color = colors.muted)
        Text(
            text = when {
                state.saving -> stringResource(Res.string.bl_editor_saving)
                !state.everSaved -> stringResource(Res.string.bl_editor_unsaved)
                state.dirty -> stringResource(Res.string.bl_editor_unsaved_changes)
                else -> stringResource(Res.string.bl_editor_saved_now)
            },
            color = if (state.dirty || !state.everSaved) colors.warning else colors.muted,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (state.status == PostStatus.PUBLISHED) {
            Text(
                text = stringResource(Res.string.bl_editor_published_badge),
                color = colors.success,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        }
        Spacer(Modifier.weight(1f))
        // The category, visible while writing rather than only in the publish
        // form. It is what the piece is about, which is a decision an author makes
        // early; buried behind Publish it was picked once, at the end, in a hurry.
        CategoryTag(state, onEvent)
        if (state.status == PostStatus.PUBLISHED) {
            TextLink(
                text = stringResource(Res.string.bl_editor_unpublish),
                onClick = { onEvent(EditorEvent.UnpublishTapped) },
                color = colors.muted,
            )
        } else if (state.everSaved) {
            // Only for something unpublished. A published post gets the unpublish
            // sheet, which argues for keeping its URL alive.
            TextLink(
                text = stringResource(Res.string.bl_editor_discard),
                onClick = { onEvent(EditorEvent.DiscardTapped) },
                color = colors.danger,
            )
        }
        PillButton(
            text = stringResource(Res.string.bl_editor_save),
            onClick = { onEvent(EditorEvent.SaveTapped) },
            filled = false,
            enabled = state.dirty && !state.saving,
        )
        PillButton(
            text = stringResource(Res.string.bl_editor_publish),
            onClick = { onEvent(EditorEvent.PublishTapped) },
            enabled = state.canPublish,
        )
    }
}

/**
 * The toolbar.
 *
 * **B** and *I* wrap the selection in the focused block — the markers are what
 * the stored runs are read back from, so what you see is what round-trips. With
 * nothing selected they open an empty pair and put the caret between them.
 *
 * The block buttons append; the last three open sheets.
 */
@Composable
private fun EditorToolbar(
    state: EditorUiState,
    focused: Int?,
    onWrap: (String) -> Unit,
    onEvent: (EditorEvent) -> Unit,
) {
    val colors = BlogThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ToolButton(stringResource(Res.string.bl_editor_bold), enabled = focused != null) {
            onWrap(BOLD_MARKER)
        }
        ToolButton(stringResource(Res.string.bl_editor_italic), enabled = focused != null) {
            onWrap(ITALIC_MARKER)
        }
        ToolButton(stringResource(Res.string.bl_editor_paragraph)) {
            onEvent(EditorEvent.BlockAdded(BlockKind.PARAGRAPH))
        }
        ToolButton(stringResource(Res.string.bl_editor_h2)) {
            onEvent(EditorEvent.BlockAdded(BlockKind.HEADING))
        }
        ToolButton(stringResource(Res.string.bl_editor_callout)) {
            onEvent(EditorEvent.BlockAdded(BlockKind.CALLOUT))
        }
        ToolButton(stringResource(Res.string.bl_editor_divider)) {
            onEvent(EditorEvent.BlockAdded(BlockKind.DIVIDER))
        }
        // The one block that sends a reader to the app. Without it an article can
        // answer the question and never offer anything.
        ToolButton(stringResource(Res.string.bl_editor_action_card)) {
            onEvent(EditorEvent.BlockAdded(BlockKind.ACTION))
        }
        ToolButton(stringResource(Res.string.bl_editor_image)) {
            onEvent(EditorEvent.InsertImageTapped)
        }
        ToolButton(stringResource(Res.string.bl_editor_import)) {
            onEvent(EditorEvent.ImportTapped)
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(Res.string.bl_editor_counts, state.wordCount, state.readingMinutes),
            color = colors.muted,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** The current category, as a tag that opens the picker. */
@Composable
private fun CategoryTag(state: EditorUiState, onEvent: (EditorEvent) -> Unit) {
    val name = state.categories.firstOrNull { it.slug == state.seo.categorySlug }?.name
    FilterChip(
        text = name ?: stringResource(Res.string.bl_editor_category_none),
        selected = name != null,
        onClick = { onEvent(EditorEvent.CategoryTapped) },
    )
}

@Composable
private fun ToolButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = BlogThemeTokens.colors
    TextLink(
        text = label,
        onClick = { if (enabled) onClick() },
        color = if (enabled) colors.dim else colors.border,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** One block, styled as whatever it is while it is being written. */
/**
 * The action card, edited as what it is.
 *
 * Five labelled boxes rather than one. The link box is the point of the whole
 * block — a card whose button always went to the same place could not send a
 * reader about insurance renewals anywhere useful — and its placeholder says
 * what blank means so nobody has to guess.
 */
@Composable
private fun ActionBlockEditor(
    block: ArticleBlock.AppShowcase,
    onFieldChange: (ShowcaseField, String) -> Unit,
    onRemove: () -> Unit,
) {
    val colors = BlogThemeTokens.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceRaised)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(stringResource(Res.string.bl_editor_action_eyebrow), color = colors.link)
            Spacer(Modifier.weight(1f))
            TextLink(
                text = stringResource(Res.string.bl_editor_remove_block),
                onClick = onRemove,
                color = colors.muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        ActionField(
            label = stringResource(Res.string.bl_editor_action_title),
            hint = stringResource(Res.string.bl_editor_action_title_hint),
            value = block.field(ShowcaseField.TITLE),
            style = MaterialTheme.typography.titleLarge,
        ) { onFieldChange(ShowcaseField.TITLE, it) }

        ActionField(
            label = stringResource(Res.string.bl_editor_action_body),
            hint = stringResource(Res.string.bl_editor_action_body_hint),
            value = block.field(ShowcaseField.BODY),
            style = MaterialTheme.typography.titleMedium,
        ) { onFieldChange(ShowcaseField.BODY, it) }

        ActionField(
            label = stringResource(Res.string.bl_editor_action_cta),
            hint = stringResource(Res.string.bl_editor_action_cta_hint),
            value = block.field(ShowcaseField.CTA_LABEL),
            style = MaterialTheme.typography.titleMedium,
        ) { onFieldChange(ShowcaseField.CTA_LABEL, it) }

        ActionField(
            label = stringResource(Res.string.bl_editor_action_link),
            hint = stringResource(Res.string.bl_editor_action_link_hint),
            value = block.field(ShowcaseField.CTA_LINK),
            style = MaterialTheme.typography.bodyMedium,
        ) { onFieldChange(ShowcaseField.CTA_LINK, it) }

        ActionField(
            label = stringResource(Res.string.bl_editor_action_screenshot),
            hint = stringResource(Res.string.bl_editor_action_screenshot_hint),
            value = block.field(ShowcaseField.SCREENSHOT),
            style = MaterialTheme.typography.bodyMedium,
        ) { onFieldChange(ShowcaseField.SCREENSHOT, it) }
    }
}

/** One labelled box inside the action card editor. */
@Composable
private fun ActionField(
    label: String,
    hint: String,
    value: String,
    style: androidx.compose.ui.text.TextStyle,
    onValueChange: (String) -> Unit,
) {
    val colors = BlogThemeTokens.colors
    // The field owns its caret. Rebuilding a TextFieldValue from the state on
    // every recomposition would drag the cursor to the end after each keystroke,
    // so anybody fixing a typo mid-sentence would type the rest of the word at
    // the end of the line. The state is only pushed back in when it changed for
    // some other reason — a load, or an import.
    var local by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    LaunchedEffect(value) {
        if (value != local.text) local = TextFieldValue(value, TextRange(value.length))
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = colors.muted, style = MaterialTheme.typography.bodySmall)
        PlainField(
            value = local,
            onValueChange = {
                local = it
                onValueChange(it.text)
            },
            placeholder = hint,
            style = style,
        )
    }
}

@Composable
private fun BlockField(
    block: ArticleBlock,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onRemove: () -> Unit,
    onFocused: () -> Unit,
) {
    val colors = BlogThemeTokens.colors
    val style = when (block) {
        is ArticleBlock.Section -> MaterialTheme.typography.headlineMedium
        is ArticleBlock.AppShowcase -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleLarge
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (block is ArticleBlock.Section) 20.dp else 4.dp),
    ) {
        if (block is ArticleBlock.Callout) {
            Eyebrow(block.label, color = colors.warning, modifier = Modifier.padding(bottom = 6.dp))
        }
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (block is ArticleBlock.Callout || block is ArticleBlock.AppShowcase) {
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surfaceRaised)
                                .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                PlainField(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = stringResource(Res.string.bl_editor_body_placeholder),
                    style = style,
                    onFocused = onFocused,
                )
            }
            TextLink(
                text = stringResource(Res.string.bl_editor_remove_block),
                onClick = onRemove,
                color = colors.muted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 12.dp, top = 6.dp),
            )
        }
    }
}

/** A text field with no chrome at all — the page is the field. */
@Composable
private fun PlainField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    style: androidx.compose.ui.text.TextStyle,
    onFocused: (() -> Unit)? = null,
) {
    val colors = BlogThemeTokens.colors
    Box(Modifier.fillMaxWidth()) {
        if (value.text.isEmpty()) {
            Text(placeholder, color = colors.muted, style = style)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = style.copy(color = colors.text),
            cursorBrush = SolidColor(colors.text),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (it.isFocused) onFocused?.invoke() },
        )
    }
}

/** The title. One line, no selection tricks — the toolbar never touches it. */
@Composable
private fun TitleField(value: String, onValueChange: (String) -> Unit) {
    val colors = BlogThemeTokens.colors
    Box(Modifier.fillMaxWidth()) {
        if (value.isEmpty()) {
            Text(
                text = stringResource(Res.string.bl_editor_title_placeholder),
                color = colors.muted,
                style = MaterialTheme.typography.displayMedium,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.displayMedium.copy(color = colors.text),
            cursorBrush = SolidColor(colors.text),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Tip() {
    val colors = BlogThemeTokens.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceRaised)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Eyebrow(stringResource(Res.string.bl_editor_tip_label))
        Text(
            text = stringResource(Res.string.bl_editor_tip),
            color = colors.dim,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** True when something is on top of the editor. Kept here so the Box reads plainly. */
internal val EditorUiState.hasSheet: Boolean get() = sheet != EditorSheet.None

/**
 * A rule in the editor: what it will look like, and a way to take it out.
 *
 * Deliberately not a text field with a placeholder. A divider has no content, and a
 * caret sitting in one would be a caret with nowhere to go.
 */
@Composable
private fun DividerBlockEditor(onRemove: () -> Unit) {
    val colors = BlogThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = colors.border)
        Spacer(Modifier.width(12.dp))
        TextLink(
            text = stringResource(Res.string.bl_editor_remove_block),
            onClick = onRemove,
            color = colors.muted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
