package com.hopcape.odo.feature.garage.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoOdometer
import com.hopcape.odo.core.designsystem.component.OdoRegistrationNumberField
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.designsystem.units.LocalOdoDistanceFormat
import com.hopcape.odo.core.domain.car.lookup.RegisteredVehicle
import com.hopcape.odo.feature.garage.presentation.components.fuelLabel
import com.hopcape.odo.feature.garage.presentation.state.text
import com.hopcape.odo.feature.garage.resources.Res
import com.hopcape.odo.feature.garage.resources.gr_ac_add
import com.hopcape.odo.feature.garage.resources.gr_ac_current_odo
import com.hopcape.odo.feature.garage.resources.gr_ac_details
import com.hopcape.odo.feature.garage.resources.gr_ac_found_meta
import com.hopcape.odo.feature.garage.resources.gr_ac_intro
import com.hopcape.odo.feature.garage.resources.gr_ac_looking
import com.hopcape.odo.feature.garage.resources.gr_ac_lookup_unavailable
import com.hopcape.odo.feature.garage.resources.gr_ac_odo_hint
import com.hopcape.odo.feature.garage.resources.gr_ac_odo_subtitle
import com.hopcape.odo.feature.garage.resources.gr_ac_reg
import com.hopcape.odo.feature.garage.resources.gr_ac_title
import com.hopcape.odo.feature.garage.resources.gr_cd_close
import com.hopcape.odo.feature.garage.resources.gr_odo_save
import com.hopcape.odo.feature.garage.resources.gr_odo_unit_km
import com.hopcape.odo.feature.garage.resources.gr_odo_unit_miles
import com.hopcape.odo.feature.garage.resources.gr_odometer
import org.jetbrains.compose.resources.stringResource

/**
 * Add-a-car full screen ([com.hopcape.odo.core.navigation.OdoDestination.Garage.AddCar]).
 *
 * Reg-number-first, like onboarding's car step: entering a whole plate asks the registry,
 * and a match fills the fields in for the owner to confirm. There is no registry behind
 * that port yet, so the fields are **always on screen** rather than hidden behind a "wrong
 * car?" link — the manual path is the one every owner actually takes, and a form that hides
 * itself waiting for a match that never comes is a dead end.
 *
 * The odometer is asked for here for the same reason onboarding asks for it: it is the
 * number the whole product hangs off, so a car enters the garage with one.
 */
@Composable
internal fun AddCarScreen(state: AddCarUiState, onEvent: (AddCarEvent) -> Unit) {
    OdoScreen(
        topBar = {
            CloseTopBar(
                title = stringResource(Res.string.gr_ac_title),
                closeLabel = stringResource(Res.string.gr_cd_close),
                onClose = { onEvent(AddCarEvent.CloseTapped) },
            )
        },
        bottomBar = {
            GarageBottomButton(
                label = stringResource(Res.string.gr_ac_add),
                onClick = { onEvent(AddCarEvent.AddTapped) },
                enabled = !state.submission.isInFlight,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(vertical = OdoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
        ) {
            OdoText(
                stringResource(Res.string.gr_ac_intro),
                style = OdoTheme.typography.body,
                color = OdoTheme.colors.textDim,
            )

            OdoRegistrationNumberField(
                modifier = Modifier.testTag(GarageTestTags.REGISTRATION_FIELD),
                value = state.fields.registration.text,
                onValueChange = { onEvent(AddCarEvent.PlateChanged(it)) },
                label = stringResource(Res.string.gr_ac_reg),
            )

            LookupStatus(state)

            Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg)) {
                FieldLabel(stringResource(Res.string.gr_ac_details))
                CarDetailFields(
                    fields = state.fields,
                    options = state.options,
                    onMakeSelected = { onEvent(AddCarEvent.MakeSelected(it)) },
                    onModelSelected = { onEvent(AddCarEvent.ModelSelected(it)) },
                    onYearSelected = { onEvent(AddCarEvent.YearSelected(it)) },
                    onFuelSelected = { onEvent(AddCarEvent.FuelSelected(it)) },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
                FieldLabel(stringResource(Res.string.gr_ac_current_odo))
                val distance = LocalOdoDistanceFormat.current
                OdoOdometer(
                    modifier = Modifier.testTag(GarageTestTags.ODOMETER_FIELD),
                    // Shown and typed in the owner's unit; stored in kilometres.
                    value = state.odometer.value?.let { distance.display(it.toInt()).toLong() },
                    onValueChange = { dialled ->
                        val km = distance.store(dialled.toInt(), state.odometer.value?.toInt())
                        onEvent(AddCarEvent.OdometerChanged(km.toLong()))
                    },
                    title = stringResource(Res.string.gr_ac_current_odo),
                    subtitle = stringResource(Res.string.gr_ac_odo_subtitle),
                    odometerLabel = stringResource(Res.string.gr_odometer),
                    saveLabel = stringResource(Res.string.gr_odo_save),
                    kmLabel = stringResource(Res.string.gr_odo_unit_km),
                    milesLabel = stringResource(Res.string.gr_odo_unit_miles),
                    hint = stringResource(Res.string.gr_ac_odo_hint),
                )
                // The odometer control has no error slot of its own — it is a drum, not a
                // text field — so a rejected reading is said underneath it.
                state.odometer.error?.let { message ->
                    OdoText(
                        message.asString(),
                        style = OdoTheme.typography.bodySmall,
                        color = OdoTheme.colors.danger,
                    )
                }
            }

            state.submission.error?.let { message ->
                OdoText(
                    message.asString(),
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.danger,
                )
            }
        }
    }
}

/**
 * What the plate lookup is doing: waiting, a match to confirm, or a plain note that the
 * registry cannot answer. The note is what owners will see today, and saying so is better
 * than a spinner that resolves into nothing.
 */
@Composable
private fun LookupStatus(state: AddCarUiState) {
    AnimatedVisibility(
        visible = state.isLookingUp,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        OdoText(
            stringResource(Res.string.gr_ac_looking),
            style = OdoTheme.typography.bodySmall,
            color = OdoTheme.colors.textDim,
        )
    }
    AnimatedVisibility(
        visible = state.match != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        state.match?.let { FoundCarCard(it) }
    }
    AnimatedVisibility(
        visible = !state.isLookingUp && state.match == null && state.fields.registration.text.isNotBlank(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        OdoText(
            stringResource(Res.string.gr_ac_lookup_unavailable),
            style = OdoTheme.typography.bodySmall,
            color = OdoTheme.colors.textDim,
        )
    }
}

/** The registry's answer, for the owner to confirm or correct in the fields below. */
@Composable
private fun FoundCarCard(vehicle: RegisteredVehicle) {
    OdoCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CarAvatar()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                OdoText(
                    listOfNotNull(vehicle.make, vehicle.model, vehicle.variant).joinToString(" "),
                    style = OdoTheme.typography.heading,
                    maxLines = 1,
                )
                OdoText(
                    stringResource(
                        Res.string.gr_ac_found_meta,
                        vehicle.year.value,
                        fuelLabel(vehicle.fuelType),
                    ),
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textDim,
                    maxLines = 1,
                )
            }
            OdoIcon(IcCheck, contentDescription = null, tint = OdoTheme.colors.accent, size = OdoTheme.iconSizes.medium)
        }
    }
}

@OdoThemePreviews
@Composable
private fun AddCarPreview() = OdoPreview(padded = false) {
    AddCarScreen(state = sampleAddCar(), onEvent = {})
}
