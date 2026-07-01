package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * Odo's card / panel container — the surface every grouped block sits on (a
 * service-log row, a fairness verdict, a reminder). On Odo's near-black UI,
 * separation is carried by a tinted [OdoTheme.colors.surface] fill plus a hairline
 * [OdoTheme.colors.border] rather than a heavy shadow, so this defaults to a flat,
 * bordered card and lays out its [content] in a padded [Column].
 *
 * Pass [onClick] to make the whole card tappable (with ripple + a11y role) — the
 * common pattern for a row that opens a detail screen:
 *
 * ```
 * OdoCard(onClick = { open(entry.id) }) {
 *     OdoText(entry.workshop, style = OdoTheme.typography.heading)
 *     OdoText("42,500 km · ₹2,800", style = OdoTheme.typography.bodySmall,
 *             color = OdoTheme.colors.textDim)
 * }
 * ```
 *
 * @param onClick when non-null, the card becomes clickable.
 * @param color the surface fill; use `surfaceRaised` for a card that must lift off
 *   another card (nested / sheet contexts).
 * @param border hairline outline; pass null to drop it (e.g. on a filled accent card).
 * @param contentPadding inner padding around [content]; defaults to `spacing.cardPadding`.
 * @param content laid out in a [Column]; arrange it with [OdoTheme] spacing tokens.
 */
@Composable
fun OdoCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: RoundedCornerShape = OdoTheme.shapes.card,
    color: Color = OdoTheme.colors.surface,
    contentColor: Color = OdoTheme.colors.text,
    border: BorderStroke? = BorderStroke(1.dp, OdoTheme.colors.border),
    contentPadding: PaddingValues = PaddingValues(OdoTheme.spacing.cardPadding),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(OdoTheme.spacing.sm),
    content: @Composable ColumnScope.() -> Unit,
) {
    val inner: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth().padding(contentPadding),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = color,
            contentColor = contentColor,
            border = border,
            content = inner,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = color,
            contentColor = contentColor,
            border = border,
            content = inner,
        )
    }
}

@OdoThemePreviews
@Composable
private fun OdoCardPreview() = OdoPreview {
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.cardGap)) {
        OdoCard {
            OdoText("Regular service", style = OdoTheme.typography.heading)
            OdoText(
                "42,500 km · ₹2,800 · Sharma Motors",
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textDim,
            )
        }
        OdoCard(onClick = {}) {
            OdoText("Tap to open", style = OdoTheme.typography.heading)
            OdoText(
                "Whole card is tappable.",
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textDim,
            )
        }
    }
}
