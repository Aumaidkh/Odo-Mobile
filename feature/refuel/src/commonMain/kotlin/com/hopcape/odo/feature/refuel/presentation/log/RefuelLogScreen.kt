package com.hopcape.odo.feature.refuel.presentation.log

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import com.hopcape.odo.core.designsystem.component.OdoBadge
import com.hopcape.odo.core.designsystem.component.OdoBadgeTone
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoChip
import com.hopcape.odo.core.designsystem.component.OdoInputField
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.refuel.presentation.RefuelTestTags
import com.hopcape.odo.feature.refuel.resources.Res
import com.hopcape.odo.feature.refuel.resources.rf_km
import com.hopcape.odo.feature.refuel.resources.rf_log_amount_label
import com.hopcape.odo.feature.refuel.resources.rf_log_cta
import com.hopcape.odo.feature.refuel.resources.rf_log_no_station
import com.hopcape.odo.feature.refuel.resources.rf_log_odometer_predicted
import com.hopcape.odo.feature.refuel.resources.rf_log_prefill_note
import com.hopcape.odo.feature.refuel.resources.rf_log_quantity_calculated
import com.hopcape.odo.feature.refuel.resources.rf_log_rate_carried
import com.hopcape.odo.feature.refuel.resources.rf_log_scan_pump
import com.hopcape.odo.feature.refuel.resources.rf_log_station
import com.hopcape.odo.feature.refuel.resources.rf_log_station_last
import com.hopcape.odo.feature.refuel.resources.rf_log_title
import com.hopcape.odo.feature.refuel.resources.rf_logged_odometer
import com.hopcape.odo.feature.refuel.resources.rf_rate_label
import org.jetbrains.compose.resources.stringResource

/**
 * The form an owner opens themselves — and the only channel that needs nothing from the
 * phone but a keyboard.
 *
 * Everything above the amount field is context rather than input: the station they last
 * used, the rate in force, the odometer Odo expects. They are shown so the owner can
 * disagree, not so they can fill them in. The single editable field is what makes this the
 * two-taps-and-a-number path the design promises.
 */
@Composable
internal fun RefuelLogScreen(
    state: RefuelLogUiState,
    onEvent: (RefuelLogEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(
        modifier = modifier,
        title = stringResource(Res.string.rf_log_title),
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OdoTheme.spacing.lg, vertical = OdoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        ) {
            OdoText(
                stringResource(Res.string.rf_log_prefill_note),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textDim,
            )

            OdoCard(modifier = Modifier.fillMaxWidth()) {
                ContextRow(
                    label = stringResource(Res.string.rf_log_station),
                    note = stringResource(Res.string.rf_log_station_last),
                    value = state.stationName ?: stringResource(Res.string.rf_log_no_station),
                )
                ContextRow(
                    label = stringResource(Res.string.rf_rate_label),
                    note = stringResource(Res.string.rf_log_rate_carried),
                    value = state.rateLabel,
                )
                ContextRow(
                    label = stringResource(Res.string.rf_logged_odometer),
                    note = if (state.odometerPredicted) {
                        stringResource(Res.string.rf_log_odometer_predicted)
                    } else {
                        null
                    },
                    value = state.odometerKm
                        ?.let { "$it ${stringResource(Res.string.rf_km)}" }
                        .orEmpty(),
                    warn = state.odometerPredicted,
                )
            }

            OdoText(
                stringResource(Res.string.rf_log_amount_label),
                style = OdoTheme.typography.caption,
                color = OdoTheme.colors.textMuted,
            )
            OdoInputField(
                value = state.amount,
                onValueChange = { onEvent(RefuelLogEvent.AmountChanged(it)) },
                errorText = state.error?.asString(),
                enabled = !state.loading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag(RefuelTestTags.LOG_AMOUNT_FIELD),
            )
            // Shown as soon as there is an amount: the owner sees what their money bought
            // before they commit, which is the check the confirm step would otherwise be for.
            state.quantityLabel?.let { quantity ->
                OdoText(
                    stringResource(Res.string.rf_log_quantity_calculated, quantity),
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textDim,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
                state.quickAmounts.forEach { quick ->
                    OdoChip(
                        label = quick.label,
                        onClick = { onEvent(RefuelLogEvent.QuickAmountTapped(quick.paise)) },
                    )
                }
                OdoChip(
                    label = stringResource(Res.string.rf_log_scan_pump),
                    onClick = { onEvent(RefuelLogEvent.ScanPumpTapped) },
                )
            }

            OdoButton(
                text = stringResource(Res.string.rf_log_cta),
                onClick = { onEvent(RefuelLogEvent.DoneTapped) },
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth().testTag(RefuelTestTags.LOG_DONE_BUTTON),
            )
        }
    }
}

/**
 * One line of read-only context.
 *
 * [note] is what makes it honest — "last used", "carried forward", "predicted" — because a
 * value with no provenance reads as a fact the app checked, and none of these are.
 */
@Composable
private fun ContextRow(
    label: String,
    note: String?,
    value: String,
    warn: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
            OdoText(label, style = OdoTheme.typography.label)
            note?.let {
                if (warn) {
                    OdoBadge(text = it, tone = OdoBadgeTone.Warning)
                } else {
                    OdoText(it, style = OdoTheme.typography.caption, color = OdoTheme.colors.textMuted)
                }
            }
        }
        OdoText(value, style = OdoTheme.typography.body, color = OdoTheme.colors.textDim)
    }
}

@OdoThemePreviews
@Composable
private fun RefuelLogPreview() = OdoPreview {
    RefuelLogScreen(
        state = RefuelLogUiState(
            loading = false,
            stationName = "Shell · Lamar Blvd",
            rateLabel = "94.70/L",
            odometerKm = 88_140,
            odometerPredicted = true,
            amount = "2000",
            quantityLabel = "21.11 L",
            quickAmounts = listOf(
                QuickAmount(paise = 200_000, label = "2000"),
                QuickAmount(paise = 150_000, label = "1500"),
            ),
        ),
        onEvent = {},
        onBack = {},
    )
}
