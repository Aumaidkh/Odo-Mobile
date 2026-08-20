package com.hopcape.odo.web.blog.ui.screen.admin

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.blog.domain.model.ArticleBlock
import com.hopcape.odo.web.blog.domain.model.PostStatus
import com.hopcape.odo.web.blog.presentation.admin.editor.BlockKind
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
import com.hopcape.odo.web.blog.resources.bl_editor_callout
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
import com.hopcape.odo.web.blog.ui.component.Hairline
import com.hopcape.odo.web.blog.ui.component.PillButton
import com.hopcape.odo.web.blog.ui.component.TextLink
import com.hopcape.odo.web.blog.ui.theme.BlogThemeTokens
import org.jetbrains.compose.resources.stringResource

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

    // Which block the toolbar acts on. A UI concern — the ViewModel has no
    // business knowing where a caret is.
    var focused by remember { mutableStateOf<Int?>(null) }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize()) {
            EditorBar(state, onEvent, onBack)
            Hairline()
            EditorToolbar(state, focused, onEvent)
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
                    PlainField(
                        value = state.title,
                        onValueChange = { onEvent(EditorEvent.TitleChanged(it)) },
                        placeholder = stringResource(Res.string.bl_editor_title_placeholder),
                        style = MaterialTheme.typography.displayMedium,
                    )

                    // The design shows this only on an untouched post — it is
                    // advice for somebody staring at an empty page, and it would
                    // be clutter next to eight hundred words.
                    if (state.blocks.isEmpty()) {
                        Tip()
                    }

                    state.blocks.forEachIndexed { index, block ->
                        BlockField(
                            block = block,
                            onChange = { onEvent(EditorEvent.BlockChanged(index, it)) },
                            onRemove = { onEvent(EditorEvent.BlockRemoved(index)) },
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
        if (state.status == PostStatus.PUBLISHED) {
            TextLink(
                text = stringResource(Res.string.bl_editor_unpublish),
                onClick = { onEvent(EditorEvent.UnpublishTapped) },
                color = colors.muted,
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
 * The block buttons append a block of that kind. **B** and **I** wrap the focused
 * block's text in markers rather than the selection — Compose gives a selection
 * only through `TextFieldValue`, which every block would then have to carry, and
 * the markers are visible either way. It is the honest 80%: the button does
 * something real and the result round-trips.
 */
@Composable
private fun EditorToolbar(state: EditorUiState, focused: Int?, onEvent: (EditorEvent) -> Unit) {
    val colors = BlogThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ToolButton(stringResource(Res.string.bl_editor_bold), enabled = focused != null) {
            focused?.let { onEvent(EditorEvent.BlockChanged(it, state.blocks[it].editableText() + "****")) }
        }
        ToolButton(stringResource(Res.string.bl_editor_italic), enabled = focused != null) {
            focused?.let { onEvent(EditorEvent.BlockChanged(it, state.blocks[it].editableText() + "**")) }
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
        ToolButton(stringResource(Res.string.bl_editor_image)) {
            onEvent(EditorEvent.InsertImageTapped)
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(Res.string.bl_editor_counts, state.wordCount, state.readingMinutes),
            color = colors.muted,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
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
@Composable
private fun BlockField(
    block: ArticleBlock,
    onChange: (String) -> Unit,
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
                    value = block.editableText(),
                    onValueChange = onChange,
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
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    style: androidx.compose.ui.text.TextStyle,
    onFocused: (() -> Unit)? = null,
) {
    val colors = BlogThemeTokens.colors
    Box(Modifier.fillMaxWidth()) {
        if (value.isEmpty()) {
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
