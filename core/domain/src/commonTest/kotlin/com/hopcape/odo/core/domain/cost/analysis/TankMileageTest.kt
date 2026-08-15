package com.hopcape.odo.core.domain.cost.analysis

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.cost.fuel.FuelUnit
import com.hopcape.odo.core.domain.cost.model.FuelFill
import com.hopcape.odo.core.domain.cost.model.FuelFillId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TankMileageTest {

    @Test
    fun distanceIsDividedByTheEarlierTanksFuel() {
        val mileage = TankMileage.between(
            previous = fill("a", date = LocalDate(2026, 3, 1), km = 30_000, quantityMilli = 20_000),
            latest = fill("b", date = LocalDate(2026, 3, 15), km = 30_320, quantityMilli = 18_000),
        )

        // 320 km on the 20 litres bought first — the 18 in the tank now have not been burned.
        assertClose(16.0, mileage)
    }

    @Test
    fun tooShortAGapIsRefusedRatherThanApproximated() {
        val mileage = TankMileage.between(
            previous = fill("a", date = LocalDate(2026, 3, 1), km = 30_000, quantityMilli = 20_000),
            latest = fill("b", date = LocalDate(2026, 3, 2), km = 30_030, quantityMilli = 5_000),
        )

        assertNull(mileage)
    }

    @Test
    fun anOdometerThatWentBackwardsProducesNoFigure() {
        val mileage = TankMileage.between(
            previous = fill("a", date = LocalDate(2026, 3, 1), km = 30_000, quantityMilli = 20_000),
            latest = fill("b", date = LocalDate(2026, 3, 15), km = 29_000, quantityMilli = 18_000),
        )

        assertNull(mileage)
    }

    @Test
    fun fillsInTheWrongDateOrderProduceNoFigure() {
        val mileage = TankMileage.between(
            previous = fill("a", date = LocalDate(2026, 3, 20), km = 30_000, quantityMilli = 20_000),
            latest = fill("b", date = LocalDate(2026, 3, 1), km = 30_400, quantityMilli = 18_000),
        )

        assertNull(mileage)
    }

    @Test
    fun theFirstFillEverHasNothingToMeasureFrom() {
        val fills = listOf(fill("a", LocalDate(2026, 3, 1), km = 30_000, quantityMilli = 20_000))

        assertNull(TankMileage.forLatest(fills))
        assertNull(TankMileage.average(fills))
    }

    @Test
    fun forLatestMeasuresTheTwoNewestFills() {
        val mileage = TankMileage.forLatest(
            listOf(
                fill("c", LocalDate(2026, 3, 30), km = 30_800, quantityMilli = 19_000),
                fill("b", LocalDate(2026, 3, 15), km = 30_320, quantityMilli = 30_000),
                fill("a", LocalDate(2026, 3, 1), km = 30_000, quantityMilli = 20_000),
            ),
        )

        // 480 km on the 30 litres of the middle fill.
        assertClose(16.0, mileage)
    }

    @Test
    fun theAverageSkipsPairsThatCannotSupportAFigure() {
        val average = TankMileage.average(
            listOf(
                // This pair is 10 km apart — under the minimum, so it drops out entirely.
                fill("d", LocalDate(2026, 4, 2), km = 30_810, quantityMilli = 5_000),
                fill("c", LocalDate(2026, 3, 30), km = 30_800, quantityMilli = 19_000),
                fill("b", LocalDate(2026, 3, 15), km = 30_320, quantityMilli = 30_000),
                fill("a", LocalDate(2026, 3, 1), km = 30_000, quantityMilli = 20_000),
            ),
        )

        // 480/30 = 16 and 320/20 = 16 both survive; the 10 km pair contributes nothing.
        assertNotNull(average)
        assertClose(16.0, average)
    }

    private fun assertClose(expected: Double, actual: Double?) {
        assertNotNull(actual)
        assertTrue(abs(expected - actual) < 0.01, "expected ~$expected but was $actual")
    }

    private fun fill(
        id: String,
        date: LocalDate,
        km: Int,
        quantityMilli: Long,
    ): FuelFill = FuelFill.reconstitute(
        id = FuelFillId(id),
        carId = CarId("car"),
        ownerId = OwnerId("owner"),
        filledOn = date,
        odometerKm = km,
        quantityMilli = quantityMilli,
        unit = FuelUnit.LITRE,
        amountPaise = 200_000,
        stationName = null,
        transactionRef = null,
    )
}
