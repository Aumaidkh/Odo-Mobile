package com.hopcape.odo.feature.billcheck.domain.usecase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.benchmark.BenchmarkBasis
import com.hopcape.odo.core.domain.benchmark.BenchmarkScope
import com.hopcape.odo.core.domain.benchmark.PriceBand
import com.hopcape.odo.core.domain.benchmark.PriceBandQuery
import com.hopcape.odo.core.domain.benchmark.PriceBandRepository
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.schedule.ServiceInterval
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogLineItemDraft
import kotlinx.datetime.LocalDate
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.shared.VehicleSegment
import com.hopcape.odo.core.domain.shared.WorkshopTier
import com.hopcape.odo.feature.billcheck.domain.Evidence
import com.hopcape.odo.feature.billcheck.domain.Reason
import com.hopcape.odo.feature.billcheck.domain.matching.BillLineMatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Which bucket each line of a bill lands in.
 *
 * The rule that matters most is negative: **nothing is flagged without a band**, and a line
 * nobody could price is never ticked as fine. A tick says "we checked this", and saying that
 * about an unpriced line is the app claiming work it did not do — at a counter, to someone
 * about to sign.
 */
class CheckBillPriceUseCaseTest {

    @Test
    fun `a line above the band is flagged with the band it beat`() = runTest {
        val check = check(lines = listOf(line("AC service", 2_400)))

        val flagged = check.flagged.single()
        assertEquals("AC service", flagged.name)
        val reason = assertIs<Reason.AboveBand>(flagged.reason)
        assertEquals(rupees(1_428), reason.low)
        assertEquals(rupees(1_932), reason.high)
    }

    @Test
    fun `a line inside the band is fine`() = runTest {
        val check = check(lines = listOf(line("AC service", 1_600)))

        assertEquals("AC service", check.fine.single().name)
        assertTrue(check.flagged.isEmpty())
    }

    /** The band's top edge is still inside it. Flagging it would be an argument over ₹1. */
    @Test
    fun `a line exactly at the top of the band is fine`() = runTest {
        val check = check(lines = listOf(line("AC service", 1_932)))

        assertEquals(1, check.fine.size)
        assertTrue(check.flagged.isEmpty())
    }

    /**
     * The whole reason the third bucket exists. Neither of these has a category the server
     * carries, so neither was checked — and `fine` would say otherwise.
     */
    @Test
    fun `a line nobody could name is unchecked and never fine`() = runTest {
        val check = check(lines = listOf(line("Throttle body cleaning", 1_800)))

        assertEquals("Throttle body cleaning", check.unchecked.single().name)
        assertTrue(check.fine.isEmpty(), "a tick would claim a check that never ran")
        assertTrue(check.flagged.isEmpty())
    }

    /** A job the tables have no price for. Named, and still not checked. */
    @Test
    fun `a job with no band is unchecked`() = runTest {
        val check = check(lines = listOf(line("Clutch plate", 14_000)), band = null)

        assertEquals(1, check.unchecked.size)
        assertTrue(check.fine.isEmpty())
    }

    /** Labour is inside the modelled band already. There is nothing to check it against. */
    @Test
    fun `a labour line is unchecked rather than fine`() = runTest {
        val check = check(lines = listOf(line("Labour + consumables", 4_350)))

        assertEquals(1, check.unchecked.size)
        assertTrue(check.fine.isEmpty())
    }

    /** A server that could not be asked must not turn into a finding either way. */
    @Test
    fun `a lookup failure leaves the line unchecked`() = runTest {
        val check = check(lines = listOf(line("AC service", 9_999)), failing = true)

        assertEquals(1, check.unchecked.size)
        assertTrue(check.flagged.isEmpty(), "an unreachable server is not an overcharge")
    }

    /**
     * The owner reads from the top, so the top is the line they can argue hardest — real
     * bills before a computed estimate, and the bigger figure first inside a rung.
     */
    @Test
    fun `findings are ordered by how much they rest on`() = runTest {
        val observed = band(basis = BenchmarkBasis.OBSERVED, sampleSize = 14)
        val check = check(
            lines = listOf(line("AC service", 2_400), line("Coolant", 2_500)),
            bandsByCategory = mapOf("ac_service" to observed, "coolant" to band()),
        )

        assertEquals(listOf("AC service", "Coolant"), check.flagged.map { it.name })
        assertEquals(Evidence.RealBills(14), check.flagged.first().evidence)
        assertEquals(Evidence.CityRates, check.flagged.last().evidence)
    }

    /**
     * An unlisted model sends no segment at all rather than the catalogue's hatchback guess.
     * The server widens to a city answer; a guess would price an SUV's bill as a hatchback's.
     */
    @Test
    fun `an unlisted model asks without a segment`() = runTest {
        val asked = mutableListOf<PriceBandQuery>()
        check(car = car(model = "Nonesuch GTX"), lines = listOf(line("AC service", 2_400)), spy = asked)

        assertEquals(null, asked.single().segment)
    }

    @Test
    fun `a listed model asks with its segment`() = runTest {
        val asked = mutableListOf<PriceBandQuery>()
        check(car = car(model = "Creta"), lines = listOf(line("AC service", 2_400)), spy = asked)

        assertEquals(VehicleSegment.SUV, asked.single().segment)
    }

    /* ------------------------------ The repeat rule ------------------------------ */

    /**
     * The scene the feature was written for. The owner's own record shows the job in April;
     * the bill in August charges for it again.
     */
    @Test
    fun `a job the record already shows is flagged from the owner's own data`() = runTest {
        val check = check(
            lines = listOf(line("AC service", 1_600)),
            history = listOf(entry(LocalDate(2026, 4, 12), "AC service")),
        )

        val flagged = check.flagged.single()
        assertEquals(Evidence.OwnRecord, flagged.evidence)
        val reason = assertIs<Reason.DoneRecently>(flagged.reason)
        assertEquals(4, reason.monthsAgo)
        assertTrue(check.fine.isEmpty(), "priced fine, and still worth asking about")
    }

    /**
     * The rule, and the reason it is halfway rather than "before it was due". Engine oil comes
     * round at twelve months; at eleven it is nearly due, and asking "why again?" would put a
     * question in front of an owner that the advisor answers in one word.
     */
    @Test
    fun `a job nearly due again is not a repeat`() = runTest {
        val check = check(
            lines = listOf(line("Engine oil 5W-30", 1_600)),
            history = listOf(entry(LocalDate(2025, 9, 12), "Engine oil")),
            intervals = mapOf("engine_oil" to ServiceInterval("engine_oil", months = 12)),
        )

        assertTrue(check.flagged.isEmpty(), "eleven months into a twelve-month interval")
        assertEquals(1, check.fine.size)
    }

    @Test
    fun `the same job well inside its interval is a repeat`() = runTest {
        val check = check(
            lines = listOf(line("Engine oil 5W-30", 1_600)),
            history = listOf(entry(LocalDate(2026, 5, 12), "Engine oil")),
            intervals = mapOf("engine_oil" to ServiceInterval("engine_oil", months = 12)),
        )

        assertIs<Reason.DoneRecently>(check.flagged.single().reason)
    }

    /**
     * A job the schedule says nothing about — most of the catalogue. Due is taken as a year,
     * so the window is six months, and that default is stated rather than inferred.
     */
    @Test
    fun `a job with no interval falls back to a six month window`() = runTest {
        val recent = check(
            lines = listOf(line("AC service", 1_600)),
            history = listOf(entry(LocalDate(2026, 4, 12), "AC service")),
        )
        val older = check(
            lines = listOf(line("AC service", 1_600)),
            history = listOf(entry(LocalDate(2025, 10, 12), "AC service")),
        )

        assertEquals(1, recent.flagged.size, "four months")
        assertTrue(older.flagged.isEmpty(), "ten months")
    }

    /**
     * The record beats the table. Both claims are true of this line, and the owner's own data
     * is the harder question — one finding per line, and the better-evidenced one.
     */
    @Test
    fun `a repeat wins over the rate claim on the same line`() = runTest {
        val check = check(
            lines = listOf(line("AC service", 9_999)),
            history = listOf(entry(LocalDate(2026, 4, 12), "AC service")),
        )

        assertIs<Reason.DoneRecently>(check.flagged.single().reason)
    }

    /** An entry from the day of the bill, or after it, is not a repeat of that bill. */
    @Test
    fun `the bill's own entry is not a repeat of itself`() = runTest {
        val check = check(
            lines = listOf(line("AC service", 1_600)),
            history = listOf(entry(BILL_DATE, "AC service")),
        )

        assertTrue(check.flagged.isEmpty())
    }

    /** Until there is a record, the screen says what adding one would buy. */
    @Test
    fun `no history means repeats cannot be flagged at all`() = runTest {
        assertEquals(false, check(lines = listOf(line("AC service", 1_600))).canFlagRepeats)
        assertEquals(
            true,
            check(
                lines = listOf(line("AC service", 1_600)),
                history = listOf(entry(LocalDate(2024, 1, 1), "Air filter")),
            ).canFlagRepeats,
        )
    }

    /* ------------------------------ The schedule rule ------------------------------ */

    /**
     * The day-1 scene. The car has never had the job, the maker puts it at 40,000 km, and the
     * odometer says 12,000 — two facts and a question, never "you do not need this".
     */
    @Test
    fun `a job the schedule puts further down the road is flagged`() = runTest {
        val check = check(
            lines = listOf(line("Coolant top up", 1_600)),
            intervals = mapOf("coolant" to ServiceInterval("coolant", km = 40_000)),
        )

        val flagged = check.flagged.single()
        val reason = assertIs<Reason.ScheduledLater>(flagged.reason)
        assertEquals(40_000, reason.dueAtKm)
        assertEquals(12_000, reason.currentKm)
    }

    /** It says nothing about the price, so it shows no price evidence. */
    @Test
    fun `a schedule claim carries no evidence dots`() = runTest {
        val check = check(
            lines = listOf(line("Coolant top up", 1_600)),
            intervals = mapOf("coolant" to ServiceInterval("coolant", km = 40_000)),
        )

        assertEquals(null, check.flagged.single().evidence)
        assertFalse(check.flagged.single().amountIsTheClaim, "the price is not the claim")
    }

    /** Nearly due is not early. The same halfway rule the repeat finder uses. */
    @Test
    fun `a job nearly due by distance is not flagged`() = runTest {
        val check = check(
            lines = listOf(line("Air filter", 900)),
            intervals = mapOf("air_filter" to ServiceInterval("air_filter", km = 15_000)),
        )

        assertTrue(check.flagged.isEmpty(), "12,000 km into a 15,000 km interval")
    }

    /** Due is measured from the last time it was done, not from zero. */
    @Test
    fun `the next due is counted from the last time it was done`() = runTest {
        val check = check(
            car = car(odometerKm = 50_000),
            lines = listOf(line("Coolant top up", 1_600)),
            history = listOf(entry(LocalDate(2024, 1, 1), "Coolant", odometerKm = 45_000)),
            intervals = mapOf("coolant" to ServiceInterval("coolant", km = 40_000)),
        )

        val reason = assertIs<Reason.ScheduledLater>(check.flagged.single().reason)
        assertEquals(85_000, reason.dueAtKm, "45,000 plus the interval")
    }

    /** A job the schedule says nothing about produces no schedule claim. */
    @Test
    fun `no interval means no schedule claim`() = runTest {
        val check = check(lines = listOf(line("Coolant top up", 1_600)))

        assertTrue(check.flagged.isEmpty())
        assertEquals(1, check.fine.size)
    }

    /* ------------------------------ Fixtures ------------------------------ */

    private suspend fun check(
        lines: List<BillLine>,
        car: Car = car(),
        band: PriceBand? = band(),
        bandsByCategory: Map<String, PriceBand>? = null,
        failing: Boolean = false,
        spy: MutableList<PriceBandQuery>? = null,
        history: List<ServiceLogEntry> = emptyList(),
        intervals: Map<String, ServiceInterval> = emptyMap(),
    ) = CheckBillPriceUseCase(
        matcher = BillLineMatcher(),
        bands = FakeBands(band, bandsByCategory, failing, spy),
        intervals = { intervals.right() },
    ).invoke(
        car = car,
        city = "Srinagar",
        workshop = WorkshopTier.AUTHORISED,
        lines = lines,
        billTotal = rupees(18_400),
        billDate = BILL_DATE,
        history = history,
    )

    private fun band(
        basis: BenchmarkBasis = BenchmarkBasis.MODELLED,
        sampleSize: Int = 0,
    ) = PriceBand(
        low = rupees(1_428),
        typical = rupees(1_680),
        high = rupees(1_932),
        sampleSize = sampleSize,
        scope = BenchmarkScope.MODELLED,
        basis = basis,
    )

    private class FakeBands(
        private val band: PriceBand?,
        private val byCategory: Map<String, PriceBand>?,
        private val failing: Boolean,
        private val spy: MutableList<PriceBandQuery>?,
    ) : PriceBandRepository {
        override suspend fun bandFor(query: PriceBandQuery): Either<DomainError, PriceBand?> {
            spy?.add(query)
            if (failing) return DomainError.LookupUnavailable.left()
            return (byCategory?.get(query.categorySlug) ?: band).right()
        }
    }

    private fun line(label: String, rupees: Int) = BillLine(label, rupees(rupees))

    private fun car(model: String = "Swift", odometerKm: Int = 12_000) = Car.create(
        id = CarId("car-1"),
        ownerId = OwnerId("owner-1"),
        make = "Maruti Suzuki",
        model = model,
        year = 2021,
        fuelType = FuelType.PETROL,
        odometerKm = odometerKm,
        variant = "VXi",
        registrationNumber = "JK01AB1234",
    ).getOrNull()!!

    private fun rupees(whole: Int) = Amount.of(whole * 100L).getOrNull() ?: Amount.ZERO

    /** An entry for [car], showing [labels], on [on]. */
    private fun entry(on: LocalDate, vararg labels: String, odometerKm: Int = 10_000) =
        ServiceLogEntry.create(
        id = ServiceLogId("entry-$on"),
        carId = CarId("car-1"),
        ownerId = OwnerId("owner-1"),
        serviceDate = on,
        odometerKm = odometerKm,
        totalAmountPaise = 100_000,
        today = BILL_DATE,
        lineItems = labels.map {
            ServiceLogLineItemDraft(label = it, category = ServiceCategory.OTHER, amountPaise = 50_000)
        },
    ).getOrNull()!!

    private companion object {
        val BILL_DATE = LocalDate(2026, 8, 12)
    }
}
