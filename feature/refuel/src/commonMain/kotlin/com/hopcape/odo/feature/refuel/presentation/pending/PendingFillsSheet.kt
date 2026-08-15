package com.hopcape.odo.feature.refuel.presentation.pending

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.refuel.presentation.RefuelTestTags
import com.hopcape.odo.feature.refuel.resources.Res
import com.hopcape.odo.feature.refuel.resources.rf_pending_body
import com.hopcape.odo.feature.refuel.resources.rf_pending_later
import com.hopcape.odo.feature.refuel.resources.rf_pending_not_fuel
import com.hopcape.odo.feature.refuel.resources.rf_pending_review
import com.hopcape.odo.feature.refuel.resources.rf_pending_title
import com.hopcape.odo.feature.refuel.resources.rf_pending_title_one
import org.jetbrains.compose.resources.stringResource

/**
 * "Fills you haven't confirmed" — the sheet that makes a missed detection recoverable.
 *
 * Every row is a payment Odo read at a pump and never got an answer about: the notification
 * was dismissed, or never arrived because the listener was not connected at the time. Without
 * this the fill would simply be gone, because Android keeps no record of a dismissed
 * notification and nothing can read one back.
 *
 * Two answers per row and no third. Reviewing goes to the same confirm surface every other
 * capture channel ends at, so the owner checks the odometer before anything is written;
 * rejecting closes the question for good. "Later" leaves everything as it is — this is a
 * reminder, not a demand.
 */
@Composable
internal fun PendingFillsSheetContent(
    state: PendingFillsUiState,
    onEvent: (PendingFillsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = OdoTheme.spacing.lg, vertical = OdoTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        OdoText(
            text = if (state.fills.size == 1) {
                stringResource(Res.string.rf_pending_title_one)
            } else {
                stringResource(Res.string.rf_pending_title, state.fills.size)
            },
            style = OdoTheme.typography.title,
        )
        OdoText(
            stringResource(Res.string.rf_pending_body),
            style = OdoTheme.typography.bodySmall,
            color = OdoTheme.colors.textDim,
        )

        state.fills.forEach { row ->
            OdoCard(modifier = Modifier.fillMaxWidth().testTag(RefuelTestTags.pendingRow(row.id))) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
                    ) {
                        OdoText(row.merchant, style = OdoTheme.typography.label)
                        OdoText(
                            row.whenLabel,
                            style = OdoTheme.typography.caption,
                            color = OdoTheme.colors.textMuted,
                        )
                    }
                    OdoText(row.amountLabel, style = OdoTheme.typography.label)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
                ) {
                    OdoButton(
                        text = stringResource(Res.string.rf_pending_review),
                        onClick = { onEvent(PendingFillsEvent.ReviewTapped(row.id)) },
                        modifier = Modifier.weight(1f),
                    )
                    OdoButton(
                        text = stringResource(Res.string.rf_pending_not_fuel),
                        onClick = { onEvent(PendingFillsEvent.DismissTapped(row.id)) },
                        variant = OdoButtonVariant.Secondary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        OdoButton(
            text = stringResource(Res.string.rf_pending_later),
            onClick = { onEvent(PendingFillsEvent.CloseTapped) },
            variant = OdoButtonVariant.Tertiary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OdoThemePreviews
@Composable
private fun PendingFillsPreview() = OdoPreview {
    PendingFillsSheetContent(
        state = PendingFillsUiState(
            loading = false,
            fills = listOf(
                PendingFillRow(
                    id = "1",
                    merchant = "Bharat Petroleum, Karol Bagh",
                    amountLabel = "Rs. 2,000",
                    whenLabel = "2026-08-15",
                    draftPayload = "",
                ),
            ),
        ),
        onEvent = {},
    )
}
