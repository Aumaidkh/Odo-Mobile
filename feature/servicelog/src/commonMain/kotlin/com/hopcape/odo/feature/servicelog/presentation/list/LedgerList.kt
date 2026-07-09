package com.hopcape.odo.feature.servicelog.presentation.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoChip
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.servicelog.presentation.list.components.VerdictPill
import com.hopcape.odo.feature.servicelog.presentation.list.components.isFlagged
import com.hopcape.odo.feature.servicelog.presentation.ui.components.CardFooter
import com.hopcape.odo.feature.servicelog.presentation.ui.components.ServiceLogEntryCard
import com.hopcape.odo.feature.servicelog.presentation.ui.components.VerificationBadge
import com.hopcape.odo.feature.servicelog.presentation.formatDate
import com.hopcape.odo.feature.servicelog.presentation.formatKm
import com.hopcape.odo.feature.servicelog.presentation.formatRupees
import com.hopcape.odo.feature.servicelog.resources.Res
import com.hopcape.odo.feature.servicelog.resources.sl_filter_all
import com.hopcape.odo.feature.servicelog.resources.sl_filter_flagged
import com.hopcape.odo.feature.servicelog.resources.sl_filter_verified
import org.jetbrains.compose.resources.stringResource


internal val ServiceLogListBottomPadding: Dp = 96.dp
/**
 * Ledger (1a) list body — cost & fairness first. Sits under the shared header; adds
 * the All / Verified / Flagged filter, then every service as a card showing what it
 * cost and whether the price was fair (flagged cards carry an amber border).
 */
@Composable
internal fun LedgerList(
    content: ServiceLogListUiState.Content.Loaded,
    filter: ServiceLogFilter,
    onFilterChange: (ServiceLogFilter) -> Unit,
    onOpenDetail: (logId: String) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.cardGap),
        contentPadding = PaddingValues(bottom = ServiceLogListBottomPadding),
    ) {
        item { LedgerFilterChips(filter, content, onFilterChange) }
        items(content.cards, key = { it.id.value }) { card ->
            LedgerCard(card, onClick = { onOpenDetail(card.id.value) }, modifier = Modifier.animateItem())
        }
    }
}

@Composable
private fun LedgerFilterChips(
    filter: ServiceLogFilter,
    content: ServiceLogListUiState.Content.Loaded,
    onFilterChange: (ServiceLogFilter) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
        OdoChip(
            label = stringResource(Res.string.sl_filter_all),
            selected = filter == ServiceLogFilter.ALL,
            onClick = { onFilterChange(ServiceLogFilter.ALL) },
        )
        OdoChip(
            label = "${stringResource(Res.string.sl_filter_verified)} · ${content.verifiedCount}",
            selected = filter == ServiceLogFilter.VERIFIED,
            onClick = { onFilterChange(ServiceLogFilter.VERIFIED) },
        )
        OdoChip(
            label = "${stringResource(Res.string.sl_filter_flagged)} · ${content.flaggedCount}",
            selected = filter == ServiceLogFilter.FLAGGED,
            onClick = { onFilterChange(ServiceLogFilter.FLAGGED) },
        )
    }
}

/** One service: workshop + trust badge + amount, date/km, then work + verdict pill. */
@Composable
private fun LedgerCard(card: ServiceLogCardUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ServiceLogEntryCard(onClick = onClick, flagged = card.isFlagged, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OdoText(text = card.workshopName ?: "—", style = OdoTheme.typography.heading)
                VerificationBadge(card.verification)
            }
            OdoText(text = formatRupees(card.amount.paise), style = OdoTheme.typography.title)
        }
        OdoText(
            text = "${formatDate(card.serviceDate)} · ${formatKm(card.odometer.km)}",
            style = OdoTheme.typography.bodySmall,
            color = OdoTheme.colors.textDim,
        )
        CardFooter(
            leading = { card.workDone?.let { OdoText(text = it, style = OdoTheme.typography.body) } },
            trailing = { VerdictPill(card.fairness) },
        )
    }
}

@OdoThemePreviews
@Composable
private fun LedgerListPreview() = OdoPreview {
    LedgerList(
        content = sampleLoadedContent(),
        filter = ServiceLogFilter.ALL,
        onFilterChange = {},
        onOpenDetail = {},
    )
}
