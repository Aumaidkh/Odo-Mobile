package com.hopcape.odo.core.domain.cost.fuel

import com.hopcape.odo.core.domain.car.model.FuelType

/**
 * What a car's fuel is sold by. Petrol and diesel go by the litre, CNG by the kilogram,
 * electricity by the unit (kWh).
 *
 * Kept apart from [FuelType] because a price and an efficiency are both *per unit*, and a
 * screen that prints "₹104.50/litre" for a CNG car is wrong in a way no amount of rounding
 * fixes.
 */
enum class FuelUnit {
    LITRE,
    KILOGRAM,
    KILOWATT_HOUR,
    ;

    companion object {
        fun of(fuelType: FuelType): FuelUnit = when (fuelType) {
            FuelType.PETROL, FuelType.DIESEL -> LITRE
            FuelType.CNG -> KILOGRAM
            FuelType.ELECTRIC -> KILOWATT_HOUR
        }
    }
}
