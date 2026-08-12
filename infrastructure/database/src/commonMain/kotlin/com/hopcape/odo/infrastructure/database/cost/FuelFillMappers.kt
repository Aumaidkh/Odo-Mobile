package com.hopcape.odo.infrastructure.database.cost

import com.hopcape.odo.infrastructure.database.db.Fuel_fills
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.cost.fuel.FuelUnit
import com.hopcape.odo.core.domain.cost.model.FuelFill
import com.hopcape.odo.core.domain.cost.model.FuelFillId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.payment.model.PaymentMethod
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
    odometerKm = odometer_km.toInt(),
    quantityMilli = quantity_milli,
    unit = fuel_unit.toFuelUnit(),
    amountPaise = amount_paise,
    stationName = station_name,
    paidVia = paid_via.toPaymentMethod(),
    transactionRef = transaction_ref,
)

/**
 * A unit this build does not know reads as [FuelUnit.LITRE] — what the overwhelming majority
 * of fills are, and the only guess that keeps a price-per-unit roughly meaningful.
 */
private fun String.toFuelUnit(): FuelUnit =
    FuelUnit.entries.firstOrNull { it.name == this } ?: FuelUnit.LITRE

/**
 * An unrecognised payment method reads as [PaymentMethod.UNKNOWN], never [PaymentMethod.UPI].
 *
 * UPI is the only value that can make a fill count as verified, so guessing it would let a
 * row written by a newer build claim Odo watched a payment it never saw.
 */
private fun String.toPaymentMethod(): PaymentMethod =
    PaymentMethod.entries.firstOrNull { it.name == this } ?: PaymentMethod.UNKNOWN
