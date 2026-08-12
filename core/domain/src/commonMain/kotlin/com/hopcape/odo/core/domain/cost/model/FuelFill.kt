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
import com.hopcape.odo.core.domain.payment.model.PaymentMethod
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
 * accuracy. Paying at the pump through Odo changes that trade: the fill is being recorded as
 * a side effect of something the owner was doing anyway.
 *
 * [odometer] is mandatory, like it is on a service entry. It is what turns two fills into a
 * measured mileage; a fill without one is a receipt, and Odo already has a way to record
 * money that bought nothing measurable.
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
    val odometer: Distance,
    /** How much fuel went in, in thousandths of a unit — 32.45 litres is `32450`. */
    val quantityMilli: Long,
    val unit: FuelUnit,
    val amount: Amount,
    val stationName: String?,
    val paidVia: PaymentMethod,
    /** The bank's reference, when Odo watched the payment happen. Null for a fill typed in later. */
    val transactionRef: String?,
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
            paidVia: PaymentMethod = PaymentMethod.UNKNOWN,
            transactionRef: String? = null,
        ): EitherNel<DomainError, FuelFill> = either {
            zipOrAccumulate(
                { validateFilledOn(filledOn, today).bind() },
                { Distance.of(odometerKm).bind() },
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
                    paidVia = paidVia,
                    transactionRef = transactionRef?.trim()?.takeIf { it.isNotEmpty() },
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
            odometerKm: Int,
            quantityMilli: Long,
            unit: FuelUnit,
            amountPaise: Long,
            stationName: String?,
            paidVia: PaymentMethod,
            transactionRef: String?,
        ): FuelFill = FuelFill(
            id = id,
            carId = carId,
            ownerId = ownerId,
            filledOn = filledOn,
            odometer = Distance.of(odometerKm)
                .getOrElse { error("corrupt fuel_fills.odometer=$odometerKm for ${id.value}") },
            quantityMilli = quantityMilli,
            unit = unit,
            amount = Amount.of(amountPaise)
                .getOrElse { error("corrupt fuel_fills.amount=$amountPaise for ${id.value}") },
            stationName = stationName,
            paidVia = paidVia,
            transactionRef = transactionRef,
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
