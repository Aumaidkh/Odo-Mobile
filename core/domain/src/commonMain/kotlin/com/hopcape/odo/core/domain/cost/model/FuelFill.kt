package com.hopcape.odo.core.domain.cost.model

import arrow.core.Either
import arrow.core.EitherNel
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.raise.either
import arrow.core.raise.zipOrAccumulate
import arrow.core.right
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.cost.fuel.FuelUnit
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.datetime.LocalDate
import kotlin.jvm.JvmInline

/** Typed identity for a [FuelFill]. */
@JvmInline
value class FuelFillId(val value: String) {
    companion object {
        fun new(ids: IdGenerator): FuelFillId = FuelFillId(ids.newId())
    }
}

/**
 * One tank of fuel, as it was actually bought.
 *
 * This is the first thing in Odo that measures fuel rather than estimating it. Until now the
 * running cost multiplied a city price by an *assumed* mileage from
 * [FuelEfficiencyPolicy][com.hopcape.odo.core.domain.cost.fuel.FuelEfficiencyPolicy],
 * because the PRD ruled that asking owners to log every fill was friction not worth the
 * accuracy. Smart refuel changes that trade: the fill is captured as a side effect of
 * something the owner was doing anyway — paying at a pump, or photographing its display.
 *
 * [odometer] is optional, unlike a service entry's. It is what turns two fills into a
 * measured mileage, so a fill without one buys no mileage — but a fill Odo detected while the
 * owner was still standing at the pump cannot wait for them to walk to the dashboard and read
 * the number. Refusing the fill would lose the whole record to save one field of it. A fill
 * with no reading is kept, counted in the running cost, and skipped by the mileage.
 *
 * [pricePerUnit] is derived, not stored as given: the pump prints a rate, but the rate that
 * matters is what this fill actually cost per litre, and deriving it means the three numbers
 * can never disagree with each other.
 */
class FuelFill private constructor(
    val id: FuelFillId,
    val carId: CarId,
    val ownerId: OwnerId,
    val filledOn: LocalDate,
    val odometer: Distance?,
    /** How much fuel went in, in thousandths of a unit — 32.45 litres is `32450`. */
    val quantityMilli: Long,
    val unit: FuelUnit,
    val amount: Amount,
    val stationName: String?,
    /**
     * A reference for the payment, when a capture channel carried one. Null for every fill
     * today: nothing Odo reads gives it one, and it is kept because the column exists and a
     * later channel may.
     */
    val transactionRef: String?,
    /** Which capture channel produced this fill. See [FillEntrySource]. */
    val entrySource: FillEntrySource,
) {
    /**
     * What this fill worked out to per unit of fuel, in paise, rounded to the nearest paise.
     *
     * Null for a fill with no quantity recorded, which cannot happen through [create] but
     * can through a corrupted row — and a rate divided by zero is worse than no rate.
     */
    val pricePerUnit: Amount?
        get() {
            if (quantityMilli <= 0) return null
            val perUnit = (amount.paise * MILLI + quantityMilli / 2) / quantityMilli
            return Amount.of(perUnit).getOrElse { null }
        }

    companion object {
        /** Thousandths — the resolution quantities are stored at. */
        const val MILLI = 1_000L

        /**
         * Validating factory. Accumulates every field failure so the confirm step can show
         * them at once. [today] is injected; the domain owns no clock.
         */
        fun create(
            id: FuelFillId,
            carId: CarId,
            ownerId: OwnerId,
            filledOn: LocalDate?,
            odometerKm: Int?,
            quantityMilli: Long?,
            unit: FuelUnit,
            amountPaise: Long?,
            today: LocalDate,
            stationName: String? = null,
            transactionRef: String? = null,
            entrySource: FillEntrySource = FillEntrySource.MANUAL,
        ): EitherNel<DomainError, FuelFill> = either {
            zipOrAccumulate(
                { validateFilledOn(filledOn, today).bind() },
                { validateOdometer(odometerKm).bind() },
                { validateQuantity(quantityMilli).bind() },
                { Amount.of(amountPaise).bind() },
            ) { validDate, validOdometer, validQuantity, validAmount ->
                FuelFill(
                    id = id,
                    carId = carId,
                    ownerId = ownerId,
                    filledOn = validDate,
                    odometer = validOdometer,
                    quantityMilli = validQuantity,
                    unit = unit,
                    amount = validAmount,
                    stationName = stationName?.trim()?.takeIf { it.isNotEmpty() },
                    transactionRef = transactionRef?.trim()?.takeIf { it.isNotEmpty() },
                    entrySource = entrySource,
                )
            }
        }

        /**
         * Rehydrate from trusted local data. Fails fast rather than accumulating: a value
         * that will not reconstruct is corruption, not user input.
         */
        fun reconstitute(
            id: FuelFillId,
            carId: CarId,
            ownerId: OwnerId,
            filledOn: LocalDate,
            odometerKm: Int?,
            quantityMilli: Long,
            unit: FuelUnit,
            amountPaise: Long,
            stationName: String?,
            transactionRef: String?,
            entrySource: FillEntrySource = FillEntrySource.MANUAL,
        ): FuelFill = FuelFill(
            id = id,
            carId = carId,
            ownerId = ownerId,
            filledOn = filledOn,
            odometer = odometerKm?.let { km ->
                Distance.of(km)
                    .getOrElse { error("corrupt fuel_fills.odometer=$km for ${id.value}") }
            },
            quantityMilli = quantityMilli,
            unit = unit,
            amount = Amount.of(amountPaise)
                .getOrElse { error("corrupt fuel_fills.amount=$amountPaise for ${id.value}") },
            stationName = stationName,
            transactionRef = transactionRef,
            entrySource = entrySource,
        )

        private fun validateFilledOn(
            date: LocalDate?,
            today: LocalDate,
        ): Either<DomainError, LocalDate> = when {
            date == null -> DomainError.MissingFillDate.left()
            date > today -> DomainError.FillDateInFuture.left()
            else -> date.right()
        }

        /**
         * A reading that was given has to be a real one; one that was not is not an error.
         *
         * The distinction matters because these are different owners. Someone who left the
         * field alone is telling Odo nothing about the odometer, which is fine. Someone who
         * typed a negative number has made a mistake worth showing them.
         */
        private fun validateOdometer(odometerKm: Int?): Either<DomainError, Distance?> =
            if (odometerKm == null) null.right() else Distance.of(odometerKm)

        /**
         * A fill has to have fuel in it. Zero is rejected rather than allowed as "unknown",
         * because a zero quantity silently makes the measured mileage meaningless and there
         * is no way to tell it apart from a real reading afterwards.
         */
        private fun validateQuantity(quantityMilli: Long?): Either<DomainError, Long> = when {
            quantityMilli == null || quantityMilli <= 0 -> DomainError.MissingFuelQuantity.left()
            else -> quantityMilli.right()
        }
    }
}
