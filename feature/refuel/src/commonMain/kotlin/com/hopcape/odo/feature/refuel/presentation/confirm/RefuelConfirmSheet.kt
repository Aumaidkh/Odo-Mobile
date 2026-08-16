package com.hopcape.odo.feature.refuel.presentation.confirm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import com.hopcape.odo.core.designsystem.component.OdoBadge
import com.hopcape.odo.core.designsystem.component.OdoBadgeTone
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoInputField
import com.hopcape.odo.core.designsystem.component.OdoOdometer
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.cost.model.FieldOrigin
import com.hopcape.odo.core.domain.cost.model.FillEntrySource
import com.hopcape.odo.feature.refuel.presentation.RefuelTestTags
import com.hopcape.odo.feature.refuel.presentation.label
import com.hopcape.odo.feature.refuel.presentation.tone
import com.hopcape.odo.feature.refuel.resources.Res
import com.hopcape.odo.feature.refuel.resources.rf_amount_label
import com.hopcape.odo.feature.refuel.resources.rf_confirm_cta
import com.hopcape.odo.feature.refuel.resources.rf_confirm_reject
import com.hopcape.odo.feature.refuel.resources.rf_confirm_title
import com.hopcape.odo.feature.refuel.resources.rf_km
import com.hopcape.odo.feature.refuel.resources.rf_mi
import com.hopcape.odo.feature.refuel.resources.rf_odometer_hint
import com.hopcape.odo.feature.refuel.resources.rf_odometer_label
import com.hopcape.odo.feature.refuel.resources.rf_odometer_optional
import com.hopcape.odo.feature.refuel.resources.rf_odometer_predicted
import com.hopcape.odo.feature.refuel.resources.rf_odometer_save
import com.hopcape.odo.feature.refuel.resources.rf_odometer_subtitle
import com.hopcape.odo.feature.refuel.resources.rf_odometer_title
import com.hopcape.odo.feature.refuel.resources.rf_quantity_label
import com.hopcape.odo.feature.refuel.resources.rf_rate_info
import com.hopcape.odo.feature.refuel.resources.rf_rate_label
import com.hopcape.odo.feature.refuel.resources.rf_rate_source
import com.hopcape.odo.feature.refuel.resources.rf_rate_unset_body
import com.hopcape.odo.feature.refuel.resources.rf_rate_unset_cta
import com.hopcape.odo.feature.refuel.resources.rf_rate_unset_title
import com.hopcape.odo.feature.refuel.resources.rf_small_body
import com.hopcape.odo.feature.refuel.resources.rf_small_title
import com.hopcape.odo.feature.refuel.resources.rf_small_usual
import org.jetbrains.compose.resources.stringResource

/**
 * The confirm surface's **body** — the one screen every captured fill passes through.
 *
 * Shown as a bottom-sheet destination
 * ([com.hopcape.odo.core.navigation.OdoDestination.Refuel.Confirm]); the `ModalBottomSheet`
 * chrome comes from the navigation layer.
 *
 * The layout is the same whichever channel captured the fill. What changes is the badge at
 * the top, the chips beside each number, and whether the small-amount question is asked
 * instead of a plain title — a detected ₹300 at a fuel brand deserves a question, a scanned
 * ₹1,500 does not.
 */
@Composable
internal fun RefuelConfirmSheetContent(
    state: RefuelConfirmUiState,
    onEvent: (RefuelConfirmEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Three number fields summon the keyboard; without this the confirm button and
            // the reject row sit under it with no way to reach them.
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = OdoTheme.spacing.lg, vertical = OdoTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        Header(state)

        state.smallAmountQuery?.let { query -> SmallAmountCard(query) }

        OdoInputField(
            value = state.amount,
            onValueChange = { onEvent(RefuelConfirmEvent.AmountChanged(it)) },
            label = stringResource(Res.string.rf_amount_label),
            enabled = !state.saving,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            trailingIcon = originChip(state.amountOrigin),
            modifier = Modifier.fillMaxWidth().testTag(RefuelTestTags.AMOUNT_FIELD),
        )
        // Nothing to divide by, so the quantity field would only ever be an empty box with a
        // label. The prompt takes its place and says why, and where to fix it.
        if (state.fuelRateUnset) FuelRateUnsetCard(onEvent)

        OdoInputField(
            value = state.quantity,
            onValueChange = { onEvent(RefuelConfirmEvent.QuantityChanged(it)) },
            label = quantityLabel(state),
            enabled = !state.saving,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            trailingIcon = originChip(state.quantityOrigin),
            modifier = Modifier.fillMaxWidth().testTag(RefuelTestTags.QUANTITY_FIELD),
        )
        OdoInputField(
            value = state.rate,
            onValueChange = { onEvent(RefuelConfirmEvent.RateChanged(it)) },
            label = stringResource(Res.string.rf_rate_label),
            // The quantity above is derived from this number, so where it came from is worth
            // one tap rather than a guess. Only offered once there is a rate to explain.
            helperText = if (state.fuelRateUnset) null else stringResource(Res.string.rf_rate_source),
            errorText = state.error?.asString(),
            enabled = !state.saving,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            trailingIcon = originChip(state.rateOrigin),
            modifier = Modifier.fillMaxWidth().testTag(RefuelTestTags.RATE_FIELD),
        )
        if (!state.fuelRateUnset) {
            OdoButton(
                text = stringResource(Res.string.rf_rate_info),
                onClick = { onEvent(RefuelConfirmEvent.FuelRateTapped) },
                variant = OdoButtonVariant.Tertiary,
                enabled = !state.saving,
                modifier = Modifier.testTag(RefuelTestTags.RATE_INFO),
            )
        }

        OdometerBlock(state, onEvent)

        OdoButton(
            text = stringResource(Res.string.rf_confirm_cta),
            onClick = { onEvent(RefuelConfirmEvent.ConfirmTapped) },
            enabled = state.canConfirm,
            loading = state.saving,
            modifier = Modifier.fillMaxWidth().testTag(RefuelTestTags.CONFIRM_BUTTON),
        )
        OdoButton(
            text = stringResource(Res.string.rf_confirm_reject),
            onClick = { onEvent(RefuelConfirmEvent.RejectTapped) },
            variant = OdoButtonVariant.Tertiary,
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Header(state: RefuelConfirmUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
            OdoText(stringResource(Res.string.rf_confirm_title), style = OdoTheme.typography.title)
            state.stationName?.let { station ->
                OdoText(station, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
            }
        }
        OdoBadge(text = state.source.label(), tone = state.source.badgeTone())
    }
}

/**
 * The question asked instead of a plain confirmation when a detected payment is far below
 * the owner's usual tank.
 *
 * It shows both figures rather than only doubting them: the amount that was paid and what
 * their fills normally cost. An owner can settle that in a second; "are you sure?" on its
 * own they cannot.
 */
@Composable
private fun SmallAmountCard(query: SmallAmountQuery) {
    OdoCard {
        OdoText(stringResource(Res.string.rf_small_title), style = OdoTheme.typography.heading)
        OdoText(
            stringResource(Res.string.rf_small_body, query.paidLabel, query.usualRangeLabel),
            style = OdoTheme.typography.bodySmall,
            color = OdoTheme.colors.textDim,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            OdoText(
                stringResource(Res.string.rf_small_usual),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textDim,
            )
            OdoText(query.usualRangeLabel, style = OdoTheme.typography.label)
        }
    }
}

/**
 * What to say when the owner has no fuel price of their own.
 *
 * Shown instead of a quantity rather than beside a guessed one. Odo can only turn ₹3,400 into
 * litres by dividing it by a price per litre, and the only price worth dividing by is the one
 * this owner set — a seeded city figure would produce a number to two decimal places that
 * nobody chose and everybody would believe.
 */
@Composable
private fun FuelRateUnsetCard(onEvent: (RefuelConfirmEvent) -> Unit) {
    OdoCard {
        OdoText(stringResource(Res.string.rf_rate_unset_title), style = OdoTheme.typography.heading)
        OdoText(
            stringResource(Res.string.rf_rate_unset_body),
            style = OdoTheme.typography.bodySmall,
            color = OdoTheme.colors.textDim,
        )
        OdoButton(
            text = stringResource(Res.string.rf_rate_unset_cta),
            onClick = { onEvent(RefuelConfirmEvent.FuelRateTapped) },
            variant = OdoButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth().testTag(RefuelTestTags.RATE_SET),
        )
    }
}

/**
 * The odometer, always as the design system's drum rather than a number field.
 *
 * The warning above it is the whole reason a predicted reading is safe to offer: the owner
 * is told the figure is Odo's guess before they are asked to agree with it.
 */
@Composable
private fun OdometerBlock(
    state: RefuelConfirmUiState,
    onEvent: (RefuelConfirmEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OdoText(
                    stringResource(Res.string.rf_odometer_label),
                    style = OdoTheme.typography.caption,
                    color = OdoTheme.colors.textMuted,
                )
                // Said out loud rather than left to be discovered by trying to confirm
                // without it. A fill this owner cannot complete is a fill they abandon.
                OdoText(
                    stringResource(Res.string.rf_odometer_optional),
                    style = OdoTheme.typography.caption,
                    color = OdoTheme.colors.textDim,
                )
            }
            if (state.odometerPredicted) {
                OdoBadge(
                    text = stringResource(Res.string.rf_odometer_predicted),
                    tone = OdoBadgeTone.Warning,
                )
            }
        }
        OdoOdometer(
            value = state.odometerKm,
            onValueChange = { onEvent(RefuelConfirmEvent.OdometerChanged(it)) },
            title = stringResource(Res.string.rf_odometer_title),
            subtitle = stringResource(Res.string.rf_odometer_subtitle),
            odometerLabel = stringResource(Res.string.rf_odometer_label),
            saveLabel = stringResource(Res.string.rf_odometer_save),
            kmLabel = stringResource(Res.string.rf_km),
            milesLabel = stringResource(Res.string.rf_mi),
            helper = stringResource(Res.string.rf_odometer_hint),
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The provenance chip, or nothing at all for a value the owner typed. */
@Composable
private fun originChip(origin: FieldOrigin): (@Composable () -> Unit)? {
    val label = origin.label() ?: return null
    return { OdoBadge(text = label, tone = origin.tone()) }
}

@Composable
private fun quantityLabel(state: RefuelConfirmUiState): String {
    val unit = state.unitLabel?.asString()
    val base = stringResource(Res.string.rf_quantity_label)
    return if (unit == null) base else "$base ($unit)"
}

/**
 * A detected fill is the only one badged as an assertion Odo is making; the rest describe
 * what the owner already did.
 */
private fun FillEntrySource.badgeTone(): OdoBadgeTone = when (this) {
    FillEntrySource.DETECTED -> OdoBadgeTone.Accent
    FillEntrySource.PUMP_OCR -> OdoBadgeTone.Success
    else -> OdoBadgeTone.Neutral
}

@OdoThemePreviews
@Composable
private fun RefuelConfirmPreview() = OdoPreview {
    RefuelConfirmSheetContent(
        state = RefuelConfirmUiState(
            source = FillEntrySource.DETECTED,
            stationName = "Bharat Petroleum, Karol Bagh",
            amount = "2000",
            amountOrigin = FieldOrigin.PAYMENT,
            quantity = "21.11",
            quantityOrigin = FieldOrigin.DERIVED,
            rate = "94.70",
            rateOrigin = FieldOrigin.HISTORY,
            odometerKm = 34_560,
            odometerOrigin = FieldOrigin.PREDICTED,
        ),
        onEvent = {},
    )
}
