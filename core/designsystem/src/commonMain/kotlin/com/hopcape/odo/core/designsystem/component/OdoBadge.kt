package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoColors
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * The semantic tones an [OdoBadge] can take — mapped to the brand status roles so
 * a badge's colour always means the same thing across the app.
 *
 * @property Neutral dim, unremarkable state (e.g. "Self-Reported", "Draft").
 * @property Accent  brand-highlighted, non-status emphasis (e.g. "Pro", "New").
 * @property Success verified · valid · savings (e.g. "Verified", "Valid").
 * @property Warning expiring · low-confidence · caution (e.g. "Expires soon").
 * @property Danger  expired · anomaly · failure (e.g. "Expired", "Km anomaly").
 */
enum class OdoBadgeTone { Neutral, Accent, Success, Warning, Danger }

/**
 * A small, pill-shaped status label — the design-system primitive features build
 * their domain badges from ("Verified" / "Self-Reported" / "Valid" / "Expired" /
 * "Km anomaly"). It deliberately carries **no product copy or rules**: the caller
 * supplies the [text] and picks a [tone]; the trust logic (e.g. "Verified needs a
 * bill photo") lives in the feature, not here.
 *
 * Renders as tinted ink on a low-alpha wash of the same hue, so it reads as a
 * quiet tag rather than a loud button. Text uses the tracked-caps `caption` style;
 * pass already-cased copy (badges are conventionally UPPERCASE — `"VERIFIED"`).
 *
 * ```
 * OdoBadge("VERIFIED", tone = OdoBadgeTone.Success)
 * OdoBadge("EXPIRED", tone = OdoBadgeTone.Danger)
 * OdoBadge("SELF-REPORTED") // Neutral
 * ```
 *
 * @param leadingIcon optional glyph before the label (e.g. a check or dot),
 *   sized small; inherits the badge's content colour.
 */
@Composable
fun OdoBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: OdoBadgeTone = OdoBadgeTone.Neutral,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val colors = OdoTheme.colors
    val content = tone.contentColor(colors)
    Surface(
        modifier = modifier,
        shape = OdoTheme.shapes.pill,
        color = tone.containerColor(colors),
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = OdoTheme.spacing.sm,
                vertical = OdoTheme.spacing.xs,
            ),
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingIcon?.invoke()
            OdoText(text, style = OdoTheme.typography.caption)
        }
    }
}

private fun OdoBadgeTone.contentColor(c: OdoColors): Color = when (this) {
    OdoBadgeTone.Neutral -> c.textDim
    OdoBadgeTone.Accent -> c.accent
    OdoBadgeTone.Success -> c.success
    OdoBadgeTone.Warning -> c.warning
    OdoBadgeTone.Danger -> c.danger
}

/** A low-alpha wash of the tone's hue; [OdoBadgeTone.Neutral] sits on the raised surface. */
private fun OdoBadgeTone.containerColor(c: OdoColors): Color = when (this) {
    OdoBadgeTone.Neutral -> c.surfaceRaised
    else -> contentColor(c).copy(alpha = 0.16f)
}

@OdoThemePreviews
@Composable
private fun OdoBadgePreview() = OdoPreview {
    Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
        OdoBadge("VERIFIED", tone = OdoBadgeTone.Success)
        OdoBadge("EXPIRES SOON", tone = OdoBadgeTone.Warning)
        OdoBadge("EXPIRED", tone = OdoBadgeTone.Danger)
        OdoBadge("SELF-REPORTED")
    }
}
