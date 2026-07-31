package com.hopcape.odo.feature.garage.presentation.components

import androidx.compose.runtime.Composable
import com.hopcape.odo.core.designsystem.component.OdoFuelKind
import com.hopcape.odo.core.designsystem.component.OdoFuelTypeOption
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.feature.garage.resources.Res
import com.hopcape.odo.feature.garage.resources.gr_fuel_cng
import com.hopcape.odo.feature.garage.resources.gr_fuel_diesel
import com.hopcape.odo.feature.garage.resources.gr_fuel_electric
import com.hopcape.odo.feature.garage.resources.gr_fuel_petrol
import org.jetbrains.compose.resources.stringResource

/**
 * The seam between the domain's [FuelType] and the design system's [OdoFuelKind].
 *
 * Two enums on purpose: the design system stays domain-free, and the domain stays
 * framework-free. This file is the only place in the garage where the two names for the
 * same fuel meet.
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
        FuelType.PETROL -> Res.string.gr_fuel_petrol
        FuelType.DIESEL -> Res.string.gr_fuel_diesel
        FuelType.CNG -> Res.string.gr_fuel_cng
        FuelType.ELECTRIC -> Res.string.gr_fuel_electric
    },
)

/**
 * The catalog's fuels as picker cards. Takes the list rather than reading [FuelType.entries]
 * itself, so the pickers offer what the catalog actually supports.
 *
 * No rate subtitles: the field will happily show "≈ ₹106 / L", but there is no fuel-price
 * feed wired, and a made-up rate is worse than none.
 */
@Composable
internal fun fuelOptions(fuels: List<FuelType>): List<OdoFuelTypeOption> = fuels.map { fuel ->
    OdoFuelTypeOption(kind = fuel.toFuelKind(), label = fuelLabel(fuel))
}
