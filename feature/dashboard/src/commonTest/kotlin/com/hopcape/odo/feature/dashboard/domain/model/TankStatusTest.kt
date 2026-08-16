package com.hopcape.odo.feature.dashboard.domain.model

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.cost.fuel.FuelUnit
import com.hopcape.odo.core.domain.cost.model.FuelFill
import com.hopcape.odo.core.domain.cost.model.FuelFillId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.Distance
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the fuel card is allowed to claim.
 *
 * Every case here is about refusing to state something the fills do not support. The card has
 * four things it can say and each one can be absent independently, so the rules that matter
 * are the ones that return null rather than the ones that return a number.
 */
class TankStatusTest {

    @Test
    fun `no fills says nothing at all`() {
        val tank = TankStatus.of(fills = emptyList(), currentOdometer = km(54_000))

        assertEquals(TankStatus.Empty, tank)
        assertTrue(!tank.hasFill)
        assertNull(tank.progress)
    }

    @Test
    fun `distance since the last fill is measured from the current reading`() {
        val tank = TankStatus.of(
            fills = listOf(fill(odometerKm = 53_588, day = 15)),
            currentOdometer = km(54_000),
        )

        assertEquals(412, tank.sinceLastFill?.km)
        assertEquals(LocalDate(2026, 8, 15), tank.lastFilledOn)
    }

    /**
     * A detected fill reaches the owner at the pump, where the dashboard reading is the one
     * number out of reach. The card names the fill and omits the distance rather than
     * printing a zero that reads like the car has not moved.
     */
    @Test
    fun `a fill with no odometer still names the fill but claims no distance`() {
        val tank = TankStatus.of(
            fills = listOf(fill(odometerKm = null, day = 15)),
            currentOdometer = km(54_000),
        )

        assertNull(tank.sinceLastFill)
        assertTrue(tank.hasFill)
    }

    @Test
    fun `an odometer behind the last fill claims no distance rather than a negative one`() {
        val tank = TankStatus.of(
            fills = listOf(fill(odometerKm = 54_500, day = 15)),
            currentOdometer = km(54_000),
        )

        assertNull(tank.sinceLastFill)
    }

    @Test
    fun `one fill is a record, not a habit`() {
        val tank = TankStatus.of(
            fills = listOf(fill(odometerKm = 53_588, day = 15)),
            currentOdometer = km(54_000),
        )

        assertNull(tank.typicalRange, "a single fill has no gap to average")
        assertNull(tank.progress, "and so no bar to draw")
    }

    @Test
    fun `the usual range is the mean gap between consecutive fills`() {
        val tank = TankStatus.of(
            fills = listOf(
                fill(odometerKm = 53_600, day = 15),
                fill(odometerKm = 53_000, day = 5),
                fill(odometerKm = 52_400, day = 1),
            ),
            currentOdometer = km(54_000),
        )

        assertEquals(600, tank.typicalRange?.km)
        // 400 of a usual 600.
        assertEquals(400, tank.sinceLastFill?.km)
        assertEquals(400f / 600f, tank.progress)
    }

    /** Fills without a reading are skipped, not counted as a gap of zero. */
    @Test
    fun `fills with no odometer do not drag the usual range down`() {
        val tank = TankStatus.of(
            fills = listOf(
                fill(odometerKm = 53_600, day = 15),
                fill(odometerKm = null, day = 10),
                fill(odometerKm = 53_000, day = 5),
            ),
            currentOdometer = km(54_000),
        )

        assertEquals(600, tank.typicalRange?.km)
    }

    @Test
    fun `running past the usual range fills the bar rather than overflowing it`() {
        val tank = TankStatus.of(
            fills = listOf(
                fill(odometerKm = 53_000, day = 15),
                fill(odometerKm = 52_800, day = 5),
            ),
            currentOdometer = km(54_000),
        )

        assertEquals(1f, tank.progress)
    }

    private fun km(value: Int) = Distance.of(value).getOrNull()

    private fun fill(odometerKm: Int?, day: Int) = FuelFill.reconstitute(
        id = FuelFillId("fill-$day"),
        carId = CarId("car-1"),
        ownerId = OwnerId("owner-1"),
        filledOn = LocalDate(2026, 8, day),
        odometerKm = odometerKm,
        quantityMilli = 40_100,
        unit = FuelUnit.LITRE,
        amountPaise = 380_900,
        stationName = null,
        transactionRef = null,
    )
}
