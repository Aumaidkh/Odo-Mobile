package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * The three button roles Odo screens use. Every screen has at most one [Primary]
 * (the odometer-orange call to action — "Scan bill", "Save"); [Secondary] and
 * [Tertiary] carry lower-emphasis choices without competing with it.
 *
 * @property Primary   accent fill, white ink — the single main action per screen.
 * @property Secondary outlined, neutral ink — a co-equal alternative (e.g. "Cancel").
 * @property Tertiary  text-only, accent ink — low-emphasis / inline actions.
 * @property Danger    danger fill — the confirm action of a destructive flow ("Remove car").
 */
enum class OdoButtonVariant { Primary, Secondary, Tertiary, Danger }

/**
 * Odo's themed button — pill-shaped, [OdoButtonVariant]-driven, with a built-in
 * [loading] state and an optional [leadingIcon]. Prefer this over a stock Material
 * `Button` so emphasis, shape, colours, and the minimum touch target stay
 * consistent across every feature.
 *
 * It sits on top of Material's `Button` / `OutlinedButton` / `TextButton` so ripple,
 * focus, and disabled semantics come for free; only the brand skin is layered on.
 *
 * ```
 * OdoButton("Scan bill", onClick = vm::scan, modifier = Modifier.fillMaxWidth())
 * OdoButton("Cancel", onClick = vm::cancel, variant = OdoButtonVariant.Secondary)
 * ```
 *
 * Width follows the [modifier] — pass `Modifier.fillMaxWidth()` for a full-bleed
 * mobile CTA, or leave it to hug its content.
 *
 * @param loading shows a spinner in place of the label and blocks clicks (the
 *   button stays visually enabled, so a submit doesn't look disabled mid-flight).
 * @param enabled greys the button out and blocks clicks.
 * @param leadingIcon optional icon drawn before the label; inherits the content
 *   colour via [LocalContentColor], so pass an icon that respects it.
 */
@Composable
fun OdoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: OdoButtonVariant = OdoButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val shape = OdoTheme.shapes.pill
    val contentPadding = ButtonDefaults.ContentPadding
    // A loading button is still "enabled" to the eye but must not fire.
    val clickable = enabled && !loading
    val sizing = modifier.heightIn(min = OdoTheme.spacing.minTouchTarget)

    val content: @Composable () -> Unit = { OdoButtonContent(text, loading, leadingIcon) }

    when (variant) {
        OdoButtonVariant.Primary -> Button(
            onClick = onClick,
            modifier = sizing,
            enabled = clickable,
            shape = shape,
            colors = primaryColors(),
            elevation = null, // flat on Odo's dark surfaces; separation is by colour
            contentPadding = contentPadding,
            content = { content() },
        )

        OdoButtonVariant.Secondary -> OutlinedButton(
            onClick = onClick,
            modifier = sizing,
            enabled = clickable,
            shape = shape,
            colors = secondaryColors(),
            border = BorderStroke(
                width = 1.dp,
                color = if (clickable) OdoTheme.colors.border else OdoTheme.colors.border.copy(alpha = 0.5f),
            ),
            contentPadding = contentPadding,
            content = { content() },
        )

        OdoButtonVariant.Tertiary -> TextButton(
            onClick = onClick,
            modifier = sizing,
            enabled = clickable,
            shape = shape,
            colors = tertiaryColors(),
            contentPadding = contentPadding,
            content = { content() },
        )

        OdoButtonVariant.Danger -> Button(
            onClick = onClick,
            modifier = sizing,
            enabled = clickable,
            shape = shape,
            colors = dangerColors(),
            elevation = null,
            contentPadding = contentPadding,
            content = { content() },
        )
    }
}

@Composable
private fun OdoButtonContent(
    text: String,
    loading: Boolean,
    leadingIcon: (@Composable () -> Unit)?,
) {
    if (loading) {
        CircularProgressIndicator(
            modifier = Modifier.size(OdoTheme.iconSizes.medium),
            color = LocalContentColor.current,
            strokeWidth = 2.dp,
        )
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingIcon?.invoke()
            OdoText(text, style = OdoTheme.typography.label)
        }
    }
}

@Composable
private fun primaryColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = OdoTheme.colors.accent,
    contentColor = OdoTheme.colors.onAccent,
    disabledContainerColor = OdoTheme.colors.surfaceRaised,
    disabledContentColor = OdoTheme.colors.textMuted,
)

@Composable
private fun secondaryColors(): ButtonColors = ButtonDefaults.outlinedButtonColors(
    contentColor = OdoTheme.colors.text,
    disabledContentColor = OdoTheme.colors.textMuted,
)

@Composable
private fun tertiaryColors(): ButtonColors = ButtonDefaults.textButtonColors(
    contentColor = OdoTheme.colors.accent,
    disabledContentColor = OdoTheme.colors.textMuted,
)

@Composable
private fun dangerColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = OdoTheme.colors.danger,
    contentColor = OdoTheme.colors.onAccent,
    disabledContainerColor = OdoTheme.colors.surfaceRaised,
    disabledContentColor = OdoTheme.colors.textMuted,
)

@OdoThemePreviews
@Composable
private fun OdoButtonPreview() = OdoPreview {
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
        OdoButton("Scan bill", onClick = {}, modifier = Modifier.fillMaxWidth())
        OdoButton("Cancel", onClick = {}, variant = OdoButtonVariant.Secondary)
        OdoButton("Learn more", onClick = {}, variant = OdoButtonVariant.Tertiary)
        OdoButton("Saving…", onClick = {}, loading = true, modifier = Modifier.fillMaxWidth())
        OdoButton("Disabled", onClick = {}, enabled = false)
    }
}
