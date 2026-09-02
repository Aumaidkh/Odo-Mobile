package com.hopcape.odo.web.admin.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.admin.resources.Res
import com.hopcape.odo.web.admin.resources.ad_dismiss
import com.hopcape.odo.web.admin.resources.ad_users_next
import com.hopcape.odo.web.admin.resources.ad_users_previous
import com.hopcape.odo.web.admin.ui.theme.AdminElevation
import com.hopcape.odo.web.admin.ui.theme.AdminTokens
import com.hopcape.odo.web.admin.ui.theme.Elevation
import com.hopcape.odo.web.admin.ui.theme.raised
import com.hopcape.odo.web.admin.ui.theme.raisedBorder
import com.hopcape.odo.web.admin.ui.theme.AdminType
import org.jetbrains.compose.resources.stringResource

/**
 * The handful of shapes every screen in the panel is assembled from.
 *
 * Written rather than taken from Material because the design is specific in a way
 * Material's roles cannot express: a card is a 12dp-radius #0A0A0A rectangle with
 * a #262626 hairline, and a Material `Card` is none of those things without being
 * argued out of all of them first.
 *
 * Everything here is presentation only. Nothing knows what a city or a user is.
 */

/**
 * A bordered panel. Everything on a page sits inside one of these.
 *
 * Bordered **and** raised. The border is what carries the structure — it is the
 * only thing separating a #0A0A0A card from a #000000 page, because a shadow under
 * black is not visible however hard it is thrown. The elevation is what makes the
 * same layout read correctly in the light theme, where the border alone is too
 * quiet and a white card needs to sit off an off-white page.
 *
 * So neither is decoration and neither is redundant: each one is doing the work in
 * the theme where the other cannot.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    elevation: Elevation = AdminElevation.card,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .raised(elevation, shape)
            .clip(shape)
            .background(AdminTokens.card)
            // A gradient border, which in the dark theme is a lit top edge fading
            // into the ordinary hairline, and in the light theme is the ordinary
            // hairline all the way round.
            .border(1.dp, raisedBorder(), shape),
        content = content,
    )
}

/**
 * A panel that is one row of a list.
 *
 * The same surface, deliberately without the lift. Every table in this panel is
 * built as a stack of one-row panels, and giving each of them a drop shadow puts
 * twenty shadows down a page — which in the light theme reads as a pile of loose
 * cards rather than as a table. The rows are all at the same depth, so none of them
 * should cast anything; the panel *around* a list is what is raised.
 *
 * Its own name rather than `Panel(elevation = flat)` at every call site, because
 * "this is a row" is the thing worth saying and the flatness follows from it.
 */
@Composable
fun RowPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Panel(modifier = modifier, elevation = AdminElevation.flat, content = content)
}

/** A panel's own title band. [trailing] is where a button or a count goes. */
@Composable
fun PanelHeader(title: String, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AdminTokens.card)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = AdminType.eyebrow, color = AdminTokens.textMuted, modifier = Modifier.weight(1f))
        trailing()
    }
    Hairline(AdminTokens.railBorder)
}

/** A one-pixel rule. Depth in this design comes from these, never from a shadow. */
@Composable
fun Hairline(color: Color = AdminTokens.hairline) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(color))
}

/**
 * A table's column headings.
 *
 * [weights] drives both this and every row, so a heading and its column cannot
 * drift apart — the one failure a hand-laid-out table always eventually has.
 */
@Composable
fun TableHead(columns: List<String>, weights: List<Float>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AdminTokens.tableHeader)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        columns.forEachIndexed { index, label ->
            Text(
                text = label,
                style = AdminType.columnHead,
                color = AdminTokens.textFaint,
                maxLines = 1,
                modifier = Modifier.weight(weights.getOrElse(index) { 1f }),
            )
        }
    }
    Hairline(AdminTokens.railBorder)
}

/**
 * One row of a table, hover-lit.
 *
 * The hover state is worth the interaction source: these rows are 12dp tall in a
 * list of hundreds, and without it there is nothing tying the pointer to the row
 * whose button is about to be clicked.
 */
@Composable
fun TableRow(
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (hovered) AdminTokens.rowHover else Color.Transparent)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interactions, indication = null, onClick = onClick)
                } else {
                    Modifier.hoverable(interactions)
                },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
    Hairline()
}

/** A cell's primary line — a name, a model, a city. */
@Composable
fun CellPrimary(text: String, modifier: Modifier = Modifier, color: Color = AdminTokens.text) {
    Text(text, style = AdminType.rowPrimary, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/** A cell's second line — an address under a name, a state under a city. */
@Composable
fun CellSecondary(text: String, modifier: Modifier = Modifier) {
    Text(text, style = AdminType.caption, color = AdminTokens.textFaint, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/** Ordinary cell text. */
@Composable
fun Cell(text: String, modifier: Modifier = Modifier, color: Color = AdminTokens.textStrong) {
    Text(text, style = AdminType.body, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/**
 * A row's action — an outlined word, not a filled button.
 *
 * Filled buttons in a dense table read as a column of blocks; the outline keeps
 * the row's text the thing you scan and the action the thing you aim at.
 */
@Composable
fun RowAction(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    color: Color = AdminTokens.textStrong,
) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .border(
                1.dp,
                if (hovered && enabled) AdminTokens.borderHover else AdminTokens.border,
                RoundedCornerShape(6.dp),
            )
            .clickable(interactionSource = interactions, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(label, style = AdminType.strong, color = if (enabled) color else AdminTokens.textDim, maxLines = 1)
    }
}

/** The one filled control on a page — the thing it is for. */
@Composable
fun PrimaryAction(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (enabled) AdminTokens.text else AdminTokens.field)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    ) {
        Text(label, style = AdminType.strong, color = if (enabled) AdminTokens.canvas else AdminTokens.textDim, maxLines = 1)
    }
}

/** A status word, in whatever colour the status means. */
@Composable
fun StatusText(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(text, style = AdminType.strong, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/** A bordered pill — a count, a note, a state. */
@Composable
fun Pill(text: String, dot: Color? = null, textColor: Color = AdminTokens.textStrong) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(AdminTokens.field)
            .border(1.dp, AdminTokens.border, RoundedCornerShape(999.dp))
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        dot?.let { Box(Modifier.size(5.dp).clip(RoundedCornerShape(999.dp)).background(it)) }
        Text(text, style = AdminType.strong, color = textColor, maxLines = 1)
    }
}

/**
 * A text field, built from [BasicTextField].
 *
 * Material's own text fields carry a label, a container and 56dp of height that
 * this design has nowhere to put. What is wanted is a bordered box with text in
 * it, which is what this is.
 */
@Composable
fun AdminField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** True for a password. Nothing else in the panel hides what it holds. */
    masked: Boolean = false,
    leading: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(AdminTokens.field)
            .border(1.dp, AdminTokens.border, RoundedCornerShape(7.dp))
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        leading?.invoke()
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(placeholder, style = AdminType.body, color = AdminTokens.textDim, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = AdminType.body.copy(color = AdminTokens.text),
                visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
                cursorBrush = SolidColor(AdminTokens.text),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A label above a field or a block. */
@Composable
fun FieldLabel(text: String) {
    Text(text, style = AdminType.columnHead, color = AdminTokens.textFaint, modifier = Modifier.padding(bottom = 6.dp))
}

/** An empty state, or any other line of context. */
@Composable
fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, style = AdminType.body, color = AdminTokens.textFaint, modifier = modifier.padding(16.dp))
}

/**
 * The last thing that happened, pinned to the bottom-left of the page.
 *
 * Not a transient toast. Half of these messages report a write somebody may need
 * to tell a colleague about, and one that vanishes on a timer is one nobody can
 * re-read.
 */
@Composable
fun BoxScope.Banner(text: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(26.dp)
            // Overlay elevation: this floats over a scrolling page, and it is the
            // one surface that has to be readable without the reader having looked
            // for it.
            .raised(AdminElevation.overlay, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(AdminTokens.field)
            .border(1.dp, raisedBorder(), RoundedCornerShape(8.dp))
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(5.dp).clip(RoundedCornerShape(999.dp)).background(AdminTokens.accent))
        Text(text, style = AdminType.body, color = AdminTokens.text)
        RowAction(label = stringResource(Res.string.ad_dismiss), onClick = onDismiss)
    }
}

/**
 * A window onto a list, and the controls for moving it.
 *
 * Paging is done in memory: the catalogs are read whole in one request (a few
 * thousand short rows) because searching across all of them is the thing these
 * screens are for, and a page-per-request would make that impossible. What this
 * fixes is drawing them all at once, not fetching them.
 */
@Immutable
data class Page(val index: Int, val size: Int = 25) {

    fun <T> windowOf(all: List<T>): List<T> =
        all.drop(index * size).take(size)

    fun first(total: Int): Int = if (total == 0) 0 else index * size + 1
    fun last(total: Int): Int = minOf((index + 1) * size, total)
    fun hasNext(total: Int): Boolean = last(total) < total
    val hasPrevious: Boolean get() = index > 0

    /** Snapped back to the start, for when the filter changes under it. */
    fun reset(): Page = copy(index = 0)
}

/** The pager's controls. Draws nothing when everything fits on one page. */
@Composable
fun Pager(
    page: Page,
    total: Int,
    label: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    if (total <= page.size) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label, style = AdminType.body, color = AdminTokens.textFaint, modifier = Modifier.weight(1f))
        RowAction(stringResource(Res.string.ad_users_previous), onPrevious, page.hasPrevious)
        RowAction(stringResource(Res.string.ad_users_next), onNext, page.hasNext(total))
    }
}
