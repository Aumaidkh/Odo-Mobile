package com.hopcape.odo.feature.dashboard.domain.usecase

import com.hopcape.odo.core.domain.activity.model.ActivityEvent
import com.hopcape.odo.core.domain.alerts.model.CarAttention
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.insight.model.CarInsight
import com.hopcape.odo.feature.dashboard.FakeCarRepository
import com.hopcape.odo.feature.dashboard.FakeCurrentCityProvider
import com.hopcape.odo.feature.dashboard.FakeCurrentOdometerProvider
import com.hopcape.odo.feature.dashboard.FakeDocumentRepository
import com.hopcape.odo.feature.dashboard.FakeFuelPriceProvider
import com.hopcape.odo.feature.dashboard.FakeHealthScoreRepository
import com.hopcape.odo.feature.dashboard.FakeOwnerProfileRepository
import com.hopcape.odo.feature.dashboard.FakeServiceLogRepository
import com.hopcape.odo.feature.dashboard.FixedClock
import com.hopcape.odo.feature.dashboard.TEST_CAR
import com.hopcape.odo.feature.dashboard.overcharged
import com.hopcape.odo.feature.dashboard.paise
import com.hopcape.odo.feature.dashboard.testDocument
import com.hopcape.odo.feature.dashboard.testEntry
import com.hopcape.odo.feature.dashboard.testFuelPrice
import com.hopcape.odo.feature.dashboard.testProfile
import com.hopcape.odo.feature.dashboard.currentOdometerFrom
import com.hopcape.odo.feature.dashboard.km
import com.hopcape.odo.feature.dashboard.testReading
import com.hopcape.odo.feature.dashboard.testSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ObserveHomeUseCaseTest {

    private val delhi = TimeZone.of("Asia/Kolkata")

    private fun useCase(
        cars: FakeCarRepository = FakeCarRepository(),
        logs: FakeServiceLogRepository = FakeServiceLogRepository(),
        documents: FakeDocumentRepository = FakeDocumentRepository(),
        scores: FakeHealthScoreRepository = FakeHealthScoreRepository(),
        owners: FakeOwnerProfileRepository = FakeOwnerProfileRepository(),
        city: FakeCurrentCityProvider = FakeCurrentCityProvider(),
        fuelPrices: FakeFuelPriceProvider = FakeFuelPriceProvider(),
        // No trips in play by default — mirrors the pre-trip-aware behaviour so existing
        // assertions keep reading `logs`'s own timeline.
        currentOdometer: FakeCurrentOdometerProvider? = null,
    ) = ObserveHomeUseCase(
        cars = cars,
        logs = logs,
        documents = documents,
        scores = scores,
        owners = owners,
        city = city,
        fuelPrices = fuelPrices,
        currentOdometer = currentOdometer ?: currentOdometerFrom(logs),
        clock = FixedClock(),
        timeZone = delhi,
    )

    /* ------------------------- header ------------------------- */

    @Test
    fun greeting_readsTheOwnersName() = runTest {
        val snapshot = useCase().invoke(TEST_CAR).first()

        assertEquals("Rahul", snapshot.ownerName)
        assertEquals("Swift VXI", snapshot.car?.modelName?.removePrefix("Maruti Suzuki "))
    }

    @Test
    fun odometer_isTheTripAwareAggregateNotJustTheRawReading() = runTest {
        // A counted auto-trip has moved the car past its last manual log — the header must
        // show that aggregate, and the score's maintenance factor must be measured against
        // it too.
        val logs = FakeServiceLogRepository(
            entries = listOf(testEntry("l1", LocalDate(2026, 6, 1), odometerKm = 50_000)),
            readings = listOf(testReading(LocalDate(2026, 6, 1), 50_000)),
        )
        val snapshot = useCase(
            logs = logs,
            currentOdometer = FakeCurrentOdometerProvider(km(50_120)),
        ).invoke(TEST_CAR).first()

        assertEquals(50_120, snapshot.odometer?.km)
    }

    @Test
    fun greeting_survivesAProfileWithNoNameYet() = runTest {
        val snapshot = useCase(owners = FakeOwnerProfileRepository(testProfile(name = null)))
            .invoke(TEST_CAR).first()

        assertNull(snapshot.ownerName)
    }

    /* ------------------------- new user gate ------------------------- */

    @Test
    fun newUser_untilSomethingIsLoggedOrFiled() = runTest {
        val snapshot = useCase().invoke(TEST_CAR).first()

        assertTrue(snapshot.isNewUser)
        assertEquals(1, snapshot.setup.doneCount)
        assertTrue(snapshot.setup.carAdded)
        assertFalse(snapshot.setup.billScanned)
        assertFalse(snapshot.setup.documentsFiled)
    }

    /** One document is enough for a score, even with nothing serviced. */
    @Test
    fun newUser_endsAtTheFirstDocument() = runTest {
        val snapshot = useCase(documents = FakeDocumentRepository(listOf(testDocument())))
            .invoke(TEST_CAR).first()

        assertFalse(snapshot.isNewUser)
    }

    /** So is one service log, even a self-reported one with no bill behind it. */
    @Test
    fun newUser_endsAtTheFirstServiceLog() = runTest {
        val snapshot = useCase(
            logs = FakeServiceLogRepository(listOf(testEntry("l1", LocalDate(2026, 6, 1)))),
        ).invoke(TEST_CAR).first()

        assertFalse(snapshot.isNewUser)
        assertFalse(snapshot.setup.billScanned)
    }

    @Test
    fun checklist_ticksTheBillStepOnlyWhenOneIsAttached() = runTest {
        val snapshot = useCase(
            logs = FakeServiceLogRepository(
                listOf(testEntry("l1", LocalDate(2026, 6, 1), verified = true)),
            ),
            documents = FakeDocumentRepository(listOf(testDocument())),
        ).invoke(TEST_CAR).first()

        assertTrue(snapshot.setup.billScanned)
        assertTrue(snapshot.setup.isComplete)
        assertEquals(3, snapshot.setup.doneCount)
    }

    /* ------------------------- score ------------------------- */

    @Test
    fun score_isComputedFromTodaysDataNotReadBack() = runTest {
        val snapshot = useCase(
            documents = FakeDocumentRepository(
                listOf(
                    testDocument(DocumentType.INSURANCE, expiresOn = LocalDate(2027, 3, 31)),
                    testDocument(DocumentType.PUC, expiresOn = LocalDate(2027, 1, 31)),
                ),
            ),
            // A stale snapshot from before those papers were filed; the dial must ignore it.
            scores = FakeHealthScoreRepository(listOf(testSnapshot("old", "2026-01-01T10:00:00Z", total = 5))),
        ).invoke(TEST_CAR).first()

        assertEquals(22, snapshot.score.total)
    }

    @Test
    fun scoreDelta_measuresAgainstASnapshotAMonthOld() = runTest {
        val snapshot = useCase(
            documents = FakeDocumentRepository(listOf(testDocument(DocumentType.INSURANCE))),
            scores = FakeHealthScoreRepository(
                listOf(
                    testSnapshot("month-ago", "2026-06-20T10:00:00Z", total = 4),
                    // Taken yesterday, so it is not a month's baseline and must be ignored.
                    testSnapshot("yesterday", "2026-07-31T10:00:00Z", total = 11),
                ),
            ),
        ).invoke(TEST_CAR).first()

        assertEquals(12, snapshot.score.total)
        assertEquals(8, snapshot.scoreDelta)
    }

    @Test
    fun scoreDelta_isAbsentWithoutAnOldEnoughSnapshot() = runTest {
        val snapshot = useCase(
            scores = FakeHealthScoreRepository(
                listOf(testSnapshot("yesterday", "2026-07-31T10:00:00Z", total = 40)),
            ),
        ).invoke(TEST_CAR).first()

        assertNull(snapshot.scoreDelta)
    }

    /* ------------------------- cost ------------------------- */

    @Test
    fun cost_coversTheLastQuarterAndTrendsAgainstTheOneBefore() = runTest {
        val logs = FakeServiceLogRepository(
            entries = listOf(
                // Previous quarter (31 Jan – 1 May): Rs. 4,000 over 38,000 → 42,000 km.
                testEntry("old", LocalDate(2026, 3, 1), odometerKm = 40_000, paise = 400_000),
                // This quarter (2 May – 1 Aug): Rs. 8,000 over 42,000 → 44,000 km.
                testEntry("new", LocalDate(2026, 7, 1), odometerKm = 44_000, paise = 800_000),
            ),
            readings = listOf(
                testReading(LocalDate(2026, 2, 1), 38_000),
                testReading(LocalDate(2026, 3, 1), 40_000),
                testReading(LocalDate(2026, 5, 1), 42_000),
                testReading(LocalDate(2026, 7, 1), 44_000),
            ),
        )

        val snapshot = useCase(logs = logs).invoke(TEST_CAR).first()

        // Rs. 8,000 over 2,000 km this quarter, against Rs. 4,000 over 4,000 km before it.
        assertEquals(paise(400), snapshot.cost.perKm)
        assertEquals(300, snapshot.costTrend?.percentChange)
    }

    @Test
    fun cost_withoutAFuelPriceCoversMaintenanceOnly() = runTest {
        val logs = FakeServiceLogRepository(
            entries = listOf(testEntry("l1", LocalDate(2026, 7, 1), odometerKm = 44_000, paise = 800_000)),
            readings = listOf(
                testReading(LocalDate(2026, 5, 1), 42_000),
                testReading(LocalDate(2026, 7, 1), 44_000),
            ),
        )

        val withoutPrice = useCase(logs = logs).invoke(TEST_CAR).first()
        val withPrice = useCase(logs = logs, fuelPrices = FakeFuelPriceProvider(testFuelPrice()))
            .invoke(TEST_CAR).first()

        assertEquals(0L, withoutPrice.cost.fuelSpend.paise)
        assertTrue(withPrice.cost.fuelSpend.paise > 0)
    }

    /* ------------------------- savings ------------------------- */

    @Test
    fun savings_addUpEveryStoredOvercharge() = runTest {
        val snapshot = useCase(
            logs = FakeServiceLogRepository(
                listOf(
                    testEntry("a", LocalDate(2026, 6, 1), fairness = overcharged(by = 70_000)),
                    testEntry("b", LocalDate(2026, 7, 1), fairness = overcharged(by = 140_000)),
                    testEntry("c", LocalDate(2026, 7, 5)),
                ),
            ),
        ).invoke(TEST_CAR).first()

        assertEquals(paise(210_000), snapshot.savings.overchargeTotal)
        assertEquals(2, snapshot.savings.overchargesCaught)
    }

    /* ------------------------- attention & insight ------------------------- */

    @Test
    fun attention_raisesTheLapsedPaper() = runTest {
        val snapshot = useCase(
            documents = FakeDocumentRepository(
                listOf(testDocument(DocumentType.PUC, expiresOn = LocalDate(2026, 7, 25))),
            ),
        ).invoke(TEST_CAR).first()

        val lapsed = assertIs<CarAttention.DocumentLapsed>(snapshot.attention)
        assertEquals(DocumentType.PUC, lapsed.type)
    }

    @Test
    fun attention_isAbsentWhenNothingIsDue() = runTest {
        val snapshot = useCase(
            documents = FakeDocumentRepository(listOf(testDocument(DocumentType.INSURANCE))),
            logs = FakeServiceLogRepository(listOf(testEntry("l1", LocalDate(2026, 6, 1)))),
        ).invoke(TEST_CAR).first()

        assertNull(snapshot.attention)
    }

    @Test
    fun insight_namesTheGapThatCostsTheOwnerMost() = runTest {
        val snapshot = useCase(
            logs = FakeServiceLogRepository(
                listOf(
                    testEntry("a", LocalDate(2026, 5, 1)),
                    testEntry("b", LocalDate(2026, 6, 1)),
                ),
            ),
        ).invoke(TEST_CAR).first()

        assertEquals(2, assertIs<CarInsight.NoBillsAttached>(snapshot.insight).serviceCount)
    }

    /* ------------------------- recent activity ------------------------- */

    @Test
    fun recent_isTheNewestEventOnTheCarsFeed() = runTest {
        val snapshot = useCase(
            logs = FakeServiceLogRepository(
                listOf(
                    testEntry("older", LocalDate(2026, 5, 1)),
                    testEntry("newest", LocalDate(2026, 7, 12)),
                ),
            ),
            documents = FakeDocumentRepository(listOf(testDocument(addedOn = LocalDate(2026, 6, 1)))),
        ).invoke(TEST_CAR).first()

        val service = assertIs<ActivityEvent.Service>(snapshot.recent)
        assertEquals(LocalDate(2026, 7, 12), service.date)
    }

    /** The car's own milestone is still an event, so a fresh record is not blank. */
    @Test
    fun recent_fallsBackToTheCarBeingAdded() = runTest {
        val snapshot = useCase().invoke(TEST_CAR).first()

        assertNotNull(snapshot.recent)
        assertIs<ActivityEvent.CarAdded>(snapshot.recent)
    }

    @Test
    fun recent_isAbsentOnACarWithNoDatesAtAll() = runTest {
        val snapshot = useCase(cars = FakeCarRepository(null)).invoke(TEST_CAR).first()

        assertNull(snapshot.recent)
        assertFalse(snapshot.setup.carAdded)
    }
}
