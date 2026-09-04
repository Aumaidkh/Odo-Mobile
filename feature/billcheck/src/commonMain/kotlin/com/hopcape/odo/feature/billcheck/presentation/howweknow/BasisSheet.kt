package com.hopcape.odo.feature.billcheck.presentation.howweknow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.round
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoDivider
import com.hopcape.odo.core.designsystem.component.OdoLoadingIndicator
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.shared.formatRupeeRangeTo
import com.hopcape.odo.core.domain.shared.formatRupees
import com.hopcape.odo.feature.billcheck.domain.BandBasis
import com.hopcape.odo.feature.billcheck.domain.BandScope
import com.hopcape.odo.feature.billcheck.domain.BillCheckFixtures
import com.hopcape.odo.feature.billcheck.domain.Rung
import com.hopcape.odo.feature.billcheck.domain.RungState
import com.hopcape.odo.feature.billcheck.presentation.result.label
import com.hopcape.odo.feature.billcheck.resources.Res
import com.hopcape.odo.feature.billcheck.resources.bc_ai_disclaimer
import com.hopcape.odo.feature.billcheck.resources.bc_basis_city
import com.hopcape.odo.feature.billcheck.resources.bc_basis_city_value
import com.hopcape.odo.feature.billcheck.resources.bc_basis_labour
import com.hopcape.odo.feature.billcheck.resources.bc_basis_labour_value
import com.hopcape.odo.feature.billcheck.resources.bc_basis_note
import com.hopcape.odo.feature.billcheck.resources.bc_basis_segment
import com.hopcape.odo.feature.billcheck.resources.bc_basis_subtitle
import com.hopcape.odo.feature.billcheck.resources.bc_basis_title
import com.hopcape.odo.feature.billcheck.resources.bc_basis_workshop
import com.hopcape.odo.feature.billcheck.resources.bc_report_price
import com.hopcape.odo.feature.billcheck.resources.bc_retry
import com.hopcape.odo.feature.billcheck.resources.bc_rung_city
import com.hopcape.odo.feature.billcheck.resources.bc_rung_national
import com.hopcape.odo.feature.billcheck.resources.bc_rung_no_data
import com.hopcape.odo.feature.billcheck.resources.bc_rung_not_needed
import com.hopcape.odo.feature.billcheck.resources.bc_rung_own
import com.hopcape.odo.feature.billcheck.resources.bc_rung_used
import com.hopcape.odo.feature.billcheck.resources.bc_rungs_title
import org.jetbrains.compose.resources.stringResource

/**
 * "How we know" — where one line's band came from.
 *
 * The band is the only thing on the result screen the owner is asked to take on trust, and
 * the product's whole argument is about not doing that at a counter. So the inputs are shown
 * in full, and the ladder says which rung actually answered rather than implying the narrowest
 * one did.
 *
 * Play's GenAI requirements live here too: the report action and the disclaimer, on the screen
 * that explains the answer rather than buried in settings.
 *
 * Stateless: renders [state] and forwards [BasisEvent]s.
 */
@Composable
internal fun BasisSheetContent(
    state: BasisUiState,
    onEvent: (BasisEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = OdoTheme.spacing.screenEdge)
            .padding(bottom = OdoTheme.spacing.xl)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
    ) {
        when (val content = state.content) {
            BasisUiState.Content.Loading -> Centred { OdoLoadingIndicator() }

            is BasisUiState.Content.Failed -> Centred {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
                ) {
                    OdoText(
                        text = content.message.asString(),
                        style = OdoTheme.typography.body,
                        color = OdoTheme.colors.textDim,
                        textAlign = TextAlign.Center,
                    )
                    OdoButton(
                        text = stringResource(Res.string.bc_retry),
                        onClick = { onEvent(BasisEvent.RetryClicked) },
                        variant = OdoButtonVariant.Secondary,
                    )
                }
            }

            is BasisUiState.Content.Ready -> Basis(content.basis, onEvent)
        }
    }
}

@Composable
private fun Basis(basis: BandBasis, onEvent: (BasisEvent) -> Unit) {
    val band = basis.low.formatRupeeRangeTo(basis.high)

    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
        OdoText(
            text = stringResource(Res.string.bc_basis_title, band),
            style = OdoTheme.typography.title,
        )
        OdoText(
            text = stringResource(Res.string.bc_basis_subtitle, basis.lineName),
            style = OdoTheme.typography.body,
            color = OdoTheme.colors.textDim,
        )
    }

    OdoCard(contentPadding = PaddingValues(0.dp)) {
        Fact(
            label = stringResource(Res.string.bc_basis_city),
            value = stringResource(Res.string.bc_basis_city_value, basis.city, basis.cityTier),
        )
        OdoDivider()
        Fact(
            label = stringResource(Res.string.bc_basis_workshop),
            value = basis.workshop.label().replaceFirstChar { it.uppercase() },
        )
        OdoDivider()
        Fact(label = stringResource(Res.string.bc_basis_segment), value = basis.segment)
        OdoDivider()
        Fact(
            label = stringResource(Res.string.bc_basis_labour),
            value = stringResource(
                Res.string.bc_basis_labour_value,
                basis.labourRatePerHour.formatRupees(),
                basis.labourHours.trimmed(),
            ),
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
        OdoText(
            text = stringResource(Res.string.bc_rungs_title),
            style = OdoTheme.typography.label,
            color = OdoTheme.colors.textMuted,
        )
        basis.rungs.forEach { RungRow(it) }
    }

    OdoText(
        text = stringResource(Res.string.bc_basis_note),
        style = OdoTheme.typography.bodySmall,
        color = OdoTheme.colors.textMuted,
    )

    OdoButton(
        text = stringResource(Res.string.bc_report_price),
        onClick = { onEvent(BasisEvent.ReportPriceClicked) },
        modifier = Modifier.fillMaxWidth(),
        variant = OdoButtonVariant.Secondary,
    )

    OdoText(
        text = stringResource(Res.string.bc_ai_disclaimer),
        style = OdoTheme.typography.bodySmall,
        color = OdoTheme.colors.textMuted,
    )
}

/** One input to the band: what it is on the left, what it was on the right. */
@Composable
private fun Fact(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = OdoTheme.spacing.cardPadding,
                vertical = OdoTheme.spacing.md,
            ),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoText(
            text = label,
            style = OdoTheme.typography.body,
            color = OdoTheme.colors.textDim,
            modifier = Modifier.weight(1f),
        )
        OdoText(text = value, style = OdoTheme.typography.body)
    }
}

/**
 * One rung of the ladder.
 *
 * The rung that answered is the only one drawn in full ink. The others are shown rather than
 * hidden because "no bills yet" is the honest reason a narrower answer was not available, and
 * it is also the argument for adding a bill.
 */
@Composable
private fun RungRow(rung: Rung) {
    val used = rung.state == RungState.USED
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .size(RUNG_DOT)
                .background(
                    color = if (used) OdoTheme.colors.text else OdoTheme.colors.border,
                    shape = CircleShape,
                ),
        )
        OdoText(
            text = rung.scope.label(),
            style = OdoTheme.typography.body,
            color = if (used) OdoTheme.colors.text else OdoTheme.colors.textMuted,
            modifier = Modifier.weight(1f),
        )
        OdoText(
            text = rung.state.label(),
            style = OdoTheme.typography.bodySmall,
            color = if (used) OdoTheme.colors.textDim else OdoTheme.colors.textMuted,
        )
    }
}

@Composable
private fun BandScope.label(): String = when (this) {
    BandScope.THIS_CAR_THIS_CENTRE -> stringResource(Res.string.bc_rung_own)
    BandScope.CITY_TIER_SEGMENT -> stringResource(Res.string.bc_rung_city)
    BandScope.NATIONAL -> stringResource(Res.string.bc_rung_national)
}

@Composable
private fun RungState.label(): String = when (this) {
    RungState.NO_DATA -> stringResource(Res.string.bc_rung_no_data)
    RungState.USED -> stringResource(Res.string.bc_rung_used)
    RungState.NOT_NEEDED -> stringResource(Res.string.bc_rung_not_needed)
}

/**
 * "1.5" rather than "1.5000", and "2" rather than "2.0".
 *
 * Rounded, not truncated: this figure is part of what justifies the band, and 2.28 hours shown
 * as "2.2" understates the labour the price is built on.
 */
private fun Double.trimmed(): String =
    if (this % 1.0 == 0.0) toInt().toString() else (round(this * 10) / 10.0).toString()

@Composable
private fun Centred(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = MESSAGE_MIN_HEIGHT),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        content()
    }
}

private val RUNG_DOT = 8.dp
private val MESSAGE_MIN_HEIGHT = 140.dp

@OdoThemePreviews
@Composable
private fun BasisSheetPreview() = OdoPreview {
    BasisSheetContent(
        state = BasisUiState(BasisUiState.Content.Ready(BillCheckFixtures.acServiceBasis)),
        onEvent = {},
    )
}
