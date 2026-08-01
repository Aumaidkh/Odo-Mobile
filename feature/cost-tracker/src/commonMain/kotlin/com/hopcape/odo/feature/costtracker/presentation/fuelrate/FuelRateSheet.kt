package com.hopcape.odo.feature.costtracker.presentation.fuelrate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoInputField
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.cost.fuel.FuelUnit
import com.hopcape.odo.feature.costtracker.resources.Res
import com.hopcape.odo.feature.costtracker.resources.ct_fuel_rate_clear
import com.hopcape.odo.feature.costtracker.resources.ct_fuel_rate_label
import com.hopcape.odo.feature.costtracker.resources.ct_fuel_rate_placeholder
import com.hopcape.odo.feature.costtracker.resources.ct_fuel_rate_save
import com.hopcape.odo.feature.costtracker.resources.ct_fuel_rate_subtitle
import com.hopcape.odo.feature.costtracker.resources.ct_fuel_rate_title
import com.hopcape.odo.feature.costtracker.resources.ct_unit_kg
import com.hopcape.odo.feature.costtracker.resources.ct_unit_kwh
import com.hopcape.odo.feature.costtracker.resources.ct_unit_litre
import org.jetbrains.compose.resources.stringResource

/**
 * The "what do you pay for fuel?" sheet **body** — one price field and the two ways out of
 * it. Shown as a bottom-sheet destination
 * ([com.hopcape.odo.core.navigation.OdoDestination.CostTracker.FuelRate]); the
 * `ModalBottomSheet` chrome comes from the navigation layer.
 *
 * The field takes rupees, because that is what a pump board shows; the ViewModel turns them
 * into the paise everything else is kept in.
 */
@Composable
internal fun FuelRateSheetContent(
    state: FuelRateUiState,
    onEvent: (FuelRateEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = OdoTheme.spacing.lg, vertical = OdoTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        OdoText(stringResource(Res.string.ct_fuel_rate_title), style = OdoTheme.typography.title)
        OdoText(
            stringResource(Res.string.ct_fuel_rate_subtitle),
            style = OdoTheme.typography.bodySmall,
            color = OdoTheme.colors.textDim,
        )
        OdoInputField(
            value = state.price,
            onValueChange = { onEvent(FuelRateEvent.PriceChanged(it)) },
            label = stringResource(Res.string.ct_fuel_rate_label, unitLabel(state.unit)),
            placeholder = stringResource(Res.string.ct_fuel_rate_placeholder),
            errorText = state.error?.asString(),
            enabled = !state.saving,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OdoButton(
            text = stringResource(Res.string.ct_fuel_rate_save),
            onClick = { onEvent(FuelRateEvent.SaveTapped) },
            loading = state.saving,
            modifier = Modifier.fillMaxWidth(),
        )
        // Only offered when they have a rate of their own to drop; otherwise the button
        // would promise a change that is already in force.
        if (state.canClear) {
            OdoButton(
                text = stringResource(Res.string.ct_fuel_rate_clear),
                onClick = { onEvent(FuelRateEvent.ClearTapped) },
                variant = OdoButtonVariant.Tertiary,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun unitLabel(unit: FuelUnit): String = stringResource(
    when (unit) {
        FuelUnit.LITRE -> Res.string.ct_unit_litre
        FuelUnit.KILOGRAM -> Res.string.ct_unit_kg
        FuelUnit.KILOWATT_HOUR -> Res.string.ct_unit_kwh
    },
)

@OdoThemePreviews
@Composable
private fun FuelRateSheetPreview() = OdoPreview {
    FuelRateSheetContent(
        state = FuelRateUiState(price = "104.40", canClear = true),
        onEvent = {},
    )
}
