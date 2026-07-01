package com.hopcape.odo.feature.onboarding.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.hopcape.odo.core.designsystem.component.OdoChip
import com.hopcape.odo.core.designsystem.component.OdoDropdownField
import com.hopcape.odo.core.designsystem.component.OdoInputField
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.component.OdoWheelPickerField
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.feature.onboarding.presentation.contract.OnboardingEvent
import com.hopcape.odo.feature.onboarding.presentation.state.OnboardingUiState
import com.hopcape.odo.feature.onboarding.resources.Res
import com.hopcape.odo.feature.onboarding.resources.onb_car_subtitle
import com.hopcape.odo.feature.onboarding.resources.onb_car_title
import com.hopcape.odo.feature.onboarding.resources.onb_done
import com.hopcape.odo.feature.onboarding.resources.onb_fuel_cng
import com.hopcape.odo.feature.onboarding.resources.onb_fuel_diesel
import com.hopcape.odo.feature.onboarding.resources.onb_fuel_electric
import com.hopcape.odo.feature.onboarding.resources.onb_fuel_petrol
import com.hopcape.odo.feature.onboarding.resources.onb_label_fuel
import com.hopcape.odo.feature.onboarding.resources.onb_label_make
import com.hopcape.odo.feature.onboarding.resources.onb_label_model
import com.hopcape.odo.feature.onboarding.resources.onb_label_odometer
import com.hopcape.odo.feature.onboarding.resources.onb_label_purchase_year
import com.hopcape.odo.feature.onboarding.resources.onb_label_year
import com.hopcape.odo.feature.onboarding.resources.onb_placeholder_choose
import com.hopcape.odo.feature.onboarding.resources.onb_placeholder_odometer
import com.hopcape.odo.feature.onboarding.resources.onb_placeholder_unset
import com.hopcape.odo.feature.onboarding.resources.onb_required
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Step 1 — Car Details. Make/Model/Year/Purchase-year are [OdoDropdownField]s fed by
 * the catalog lists in [state]; odometer is the one **mandatory** field (PRD). All
 * validation lives in the domain — this only renders the current values and any
 * per-field errors, and reports edits back via [onEvent].
 */
@Composable
internal fun CarDetailsStep(
    state: OnboardingUiState,
    onEvent: (OnboardingEvent) -> Unit,
) {
    val form = state.form
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
    ) {
        StepHeading(stringResource(Res.string.onb_car_title), stringResource(Res.string.onb_car_subtitle))

        LabeledField(stringResource(Res.string.onb_label_make)) {
            OdoDropdownField(
                selected = form.make.value,
                options = state.makes,
                onSelect = { onEvent(OnboardingEvent.MakeChanged(it)) },
                placeholder = stringResource(Res.string.onb_placeholder_choose),
                errorText = form.make.error?.asString(),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
            LabeledField(stringResource(Res.string.onb_label_model), modifier = Modifier.weight(1.6f)) {
                OdoDropdownField(
                    selected = form.model.value,
                    options = state.models,
                    onSelect = { onEvent(OnboardingEvent.ModelChanged(it)) },
                    placeholder = stringResource(Res.string.onb_placeholder_choose),
                    errorText = form.model.error?.asString(),
                    // Models only load once a make is picked — no make, no menu.
                    enabled = form.make.value != null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            LabeledField(stringResource(Res.string.onb_label_year), modifier = Modifier.weight(1f)) {
                OdoWheelPickerField(
                    selected = form.year.value,
                    options = state.years,
                    onSelect = { onEvent(OnboardingEvent.YearChanged(it)) },
                    confirmLabel = stringResource(Res.string.onb_done),
                    placeholder = stringResource(Res.string.onb_placeholder_unset),
                    errorText = form.year.error?.asString(),
                    sheetTitle = stringResource(Res.string.onb_label_year),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        LabeledField(stringResource(Res.string.onb_label_fuel)) {
            FuelTypeSelector(
                options = state.fuelTypes,
                selected = form.fuelType.value,
                error = form.fuelType.error?.asString(),
                onSelect = { onEvent(OnboardingEvent.FuelTypeChanged(it)) },
            )
        }

        LabeledField(stringResource(Res.string.onb_label_odometer), required = true) {
            OdoInputField(
                value = form.odometer.value.orEmpty(),
                onValueChange = { onEvent(OnboardingEvent.OdometerChanged(it)) },
                placeholder = stringResource(Res.string.onb_placeholder_odometer),
                errorText = form.odometer.error?.asString(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        LabeledField(stringResource(Res.string.onb_label_purchase_year)) {
            OdoWheelPickerField(
                selected = form.purchaseYear.value,
                options = state.years,
                onSelect = { onEvent(OnboardingEvent.PurchaseYearChanged(it)) },
                confirmLabel = stringResource(Res.string.onb_done),
                placeholder = stringResource(Res.string.onb_placeholder_unset),
                errorText = form.purchaseYear.error?.asString(),
                sheetTitle = stringResource(Res.string.onb_label_purchase_year),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A static field label above its control, with an optional "★ REQUIRED" flag (odometer). */
@Composable
private fun LabeledField(
    label: String,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OdoText(label, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
            if (required) {
                OdoText(
                    text = stringResource(Res.string.onb_required),
                    style = OdoTheme.typography.caption,
                    color = OdoTheme.colors.accent,
                )
            }
        }
        content()
    }
}

/** The Petrol / Diesel / CNG / EV picker — a row of selectable [OdoChip]s that wraps if tight. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FuelTypeSelector(
    options: List<FuelType>,
    selected: FuelType?,
    error: String?,
    onSelect: (FuelType) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
            options.forEach { fuel ->
                OdoChip(
                    label = stringResource(fuel.labelRes()),
                    onClick = { onSelect(fuel) },
                    selected = fuel == selected,
                )
            }
        }
        if (error != null) {
            OdoText(
                text = error,
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.danger,
                modifier = Modifier.padding(horizontal = OdoTheme.spacing.lg),
            )
        }
    }
}

/** Short, user-facing fuel labels ("EV" reads better than the domain's ELECTRIC). */
private fun FuelType.labelRes(): StringResource = when (this) {
    FuelType.PETROL -> Res.string.onb_fuel_petrol
    FuelType.DIESEL -> Res.string.onb_fuel_diesel
    FuelType.CNG -> Res.string.onb_fuel_cng
    FuelType.ELECTRIC -> Res.string.onb_fuel_electric
}

@OdoThemePreviews
@Composable
private fun CarDetailsStepPreview() = OdoPreview(padded = false) {
    OnboardingScreen(state = previewCarDetailsState(), onEvent = {})
}
