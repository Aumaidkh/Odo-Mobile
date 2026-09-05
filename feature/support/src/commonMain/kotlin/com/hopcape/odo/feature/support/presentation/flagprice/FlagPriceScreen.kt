package com.hopcape.odo.feature.support.presentation.flagprice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoInputField
import com.hopcape.odo.core.designsystem.component.OdoOptionCard
import com.hopcape.odo.core.designsystem.component.OdoOptionCardStyle
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCamera
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.formatRupeeRangeTo
import com.hopcape.odo.core.domain.shared.formatRupees
import com.hopcape.odo.feature.support.presentation.SectionLabel
import com.hopcape.odo.feature.support.resources.Res
import com.hopcape.odo.feature.support.resources.sp_flag_attach_bill
import com.hopcape.odo.feature.support.resources.sp_flag_attach_optional
import com.hopcape.odo.feature.support.resources.sp_flag_band_label
import com.hopcape.odo.feature.support.resources.sp_flag_footer
import com.hopcape.odo.feature.support.resources.sp_flag_no_band
import com.hopcape.odo.feature.support.resources.sp_flag_paid
import com.hopcape.odo.feature.support.resources.sp_flag_paid_note
import com.hopcape.odo.feature.support.resources.sp_flag_send
import com.hopcape.odo.feature.support.resources.sp_flag_title
import com.hopcape.odo.feature.support.resources.sp_flag_too_high
import com.hopcape.odo.feature.support.resources.sp_flag_too_low
import com.hopcape.odo.feature.support.resources.sp_flag_which_hint
import com.hopcape.odo.feature.support.resources.sp_flag_which_label
import com.hopcape.odo.feature.support.resources.sp_flag_whats_off
import com.hopcape.odo.feature.support.resources.sp_flag_wrong_item
import org.jetbrains.compose.resources.stringResource

/**
 * The other end of "How we know".
 *
 * The band is shown back before anything is asked, with the filters it was built at. An owner
 * disputing a number needs to see which number, and "AC service, Srinagar, company centre" is
 * also the answer to "is it even talking about my car".
 *
 * **What it asks for is one figure.** A paragraph about a band cannot move it; a rupee amount
 * against a named job is a data point, and the note says so rather than leaving the owner to
 * guess why the field is there.
 */
@Composable
internal fun FlagPriceScreen(
    state: FlagPriceUiState,
    onEvent: (FlagPriceEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(
        modifier = modifier,
        title = stringResource(Res.string.sp_flag_title),
        onBack = { onEvent(FlagPriceEvent.BackClicked) },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(OdoTheme.spacing.screenEdge),
                verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OdoButton(
                    text = stringResource(Res.string.sp_flag_send),
                    onClick = { onEvent(FlagPriceEvent.SendClicked) },
                    enabled = state.canSend,
                    loading = state.sending,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Said here rather than discovered later. A correction is not a support
                // ticket, and an owner who expects a reply to one is an owner we have let down.
                OdoText(
                    text = stringResource(Res.string.sp_flag_footer),
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textMuted,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        ) {
            val band = state.band
            if (band != null) {
                BandCard(band)
            } else {
                // Opened from the help sheet, so there is no band to echo. The owner names
                // the job instead — without one, a figure has nothing to correct.
                OdoText(
                    text = stringResource(Res.string.sp_flag_no_band),
                    style = OdoTheme.typography.body,
                    color = OdoTheme.colors.textDim,
                )
                SectionLabel(stringResource(Res.string.sp_flag_which_label))
                OdoInputField(
                    value = state.jobName,
                    onValueChange = { onEvent(FlagPriceEvent.JobNameChanged(it)) },
                    placeholder = stringResource(Res.string.sp_flag_which_hint),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionLabel(stringResource(Res.string.sp_flag_whats_off))
            BandComplaint.entries.forEach { complaint ->
                OdoOptionCard(
                    label = complaint.label(),
                    selected = state.complaint == complaint,
                    onClick = { onEvent(FlagPriceEvent.ComplaintPicked(complaint)) },
                    style = OdoOptionCardStyle.Filled,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionLabel(stringResource(Res.string.sp_flag_paid))
            OdoInputField(
                value = state.paidRupees,
                // Digits only. A rupee field that accepts "about 2,300" produces a data point
                // nobody can put in a table.
                onValueChange = { typed -> onEvent(FlagPriceEvent.PaidChanged(typed.filter { it.isDigit() })) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                leadingIcon = { OdoText(text = RUPEE, style = OdoTheme.typography.body) },
                modifier = Modifier.fillMaxWidth(),
            )
            OdoText(
                text = stringResource(Res.string.sp_flag_paid_note),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textMuted,
            )

            OdoCard(onClick = { onEvent(FlagPriceEvent.AttachBillClicked) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OdoIcon(imageVector = IcCamera, contentDescription = null)
                        OdoText(
                            text = stringResource(Res.string.sp_flag_attach_bill),
                            style = OdoTheme.typography.body,
                        )
                    }
                    OdoText(
                        text = stringResource(Res.string.sp_flag_attach_optional),
                        style = OdoTheme.typography.bodySmall,
                        color = OdoTheme.colors.textMuted,
                    )
                }
            }
        }
    }
}

/** The band, and the filters it was built at. */
@Composable
private fun BandCard(band: DisputedBand) {
    OdoCard {
        OdoText(
            text = stringResource(Res.string.sp_flag_band_label),
            style = OdoTheme.typography.label,
            color = OdoTheme.colors.textMuted,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OdoText(text = band.lineName, style = OdoTheme.typography.title)
            OdoText(text = band.range(), style = OdoTheme.typography.title)
        }
        val filters = listOfNotNull(band.city, band.workshop, band.segment)
        if (filters.isNotEmpty()) {
            OdoText(
                text = filters.joinToString(FILTER_SEPARATOR),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textDim,
            )
        }
    }
}

private fun DisputedBand.range(): String {
    val low = Amount.of(lowPaise).getOrNull() ?: Amount.ZERO
    val high = Amount.of(highPaise).getOrNull() ?: Amount.ZERO
    return if (highPaise > lowPaise) low.formatRupeeRangeTo(high) else low.formatRupees()
}

@Composable
private fun BandComplaint.label(): String = stringResource(
    when (this) {
        BandComplaint.TOO_LOW -> Res.string.sp_flag_too_low
        BandComplaint.TOO_HIGH -> Res.string.sp_flag_too_high
        BandComplaint.WRONG_ITEM -> Res.string.sp_flag_wrong_item
    },
)

private const val FILTER_SEPARATOR = " · "

/**
 * The field's prefix, matching what the band above it is rendered with.
 *
 * "Rs." rather than the mockup's "₹" for one reason: the card at the top of this screen reads
 * "Rs. 1,400–1,800" through the app's own formatter, and two currency marks on one screen is
 * worse than either mark on its own. Whether the whole app moves to ₹ is a separate change,
 * and it is one line in `AmountFormat.kt`.
 */
private const val RUPEE = "Rs."

@OdoThemePreviews
@Composable
private fun FlagPricePreview() = OdoPreview(padded = false) {
    FlagPriceScreen(
        state = FlagPriceUiState(
            band = DisputedBand(
                lineName = "AC service",
                lowPaise = 140_000L,
                highPaise = 180_000L,
                city = "Srinagar",
                workshop = "company centre",
                segment = "1.2L petrol hatchback",
            ),
            complaint = BandComplaint.TOO_LOW,
            paidRupees = "2350",
        ),
        onEvent = {},
    )
}
