package com.hopcape.odo.infrastructure.database.cost

import com.hopcape.odo.infrastructure.database.db.Fuel_fills
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.cost.fuel.FuelUnit
import com.hopcape.odo.core.domain.cost.model.FillEntrySource
import com.hopcape.odo.core.domain.cost.model.FuelFill
import com.hopcape.odo.core.domain.cost.model.FuelFillId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import kotlinx.datetime.LocalDate

/**
 * DB row → domain. Rehydrates via `reconstitute`: the row was validated when it was written,
 * and the domain never sees the generated row type.
 *
 * The sync columns are read and ignored — they exist for the engine, and letting them reach
 * the domain is what the layering forbids.
 */
internal fun Fuel_fills.toDomain(): FuelFill = FuelFill.reconstitute(
    id = FuelFillId(id),
    carId = CarId(car_id),
    ownerId = OwnerId(owner_id),
    filledOn = LocalDate.parse(filled_on),
    odometerKm = odometer_km?.toInt(),
    quantityMilli = quantity_milli,
    unit = fuel_unit.toFuelUnit(),
    amountPaise = amount_paise,
    stationName = station_name,
    transactionRef = transaction_ref,
    entrySource = entry_source.toEntrySource(),
)

/**
 * A channel this build does not know reads as [FillEntrySource.MANUAL] — the same value the
 * column's own default gives rows written before the column existed.
 *
 * The direction matters: guessing `DETECTED` would let a row claim it came from a listener
 * that may never have run on this phone, and the auto-detect screen counts those rows to
 * show what detection has earned.
 */
private fun String.toEntrySource(): FillEntrySource =
    FillEntrySource.entries.firstOrNull { it.name == this } ?: FillEntrySource.MANUAL

/**
 * A unit this build does not know reads as [FuelUnit.LITRE] — what the overwhelming majority
 * of fills are, and the only guess that keeps a price-per-unit roughly meaningful.
 */
private fun String.toFuelUnit(): FuelUnit =
    FuelUnit.entries.firstOrNull { it.name == this } ?: FuelUnit.LITRE
