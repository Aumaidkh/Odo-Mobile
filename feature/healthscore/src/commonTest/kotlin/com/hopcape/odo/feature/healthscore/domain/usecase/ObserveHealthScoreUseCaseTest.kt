package com.hopcape.odo.feature.healthscore.domain.usecase

import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.entitlement.EntitlementSource
import com.hopcape.odo.core.domain.entitlement.Entitlements
import com.hopcape.odo.core.domain.entitlement.Plan
import com.hopcape.odo.core.domain.entitlement.ProFeature
import com.hopcape.odo.core.domain.health.model.HealthBand
import com.hopcape.odo.core.domain.health.model.HealthFactorKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ObserveHealthScoreUseCaseTest {

    /** 1 Aug 2026, 10:00 UTC — the day every expectation here is read on. */
    private val now = Instant.parse("2026-08-01T10:00:00Z")

    private val entries = listOf(
        testEntry("log-1", LocalDate(2026, 1, 15), odometerKm = 36_000, verified = true),
        testEntry("log-2", LocalDate(2026, 6, 20), odometerKm = 43_000, verified = true),
    )
    private val readings = listOf(
        reading(LocalDate(2026, 1, 15), km = 36_000),
        reading(LocalDate(2026, 6, 20), km = 43_000),
    )
    private val documents = listOf(
        testDocument(DocumentType.INSURANCE, LocalDate(2027, 3, 31)),
        testDocument(DocumentType.PUC, LocalDate(2026, 12, 1)),
        testDocument(DocumentType.RC, expiresOn = null),
    )

    private fun useCase(
        logs: FakeServiceLogRepository = FakeServiceLogRepository(entries, readings),
        docs: FakeDocumentRepository = FakeDocumentRepository(documents),
        snapshots: FakeHealthScoreRepository = FakeHealthScoreRepository(),
        isPro: Boolean = true,
        // No trips in play by default — mirrors the pre-trip-aware behaviour so existing
        // assertions keep reading `logs`'s own timeline.
        currentOdometer: FakeCurrentOdometerProvider? = null,
    ) = ObserveHealthScoreUseCase(
        logs = logs,
        documents = docs,
        snapshots = snapshots,
        entitlements = entitlementsOf(isPro),
        currentOdometer = currentOdometer ?: currentOdometerFrom(logs),
        clock = FixedClock(now),
        timeZone = TimeZone.UTC,
    )

    @Test
    fun scoresTheCarFromItsLogsPapersAndReadings() = runTest {
        val summary = useCase().invoke(TEST_CAR).first()

        // Serviced on time and twice this year (35), all three papers in force (30),
        // nothing benchmarked (0), both entries bill-backed on a clean timeline (11).
        assertEquals(35, summary.score.factorFor(HealthFactorKind.MAINTENANCE)?.earned)
        assertEquals(30, summary.score.factorFor(HealthFactorKind.DOCUMENTATION)?.earned)
        assertEquals(0, summary.score.factorFor(HealthFactorKind.COST_EFFICIENCY)?.earned)
        assertEquals(11, summary.score.factorFor(HealthFactorKind.HISTORY)?.earned)
        assertEquals(76, summary.score.total)
        assertEquals(HealthBand.GOOD, summary.score.band)
    }

    @Test
    fun withoutAnOldEnoughSnapshot_thereIsNoDelta() = runTest {
        val summary = useCase().invoke(TEST_CAR).first()

        assertNull(summary.delta)
    }

    @Test
    fun theDeltaIsMeasuredAgainstTheScoreFromAMonthAgo() = runTest {
        val snapshots = FakeHealthScoreRepository(
            mutableListOf(testSnapshot(score = testScore(maintenance = 25, documentation = 30, cost = 0, history = 11))),
        )

        val summary = useCase(snapshots = snapshots).invoke(TEST_CAR).first()

        // 76 now against 66 a month ago.
        assertEquals(10, summary.delta)
    }

    @Test
    fun theBaselineIsAskedForThirtyDaysBack() = runTest {
        val snapshots = FakeHealthScoreRepository()

        useCase(snapshots = snapshots).invoke(TEST_CAR).first()

        assertEquals(listOf(Instant.parse("2026-07-02T10:00:00Z")), snapshots.baselineAsks)
    }

    @Test
    fun aSnapshotFromLastWeekIsNotAMonthAgo() = runTest {
        val snapshots = FakeHealthScoreRepository(
            mutableListOf(testSnapshot(computedAt = "2026-07-25T10:00:00Z", score = testScore())),
        )

        // Too recent to be "this month" — the badge stays hidden rather than quoting a
        // week's movement as a month's.
        assertNull(useCase(snapshots = snapshots).invoke(TEST_CAR).first().delta)
    }

    @Test
    fun theEntitlementTravelsWithTheScore() = runTest {
        assertTrue(useCase(isPro = true).invoke(TEST_CAR).first().entitlements.has(ProFeature.HEALTH_BREAKDOWN))
        assertEquals(false, useCase(isPro = false).invoke(TEST_CAR).first().entitlements.has(ProFeature.HEALTH_BREAKDOWN))
    }

    @Test
    fun aNewCarScoresNothingRatherThanBeingGivenTheBenefitOfTheDoubt() = runTest {
        val summary = useCase(
            logs = FakeServiceLogRepository(emptyList(), listOf(reading(LocalDate(2026, 8, 1), 45_000))),
            docs = FakeDocumentRepository(emptyList()),
        ).invoke(TEST_CAR).first()

        assertEquals(0, summary.score.total)
        assertEquals(HealthBand.POOR, summary.score.band)
        assertEquals(HealthFactorKind.MAINTENANCE, summary.score.biggestGap?.kind)
    }

    @Test
    fun uploadingADocumentRescoresWithoutRevisitingTheScreen() = runTest {
        val docs = FakeDocumentRepository(emptyList())
        val scores = mutableListOf<Int>()

        val flow = useCase(docs = docs).invoke(TEST_CAR)
        scores += flow.first().score.total
        docs.emit(documents)
        scores += flow.first().score.total

        assertEquals(listOf(46, 76), scores)
    }

    @Test
    fun theMaintenanceFactorReadsTheTripAwareAggregate_notJustTheRawReadings() = runTest {
        // The provider already folds counted auto-trips on top of the manual timeline —
        // this proves the maintenance factor is measured against that aggregate.
        val summary = useCase(
            currentOdometer = FakeCurrentOdometerProvider(reading(LocalDate(2026, 7, 30), 56_000).odometer),
        ).invoke(TEST_CAR).first()

        // Same 13,000 km-past-service outcome as `anOdometerUpdateFromTheGarageRescoresToo`,
        // but driven entirely by the aggregate rather than by a new reading in the fake.
        assertEquals(20, summary.score.factorFor(HealthFactorKind.MAINTENANCE)?.earned)
    }

    @Test
    fun anOdometerUpdateFromTheGarageRescoresToo() = runTest {
        // The car's own reading moves without a service log, which is the case a
        // logs-only stream would miss.
        val logs = FakeServiceLogRepository(entries, readings)
        val flow = useCase(logs = logs).invoke(TEST_CAR)

        val before = flow.first().score.factorFor(HealthFactorKind.MAINTENANCE)?.earned
        logs.emit(entries, readings + reading(LocalDate(2026, 7, 30), km = 56_000))
        val after = flow.first().score.factorFor(HealthFactorKind.MAINTENANCE)?.earned

        // 13,000 km since the last service is 3,000 km past the interval, so the
        // interval points drop from 20 to 5 while the cadence points stay.
        assertEquals(35, before)
        assertEquals(20, after)
    }
}

/** An [EntitlementSource] that stands still on one plan. */
private fun entitlementsOf(isPro: Boolean) = object : EntitlementSource {
    override fun observe() = flowOf(Entitlements(if (isPro) Plan.PRO else Plan.FREE))
    override suspend fun refresh() = Unit
}
