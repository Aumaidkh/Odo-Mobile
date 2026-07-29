package com.hopcape.odo.feature.garage.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoCarMake
import com.hopcape.odo.core.designsystem.component.OdoCarModel
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoFuelKind
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoOdometer
import com.hopcape.odo.core.designsystem.component.OdoRegistrationNumberField
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.garage.resources.Res
import com.hopcape.odo.feature.garage.resources.gr_ac_add
import com.hopcape.odo.feature.garage.resources.gr_ac_current_odo
import com.hopcape.odo.feature.garage.resources.gr_ac_details
import com.hopcape.odo.feature.garage.resources.gr_ac_found_meta
import com.hopcape.odo.feature.garage.resources.gr_ac_intro
import com.hopcape.odo.feature.garage.resources.gr_ac_manual
import com.hopcape.odo.feature.garage.resources.gr_ac_odo_hint
import com.hopcape.odo.feature.garage.resources.gr_ac_odo_subtitle
import com.hopcape.odo.feature.garage.resources.gr_ac_reg
import com.hopcape.odo.feature.garage.resources.gr_ac_title
import com.hopcape.odo.feature.garage.resources.gr_ac_wrong
import com.hopcape.odo.feature.garage.resources.gr_cd_close
import com.hopcape.odo.feature.garage.resources.gr_fuel_petrol
import com.hopcape.odo.feature.garage.resources.gr_odo_save
import com.hopcape.odo.feature.garage.resources.gr_odo_unit_km
import com.hopcape.odo.feature.garage.resources.gr_odo_unit_miles
import com.hopcape.odo.feature.garage.resources.gr_odometer
import org.jetbrains.compose.resources.stringResource

/**
 * Add-a-car full screen ([com.hopcape.odo.core.navigation.OdoDestination.Garage.AddCar]).
 *
 * Reg-number-first, exactly like onboarding's car step: entering the plate surfaces an
 * RTO-matched car (sample), and "that's the wrong car" swaps the match for the same
 * make/model/year/fuel pickers onboarding falls back to — [CarDetailFields], built from
 * the shared design-system fields rather than imported from `:feature:onboarding`.
 *
 * The odometer is asked for here for the same reason onboarding asks for it: it is the
 * number the whole product hangs off, so a car enters the garage with one.
 *
 * Holds its own field state; [onAdd] / [onClose] pop back.
 */
@Composable
internal fun AddCarScreen(onClose: () -> Unit, onAdd: () -> Unit) {
    var reg by remember { mutableStateOf("MH12CD5678") }
    var odometer by remember { mutableStateOf<Long?>(22_300L) }
    var manual by remember { mutableStateOf(false) }
    var make by remember { mutableStateOf<OdoCarMake?>(null) }
    var model by remember { mutableStateOf<OdoCarModel?>(null) }
    var year by remember { mutableStateOf<Int?>(null) }
    var fuel by remember { mutableStateOf<OdoFuelKind?>(null) }

    OdoScreen(
        topBar = { CloseTopBar(stringResource(Res.string.gr_ac_title), stringResource(Res.string.gr_cd_close), onClose) },
        bottomBar = { GarageBottomButton(stringResource(Res.string.gr_ac_add), onAdd) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(vertical = OdoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
        ) {
            OdoText(stringResource(Res.string.gr_ac_intro), style = OdoTheme.typography.body, color = OdoTheme.colors.textDim)

            OdoRegistrationNumberField(
                value = reg,
                onValueChange = { reg = it },
                label = stringResource(Res.string.gr_ac_reg),
            )

            // The match and the manual form occupy the same slot: the owner is either
            // confirming what the RTO found or describing the car themselves, never both.
            AnimatedVisibility(
                visible = !manual,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
                    FoundCarCard()
                    Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                        OdoText(
                            stringResource(Res.string.gr_ac_wrong),
                            style = OdoTheme.typography.bodySmall,
                            color = OdoTheme.colors.textDim,
                        )
                        OdoText(
                            stringResource(Res.string.gr_ac_manual),
                            style = OdoTheme.typography.bodySmall,
                            color = OdoTheme.colors.accent,
                            modifier = Modifier.clickable { manual = true },
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = manual,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg)) {
                    FieldLabel(stringResource(Res.string.gr_ac_details))
                    CarDetailFields(
                        make = make,
                        onMakeChange = { make = it; model = null },
                        model = model,
                        onModelChange = { model = it },
                        year = year,
                        onYearChange = { year = it },
                        fuel = fuel,
                        onFuelChange = { fuel = it },
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
                FieldLabel(stringResource(Res.string.gr_ac_current_odo))
                OdoOdometer(
                    value = odometer,
                    onValueChange = { odometer = it },
                    title = stringResource(Res.string.gr_ac_current_odo),
                    subtitle = stringResource(Res.string.gr_ac_odo_subtitle),
                    odometerLabel = stringResource(Res.string.gr_odometer),
                    saveLabel = stringResource(Res.string.gr_odo_save),
                    kmLabel = stringResource(Res.string.gr_odo_unit_km),
                    milesLabel = stringResource(Res.string.gr_odo_unit_miles),
                    hint = stringResource(Res.string.gr_ac_odo_hint),
                )
            }
        }
    }
}

/** The RTO-matched car preview (sample content). */
@Composable
private fun FoundCarCard() {
    OdoCard {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            CarAvatar()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                OdoText("Hyundai i20 Asta", style = OdoTheme.typography.heading, maxLines = 1)
                OdoText(
                    stringResource(Res.string.gr_ac_found_meta, 2022, stringResource(Res.string.gr_fuel_petrol)),
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
    AddCarScreen(onClose = {}, onAdd = {})
}
