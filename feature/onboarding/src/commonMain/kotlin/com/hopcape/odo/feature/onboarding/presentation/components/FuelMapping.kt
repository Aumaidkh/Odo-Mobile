package com.hopcape.odo.feature.onboarding.presentation.components

import androidx.compose.runtime.Composable
import com.hopcape.odo.core.designsystem.component.OdoFuelKind
import com.hopcape.odo.core.designsystem.component.OdoFuelTypeOption
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.feature.onboarding.resources.Res
import com.hopcape.odo.feature.onboarding.resources.onb_fuel_cng
import com.hopcape.odo.feature.onboarding.resources.onb_fuel_diesel
import com.hopcape.odo.feature.onboarding.resources.onb_fuel_electric
import com.hopcape.odo.feature.onboarding.resources.onb_fuel_petrol
import org.jetbrains.compose.resources.stringResource

/**
 * The seam between the domain's [FuelType] and the design system's [OdoFuelKind].
 *
 * Two enums on purpose: the design system stays domain-free (it can be dropped into any
 * screen), and the domain stays framework-free. This file is the only place the two names
 * for the same fuel meet.
 */
internal fun FuelType.toFuelKind(): OdoFuelKind = when (this) {
    FuelType.PETROL -> OdoFuelKind.PETROL
    FuelType.DIESEL -> OdoFuelKind.DIESEL
    FuelType.CNG -> OdoFuelKind.CNG
    FuelType.ELECTRIC -> OdoFuelKind.ELECTRIC
}

internal fun OdoFuelKind.toDomain(): FuelType = when (this) {
    OdoFuelKind.PETROL -> FuelType.PETROL
    OdoFuelKind.DIESEL -> FuelType.DIESEL
    OdoFuelKind.CNG -> FuelType.CNG
    OdoFuelKind.ELECTRIC -> FuelType.ELECTRIC
}

/** The owner-facing name of a fuel ("Petrol", "CNG") — copy lives here, not in the field. */
@Composable
internal fun fuelLabel(fuel: FuelType): String = stringResource(
    when (fuel) {
        FuelType.PETROL -> Res.string.onb_fuel_petrol
        FuelType.DIESEL -> Res.string.onb_fuel_diesel
        FuelType.CNG -> Res.string.onb_fuel_cng
        FuelType.ELECTRIC -> Res.string.onb_fuel_electric
    },
)

/**
 * Every fuel as a picker card. No rate subtitles yet: the design system will happily show
 * "≈ ₹106 / L in Pune", but the fuel-price feed isn't wired — and a made-up rate is worse
 * than none (PRD: never show false precision).
 */
@Composable
internal fun fuelOptions(): List<OdoFuelTypeOption> = FuelType.entries.map { fuel ->
    OdoFuelTypeOption(kind = fuel.toFuelKind(), label = fuelLabel(fuel))
}
