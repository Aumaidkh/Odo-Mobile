package com.hopcape.odo.core.domain.cost

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.cost.fuel.FuelUnit
import com.hopcape.odo.core.domain.cost.model.FuelFill
import com.hopcape.odo.core.domain.cost.model.FuelFillId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
    fun a_fill_with_no_odometer_is_accepted_and_keeps_no_reading() {
        // The odometer is optional on a fill, unlike on a service entry. A detected fill
        // reaches the owner at the pump, where the dashboard is the one number they cannot
        // read, and losing the whole record over that field helps nobody.
        val fill = create(odometerKm = null).getOrNull()

        assertNotNull(fill)
        assertNull(fill.odometer)
    }

    @Test
    fun an_odometer_that_was_given_still_has_to_be_a_real_reading() {
        // Left alone is not the same as typed wrong. Only the second is worth an error.
        val errors = create(odometerKm = -1).leftOrNull()

        assertTrue(errors?.contains(DomainError.NegativeOdometer) == true)
    }

    @Test
    fun a_fill_dated_in_the_future_is_rejected() {
        val errors = create(filledOn = LocalDate(2026, 8, 5)).leftOrNull()
        assertTrue(errors?.contains(DomainError.FillDateInFuture) == true)
    }

    @Test
    fun every_field_failure_is_reported_at_once() {
        // An absent odometer is no longer one of them, so a fill missing all three inputs
        // reports the two that are still required.
        val errors = create(odometerKm = null, quantityMilli = null, filledOn = null).leftOrNull()
        assertEquals(2, errors?.size)
    }

    private fun create(
        filledOn: LocalDate? = today,
        odometerKm: Int? = 40_000,
        quantityMilli: Long? = 32_000,
        amountPaise: Long? = 320_000,
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
        transactionRef = transactionRef,
    )
}

private fun <A, B> arrow.core.Either<A, B>.leftOrNull(): A? = fold({ it }, { null })
