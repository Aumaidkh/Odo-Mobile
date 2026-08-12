package com.hopcape.odo.core.domain.cost

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.cost.fuel.FuelUnit
import com.hopcape.odo.core.domain.cost.model.FuelFill
import com.hopcape.odo.core.domain.cost.model.FuelFillId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.payment.model.PaymentMethod
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FuelFillTest {

    private val today = LocalDate(2026, 8, 4)

    @Test
    fun a_complete_fill_is_created_and_derives_its_price_per_unit() {
        val fill = create(quantityMilli = 32_000, amountPaise = 320_000)
            .getOrElse { error("expected a fill, got $it") }

        // 3200 rupees over 32 litres is 100 rupees a litre.
        assertEquals(10_000, fill.pricePerUnit?.paise)
    }

    @Test
    fun the_price_per_unit_rounds_to_the_nearest_paise() {
        val fill = create(quantityMilli = 3_000, amountPaise = 10_000)
            .getOrElse { error("expected a fill") }
        // 100.00 rupees over 3 litres = 33.333… rupees, which rounds to 3333 paise.
        assertEquals(3_333, fill.pricePerUnit?.paise)
    }

    @Test
    fun a_fill_with_no_fuel_is_rejected() {
        // Zero is refused rather than stored as "unknown": it makes the measured mileage
        // meaningless with no way to tell afterwards.
        listOf(null, 0L, -1L).forEach { quantity ->
            val errors = create(quantityMilli = quantity).leftOrNull()
            assertTrue(
                errors?.contains(DomainError.MissingFuelQuantity) == true,
                "quantity=$quantity should be rejected",
            )
        }
    }

    @Test
    fun a_fill_with_no_odometer_is_rejected() {
        val errors = create(odometerKm = null).leftOrNull()
        assertTrue(errors?.contains(DomainError.MissingOdometer) == true)
    }

    @Test
    fun a_fill_dated_in_the_future_is_rejected() {
        val errors = create(filledOn = LocalDate(2026, 8, 5)).leftOrNull()
        assertTrue(errors?.contains(DomainError.FillDateInFuture) == true)
    }

    @Test
    fun every_field_failure_is_reported_at_once() {
        val errors = create(odometerKm = null, quantityMilli = null, filledOn = null).leftOrNull()
        assertEquals(3, errors?.size)
    }

    private fun create(
        filledOn: LocalDate? = today,
        odometerKm: Int? = 40_000,
        quantityMilli: Long? = 32_000,
        amountPaise: Long? = 320_000,
        paidVia: PaymentMethod = PaymentMethod.UPI,
        transactionRef: String? = "REF9",
    ) = FuelFill.create(
        id = FuelFillId("fill-1"),
        carId = CarId("car-1"),
        ownerId = OwnerId("owner-1"),
        filledOn = filledOn,
        odometerKm = odometerKm,
        quantityMilli = quantityMilli,
        unit = FuelUnit.LITRE,
        amountPaise = amountPaise,
        today = today,
        paidVia = paidVia,
        transactionRef = transactionRef,
    )
}

private fun <A, B> arrow.core.Either<A, B>.leftOrNull(): A? = fold({ it }, { null })
