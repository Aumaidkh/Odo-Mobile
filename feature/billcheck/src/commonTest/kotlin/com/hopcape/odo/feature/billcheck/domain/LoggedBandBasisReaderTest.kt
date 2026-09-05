package com.hopcape.odo.feature.billcheck.domain

import arrow.core.Either
import arrow.core.right
import com.hopcape.odo.core.domain.advisory.matching.BillLineMatcher
import com.hopcape.odo.core.domain.benchmark.BandWorking
import com.hopcape.odo.core.domain.benchmark.BenchmarkBasis
import com.hopcape.odo.core.domain.benchmark.BenchmarkScope
import com.hopcape.odo.core.domain.benchmark.PriceBand
import com.hopcape.odo.core.domain.benchmark.PriceBandQuery
import com.hopcape.odo.core.domain.benchmark.PriceBandRepository
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.city.City
import com.hopcape.odo.core.domain.city.CityCatalog
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.owner.model.QuestionAnswer
import com.hopcape.odo.core.domain.owner.model.QuestionKey
import com.hopcape.odo.core.domain.owner.model.QuestionKeys
import com.hopcape.odo.core.domain.owner.repository.QuestionnaireRepository
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogLineItemDraft
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.shared.WorkshopTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

/**
 * The sheet that says a band is defensible.
 *
 * Everything it shows has to be the same thing the check asked for — a different city or a
 * different workshop tier here would be worse than showing nothing, because it is presented as
 * the reason to trust the figure on the screen behind it.
 */
class LoggedBandBasisReaderTest {

    @Test
    fun `the working behind a modelled band is shown in full`() = runTest {
        val basis = assertNotNull(reader().basisFor(BILL, "AC service").getOrNull())

        assertEquals("AC service", basis.lineName)
        assertEquals(rupees(1_428), basis.low)
        assertEquals(rupees(1_932), basis.high)
        assertEquals("Srinagar", basis.city)
        assertEquals(2, basis.cityTier)
        assertEquals(WorkshopTier.AUTHORISED, basis.workshop)
        assertEquals("petrol hatchback", basis.segment)
        assertEquals(rupees(520), basis.labourRatePerHour)
        assertEquals(1.5, basis.labourHours)
    }

    /** The band the check used, asked for the same way — same city, segment and tier. */
    @Test
    fun `the band is asked for exactly as the check asked for it`() = runTest {
        val asked = mutableListOf<PriceBandQuery>()

        reader(spy = asked).basisFor(BILL, "AC service")

        val query = asked.single()
        assertEquals("ac_service", query.categorySlug)
        assertEquals("Srinagar", query.city)
        assertEquals(WorkshopTier.AUTHORISED, query.workshopTier)
        assertEquals(FuelType.PETROL, query.fuel)
    }

    /* ------------------------------ The ladder ------------------------------ */

    /**
     * A computed band. The narrowest rung had no bills — which is why the server widened —
     * and the widest was never reached.
     */
    @Test
    fun `a modelled band answered at the middle rung`() = runTest {
        val basis = assertNotNull(reader().basisFor(BILL, "AC service").getOrNull())

        assertEquals(
            listOf(RungState.NO_DATA, RungState.USED, RungState.NOT_NEEDED),
            basis.rungs.map { it.state },
        )
        assertEquals(BandScope.CITY_TIER_SEGMENT, basis.rungs.single { it.state == RungState.USED }.scope)
    }

    /** Real bills at the narrowest filter: as close as the pool gets to this car and centre. */
    @Test
    fun `observed bills at the narrowest filter answer at the top rung`() = runTest {
        val basis = assertNotNull(
            reader(band = band(BenchmarkScope.CITY_TIER_SEGMENT, BenchmarkBasis.OBSERVED))
                .basisFor(BILL, "AC service").getOrNull(),
        )

        assertEquals(BandScope.THIS_CAR_THIS_CENTRE, basis.rungs.first().scope)
        assertEquals(RungState.USED, basis.rungs.first().state)
        assertEquals(listOf(RungState.NOT_NEEDED, RungState.NOT_NEEDED), basis.rungs.drop(1).map { it.state })
    }

    /** Nothing closer had anything, so both rungs above it are empty rather than unused. */
    @Test
    fun `a national band leaves both narrower rungs empty`() = runTest {
        val basis = assertNotNull(
            reader(band = band(BenchmarkScope.NATIONAL_TIER, BenchmarkBasis.OBSERVED))
                .basisFor(BILL, "AC service").getOrNull(),
        )

        assertEquals(
            listOf(RungState.NO_DATA, RungState.NO_DATA, RungState.USED),
            basis.rungs.map { it.state },
        )
    }

    /** Real bills have no sum behind them, and the sheet shows no invented one. */
    @Test
    fun `an observed band shows no labour working`() = runTest {
        val basis = assertNotNull(
            reader(band = band(BenchmarkScope.CITY_TIER, BenchmarkBasis.OBSERVED, working = null))
                .basisFor(BILL, "AC service").getOrNull(),
        )

        assertEquals(Amount.ZERO, basis.labourRatePerHour)
        assertEquals(0.0, basis.labourHours)
    }

    /* ------------------------------ Nothing to show ------------------------------ */

    /** A line the rules cannot name has no band, and a sheet about it would be invented. */
    @Test
    fun `a line the rules cannot name has no basis`() = runTest {
        val result = reader().basisFor(BILL, "Throttle body cleaning")

        assertNull(result.getOrNull())
        assertIs<DomainError.LookupUnavailable>(result.leftOrNull())
    }

    @Test
    fun `a job the tables cannot price has no basis`() = runTest {
        assertNull(reader(band = null).basisFor(BILL, "AC service").getOrNull())
    }

    @Test
    fun `a bill that is not there has no basis`() = runTest {
        val result = reader(missing = true).basisFor(BILL, "AC service")

        assertIs<DomainError.ServiceLogNotFound>(result.leftOrNull())
    }

    /**
     * An unlisted city names no tier at all. The server resolves the tier when it builds the
     * band, so a guessed one here would be a different number from the one it was built at —
     * on the sheet that exists to show what it *was* built at.
     */
    @Test
    fun `an unlisted city names no tier rather than a plausible one`() = runTest {
        val basis = assertNotNull(
            reader(catalog = emptyList()).basisFor(BILL, "AC service").getOrNull(),
        )

        assertNull(basis.cityTier)
        assertEquals("Srinagar", basis.city, "the city itself is still known")
    }

    /* ------------------------------ Fixtures ------------------------------ */

    private fun reader(
        band: PriceBand? = band(BenchmarkScope.MODELLED, BenchmarkBasis.MODELLED),
        spy: MutableList<PriceBandQuery>? = null,
        missing: Boolean = false,
        catalog: List<City> = listOf(City(id = "c1", name = "Srinagar", state = "J&K", tier = 2)),
    ) = LoggedBandBasisReader(
        entries = FakeEntries(if (missing) null else entry()),
        cars = FakeCars(car()),
        cities = { "Srinagar" },
        cityCatalog = object : CityCatalog {
            override suspend fun cities() = catalog
        },
        questionnaire = FakeQuestionnaire,
        matcher = BillLineMatcher(),
        bands = FakeBands(band, spy),
    )

    private fun band(
        scope: BenchmarkScope,
        basis: BenchmarkBasis,
        working: BandWorking? = BandWorking(
            partsPaise = 90_000,
            labourHours = 1.5,
            labourRatePerHour = rupees(520),
        ),
    ) = PriceBand(
        low = rupees(1_428),
        typical = rupees(1_680),
        high = rupees(1_932),
        sampleSize = if (basis == BenchmarkBasis.OBSERVED) 14 else 0,
        scope = scope,
        basis = basis,
        working = working,
    )

    private class FakeBands(
        private val band: PriceBand?,
        private val spy: MutableList<PriceBandQuery>?,
    ) : PriceBandRepository {
        override suspend fun bandFor(query: PriceBandQuery): Either<DomainError, PriceBand?> {
            spy?.add(query)
            return band.right()
        }
    }

    private class FakeEntries(private val stored: ServiceLogEntry?) : ServiceLogRepository {
        override fun observe(id: ServiceLogId): Flow<ServiceLogEntry?> = flowOf(stored)
        override fun observe(carId: CarId): Flow<List<ServiceLogEntry>> = flowOf(listOfNotNull(stored))
        override suspend fun add(entry: ServiceLogEntry) = error("not called")
        override suspend fun update(entry: ServiceLogEntry) = error("not called")
        override suspend fun softDelete(id: ServiceLogId) = error("not called")
        override suspend fun odometerReadings(carId: CarId) = error("not called")
        override fun observeOdometerReadings(carId: CarId) = error("not called")
    }

    private class FakeCars(private val car: Car) : CarRepository {
        override fun observe(id: CarId): Flow<Car?> = flowOf(car)
        override suspend fun add(car: Car) = error("not called")
        override suspend fun update(car: Car) = error("not called")
        override fun observePrimaryCar(): Flow<Car?> = error("not called")
        override suspend fun softDelete(id: CarId) = error("not called")
    }

    private object FakeQuestionnaire : QuestionnaireRepository {
        override suspend fun save(key: QuestionKey, values: Set<String>) = Unit.right()
        override fun observe() = flowOf(emptyList<QuestionAnswer>())
        override suspend fun answersFor(key: QuestionKey) = listOf(
            QuestionAnswer(
                key = QuestionKeys.Workshop,
                value = WorkshopTier.AUTHORISED.name,
                answeredAt = Instant.fromEpochSeconds(0),
            ),
        ).right()
    }

    private companion object {
        const val BILL = "entry-1"

        fun rupees(whole: Int) = Amount.of(whole * 100L).getOrNull() ?: Amount.ZERO

        fun car() = Car.create(
            id = CarId("car-1"),
            ownerId = OwnerId("owner-1"),
            make = "Maruti Suzuki",
            model = "Swift",
            year = 2021,
            fuelType = FuelType.PETROL,
            odometerKm = 12_000,
            variant = "VXi",
        ).getOrNull()!!

        fun entry() = ServiceLogEntry.create(
            id = ServiceLogId(BILL),
            carId = CarId("car-1"),
            ownerId = OwnerId("owner-1"),
            serviceDate = LocalDate(2026, 8, 12),
            odometerKm = 12_000,
            totalAmountPaise = 240_000,
            today = LocalDate(2026, 8, 12),
            lineItems = listOf(
                ServiceLogLineItemDraft(
                    label = "AC service",
                    category = ServiceCategory.AC,
                    amountPaise = 240_000,
                ),
            ),
        ).getOrNull()!!
    }
}
