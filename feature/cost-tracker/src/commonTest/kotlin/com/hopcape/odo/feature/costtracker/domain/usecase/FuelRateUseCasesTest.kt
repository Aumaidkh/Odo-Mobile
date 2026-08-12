package com.hopcape.odo.feature.costtracker.domain.usecase

import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.cost.fuel.FuelPrice
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class FuelRateUseCasesTest {

    private val today = LocalDate(2026, 8, 1)

    @Test
    fun theOwnersRateIsStoredAgainstTheirFuelAndToday() = runTest {
        val overrides = FakeFuelPriceOverrides()

        val result = setUseCase(overrides).invoke(FuelType.PETROL, pricePaise = 10_440)

        assertTrue(result.isRight())
        assertEquals(1, overrides.set.size)
        val (fuelType, amount, on) = overrides.set.single()
        assertEquals(FuelType.PETROL, fuelType)
        assertEquals(10_440L, amount.paise)
        assertEquals(today, on)
    }

    @Test
    fun aSlippedDecimalPointIsRejectedAndNothingIsStored() = runTest {
        val overrides = FakeFuelPriceOverrides()

        // ₹10,440 a litre — a hundred times the real price.
        val result = setUseCase(overrides).invoke(FuelType.PETROL, pricePaise = 1_044_000)

        assertEquals(
            DomainError.FuelPriceOutOfRange(
                FuelPrice.MIN_PAISE_PER_UNIT,
                FuelPrice.MAX_PAISE_PER_UNIT,
            ),
            result.leftOrNull(),
        )
        assertTrue(overrides.set.isEmpty())
    }

    @Test
    fun anEmptyOrZeroPriceIsRejected() = runTest {
        val overrides = FakeFuelPriceOverrides()
        val useCase = setUseCase(overrides)

        assertTrue(useCase.invoke(FuelType.PETROL, pricePaise = null).isLeft())
        assertTrue(useCase.invoke(FuelType.PETROL, pricePaise = 0).isLeft())
        assertTrue(overrides.set.isEmpty())
    }

    @Test
    fun aFailedWriteIsReportedAsIs() = runTest {
        val result = setUseCase(FakeFuelPriceOverrides(failing = true))
            .invoke(FuelType.PETROL, pricePaise = 10_440)

        assertIs<DomainError.PersistenceFailure>(result.leftOrNull())
    }

    @Test
    fun clearingDropsTheRateForThatFuelOnly() = runTest {
        val overrides = FakeFuelPriceOverrides()

        val result = ClearFuelRateUseCase(overrides).invoke(FuelType.DIESEL)

        assertTrue(result.isRight())
        assertEquals(listOf(FuelType.DIESEL), overrides.cleared)
    }

    @Test
    fun aFailedClearIsReportedAsIs() = runTest {
        val result = ClearFuelRateUseCase(FakeFuelPriceOverrides(failing = true)).invoke(FuelType.PETROL)

        assertIs<DomainError.PersistenceFailure>(result.leftOrNull())
    }

    private fun setUseCase(overrides: FakeFuelPriceOverrides) = SetFuelRateUseCase(
        overrides = overrides,
        clock = FixedClock(Instant.parse("2026-08-01T09:00:00Z")),
        timeZone = TimeZone.UTC,
    )
}
