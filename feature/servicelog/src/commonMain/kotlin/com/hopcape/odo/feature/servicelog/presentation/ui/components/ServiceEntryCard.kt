package com.hopcape.odo.feature.servicelog.presentation.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoDivider
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * A service card — an [OdoCard] whose border turns amber when [flagged] (an
 * overcharge). The building block both the ledger card and the timeline card compose
 * on; the caller fills the body via [content].
 */
@Composable
internal fun ServiceLogEntryCard(
    onClick: () -> Unit,
    flagged: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val border = BorderStroke(1.dp, if (flagged) OdoTheme.colors.warning else OdoTheme.colors.border)
    OdoCard(onClick = onClick, modifier = modifier, border = border, content = content)
}

/**
 * A card's footer: a divider over a "[leading]……[trailing]" row — the shared shape of
 * "work · verdict" (ledger) and "status · amount" (timeline). [leading] takes the
 * flexible space; [trailing] hugs the end.
 */
@Composable
internal fun CardFooter(
    leading: @Composable BoxScope.() -> Unit,
    trailing: @Composable () -> Unit,
) {
    OdoDivider()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), content = leading)
        trailing()
    }
}

@OdoThemePreviews
@Composable
private fun ServiceLogEntryCardPreview() = OdoPreview {
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
        ServiceLogEntryCard(onClick = {}, flagged = false) {
            OdoText("Sharma Motors", style = OdoTheme.typography.heading)
            OdoText("12 Jun 2026 · 54,000 km", style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
            CardFooter(
                leading = { OdoText("Oil change + oil filter", style = OdoTheme.typography.body) },
                trailing = { OdoText("Rs. 3,200", style = OdoTheme.typography.title) },
            )
        }
        ServiceLogEntryCard(onClick = {}, flagged = true) {
            OdoText("AutoCare Pune", style = OdoTheme.typography.heading)
            OdoText("02 Mar 2026 · 48,500 km", style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
            CardFooter(
                leading = { OdoText("Front brake pads", style = OdoTheme.typography.body) },
                trailing = { OdoText("Rs. 4,800", style = OdoTheme.typography.title) },
            )
        }
    }
}
