package com.hopcape.odo.feature.advisory.domain

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.servicelog.model.LogSource
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.shared.VehicleSegment
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The estimator's rules, not its exact rupees.
 *
 * Every figure it produces comes from hand-entered bands that will be re-tuned, so
 * asserting one would make the tests a copy of the table. What has to hold is the shape:
 * which way each input moves the price, and that the gap the screen is built on behaves.
 */
class CarValueEstimatorTest {

    @Test
    fun anOlderCarIsWorthLess() {
        val newer = estimate(car(year = 2024))
        val older = estimate(car(year = 2018))

        assertTrue(
            older.today.paise < newer.today.paise,
            "2018 (${older.today.paise}) should be under 2024 (${newer.today.paise})",
        )
    }

    @Test
    fun aHigherReadingIsWorthLess() {
        val low = estimate(car(odometerKm = 20_000))
        val high = estimate(car(odometerKm = 90_000))

        assertTrue(high.today.paise < low.today.paise)
    }

    /**
     * Past the clamp the buyer prices the condition, which no table can see. A 2022 car is
     * four years old here, so 150,000 km is already well past it.
     */
    @Test
    fun theReadingsEffectIsCapped() {
        val far = estimate(car(odometerKm = 150_000))
        val absurd = estimate(car(odometerKm = 400_000))

        assertEquals(far.today.paise, absurd.today.paise)
    }

    @Test
    fun aBiggerSegmentIsWorthMore() {
        val hatchback = estimate(car(model = "Baleno"))
        val suv = estimate(car(model = "Creta"))

        assertEquals(VehicleSegment.HATCHBACK, SegmentCatalog.segmentOf("Baleno"))
        assertEquals(VehicleSegment.SUV, SegmentCatalog.segmentOf("Creta"))
        assertTrue(suv.today.paise > hatchback.today.paise)
    }

    /** A model nobody entered is priced as a hatchback rather than not priced at all. */
    @Test
    fun anUnlistedModelFallsBackToTheDefaultSegment() {
        assertEquals(SegmentCatalog.DEFAULT, SegmentCatalog.segmentOf("Nonesuch GTX"))

        val unlisted = estimate(car(model = "Nonesuch GTX"))
        val hatchback = estimate(car(model = "Baleno"))

        assertEquals(hatchback.today.paise, unlisted.today.paise)
    }

    @Test
    fun aSmallerCityIsWorthLess() {
        val metro = estimate(car(), cityTier = 1)
        val small = estimate(car(), cityTier = 3)

        assertTrue(small.today.paise < metro.today.paise)
    }

    /** No city set is not a failure; it is priced as the middle tier. */
    @Test
    fun anUnknownCityIsPricedInTheMiddle() {
        assertEquals(estimate(car(), cityTier = 2).today, estimate(car(), cityTier = null).today)
    }

    /* ------------------------------ The gap ------------------------------ */

    @Test
    fun withNoRecordTheGapIsTheWholePremium() {
        val value = estimate(car(), logs = emptyList())

        assertTrue(value.hasNoRecord)
        assertEquals(0.0, value.recordCompleteness)
        assertTrue(value.recordWorth.paise > 0)
        assertTrue(value.today.paise < value.withFullRecord.low.paise)
    }

    /**
     * The point of the screen: proving the history closes the gap. An owner with a complete
     * record is already worth the low end, so there is little left to earn.
     */
    @Test
    fun aCompleteRecordClosesMostOfTheGap() {
        val none = estimate(car(year = 2022), logs = emptyList())
        val full = estimate(car(year = 2022), logs = List(4) { verifiedLog(it) })

        assertEquals(1.0, full.recordCompleteness)
        assertTrue(full.today.paise > none.today.paise)
        assertTrue(full.recordWorth.paise < none.recordWorth.paise)
    }

    /**
     * Only a bill counts. A typed entry is worth having in the app and worth nothing to a
     * buyer, and paying the premium for one would remove the reason to scan anything.
     */
    @Test
    fun aSelfReportedServiceEarnsNothing() {
        val typed = estimate(car(year = 2022), logs = List(4) { selfReportedLog(it) })
        val none = estimate(car(year = 2022), logs = emptyList())

        assertEquals(0.0, typed.recordCompleteness)
        assertTrue(typed.hasNoRecord, "typing four services is still not a record")
        assertEquals(none.today.paise, typed.today.paise)
    }

    @Test
    fun aPartialRecordEarnsPartOfThePremium() {
        val none = estimate(car(year = 2022), logs = emptyList())
        val half = estimate(car(year = 2022), logs = List(2) { verifiedLog(it) })
        val full = estimate(car(year = 2022), logs = List(4) { verifiedLog(it) })

        assertTrue(half.today.paise > none.today.paise)
        assertTrue(half.today.paise < full.today.paise)
    }

    /**
     * A brand-new car earns the premium like any other; it is not granted it.
     *
     * Granting it put today's figure exactly on the low bound of the "with a full record"
     * band, and told an owner holding no bills "today, with your record" on the screen whose
     * whole job is to ask them to start one.
     */
    @Test
    fun aBrandNewCarWithNoBillsStillHasNoRecord() {
        val value = estimate(car(year = CURRENT_YEAR), logs = emptyList())

        assertEquals(0.0, value.recordCompleteness)
        assertEquals(0, value.provenServices)
        assertTrue(value.hasNoRecord)
        assertTrue(value.today.paise < value.withFullRecord.low.paise)
    }

    /** One bill is a complete record for a car that has only had time for one service. */
    @Test
    fun aBrandNewCarIsCompleteWithOneBill() {
        assertEquals(
            1.0,
            estimate(car(year = CURRENT_YEAR), logs = listOf(verifiedLog(0))).recordCompleteness,
        )
    }

    @Test
    fun oneBillIsEnoughToCountAsARecord() {
        assertTrue(!estimate(car(), logs = listOf(verifiedLog(0))).hasNoRecord)
    }

    /** A running car is never worthless, however old it is. */
    @Test
    fun aVeryOldCarStillHasAFloorPrice() {
        assertTrue(estimate(car(year = 1995)).today.paise > 0)
    }

    /* ------------------------------ Fixtures ------------------------------ */

    private fun estimate(
        car: Car,
        logs: List<ServiceLogEntry> = emptyList(),
        cityTier: Int? = 2,
    ) = CarValueEstimator.estimate(
        car = car,
        logs = logs,
        cityTier = cityTier,
        currentYear = CURRENT_YEAR,
    )

    private fun car(
        model: String = "Baleno",
        year: Int = 2022,
        odometerKm: Int = 38_400,
        fuel: FuelType = FuelType.PETROL,
    ): Car = Car.create(
        id = CarId("car-1"),
        ownerId = OWNER,
        make = "Maruti Suzuki",
        model = model,
        year = year,
        fuelType = fuel,
        odometerKm = odometerKm,
        isPrimary = true,
    ).getOrElse { error("invalid fixture: $it") }

    private fun verifiedLog(index: Int) = log(index, billPhotoRef = "bill-$index")

    private fun selfReportedLog(index: Int) = log(index, billPhotoRef = null)

    private fun log(index: Int, billPhotoRef: String?) = ServiceLogEntry.reconstitute(
        id = ServiceLogId("log-$index"),
        carId = CarId("car-1"),
        ownerId = OWNER,
        serviceDate = LocalDate(2022 + index, 6, 1),
        odometerKm = 10_000 * (index + 1),
        totalAmountPaise = 500_000,
        workshopName = null,
        notes = null,
        source = LogSource.MANUAL,
        billId = null,
        billPhotoRef = billPhotoRef,
    )

    private companion object {
        const val CURRENT_YEAR = 2026
        val OWNER = OwnerId("owner-1")
    }
}
