package com.hopcape.odo.feature.billcheck.domain

import arrow.core.Either
import arrow.core.right
import com.hopcape.odo.core.domain.benchmark.BenchmarkBasis
import com.hopcape.odo.core.domain.benchmark.FairnessContributor
import com.hopcape.odo.core.domain.benchmark.PriceObservation
import com.hopcape.odo.core.domain.benchmark.BenchmarkScope
import com.hopcape.odo.core.domain.benchmark.PriceBand
import com.hopcape.odo.core.domain.benchmark.PriceBandQuery
import com.hopcape.odo.core.domain.benchmark.PriceBandRepository
import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.owner.model.QuestionAnswer
import com.hopcape.odo.core.domain.owner.model.QuestionKey
import com.hopcape.odo.core.domain.owner.model.QuestionKeys
import com.hopcape.odo.core.domain.owner.repository.QuestionnaireRepository
import com.hopcape.odo.core.domain.scan.entitlement.BillCheckLedger
import com.hopcape.odo.core.domain.scan.entitlement.ScanCharger
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogLineItemDraft
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.shared.WorkshopTier
import com.hopcape.odo.feature.billcheck.domain.matching.BillLineMatcher
import com.hopcape.odo.feature.billcheck.domain.usecase.CheckBillPriceUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * When a check costs the owner something.
 *
 * The offers sheet promises "if a check fails the credit comes back". This delivers it by
 * never taking one — a read that could not name a single line has cost nothing to run and
 * takes nothing, which is a promise that cannot go wrong halfway.
 */
class LoggedBillCheckReaderTest {

    @Test
    fun `a check that found something is charged`() = runTest {
        val charger = CountingCharger()

        val result = reader(charger = charger).read(BILL)

        assertTrue(result.getOrNull()!!.flagged.isNotEmpty())
        assertEquals(1, charger.charges)
    }

    /**
     * Every line unchecked. Nothing was compared against anything, and the owner is not paying
     * for a screen that told them nothing.
     */
    @Test
    fun `a check that could name nothing is not charged`() = runTest {
        val charger = CountingCharger()

        val result = reader(
            charger = charger,
            lines = listOf("Throttle body cleaning" to 1_800, "Injector cleaning" to 3_100),
        ).read(BILL)

        val check = result.getOrNull()!!
        assertEquals(2, check.unchecked.size)
        assertEquals(0, charger.charges, "nothing was checked, so nothing is owed")
    }

    /** A clean bill is still a check. It said "these six are priced fine", which is an answer. */
    @Test
    fun `a bill with nothing wrong is still charged`() = runTest {
        val charger = CountingCharger()

        reader(charger = charger, lines = listOf("AC service" to 1_500)).read(BILL)

        assertEquals(1, charger.charges)
    }

    @Test
    fun `a bill that is not there is not charged`() = runTest {
        val charger = CountingCharger()

        val result = reader(charger = charger, missing = true).read(BILL)

        assertIs<DomainError.ServiceLogNotFound>(result.leftOrNull())
        assertEquals(0, charger.charges)
    }

    /**
     * The owner's answer is the labour rate every price is quoted at, so it has to reach the
     * query rather than being defaulted away.
     */
    @Test
    fun `the owner's workshop answer is what the band is asked for`() = runTest {
        val asked = mutableListOf<PriceBandQuery>()

        reader(workshop = WorkshopTier.LOCAL, spy = asked).read(BILL)

        assertEquals(WorkshopTier.LOCAL, asked.first().workshopTier)
    }

    /** Unanswered falls to the middle tier — the same choice the question makes for "not sure". */
    @Test
    fun `an unanswered workshop question falls to the middle tier`() = runTest {
        val asked = mutableListOf<PriceBandQuery>()

        reader(workshop = null, spy = asked).read(BILL)

        assertEquals(WorkshopTier.MULTI_BRAND, asked.first().workshopTier)
    }

    /**
     * No city means no band to ask for. The check still runs on what it knows without one, so
     * the owner gets a thinner screen rather than none — and is not charged for it, because
     * without a band there was nothing to compare.
     */
    @Test
    fun `a missing city leaves the bill unchecked rather than failing`() = runTest {
        val charger = CountingCharger()

        val result = reader(charger = charger, city = null).read(BILL)

        assertEquals(1, result.getOrNull()!!.unchecked.size)
        assertEquals(0, charger.charges)
    }

    /** The bill being checked is not part of its own history. */
    @Test
    fun `the entry under check is left out of its own history`() = runTest {
        // The same job, on the same entry. Counted as history it would flag itself as a repeat.
        val result = reader(lines = listOf("AC service" to 2_400)).read(BILL)

        assertIs<Reason.AboveBand>(result.getOrNull()!!.flagged.single().reason)
    }

    /** The pool is fed at the same moment the check is charged, and not before. */
    @Test
    fun `a charged check gives its prices back`() = runTest {
        val given = mutableListOf<PriceObservation>()

        reader(contributor = { given += it }).read(BILL)

        assertEquals(1, given.size)
        assertEquals("ac_service", given.single().categorySlug)
    }

    /** Nothing was checked, so there is nothing true to file. */
    @Test
    fun `a check that named nothing gives nothing back`() = runTest {
        val given = mutableListOf<PriceObservation>()

        reader(
            contributor = { given += it },
            lines = listOf("Throttle body cleaning" to 1_800),
        ).read(BILL)

        assertTrue(given.isEmpty())
    }

    /**
     * The screen re-reads on every visit — a fresh ViewModel per navigation. Without the
     * ledger, three back-and-forths burn three checks and file the same prices three times.
     */
    @Test
    fun `a second look at the same bill is free`() = runTest {
        val charger = CountingCharger()
        val given = mutableListOf<PriceObservation>()
        val ledger = OnceLedger()
        val reader = reader(charger = charger, contributor = { given += it }, ledger = ledger)

        reader.read(BILL)
        reader.read(BILL)
        reader.read(BILL)

        assertEquals(1, charger.charges)
        assertEquals(1, given.size, "and the pool is not fed the same bill three times")
    }

    /**
     * The screen masks a result the owner has not paid for. Charging for it takes money for
     * nothing shown — and the tally moves even with no credit, because the charger counts a
     * scan it could not take a credit for.
     */
    @Test
    fun `a masked result is not charged for`() = runTest {
        val charger = CountingCharger()

        reader(charger = charger, unlocked = false).read(BILL)

        assertEquals(0, charger.charges)
    }

    /** And once they have paid, the next read is the one that charges. */
    @Test
    fun `the read after unlocking is charged`() = runTest {
        val charger = CountingCharger()
        val ledger = OnceLedger()
        reader(charger = charger, ledger = ledger, unlocked = false).read(BILL)

        reader(charger = charger, ledger = ledger, unlocked = true).read(BILL)

        assertEquals(1, charger.charges)
    }

    /* ------------------------------ Fixtures ------------------------------ */

    private fun reader(
        charger: ScanCharger = CountingCharger(),
        lines: List<Pair<String, Int>> = listOf("AC service" to 2_400),
        /** No such entry — the id points at nothing. */
        missing: Boolean = false,
        city: String? = "Srinagar",
        workshop: WorkshopTier? = WorkshopTier.AUTHORISED,
        spy: MutableList<PriceBandQuery>? = null,
        contributor: FairnessContributor = FairnessContributor {},
        ledger: BillCheckLedger = OnceLedger(),
        /** Whether the owner may see the result. A masked one is never charged for. */
        unlocked: Boolean = true,
    ): LoggedBillCheckReader {
        val stored = if (missing) null else entryOf(lines)
        return LoggedBillCheckReader(
            entries = FakeEntries(stored),
            cars = FakeCars(car()),
            cities = { city },
            questionnaire = FakeQuestionnaire(workshop),
            check = CheckBillPriceUseCase(
                matcher = BillLineMatcher(),
                bands = FakeBands(spy),
                intervals = { emptyMap<String, com.hopcape.odo.core.domain.schedule.ServiceInterval>().right() },
            ),
            charger = charger,
            contributor = contributor,
            ledger = ledger,
            unlocked = { unlocked },
        )
    }

    private class CountingCharger : ScanCharger {
        var charges = 0
            private set

        override suspend fun chargeOne() {
            charges++
        }
    }

    private class FakeBands(private val spy: MutableList<PriceBandQuery>?) : PriceBandRepository {
        override suspend fun bandFor(query: PriceBandQuery): Either<DomainError, PriceBand?> {
            spy?.add(query)
            return PriceBand(
                low = rupees(1_400),
                typical = rupees(1_600),
                high = rupees(1_800),
                sampleSize = 0,
                scope = BenchmarkScope.MODELLED,
                basis = BenchmarkBasis.MODELLED,
            ).right()
        }
    }

    /**
     * Everything the reader does not touch throws.
     *
     * A fake that answered every method would hide the reader's real surface; one that throws
     * documents it, and a later slice that starts writing through here fails loudly rather
     * than silently doing nothing.
     */
    private class FakeEntries(private val stored: ServiceLogEntry?) : ServiceLogRepository {
        override fun observe(id: ServiceLogId): Flow<ServiceLogEntry?> = flowOf(stored)

        override fun observe(carId: CarId): Flow<List<ServiceLogEntry>> =
            flowOf(listOfNotNull(stored))

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

    /** True the first time it is asked about a bill, exactly like the real table's index. */
    private class OnceLedger : BillCheckLedger {
        private val claimed = mutableSetOf<String>()
        override suspend fun claim(billId: String) = claimed.add(billId)
    }

    private class FakeQuestionnaire(private val workshop: WorkshopTier?) : QuestionnaireRepository {
        override suspend fun save(key: QuestionKey, values: Set<String>) = Unit.right()

        override fun observe() = flowOf(emptyList<QuestionAnswer>())

        override suspend fun answersFor(key: QuestionKey) =
            listOfNotNull(
                workshop?.let {
                    QuestionAnswer(
                        key = QuestionKeys.Workshop,
                        value = it.name,
                        answeredAt = Instant.fromEpochSeconds(0),
                    )
                },
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

        fun entryOf(lines: List<Pair<String, Int>>) = ServiceLogEntry.create(
            id = ServiceLogId(BILL),
            carId = CarId("car-1"),
            ownerId = OwnerId("owner-1"),
            serviceDate = LocalDate(2026, 8, 12),
            odometerKm = 12_000,
            totalAmountPaise = 18_400 * 100L,
            today = LocalDate(2026, 8, 12),
            lineItems = lines.map { (label, rupees) ->
                ServiceLogLineItemDraft(
                    label = label,
                    category = ServiceCategory.OTHER,
                    amountPaise = rupees * 100L,
                )
            },
        ).getOrNull()!!
    }
}
