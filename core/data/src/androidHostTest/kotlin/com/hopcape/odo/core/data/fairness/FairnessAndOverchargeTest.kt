package com.hopcape.odo.core.data.fairness

import com.hopcape.odo.core.data.sync.silentSyncTelemetry
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.data.owner.ProfileCityProvider
import com.hopcape.odo.core.data.owner.SqlDelightProfileLocalDataSource
import com.hopcape.odo.core.data.sync.SyncRunner
import com.hopcape.odo.core.data.sync.SyncStatus
import com.hopcape.odo.core.domain.fairness.model.FairnessConfidence
import com.hopcape.odo.core.domain.fairness.model.FairnessOutcome
import com.hopcape.odo.core.domain.fairness.model.FairnessQuery
import com.hopcape.odo.core.domain.fairness.model.FairnessQueryItem
import com.hopcape.odo.core.domain.fairness.model.OverchargeReason
import com.hopcape.odo.core.domain.fairness.model.OverchargeReport
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogId
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.sync.SyncReason
import com.hopcape.odo.core.sync.SyncScheduler
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class FairnessAndOverchargeTest {

    /* ------------------------- fixtures ------------------------- */

    private object NoopLogger : Logger {
        override fun log(
            level: LogLevel,
            tag: String,
            event: String,
            traceContext: TraceContext?,
            fields: Map<String, Any?>,
        ) = Unit
        override fun flush() = Unit
    }

    private class FakeSpan(
        override val spanId: String,
        override val traceId: String,
        override val parentSpanId: String?,
        override val name: String,
    ) : Span {
        override fun setAttribute(key: String, value: Any?): Span = this
    }

    private object NoopTracer : PerformanceTracer {
        override fun startSpan(name: String, traceId: String, parentSpanId: String?): Span =
            FakeSpan("span", traceId, parentSpanId, name)
        override fun endSpan(span: Span) = Unit
        override fun flush() = Unit
    }

    private object NoopCrash : CrashRecorder {
        override fun recordNonFatal(throwable: Throwable, customKeys: Map<String, Any?>) = Unit
        override fun leaveBreadcrumb(tag: String, message: String) = Unit
        override fun setCustomKey(key: String, value: Any?) = Unit
        override fun setUserId(userId: String?) = Unit
    }

    private val telemetry = DataTelemetry(NoopLogger, NoopTracer, NoopCrash)

    private fun newDb(): OdoDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OdoDatabase.Schema.create(driver)
        return OdoDatabase(driver)
    }

    /* ------------------------- fairness ------------------------- */

    @Test
    fun benchmarks_comeBackKeyedByCategory() = runTest {
        val repo = FairnessRepositoryImpl(FakeFairnessRemoteDataSource(), telemetry)

        val estimates = repo.estimates(setOf(ServiceCategory.BRAKES, ServiceCategory.OIL_CHANGE), "Pune")

        assertEquals(2, estimates.size)
        val brakes = assertNotNull(estimates[ServiceCategory.BRAKES])
        assertEquals(340_000L, brakes.cityAverage.paise)
        assertEquals("Pune", brakes.city)
    }

    @Test
    fun aCategoryWithNoBenchmark_isAbsentRatherThanNull() = runTest {
        val repo = FairnessRepositoryImpl(FakeFairnessRemoteDataSource(), telemetry)

        val estimates = repo.estimates(setOf(ServiceCategory.ELECTRICAL), "Pune")

        assertTrue(estimates.isEmpty(), "absent means no data — the caller does one lookup, not two")
    }

    @Test
    fun aThinlySampledBenchmark_reportsLowConfidence() = runTest {
        val repo = FairnessRepositoryImpl(FakeFairnessRemoteDataSource(), telemetry)

        val ac = assertNotNull(repo.estimates(setOf(ServiceCategory.AC), "Pune")[ServiceCategory.AC])

        // The PRD's guardrail: under five data points there is no confident verdict.
        assertEquals(FairnessConfidence.LOW, ac.confidence)
        // …which is exactly when the range has to be there: it is all the screen can show.
        val range = assertNotNull(ac.range)
        assertTrue(range.low.paise <= ac.cityAverage.paise && ac.cityAverage.paise <= range.high.paise)
    }

    @Test
    fun aHalfRange_isNoRangeAtAll() = runTest {
        val halfPriced = object : FairnessRemoteDataSource {
            override suspend fun estimates(categories: List<String>, city: String) = listOf(
                FairnessEstimateDto(
                    category = ServiceCategory.BRAKES.name,
                    city = city,
                    cityAveragePaise = 340_000,
                    sampleSize = 24,
                    p25Paise = 290_000,
                    p75Paise = null,
                ),
            )
        }

        val brakes = assertNotNull(
            FairnessRepositoryImpl(halfPriced, telemetry).estimates(setOf(ServiceCategory.BRAKES), "Pune")[ServiceCategory.BRAKES],
        )

        assertNull(brakes.range, "one end of a range cannot be drawn")
    }

    @Test
    fun aBackwardsRange_isDropped() = runTest {
        val backwards = object : FairnessRemoteDataSource {
            override suspend fun estimates(categories: List<String>, city: String) = listOf(
                FairnessEstimateDto(
                    category = ServiceCategory.BRAKES.name,
                    city = city,
                    cityAveragePaise = 340_000,
                    sampleSize = 24,
                    p25Paise = 395_000,
                    p75Paise = 290_000,
                ),
            )
        }

        val brakes = assertNotNull(
            FairnessRepositoryImpl(backwards, telemetry).estimates(setOf(ServiceCategory.BRAKES), "Pune")[ServiceCategory.BRAKES],
        )

        assertNull(brakes.range)
    }

    @Test
    fun theAnalyzer_judgesAQueryAgainstTheSameBenchmarks() = runTest {
        val analyzer = RepositoryFairnessAnalyzer(FairnessRepositoryImpl(FakeFairnessRemoteDataSource(), telemetry))

        val report = analyzer.analyze(
            FairnessQuery(
                city = "Pune",
                items = listOf(
                    FairnessQueryItem(
                        label = "Oil change",
                        category = ServiceCategory.OIL_CHANGE,
                        amount = Amount.of(280_000).getOrNull()!!,
                    ),
                ),
            ),
        )

        val over = assertIs<FairnessOutcome.Over>(report.outcome)
        assertEquals(70_000L, over.by.paise)
    }

    @Test
    fun theAnalyzer_claimsNothingWithoutABenchmark() = runTest {
        val analyzer = RepositoryFairnessAnalyzer(FairnessRepositoryImpl(FakeFairnessRemoteDataSource(), telemetry))

        val report = analyzer.analyze(
            FairnessQuery(
                city = "Pune",
                items = listOf(
                    FairnessQueryItem(
                        label = "Rewiring",
                        category = ServiceCategory.ELECTRICAL,
                        amount = Amount.of(280_000).getOrNull()!!,
                    ),
                ),
            ),
        )

        assertEquals(FairnessOutcome.NoBenchmark, report.outcome)
    }

    @Test
    fun askingForNothing_doesNotHitTheSource() = runTest {
        var calls = 0
        val counting = object : FairnessRemoteDataSource {
            override suspend fun estimates(categories: List<String>, city: String): List<FairnessEstimateDto> {
                calls++
                return emptyList()
            }
        }

        FairnessRepositoryImpl(counting, telemetry).estimates(emptySet(), "Pune")

        assertEquals(0, calls)
    }

    @Test
    fun aFailingSource_yieldsNoVerdictRatherThanAnError() = runTest {
        val broken = object : FairnessRemoteDataSource {
            override suspend fun estimates(categories: List<String>, city: String): List<FairnessEstimateDto> =
                error("RPC down")
        }

        val estimates = FairnessRepositoryImpl(broken, telemetry).estimates(setOf(ServiceCategory.BRAKES), "Pune")

        assertTrue(estimates.isEmpty(), "'we don't know' is a legitimate answer; throwing at the caller is not")
    }

    /* ------------------------- overcharge reports ------------------------- */

    private fun OdoDatabase.seedLog(id: String = "log-1", ownerId: String = "owner-1") {
        serviceLogQueries.insertServiceLog(
            id = id, carId = "car-1", ownerId = ownerId, serviceDate = "2026-06-15",
            odometerKm = 50_000, totalAmountPaise = 330_000, workshopName = null, notes = null,
            source = "MANUAL", billId = null, billPhotoPath = null, fairnessSnapshot = null,
            now = "2026-07-30T10:00:00Z", syncStatus = SyncStatus.PENDING.name,
        )
    }

    /** Records what the repository asked the scheduler for. */
    private class RecordingScheduler : SyncScheduler {
        val requested = mutableListOf<SyncReason>()
        override fun scheduleStartupSync() = Unit
        override fun requestSync(reason: SyncReason) { requested += reason }
    }

    private fun overchargeRepo(
        db: OdoDatabase,
        scheduler: SyncScheduler = RecordingScheduler(),
    ): OverchargeReportRepositoryImpl {
        val fixedClock = object : Clock { override fun now() = Instant.parse("2026-07-30T11:00:00Z") }
        return OverchargeReportRepositoryImpl(
            local = SqlDelightOverchargeReportLocalDataSource(
                database = db,
                clock = fixedClock,
                dispatcher = Dispatchers.Unconfined,
            ),
            telemetry = telemetry,
            idGenerator = IdGenerator { "report-1" },
            scheduler = scheduler,
            runner = SyncRunner(
                entity = SyncEntity.OVERCHARGE_REPORTS,
                table = OverchargeReportSyncTable(database = db, remote = FakeOverchargeRemoteDataSource()),
                database = db,
                telemetry = silentSyncTelemetry(),
                clock = fixedClock,
            ),
        )
    }

    @Test
    fun aReport_isStoredPendingWithTheOwnerTakenFromTheEntry() = runTest {
        val db = newDb().apply { seedLog(ownerId = "owner-7") }

        val result = overchargeRepo(db).submit(
            OverchargeReport(
                logId = ServiceLogId("log-1"),
                reason = OverchargeReason.ABOVE_MARKET_RATE,
                category = ServiceCategory.BRAKES,
                note = "quoted 2x",
            ),
        )

        assertTrue(result.isRight(), "expected Right but was $result")
        val stored = db.overchargeReportQueries.selectByServiceLog("log-1").executeAsOne()
        assertEquals("report-1", stored.id)
        assertEquals(OverchargeReason.ABOVE_MARKET_RATE.name, stored.reason)
        assertEquals(ServiceCategory.BRAKES.name, stored.category)
        assertEquals("quoted 2x", stored.note)
        // Ownership is derived from the entry, never asserted by the client.
        assertEquals("owner-7", stored.owner_id)
        // Nothing can push it yet, so it waits.
        assertEquals(SyncStatus.PENDING.name, stored.sync_status)
    }

    @Test
    fun aFiledReport_asksForASync() = runTest {
        val db = newDb().apply { seedLog() }
        val scheduler = RecordingScheduler()

        overchargeRepo(db, scheduler).submit(
            OverchargeReport(logId = ServiceLogId("log-1"), reason = OverchargeReason.UNNECESSARY_PARTS),
        )

        assertEquals(listOf(SyncReason.LocalWrite), scheduler.requested)
    }

    @Test
    fun aRejectedReport_doesNotAskForASync() = runTest {
        val db = newDb()
        val scheduler = RecordingScheduler()

        overchargeRepo(db, scheduler).submit(
            OverchargeReport(logId = ServiceLogId("ghost"), reason = OverchargeReason.WORK_NOT_DONE),
        )

        assertTrue(scheduler.requested.isEmpty())
    }

    @Test
    fun reportingAnUnknownEntry_isServiceLogNotFound() = runTest {
        val db = newDb()

        val result = overchargeRepo(db).submit(
            OverchargeReport(logId = ServiceLogId("ghost"), reason = OverchargeReason.WORK_NOT_DONE),
        )

        assertEquals(DomainError.ServiceLogNotFound, result.leftOrNull())
        assertTrue(db.overchargeReportQueries.selectByServiceLog("ghost").executeAsList().isEmpty())
    }

    /* ------------------------- city ------------------------- */

    @Test
    fun cityIsNullUntilTheOwnerSetsOne() = runTest {
        val db = newDb()
        db.profileQueries.insertProfile(
            id = "owner-1", fullName = "Rahul", onboardingGoal = null, onboardingCompletedAt = null,
            city = null, email = null, avatarPath = null, now = "2026-07-30T10:00:00Z", syncStatus = SyncStatus.PENDING.name,
        )

        assertNull(cityProvider(db).currentCity())
    }

    @Test
    fun cityIsReadBackOnceSet() = runTest {
        val db = newDb()
        db.profileQueries.insertProfile(
            id = "owner-1", fullName = "Rahul", onboardingGoal = null, onboardingCompletedAt = null,
            city = "Pune", email = null, avatarPath = null, now = "2026-07-30T10:00:00Z", syncStatus = SyncStatus.PENDING.name,
        )

        assertEquals("Pune", cityProvider(db).currentCity())
    }

    @Test
    fun withNoProfileAtAll_cityIsNullRatherThanAFailure() = runTest {
        assertNull(cityProvider(newDb()).currentCity())
    }

    private fun cityProvider(db: OdoDatabase) = ProfileCityProvider(
        local = SqlDelightProfileLocalDataSource(database = db, dispatcher = Dispatchers.Unconfined),
        telemetry = telemetry,
    )
}
