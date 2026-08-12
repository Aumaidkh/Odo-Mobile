package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * A generic list row — leading slot, a text block ([overline] / [headline] /
 * [supporting]), and a trailing slot — that features compose their domain rows on
 * (a service-log entry, a document-vault item, a reminder). It owns the shared
 * mechanics only: the minimum touch height, row padding, vertical centring, and
 * the brand type/colour for each text line. It holds **no domain knowledge** —
 * the caller decides what goes in each slot (e.g. an [OdoBadge] in [trailing], the
 * odometer + cost in [supporting]).
 *
 * ```
 * OdoListItem(
 *     overline = "12 JUN 2026",
 *     headline = "Sharma Motors",
 *     supporting = "42,500 km · ₹2,800",
 *     trailing = { OdoBadge("VERIFIED", tone = OdoBadgeTone.Success) },
 *     onClick = { open(entry.id) },
 * )
 * ```
 *
 * @param overline small tracked-caps line above the headline (date, category…).
 * @param supporting secondary line below the headline, in dimmed ink.
 * @param leading / [trailing] optional slots at the row's start / end.
 * @param onClick when non-null, the whole row is tappable (ripple + a11y role).
 */
@Composable
fun OdoListItem(
    headline: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    supporting: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val rowModifier = modifier
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .heightIn(min = OdoTheme.spacing.minTouchTarget)
        .padding(vertical = OdoTheme.spacing.listRowVertical)
    Row(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Column(
            // Take the remaining width so the trailing slot stays pinned right and
            // long text truncates rather than shoving it off-screen.
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (overline != null) {
                OdoText(
                    overline,
                    style = OdoTheme.typography.caption,
                    color = OdoTheme.colors.textDim,
                    maxLines = 1,
                )
            }
            OdoText(headline, style = OdoTheme.typography.heading, maxLines = 1)
            if (supporting != null) {
                OdoText(
                    supporting,
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textDim,
                    maxLines = 2,
                )
            }
        }
        trailing?.invoke()
    }
}

@OdoThemePreviews
@Composable
private fun OdoListItemPreview() = OdoPreview(padded = false) {
    Column {
        OdoListItem(
            overline = "12 JUN 2026",
            headline = "Sharma Motors",
            supporting = "42,500 km · ₹2,800",
            trailing = { OdoBadge("VERIFIED", tone = OdoBadgeTone.Success) },
            onClick = {},
        )
        OdoDivider()
        OdoListItem(
            overline = "28 MAY 2026",
            headline = "Self service",
            supporting = "41,900 km · ₹450",
            trailing = { OdoBadge("SELF-REPORTED") },
            onClick = {},
        )
    }
}
