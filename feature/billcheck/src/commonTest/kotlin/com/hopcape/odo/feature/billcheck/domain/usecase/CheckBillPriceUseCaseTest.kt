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

    /* ------------------------------ Fixtures ------------------------------ */

    private suspend fun check(
        lines: List<BillLine>,
        car: Car = car(),
        band: PriceBand? = band(),
        bandsByCategory: Map<String, PriceBand>? = null,
        failing: Boolean = false,
        spy: MutableList<PriceBandQuery>? = null,
    ) = CheckBillPriceUseCase(
        matcher = BillLineMatcher(),
        bands = FakeBands(band, bandsByCategory, failing, spy),
    ).invoke(
        car = car,
        city = "Srinagar",
        workshop = WorkshopTier.AUTHORISED,
        lines = lines,
        billTotal = rupees(18_400),
        canFlagRepeats = false,
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

    private fun car(model: String = "Swift") = Car.create(
        id = CarId("car-1"),
        ownerId = OwnerId("owner-1"),
        make = "Maruti Suzuki",
        model = model,
        year = 2021,
        fuelType = FuelType.PETROL,
        odometerKm = 12_000,
        variant = "VXi",
        registrationNumber = "JK01AB1234",
    ).getOrNull()!!

    private fun rupees(whole: Int) = Amount.of(whole * 100L).getOrNull() ?: Amount.ZERO
}
