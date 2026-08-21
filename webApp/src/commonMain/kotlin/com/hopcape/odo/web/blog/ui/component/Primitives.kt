package com.hopcape.odo.web.blog.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.blog.ui.theme.BlogThemeTokens

/**
 * The pieces every screen is assembled from.
 *
 * They are here rather than inside the screens for the same reason the app keeps
 * its controls in `:core:designsystem`: a pill drawn by hand on the article page
 * and another on the 404 page drift within a week, and nobody notices until both
 * are in one screenshot.
 */

private val PILL = RoundedCornerShape(999.dp)
private val FIELD = RoundedCornerShape(11.dp)

/**
 * A small uppercase label with wide tracking — the design's category tag, column
 * header and section marker are all this one thing.
 */
@Composable
fun Eyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = BlogThemeTokens.colors.muted,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.labelSmall,
    )
}

/**
 * The design's only button shape: a full-radius pill.
 *
 * [filled] is the one asking for the tap — "Get Odo", "Publish". Everything else
 * is outlined, so no page ever has two things competing to be pressed.
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val colors = BlogThemeTokens.colors
    val background = when {
        !filled -> Color.Transparent
        danger -> colors.danger
        else -> colors.actionBackground
    }
    val foreground = when {
        danger && filled -> Color.White
        danger -> colors.danger
        filled -> colors.actionText
        else -> colors.text
    }
    Box(
        modifier = modifier
            .clip(PILL)
            .background(if (enabled) background else background.copy(alpha = 0.35f))
            .then(
                if (filled) {
                    Modifier
                } else {
                    Modifier.border(BorderStroke(1.dp, if (danger) colors.danger else colors.borderStrong), PILL)
                },
            )
            .clickableIfEnabled(enabled, onClick)
            .defaultMinSize(minHeight = 40.dp)
            .padding(horizontal = 22.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) foreground else foreground.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

/** A filter chip. Selected inverts, the way the design's "All" chip does. */
@Composable
fun FilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BlogThemeTokens.colors
    Box(
        modifier = modifier
            .clip(PILL)
            .background(if (selected) colors.actionBackground else Color.Transparent)
            .border(BorderStroke(1.dp, if (selected) colors.actionBackground else colors.border), PILL)
            .clickableIfEnabled(true, onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            color = if (selected) colors.actionText else colors.dim,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

/** A quiet text link. It brightens on hover rather than growing an underline. */
@Composable
fun TextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = BlogThemeTokens.colors.dim,
    style: TextStyle = MaterialTheme.typography.labelLarge,
    maxLines: Int = 1,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Text(
        text = text,
        modifier = modifier
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        color = if (hovered) BlogThemeTokens.colors.text else color,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The avatar circle on a byline. An initial, until there is a photograph. */
@Composable
fun InitialAvatar(initial: String, modifier: Modifier = Modifier, diameter: Int = 34) {
    val colors = BlogThemeTokens.colors
    Box(
        modifier = modifier
            .size(diameter.dp)
            .clip(CircleShape)
            .background(colors.surfaceRaised)
            .border(1.dp, colors.border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            color = colors.dim,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

/** A hairline. Sections are separated by space; this is for tables and lists. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(BlogThemeTokens.colors.border),
    )
}

/**
 * A bordered input.
 *
 * Built on [BasicTextField] rather than Material's, whose outlined field brings a
 * floating label, a 56dp minimum height and a focus ring that all belong to a
 * different design.
 */
@Composable
fun BlogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    password: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    onSubmit: (() -> Unit)? = null,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val colors = BlogThemeTokens.colors
    Box(
        modifier = modifier
            .clip(FIELD)
            .background(colors.surface)
            .border(1.dp, colors.border, FIELD)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty() && placeholder.isNotEmpty()) {
            Text(placeholder, color = colors.muted, style = textStyle, maxLines = 1)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            textStyle = textStyle.copy(color = colors.text),
            cursorBrush = SolidColor(colors.text),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(imeAction = if (onSubmit != null) ImeAction.Go else ImeAction.Default),
            keyboardActions = KeyboardActions(onGo = { onSubmit?.invoke() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A labelled field. Every form in the CMS is a column of these. */
@Composable
fun LabelledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    trailingLabel: String? = null,
    trailingIsWarning: Boolean = false,
    placeholder: String = "",
    password: Boolean = false,
    prefix: String? = null,
    error: String? = null,
) {
    val colors = BlogThemeTokens.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = label,
                color = colors.dim,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            )
            if (trailingLabel != null) {
                Text(
                    text = trailingLabel,
                    // The design turns the counter amber once the field is past
                    // what Google will show, rather than blocking the keystroke.
                    color = if (trailingIsWarning) colors.warning else colors.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (prefix != null) {
                Text(
                    text = prefix,
                    color = colors.muted,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
            BlogTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = placeholder,
                password = password,
                modifier = Modifier.weight(1f),
            )
        }
        if (error != null) {
            Text(error, color = colors.danger, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** `clickable`, plus the cursor that tells a mouse this is clickable. */
private fun Modifier.clickableIfEnabled(enabled: Boolean, onClick: () -> Unit): Modifier =
    if (!enabled) this else pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick)
