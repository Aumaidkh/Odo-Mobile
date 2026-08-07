package com.hopcape.odo.core.data.health

import com.hopcape.odo.core.data.sync.silentSyncTelemetry
import com.hopcape.odo.core.data.health.FakeHealthScoreRemoteDataSource
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.data.sync.SyncRunner
import com.hopcape.odo.core.data.sync.SyncStatus
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.health.analysis.HealthScoreCalculator
import com.hopcape.odo.core.domain.health.model.HealthFactor
import com.hopcape.odo.core.domain.health.model.HealthFactorKind
import com.hopcape.odo.core.domain.health.model.HealthScore
import com.hopcape.odo.core.domain.health.model.HealthSnapshot
import com.hopcape.odo.core.domain.health.model.HealthSnapshotId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.sync.SyncEntity
import com.hopcape.odo.core.sync.SyncReason
import com.hopcape.odo.core.sync.SyncScheduler
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class HealthScoreRepositoryImplTest {

    private val carId = CarId("car-1")
    private val ownerId = OwnerId("owner-1")

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

    private class RecordingCrash : CrashRecorder {
        val nonFatals = mutableListOf<Throwable>()
        override fun recordNonFatal(throwable: Throwable, customKeys: Map<String, Any?>) {
            nonFatals += throwable
        }
        override fun leaveBreadcrumb(tag: String, message: String) = Unit
        override fun setCustomKey(key: String, value: Any?) = Unit
        override fun setUserId(userId: String?) = Unit
    }

    private class FixedClock(private val instant: Instant) : Clock {
        override fun now(): Instant = instant
    }

    private class RecordingScheduler : SyncScheduler {
        val requested = mutableListOf<SyncReason>()
        override fun scheduleStartupSync() = Unit
        override fun requestSync(reason: SyncReason) { requested += reason }
    }

    private fun newDb(): OdoDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OdoDatabase.Schema.create(driver)
        return OdoDatabase(driver)
    }

    private fun repo(
        db: OdoDatabase,
        crash: CrashRecorder = RecordingCrash(),
        now: String = "2026-08-01T10:00:00Z",
        scheduler: SyncScheduler = RecordingScheduler(),
    ) = HealthScoreRepositoryImpl(
        local = SqlDelightHealthScoreLocalDataSource(database = db, clock = FixedClock(Instant.parse(now))),
        telemetry = DataTelemetry(logger = NoopLogger, tracer = NoopTracer, crash = crash),
        scheduler = scheduler,
        runner = SyncRunner(
            entity = SyncEntity.HEALTH_SCORES,
            table = HealthScoreSyncTable(database = db, remote = FakeHealthScoreRemoteDataSource(), carId = { null }),
            database = db,
            telemetry = silentSyncTelemetry(),
            clock = FixedClock(Instant.parse(now)),
        ),
    )

    private fun score(maintenance: Int = 28, documentation: Int = 24, cost: Int = 14, history: Int = 8) =
        HealthScore(
            factors = listOf(
                HealthFactor.of(HealthFactorKind.MAINTENANCE, maintenance),
                HealthFactor.of(HealthFactorKind.DOCUMENTATION, documentation),
                HealthFactor.of(HealthFactorKind.COST_EFFICIENCY, cost),
                HealthFactor.of(HealthFactorKind.HISTORY, history),
            ),
        )

    private fun snapshot(
        id: String = "snap-1",
        car: CarId = carId,
        computedAt: String = "2026-08-01T10:00:00Z",
        score: HealthScore = score(),
        algoVersion: String = HealthScoreCalculator.RULES_VERSION,
    ) = HealthSnapshot(
        id = HealthSnapshotId(id),
        carId = car,
        ownerId = ownerId,
        score = score,
        computedAt = Instant.parse(computedAt),
        algoVersion = algoVersion,
    )

    /* ------------------------- tests ------------------------- */

    @Test
    fun record_storesTheBreakdownAndReadsItBack() = runTest {
        val db = newDb()
        val repository = repo(db)

        val stored = repository.record(snapshot())

        assertTrue(stored.isRight(), "expected Right but was $stored")
        val latest = assertNotNull(repository.latest(carId))
        assertEquals("snap-1", latest.id.value)
        assertEquals(74, latest.score.total)
        assertEquals(listOf(28, 24, 14, 8), latest.score.factors.map { it.earned })
        assertEquals(Instant.parse("2026-08-01T10:00:00Z"), latest.computedAt)
    }

    @Test
    fun record_landsPendingForTheSyncEngine() = runTest {
        val db = newDb()
        repo(db).record(snapshot())

        val row = db.healthScoreQueries.selectLatest(carId.value).executeAsOne()
        assertEquals(SyncStatus.PENDING.name, row.sync_status)
        assertEquals("2026-08-01T10:00:00Z", row.created_at)
        assertEquals("rule-v1", row.algo_version)
    }

    @Test
    fun record_asksForASync() = runTest {
        val scheduler = RecordingScheduler()

        repo(newDb(), scheduler = scheduler).record(snapshot())

        assertEquals(listOf(SyncReason.LocalWrite), scheduler.requested)
    }

    @Test
    fun latest_isTheNewestSnapshotNotTheLastWritten() = runTest {
        val db = newDb()
        val repository = repo(db)

        // Written newest-first, which is what a backfill would do.
        repository.record(snapshot(id = "new", computedAt = "2026-08-01T10:00:00Z", score = score(maintenance = 30)))
        repository.record(snapshot(id = "old", computedAt = "2026-06-01T10:00:00Z"))

        assertEquals("new", repository.latest(carId)?.id?.value)
    }

    @Test
    fun latest_isNullForACarWithNoHistory() = runTest {
        assertNull(repo(newDb()).latest(CarId("never-scored")))
    }

    @Test
    fun latest_ignoresAnotherCarsHistory() = runTest {
        val db = newDb()
        val repository = repo(db)
        repository.record(snapshot(id = "other", car = CarId("car-2")))

        assertNull(repository.latest(carId))
    }

    @Test
    fun latestOnOrBefore_picksTheNewestSnapshotThatIsOldEnough() = runTest {
        val db = newDb()
        val repository = repo(db)
        repository.record(snapshot(id = "june", computedAt = "2026-06-15T10:00:00Z", score = score(maintenance = 20)))
        repository.record(snapshot(id = "july", computedAt = "2026-07-01T10:00:00Z", score = score(maintenance = 24)))
        repository.record(snapshot(id = "today", computedAt = "2026-08-01T10:00:00Z"))

        val baseline = repository.latestOnOrBefore(carId, Instant.parse("2026-07-02T10:00:00Z"))

        assertEquals("july", baseline?.id?.value)
    }

    @Test
    fun latestOnOrBefore_includesASnapshotTakenExactlyThen() = runTest {
        val db = newDb()
        val repository = repo(db)
        repository.record(snapshot(id = "july", computedAt = "2026-07-01T10:00:00Z"))

        assertEquals("july", repository.latestOnOrBefore(carId, Instant.parse("2026-07-01T10:00:00Z"))?.id?.value)
    }

    @Test
    fun latestOnOrBefore_isNullWhenNothingIsThatOld() = runTest {
        val db = newDb()
        val repository = repo(db)
        repository.record(snapshot(computedAt = "2026-08-01T10:00:00Z"))

        // The screen hides the delta rather than comparing against a newer number.
        assertNull(repository.latestOnOrBefore(carId, Instant.parse("2026-07-01T10:00:00Z")))
    }

    @Test
    fun record_reportsABrokenDatabaseInsteadOfThrowing() = runTest {
        // A driver with no schema: every statement fails the way a missing table would.
        val db = OdoDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY))
        val crash = RecordingCrash()

        val result = repo(db, crash = crash).record(snapshot())

        assertTrue(result.isLeft(), "expected Left but was $result")
        assertEquals(1, crash.nonFatals.size)
    }

    @Test
    fun latest_answersNullOnABrokenDatabase() = runTest {
        val db = OdoDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY))
        val crash = RecordingCrash()

        assertNull(repo(db, crash = crash).latest(carId))
        assertEquals(1, crash.nonFatals.size)
    }

    @Test
    fun observeHistory_isOldestFirst() = runTest {
        val db = newDb()
        val repository = repo(db)
        repository.record(snapshot(id = "july", computedAt = "2026-07-01T10:00:00Z"))
        repository.record(snapshot(id = "june", computedAt = "2026-06-01T10:00:00Z"))
        repository.record(snapshot(id = "august", computedAt = "2026-08-01T10:00:00Z"))

        // Written out of order, read in the order the moves are worked out in.
        val history = repository.observeHistory(carId).first()

        assertEquals(listOf("june", "july", "august"), history.map { it.id.value })
    }

    @Test
    fun observeHistory_carriesTheRulesVersionEachScoreWasTakenUnder() = runTest {
        val db = newDb()
        val repository = repo(db)
        repository.record(snapshot(id = "old-rules", algoVersion = "rule-v0"))
        repository.record(snapshot(id = "new-rules", computedAt = "2026-08-02T10:00:00Z"))

        val history = repository.observeHistory(carId).first()

        // The stamp travels with the row rather than being rewritten by whatever this build
        // computes — a comparison across the two is what the timeline has to refuse.
        assertEquals(listOf("rule-v0", HealthScoreCalculator.RULES_VERSION), history.map { it.algoVersion })
    }

    @Test
    fun observeHistory_ignoresAnotherCarsSnapshots() = runTest {
        val db = newDb()
        val repository = repo(db)
        repository.record(snapshot(id = "mine"))
        repository.record(snapshot(id = "theirs", car = CarId("car-2")))

        assertEquals(listOf("mine"), repository.observeHistory(carId).first().map { it.id.value })
    }

    @Test
    fun observeHistory_isEmptyOnABrokenDatabase() = runTest {
        val db = OdoDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY))
        val crash = RecordingCrash()

        // The timeline drops its score events and still renders everything else.
        assertEquals(emptyList(), repo(db, crash = crash).observeHistory(carId).first())
        assertEquals(1, crash.nonFatals.size)
    }
}
